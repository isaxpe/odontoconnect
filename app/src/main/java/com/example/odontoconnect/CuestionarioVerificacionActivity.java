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

public class CuestionarioVerificacionActivity extends AppCompatActivity {

    private TextView tvPregunta1, tvPregunta2, tvPregunta3, tvPregunta4, tvPregunta5;
    private EditText etRespuesta1, etRespuesta2, etRespuesta3, etRespuesta4, etRespuesta5;
    private Button btnVerificar;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Respuestas correctas cargadas de Firebase
    private String rNombre    = "";
    private String rCorreo    = "";
    private String rId        = "";
    private String rRol       = "";
    private String rFaltas    = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cuestionario_verificacion);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        tvPregunta1  = findViewById(R.id.tvPregunta1);
        tvPregunta2  = findViewById(R.id.tvPregunta2);
        tvPregunta3  = findViewById(R.id.tvPregunta3);
        tvPregunta4  = findViewById(R.id.tvPregunta4);
        tvPregunta5  = findViewById(R.id.tvPregunta5);
        etRespuesta1 = findViewById(R.id.etRespuesta1);
        etRespuesta2 = findViewById(R.id.etRespuesta2);
        etRespuesta3 = findViewById(R.id.etRespuesta3);
        etRespuesta4 = findViewById(R.id.etRespuesta4);
        etRespuesta5 = findViewById(R.id.etRespuesta5);
        btnVerificar = findViewById(R.id.btnVerificar);

        // Preguntas fijas
        tvPregunta1.setText("1. ¿Cuál es tu nombre completo?");
        tvPregunta2.setText("2. ¿Cuál es tu correo electrónico registrado?");
        tvPregunta3.setText("3. ¿Cuál es tu ID de paciente? (6 caracteres)");
        tvPregunta4.setText("4. ¿Cuál es tu rol en la app? (paciente/odontologo)");
        tvPregunta5.setText("5. ¿Cuántas faltas tienes acumuladas?");

        cargarDatosDelPaciente();

        btnVerificar.setOnClickListener(v -> verificarRespuestas());
    }

    private void cargarDatosDelPaciente() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // Respuesta 1: nombre
                        String nombre = doc.getString("Nombre");
                        if (nombre == null) nombre = doc.getString("nombre");
                        rNombre = nombre != null ? nombre.trim().toLowerCase() : "";

                        // Respuesta 2: correo
                        String correo = doc.getString("correo");
                        if (correo == null && mAuth.getCurrentUser() != null) {
                            correo = mAuth.getCurrentUser().getEmail();
                        }
                        rCorreo = correo != null ? correo.trim().toLowerCase() : "";

                        // Respuesta 3: paciente_id
                        String pid = doc.getString("paciente_id");
                        if (pid == null) pid = uid.substring(0, 6).toUpperCase();
                        rId = pid.trim().toUpperCase();

                        // Respuesta 4: rol
                        String rol = doc.getString("rol");
                        rRol = rol != null ? rol.trim().toLowerCase() : "paciente";

                        // Respuesta 5: faltas
                        Long faltas = doc.getLong("faltas");
                        rFaltas = String.valueOf(faltas != null ? faltas : 0);
                    }
                });
    }

    private void verificarRespuestas() {
        String r1 = etRespuesta1.getText().toString().trim().toLowerCase();
        String r2 = etRespuesta2.getText().toString().trim().toLowerCase();
        String r3 = etRespuesta3.getText().toString().trim().toUpperCase();
        String r4 = etRespuesta4.getText().toString().trim().toLowerCase();
        String r5 = etRespuesta5.getText().toString().trim();

        if (r1.isEmpty() || r2.isEmpty() || r3.isEmpty() || r4.isEmpty() || r5.isEmpty()) {
            Toast.makeText(this, "Por favor responde todas las preguntas",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int correctas = 0;
        if (r1.equals(rNombre))  correctas++;
        if (r2.equals(rCorreo))  correctas++;
        if (r3.equals(rId))      correctas++;
        if (r4.equals(rRol))     correctas++;
        if (r5.equals(rFaltas))  correctas++;

        if (correctas >= 4) {
            // Pasó la verificación — ir a registrar familiar
            Toast.makeText(this,
                    "✅ Verificación exitosa (" + correctas + "/5 correctas)",
                    Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, RegistrarFamiliarActivity.class));
            finish();
        } else {
            // No pasó
            Toast.makeText(this,
                    "❌ Solo " + correctas + " de 5 correctas. Necesitas al menos 4 para continuar.",
                    Toast.LENGTH_LONG).show();

            // Limpiar respuestas para que intente de nuevo con los campos vacíos
            etRespuesta1.setText("");
            etRespuesta2.setText("");
            etRespuesta3.setText("");
            etRespuesta4.setText("");
            etRespuesta5.setText("");
        }
    }
}
