package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnIngresar;
    private TextView tvRegistrarse, tvOlvidePassword;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        etEmail           = findViewById(R.id.etEmail);
        etPassword        = findViewById(R.id.etPassword);
        btnIngresar       = findViewById(R.id.btnIngresar);
        tvRegistrarse     = findViewById(R.id.tvRegistrarse);
        tvOlvidePassword  = findViewById(R.id.tvOlvidePassword);

        btnIngresar.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pass  = etPassword.getText().toString().trim();

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Por favor completa todos los campos",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            iniciarSesion(email, pass);
        });

        tvRegistrarse.setOnClickListener(v ->
                startActivity(new Intent(this, RegistroActivity.class))
        );

        // NUEVO: olvidé mi contraseña
        if (tvOlvidePassword != null) {
            tvOlvidePassword.setOnClickListener(v -> {
                String email = etEmail.getText().toString().trim();
                if (email.isEmpty()) {
                    Toast.makeText(this,
                            "Escribe tu correo arriba para recuperar tu contraseña",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Recuperar contraseña")
                        .setMessage("Te enviaremos un correo a:\n" + email)
                        .setPositiveButton("Enviar", (d, w) ->
                                mAuth.sendPasswordResetEmail(email)
                                        .addOnSuccessListener(aVoid ->
                                                Toast.makeText(this,
                                                        "📧 Correo enviado. Revisa tu bandeja.",
                                                        Toast.LENGTH_LONG).show()
                                        )
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this,
                                                        "No encontramos ese correo registrado.",
                                                        Toast.LENGTH_SHORT).show()
                                        )
                        )
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        }
    }

    private void iniciarSesion(String email, String pass) {
        btnIngresar.setEnabled(false);
        btnIngresar.setText("Ingresando...");

        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(authResult -> verificarRol())
                .addOnFailureListener(e -> {
                    resetBoton();
                    // CORRECCIÓN: mensajes de error en español
                    String msg = traduzirError(e.getMessage());
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
    }

    private String traduzirError(String errorEn) {
        if (errorEn == null) return "Error desconocido";
        if (errorEn.contains("password")) return "Contraseña incorrecta";
        if (errorEn.contains("no user")) return "No existe cuenta con ese correo";
        if (errorEn.contains("badly formatted")) return "El formato del correo no es válido";
        if (errorEn.contains("network")) return "Sin conexión a internet";
        if (errorEn.contains("too many")) return "Demasiados intentos. Intenta más tarde";
        return "Error al iniciar sesión. Verifica tus datos";
    }

    private void verificarRol() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String rol = doc.getString("rol");
                        if (rol != null) {
                            rol = rol.toLowerCase().trim();
                            if (rol.equals("odontologo") || rol.equals("doctor")) {
                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                            } else if (rol.equals("paciente")) {
                                startActivity(new Intent(this, PacienteMainActivity.class));
                                finish();
                            } else {
                                Toast.makeText(this, "Rol no reconocido",
                                        Toast.LENGTH_SHORT).show();
                                mAuth.signOut();
                                resetBoton();
                            }
                        } else {
                            Toast.makeText(this, "Tu cuenta no tiene rol asignado",
                                    Toast.LENGTH_SHORT).show();
                            mAuth.signOut();
                            resetBoton();
                        }
                    } else {
                        mAuth.signOut();
                        resetBoton();
                        Toast.makeText(this, "Perfil no encontrado",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    resetBoton();
                    Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show();
                });
    }

    private void resetBoton() {
        btnIngresar.setEnabled(true);
        btnIngresar.setText("INICIAR SESIÓN");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() != null) verificarRol();
    }
}
