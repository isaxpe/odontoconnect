package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class VerCitasActivity extends AppCompatActivity {

    private LinearLayout contenedorSolicitudes;
    private TextView tvMensajeVacio;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration listenerCitas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ver_citas);

        contenedorSolicitudes = findViewById(R.id.contenedorSolicitudes);
        tvMensajeVacio        = findViewById(R.id.tvMensajeVacio);
        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        cargarSolicitudesPendientes();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_inicio) {
                    startActivity(new Intent(this, MainActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                } else if (id == R.id.nav_agenda) {
                    startActivity(new Intent(this, AgendaActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                } else if (id == R.id.nav_pacientes) {
                    startActivity(new Intent(this, PacientesActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                } else if (id == R.id.nav_perfil) {
                    startActivity(new Intent(this, PerfilDoctorActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                }
                return false;
            });
        }
    }

    private void cargarSolicitudesPendientes() {
        if (mAuth.getCurrentUser() == null) return;
        String miUidDoctor = mAuth.getCurrentUser().getUid();

        listenerCitas = db.collection("citas")
                .whereEqualTo("idDoctor", miUidDoctor)
                .whereEqualTo("estado", "pendiente")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    contenedorSolicitudes.removeAllViews();

                    if (snap.isEmpty()) {
                        tvMensajeVacio.setText("✅ No tienes solicitudes pendientes.");
                        tvMensajeVacio.setVisibility(View.VISIBLE);
                    } else {
                        tvMensajeVacio.setVisibility(View.GONE);
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            crearTarjeta(doc);
                        }
                    }
                });
    }

    private void crearTarjeta(DocumentSnapshot citaDoc) {
        String idCita      = citaDoc.getId();
        String fecha       = citaDoc.getString("fecha");
        String hora        = citaDoc.getString("horaDisplay");
        if (hora == null) hora = citaDoc.getString("hora");
        String tratamiento = citaDoc.getString("tratamiento");
        String idPaciente  = citaDoc.getString("idPaciente");
        String estadoPago  = citaDoc.getString("estadoPago");

        Boolean triajeOk   = citaDoc.getBoolean("completado");
        String nivelDolor  = citaDoc.getString("nivelDolor");
        String primeraVis  = citaDoc.getString("primeraVisita");
        String medicamentos = citaDoc.getString("tomaMedicamentos");
        String descripcion = citaDoc.getString("descripcionMolestia");

        View tarjeta = LayoutInflater.from(this)
                .inflate(R.layout.item_solicitud, contenedorSolicitudes, false);

        TextView tvInfo       = tarjeta.findViewById(R.id.tvInfoCita);
        TextView tvTriaje     = tarjeta.findViewById(R.id.tvTriajeCita);
        TextView tvEstPago    = tarjeta.findViewById(R.id.tvEstadoPagoCita);
        MaterialButton btnVerComp = tarjeta.findViewById(R.id.btnVerComprobante);
        Button btnAceptar    = tarjeta.findViewById(R.id.btnAceptar);
        Button btnRechazar   = tarjeta.findViewById(R.id.btnRechazar);

        final String horaFinal  = hora;
        final String tratFinal  = tratamiento;
        final String fechaFinal = fecha;

        tvInfo.setText("🦷 " + (tratamiento != null ? tratamiento : "Cita") +
                "\n📅 " + (fecha != null ? fecha : "--") +
                "  🕐 " + (hora != null ? hora : "--") +
                "\n👤 Cargando...");

        // Cargar nombre del paciente
        if (idPaciente != null) {
            db.collection("usuarios").document(idPaciente).get()
                    .addOnSuccessListener(pac -> {
                        String nombre = pac.getString("Nombre");
                        if (nombre == null) nombre = pac.getString("nombre");
                        tvInfo.setText("🦷 " + (tratFinal != null ? tratFinal : "Cita") +
                                "\n📅 " + (fechaFinal != null ? fechaFinal : "--") +
                                "  🕐 " + (horaFinal != null ? horaFinal : "--") +
                                "\n👤 " + (nombre != null ? nombre : "Paciente"));
                    });
        }

        // Triaje
        if (Boolean.TRUE.equals(triajeOk)) {
            StringBuilder sb = new StringBuilder("📋 Triaje:\n");
            if (nivelDolor != null)   sb.append("• Dolor: ").append(nivelDolor).append("\n");
            if (primeraVis != null)   sb.append("• Visita: ").append(primeraVis).append("\n");
            if (medicamentos != null) sb.append("• Medicamentos: ").append(medicamentos);
            if (descripcion != null && !descripcion.isEmpty())
                sb.append("\n• Molestia: ").append(descripcion);
            tvTriaje.setText(sb.toString().trim());
        } else {
            tvTriaje.setText("⏳ Sin triaje completado.");
            tvTriaje.setTextColor(0xFF888888);
        }
        tvTriaje.setVisibility(View.VISIBLE);

        // Estado pago
        if ("pagado".equals(estadoPago)) {
            tvEstPago.setText("📤 Comprobante enviado — pendiente de revisión");
            tvEstPago.setVisibility(View.VISIBLE);
            btnVerComp.setVisibility(View.VISIBLE);
            btnVerComp.setOnClickListener(v -> {
                Intent intent = new Intent(this, ConfirmarPagoActivity.class);
                intent.putExtra("idCita", idCita);
                startActivity(intent);
            });
        } else if ("confirmado".equals(estadoPago)) {
            tvEstPago.setText("✅ Anticipo confirmado");
            tvEstPago.setBackgroundColor(0xFFE8F5E9);
            tvEstPago.setTextColor(0xFF2E7D32);
            tvEstPago.setVisibility(View.VISIBLE);
        } else if ("rechazado".equals(estadoPago)) {
            tvEstPago.setText("❌ Comprobante rechazado — paciente debe reenviar");
            tvEstPago.setBackgroundColor(0xFFFFEBEE);
            tvEstPago.setTextColor(0xFFB71C1C);
            tvEstPago.setVisibility(View.VISIBLE);
        } else {
            tvEstPago.setText("💰 Anticipo pendiente de pago");
            tvEstPago.setVisibility(View.VISIBLE);
        }

        // ── ACEPTAR con ntfy ──
        final String idPacienteFinal = idPaciente;
        btnAceptar.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Aceptar cita")
                        .setMessage("¿Confirmas aceptar esta solicitud?")
                        .setPositiveButton("Sí, aceptar", (d, w) -> {
                            db.collection("citas").document(idCita)
                                    .update("estado", "aceptada")
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this,
                                                "✅ Cita aceptada",
                                                Toast.LENGTH_SHORT).show();

                                        // NTFY: notificar al paciente
                                        if (idPacienteFinal != null) {
                                            NtfyHelper.citaAceptada(
                                                    idPacienteFinal,
                                                    tratFinal != null ? tratFinal : "tu cita",
                                                    fechaFinal != null ? fechaFinal : "--",
                                                    horaFinal != null ? horaFinal : "--");

                                            // Programar recordatorio 24h antes
                                            if (fechaFinal != null) {
                                                RecordatorioReceiver.programar(
                                                        this, idCita,
                                                        idPacienteFinal,
                                                        tratFinal != null ? tratFinal : "Cita",
                                                        fechaFinal,
                                                        horaFinal != null ? horaFinal : "--");
                                            }
                                        }
                                    });
                        })
                        .setNegativeButton("Cancelar", null)
                        .show()
        );

        // ── RECHAZAR con ntfy ──
        btnRechazar.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Rechazar solicitud")
                        .setMessage("¿Rechazar? Rechazar NO suma falta al paciente.")
                        .setPositiveButton("Sí, rechazar", (d, w) -> {
                            db.collection("citas").document(idCita)
                                    .update("estado", "rechazada")
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this,
                                                "❌ Solicitud rechazada",
                                                Toast.LENGTH_SHORT).show();

                                        // NTFY: notificar al paciente
                                        if (idPacienteFinal != null) {
                                            NtfyHelper.citaRechazada(
                                                    idPacienteFinal,
                                                    tratFinal != null ? tratFinal : "tu cita");
                                        }

                                        // Cancelar recordatorio si existía
                                        RecordatorioReceiver.cancelar(this, idCita);
                                    });
                        })
                        .setNegativeButton("Cancelar", null)
                        .show()
        );

        contenedorSolicitudes.addView(tarjeta);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerCitas != null) listenerCitas.remove();
    }
}
