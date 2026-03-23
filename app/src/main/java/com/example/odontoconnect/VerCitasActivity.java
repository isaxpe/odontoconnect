package com.example.odontoconnect;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class VerCitasActivity extends AppCompatActivity {

    private LinearLayout contenedorSolicitudes;
    private TextView tvMensajeVacio;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ver_citas);

        contenedorSolicitudes = findViewById(R.id.contenedorSolicitudes);
        tvMensajeVacio        = findViewById(R.id.tvMensajeVacio);
        db   = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        cargarSolicitudesPendientes();
    }

    private void cargarSolicitudesPendientes() {
        if (mAuth.getCurrentUser() == null) return;
        String miUidDoctor = mAuth.getCurrentUser().getUid();

        // CORRECCIÓN: busca "pendiente" en minúscula, coherente con AgendarCitaActivity
        db.collection("citas")
                .whereEqualTo("idDoctor", miUidDoctor)
                .whereEqualTo("estado", "pendiente")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    contenedorSolicitudes.removeAllViews();

                    if (queryDocumentSnapshots.isEmpty()) {
                        tvMensajeVacio.setText("No tienes solicitudes pendientes.");
                        tvMensajeVacio.setVisibility(View.VISIBLE);
                    } else {
                        tvMensajeVacio.setVisibility(View.GONE);
                        for (DocumentSnapshot citaDoc : queryDocumentSnapshots.getDocuments()) {
                            crearTarjetaDeCita(citaDoc);
                        }
                    }
                })
                .addOnFailureListener(e -> tvMensajeVacio.setText("Error al cargar las citas."));
    }

    private void crearTarjetaDeCita(DocumentSnapshot citaDoc) {
        String idCita      = citaDoc.getId();
        String fecha       = citaDoc.getString("fecha");
        String hora        = citaDoc.getString("hora");
        String tratamiento = citaDoc.getString("tratamiento");
        String idPaciente  = citaDoc.getString("idPaciente");

        View tarjeta = getLayoutInflater().inflate(R.layout.item_solicitud, null);

        TextView tvInfo    = tarjeta.findViewById(R.id.tvInfoCita);
        Button btnAceptar  = tarjeta.findViewById(R.id.btnAceptar);
        Button btnRechazar = tarjeta.findViewById(R.id.btnRechazar);

        tvInfo.setText("Tratamiento: " + tratamiento + "\nFecha: " + fecha + " a las " + hora);

        // CORRECCIÓN: estado siempre en minúsculas
        btnAceptar.setOnClickListener(v -> {
            actualizarEstadoCita(idCita, "aceptada");
            contenedorSolicitudes.removeView(tarjeta);
        });

        btnRechazar.setOnClickListener(v -> {
            // Al rechazar, incrementamos el contador de faltas del paciente
            actualizarEstadoCita(idCita, "rechazada");
            if (idPaciente != null) {
                incrementarFaltasPaciente(idPaciente);
            }
            contenedorSolicitudes.removeView(tarjeta);
        });

        contenedorSolicitudes.addView(tarjeta);
    }

    private void actualizarEstadoCita(String idCita, String nuevoEstado) {
        db.collection("citas").document(idCita)
                .update("estado", nuevoEstado)   // minúscula siempre
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Cita " + nuevoEstado, Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show()
                );
    }

    private void incrementarFaltasPaciente(String idPaciente) {
        db.collection("usuarios").document(idPaciente)
                .update("faltas", FieldValue.increment(1))
                .addOnSuccessListener(aVoid -> {
                    // Verificar si el paciente ya acumuló 3 faltas para bloquearlo
                    db.collection("usuarios").document(idPaciente).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    Long faltas = doc.getLong("faltas");
                                    if (faltas != null && faltas >= 3) {
                                        doc.getReference().update("bloqueado", true);
                                    }
                                }
                            });
                });
    }
}
