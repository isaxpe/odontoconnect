package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnIngresar;
    private TextView tvRegistrarse;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnIngresar = findViewById(R.id.btnIngresar);
        tvRegistrarse = findViewById(R.id.tvRegistrarse);

        btnIngresar.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();

            if (!email.isEmpty() && !pass.isEmpty()) {
                iniciarSesion(email, pass);
            } else {
                Toast.makeText(LoginActivity.this, "Por favor, completa los campos", Toast.LENGTH_SHORT).show();
            }
        });

        tvRegistrarse.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegistroActivity.class))
        );
    }

    private void iniciarSesion(String email, String pass) {
        btnIngresar.setEnabled(false);
        btnIngresar.setText("Ingresando...");

        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(authResult -> {
                    guardarFcmToken(); // Guardar token antes de navegar
                    verificarRol();
                })
                .addOnFailureListener(e -> {
                    resetBoton();
                    Toast.makeText(LoginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // Obtiene el token FCM del dispositivo y lo guarda en Firestore
    // Esto es lo que permite que las notificaciones push lleguen a este teléfono
    private void guardarFcmToken() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    db.collection("usuarios").document(uid)
                            .update("fcmToken", token)
                            .addOnFailureListener(e ->
                                    // Si falla el update (ej: documento no existe aún), usamos set con merge
                                    db.collection("usuarios").document(uid)
                                            .set(new java.util.HashMap<String, Object>() {{
                                                put("fcmToken", token);
                                            }}, com.google.firebase.firestore.SetOptions.merge())
                            );
                })
                .addOnFailureListener(e ->
                        // No es crítico si falla, la app sigue funcionando, solo sin notificaciones
                        android.util.Log.w("FCM", "No se pudo obtener el token FCM: " + e.getMessage())
                );
    }

    private void verificarRol() {
        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();

        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String rol = documentSnapshot.getString("rol");

                        if (rol != null) {
                            rol = rol.toLowerCase().trim();

                            if (rol.equals("odontologo") || rol.equals("doctor")) {
                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                finish();
                            } else if (rol.equals("paciente")) {
                                startActivity(new Intent(LoginActivity.this, PacienteMainActivity.class));
                                finish();
                            } else {
                                Toast.makeText(this, "Rol no reconocido: " + rol, Toast.LENGTH_SHORT).show();
                                mAuth.signOut();
                                resetBoton();
                            }
                        } else {
                            Toast.makeText(this, "Este usuario no tiene un rol asignado", Toast.LENGTH_SHORT).show();
                            mAuth.signOut();
                            resetBoton();
                        }
                    } else {
                        mAuth.signOut();
                        resetBoton();
                        Toast.makeText(this, "El perfil no existe en Firestore", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    resetBoton();
                    Toast.makeText(this, "Error de base de datos", Toast.LENGTH_SHORT).show();
                });
    }

    private void resetBoton() {
        btnIngresar.setEnabled(true);
        btnIngresar.setText("Ingresar");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() != null) {
            guardarFcmToken(); // Actualizar token también al reabrir la app
            verificarRol();
        }
    }
}