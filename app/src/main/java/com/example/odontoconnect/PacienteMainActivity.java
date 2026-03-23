package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

public class PacienteMainActivity extends AppCompatActivity {

    private TextView tvNombrePaciente, tvIdPaciente, tvEstadoPaciente,
                     tvContadorFaltas, tvDetallesProximaCita;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paciente_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tvNombrePaciente      = findViewById(R.id.tvNombrePaciente);
        tvIdPaciente          = findViewById(R.id.tvIdPaciente);
        tvEstadoPaciente      = findViewById(R.id.tvEstadoPaciente);
        tvContadorFaltas      = findViewById(R.id.tvContadorFaltas);
        tvDetallesProximaCita = findViewById(R.id.tvDetallesProximaCita);

        configurarBotones();

        FirebaseUser usuarioActual = mAuth.getCurrentUser();
        if (usuarioActual != null) {
            String uid = usuarioActual.getUid();
            cargarPerfilPaciente(uid, usuarioActual.getEmail());
            cargarProximaCita(uid);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationPaciente);
        if (bottomNav != null) {
            // La navegación inferior puede configurarse aquí si se necesita
        }
    }

    private void configurarBotones() {
        View btnAgendar = findViewById(R.id.btnAbrirAgendar);
        if (btnAgendar != null) {
            btnAgendar.setOnClickListener(v ->
                    startActivity(new Intent(PacienteMainActivity.this, AgendarCitaActivity.class))
            );
        }

        View btnMisCitas = findViewById(R.id.btnMisCitas);
        if (btnMisCitas != null) {
            btnMisCitas.setOnClickListener(v ->
                    startActivity(new Intent(PacienteMainActivity.this, MisCitasActivity.class))
            );
        }
    }

    private void cargarPerfilPaciente(String uid, String email) {
        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        mostrarDatos(documentSnapshot);
                    } else {
                        crearPerfilPorDefecto(uid, email);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al conectar con la base de datos", Toast.LENGTH_SHORT).show()
                );
    }

    private void mostrarDatos(DocumentSnapshot document) {
        String nombre = document.getString("Nombre");
        if (nombre == null) nombre = document.getString("nombre");

        String idPaciente = document.getString("paciente_id");
        if (idPaciente == null) idPaciente = document.getId().substring(0, 6).toUpperCase();

        Long faltas = document.getLong("faltas");
        Boolean bloqueado = document.getBoolean("bloqueado");

        if (faltas == null) faltas = 0L;
        if (bloqueado == null) bloqueado = false;

        tvNombrePaciente.setText(nombre != null ? nombre : "Paciente");
        tvIdPaciente.setText("ID: " + idPaciente);

        if (bloqueado || faltas >= 3) {
            tvEstadoPaciente.setText("BLOQUEADO");
            tvEstadoPaciente.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            tvContadorFaltas.setText("⚠️ Faltas: " + faltas + " / 3 — Cuenta suspendida");
            tvContadorFaltas.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

            // Deshabilitar botón Agendar si está bloqueado
            View btnAgendar = findViewById(R.id.btnAbrirAgendar);
            if (btnAgendar != null) {
                btnAgendar.setEnabled(false);
                btnAgendar.setAlpha(0.5f);
            }
        } else {
            tvEstadoPaciente.setText("ACTIVO");
            tvEstadoPaciente.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            tvContadorFaltas.setText("Contador de Faltas: " + faltas + " / 3");
            tvContadorFaltas.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    private void cargarProximaCita(String uid) {
        if (tvDetallesProximaCita == null) return;

        // CORRECCIÓN: busca citas "aceptada" del paciente (minúscula)
        db.collection("citas")
                .whereEqualTo("idPaciente", uid)
                .whereEqualTo("estado", "aceptada")
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot proximaCita = queryDocumentSnapshots.getDocuments().get(0);
                        String tratamiento = proximaCita.getString("tratamiento");
                        String fecha       = proximaCita.getString("fecha");
                        String hora        = proximaCita.getString("hora");
                        tvDetallesProximaCita.setText(
                                (tratamiento != null ? tratamiento : "Cita") +
                                "\n📅 " + (fecha != null ? fecha : "--") +
                                "  🕐 " + (hora != null ? hora : "--")
                        );
                        tvDetallesProximaCita.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    } else {
                        tvDetallesProximaCita.setText("Aún no tienes citas aceptadas.");
                    }
                });
    }

    private void crearPerfilPorDefecto(String uid, String email) {
        Map<String, Object> nuevoPaciente = new HashMap<>();
        nuevoPaciente.put("nombre", "Paciente Nuevo");
        nuevoPaciente.put("Nombre", "Paciente Nuevo");
        nuevoPaciente.put("email", email);
        nuevoPaciente.put("rol", "paciente");          // campo requerido por LoginActivity
        nuevoPaciente.put("faltas", 0);
        nuevoPaciente.put("bloqueado", false);
        nuevoPaciente.put("paciente_id", uid.substring(0, 6).toUpperCase());

        db.collection("usuarios").document(uid).set(nuevoPaciente)
                .addOnSuccessListener(aVoid -> cargarPerfilPaciente(uid, email));
    }
}
