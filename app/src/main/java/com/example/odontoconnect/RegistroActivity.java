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
        if (password.length() < 6) {
            Toast.makeText(this,
                    "La contraseña debe tener al menos 6 caracteres",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnRegistrarse.setEnabled(false);
        btnRegistrarse.setText("Registrando...");

        mAuth.createUserWithEmailAndPassword(correo, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) guardarEnFirestore(user.getUid(), nombre, correo);
                    } else {
                        btnRegistrarse.setEnabled(true);
                        btnRegistrarse.setText("REGISTRARME");
                        String err = task.getException() != null
                                ? task.getException().getMessage() : "";
                        String msg = traducirError(err);
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String traducirError(String err) {
        if (err == null) return "Error desconocido";
        if (err.contains("already in use"))
            return "Ese correo ya está registrado";
        if (err.contains("badly formatted"))
            return "El formato del correo no es válido";
        if (err.contains("network"))
            return "Sin conexión a internet";
        return "Error al registrarse. Intenta de nuevo";
    }

    private void guardarEnFirestore(String uid, String nombre, String correo) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("uid",              uid);
        datos.put("nombre",           nombre);
        datos.put("Nombre",           nombre);
        datos.put("correo",           correo);
        datos.put("rol",              "paciente");
        datos.put("faltas",           0);
        datos.put("bloqueado",        false);
        datos.put("nivelPenalizacion", 0);
        datos.put("paciente_id",      uid.substring(0, 6).toUpperCase());

        db.collection("usuarios").document(uid).set(datos)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "¡Registro exitoso!",
                            Toast.LENGTH_SHORT).show();
                    // NUEVO: al registrarse, va al cuestionario de bienvenida
                    // para completar su perfil médico básico
                    Intent intent = new Intent(this,
                            CuestionarioBienvenidaActivity.class);
                    intent.putExtra("uid", uid);
                    intent.putExtra("esNuevo", true);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnRegistrarse.setEnabled(true);
                    btnRegistrarse.setText("REGISTRARME");
                    Toast.makeText(this, "Error al guardar datos: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
}
