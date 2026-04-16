package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegistroActivity extends AppCompatActivity {

    private EditText etRegistroNombre, etRegistroCorreo, etRegistroPassword;
    private Button btnRegistrarse;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        etRegistroNombre   = findViewById(R.id.etRegistroNombre);
        etRegistroCorreo   = findViewById(R.id.etRegistroCorreo);
        etRegistroPassword = findViewById(R.id.etRegistroPassword);
        btnRegistrarse     = findViewById(R.id.btnRegistrarse);

        btnRegistrarse.setOnClickListener(v -> registrarNuevoUsuario());
    }

    private void registrarNuevoUsuario() {
        String nombre   = etRegistroNombre.getText().toString().trim();
        String correo   = etRegistroCorreo.getText().toString().trim();
        String password = etRegistroPassword.getText().toString().trim();

        if (nombre.isEmpty() || correo.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor llena todos los campos",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            Toast.makeText(this, "El correo no tiene un formato valido",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "La contrasena debe tener al menos 6 caracteres",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnRegistrarse.setEnabled(false);
        btnRegistrarse.setText("Registrando...");

        // Paso 1: crear cuenta en Firebase Auth
        mAuth.createUserWithEmailAndPassword(correo, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        guardarEnFirestore(user.getUid(), nombre, correo);
                    } else {
                        resetBoton();
                        Toast.makeText(this, "Error inesperado. Intenta de nuevo.",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    resetBoton();
                    Toast.makeText(this,
                            traducirError(e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void guardarEnFirestore(String uid, String nombre, String correo) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("uid",               uid);
        datos.put("nombre",            nombre);
        datos.put("Nombre",            nombre);
        datos.put("correo",            correo);
        datos.put("rol",               "paciente");
        datos.put("faltas",            0);
        datos.put("bloqueado",         false);
        datos.put("nivelPenalizacion", 0);
        datos.put("paciente_id",       uid.substring(0, 6).toUpperCase());
        datos.put("correoVerificado",  false);

        db.collection("usuarios").document(uid).set(datos)
                .addOnSuccessListener(aVoid -> enviarVerificacion(uid, nombre, correo))
                .addOnFailureListener(e -> {
                    resetBoton();
                    Toast.makeText(this,
                            "Error al guardar datos: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void enviarVerificacion(String uid, String nombre, String correo) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            // Si por alguna razon no hay usuario, ir directo al cuestionario
            irACuestionario(uid);
            return;
        }

        user.sendEmailVerification()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this,
                            "Revisa tu correo para verificar tu cuenta",
                            Toast.LENGTH_LONG).show();

                    // Ir a pantalla de verificacion
                    Intent intent = new Intent(this, VerificacionCorreoActivity.class);
                    intent.putExtra("uid",    uid);
                    intent.putExtra("correo", correo);
                    intent.putExtra("nombre", nombre);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    // Si falla el envio del correo, ir directo al cuestionario
                    // para no bloquear al usuario
                    Toast.makeText(this,
                            "Cuenta creada. No se pudo enviar el correo de verificacion.",
                            Toast.LENGTH_LONG).show();
                    irACuestionario(uid);
                });
    }

    private void irACuestionario(String uid) {
        Intent intent = new Intent(this, CuestionarioBienvenidaActivity.class);
        intent.putExtra("uid",     uid);
        intent.putExtra("esNuevo", true);
        startActivity(intent);
        finish();
    }

    private void resetBoton() {
        btnRegistrarse.setEnabled(true);
        btnRegistrarse.setText("REGISTRARME");
    }

    private String traducirError(String err) {
        if (err == null) return "Error desconocido";
        if (err.contains("already in use"))
            return "Ese correo ya esta registrado";
        if (err.contains("badly formatted"))
            return "El formato del correo no es valido";
        if (err.contains("network"))
            return "Sin conexion a internet";
        if (err.contains("weak-password"))
            return "La contrasena es muy debil";
        return "Error al registrarse. Intenta de nuevo";
    }
}
