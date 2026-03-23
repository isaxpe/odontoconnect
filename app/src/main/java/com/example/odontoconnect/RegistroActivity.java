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
        db = FirebaseFirestore.getInstance();

        etRegistroNombre = findViewById(R.id.etRegistroNombre);
        etRegistroCorreo = findViewById(R.id.etRegistroCorreo);
        etRegistroPassword = findViewById(R.id.etRegistroPassword);
        btnRegistrarse = findViewById(R.id.btnRegistrarse);

        btnRegistrarse.setOnClickListener(v -> registrarNuevoUsuario());
    }

    private void registrarNuevoUsuario() {
        String nombre = etRegistroNombre.getText().toString().trim();
        String correo = etRegistroCorreo.getText().toString().trim();
        String password = etRegistroPassword.getText().toString().trim();

        if (nombre.isEmpty() || correo.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor, llena todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRegistrarse.setEnabled(false);
        btnRegistrarse.setText("Registrando...");

        mAuth.createUserWithEmailAndPassword(correo, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // CORRECCIÓN: todos los registros por esta pantalla son pacientes
                            guardarEnFirestore(user.getUid(), nombre, correo);
                        }
                    } else {
                        btnRegistrarse.setEnabled(true);
                        btnRegistrarse.setText("REGISTRARME");
                        Toast.makeText(this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void guardarEnFirestore(String uid, String nombre, String correo) {
        Map<String, Object> datosUsuario = new HashMap<>();
        datosUsuario.put("uid", uid);
        datosUsuario.put("nombre", nombre);
        datosUsuario.put("Nombre", nombre);          // guardamos ambas variantes para compatibilidad
        datosUsuario.put("correo", correo);
        datosUsuario.put("rol", "paciente");          // CORRECCIÓN: campo 'rol' requerido por LoginActivity
        datosUsuario.put("estado", "activo");
        datosUsuario.put("faltas", 0);
        datosUsuario.put("faltas_maximas", 3);
        datosUsuario.put("bloqueado", false);
        datosUsuario.put("paciente_id", uid.substring(0, 6).toUpperCase());

        db.collection("usuarios").document(uid).set(datosUsuario)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "¡Registro exitoso! Bienvenido a OdontoConnect", Toast.LENGTH_SHORT).show();
                    // CORRECCIÓN: redirige a PacienteMainActivity, NO a PacientesActivity (esa es del doctor)
                    startActivity(new Intent(this, PacienteMainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnRegistrarse.setEnabled(true);
                    btnRegistrarse.setText("REGISTRARME");
                    Toast.makeText(this, "Error al guardar en BD: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
