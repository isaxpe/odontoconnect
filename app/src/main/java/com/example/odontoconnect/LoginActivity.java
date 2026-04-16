package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

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
        db    = FirebaseFirestore.getInstance();

        etEmail       = findViewById(R.id.etEmail);
        etPassword    = findViewById(R.id.etPassword);
        btnIngresar   = findViewById(R.id.btnIngresar);
        tvRegistrarse = findViewById(R.id.tvRegistrarse);

        btnIngresar.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pass  = etPassword.getText().toString().trim();
            if (!email.isEmpty() && !pass.isEmpty()) {
                iniciarSesion(email, pass);
            } else {
                Toast.makeText(this, "Por favor completa los campos",
                        Toast.LENGTH_SHORT).show();
            }
        });

        tvRegistrarse.setOnClickListener(v ->
                startActivity(new Intent(this, RegistroActivity.class)));
    }

    private void iniciarSesion(String email, String pass) {
        btnIngresar.setEnabled(false);
        btnIngresar.setText("Ingresando...");

        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(authResult -> verificarRol())
                .addOnFailureListener(e -> {
                    resetBoton();
                    Toast.makeText(this,
                            traducirError(e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void verificarRol() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { resetBoton(); return; }

        String uid = user.getUid();

        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        mAuth.signOut();
                        resetBoton();
                        Toast.makeText(this, "El perfil no existe",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    String rol = doc.getString("rol");
                    if (rol == null) {
                        mAuth.signOut();
                        resetBoton();
                        Toast.makeText(this, "Usuario sin rol asignado",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    rol = rol.toLowerCase().trim();

                    if (rol.equals("odontologo") || rol.equals("doctor")) {
                        // Doctor: no necesita verificacion de correo
                        startActivity(new Intent(this, MainActivity.class));
                        finish();

                    } else if (rol.equals("paciente")) {
                        // FIX: verificar que el paciente verifico su correo
                        // Recargar estado del usuario para obtener isEmailVerified actualizado
                        user.reload().addOnSuccessListener(aVoid -> {
                            if (user.isEmailVerified()) {
                                // Correo verificado ✅ → entrar a la app
                                startActivity(new Intent(this, PacienteMainActivity.class));
                                finish();
                            } else {
                                // Correo NO verificado → mandar a verificar
                                mAuth.signOut();
                                resetBoton();
                                Toast.makeText(this,
                                        "Debes verificar tu correo antes de entrar.\n" +
                                        "Revisa tu bandeja de entrada.",
                                        Toast.LENGTH_LONG).show();

                                // Reenviar correo y mandar a pantalla de verificacion
                                user.sendEmailVerification();
                                Intent intent = new Intent(this,
                                        VerificacionCorreoActivity.class);
                                intent.putExtra("uid",    uid);
                                intent.putExtra("correo", doc.getString("correo"));
                                intent.putExtra("nombre", doc.getString("Nombre") != null
                                        ? doc.getString("Nombre")
                                        : doc.getString("nombre"));
                                startActivity(intent);
                            }
                        }).addOnFailureListener(e -> {
                            // Sin conexion: dejar pasar (mejor experiencia offline)
                            startActivity(new Intent(this, PacienteMainActivity.class));
                            finish();
                        });
                    } else {
                        mAuth.signOut();
                        resetBoton();
                        Toast.makeText(this, "Rol no reconocido: " + rol,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    resetBoton();
                    Toast.makeText(this, "Error de conexion. Intenta de nuevo.",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private String traducirError(String err) {
        if (err == null) return "Error desconocido";
        if (err.contains("no user record") || err.contains("identifier"))
            return "Correo no registrado";
        if (err.contains("password"))
            return "Contrasena incorrecta";
        if (err.contains("network"))
            return "Sin conexion a internet";
        if (err.contains("blocked") || err.contains("too many"))
            return "Demasiados intentos. Espera unos minutos.";
        return "Error al iniciar sesion";
    }

    private void resetBoton() {
        btnIngresar.setEnabled(true);
        btnIngresar.setText("Ingresar");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() != null) verificarRol();
    }
}
