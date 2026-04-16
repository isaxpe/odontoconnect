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

import java.util.Arrays;

public class VerCitasActivity extends AppCompatActivity {

    private LinearLayout contenedorSolicitudes;
    private TextView tvMensajeVacio;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration listenerPendientes;
    private ListenerRegistration listenerComprobantes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ver_citas);

        contenedorSolicitudes = findViewById(R.id.contenedorSolicitudes);
        tvMensajeVacio        = findViewById(R.id.tvMensajeVacio);
        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        cargarCitas();

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

    private void cargarCitas() {
        if (mAuth.getCurrentUser() == null) return;
        String miUid = mAuth.getCurrentUser().getUid();

        // FIX BUG 9: cargar DOS tipos de citas:
        // 1. Citas PENDIENTES → para aceptar/rechazar
        // 2. Citas ACEPTADAS con estadoPago=pagado → para revisar comprobante
        listenerPendientes = db.collection("citas")
                .whereEqualTo("idDoctor", miUid)
                .whereIn("estado", Arrays.asList("pendiente", "aceptada"))
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    contenedorSolicitudes.removeAllViews();

                    if (snap.isEmpty()) {
                        tvMensajeVacio.setVisibility(View.VISIBLE);
                        return;
                    }

                    boolean hayItems = false;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String estado     = doc.getString("estado");
                        String estadoPago = doc.getString("estadoPago");

                        boolean esPendiente = "pendiente".equals(estado);
                        // BUG 9 FIX: mostrar citas aceptadas SOLO si tienen comprobante pendiente
                        boolean tieneComprobante = "aceptada".equals(estado) &&
                                "pagado".equals(estadoPago);

                        if (esPendiente || tieneComprobante) {
                            crearTarjeta(doc);
                            hayItems = true;
                        }
                    }

                    tvMensajeVacio.setVisibility(hayItems ? View.GONE : View.VISIBLE);
                });
    }

    private void crearTarjeta(DocumentSnapshot citaDoc) {
        String idCita      = citaDoc.getId();
        String fecha       = citaDoc.getString("fecha");
        String hora        = citaDoc.getString("horaDisplay");
        if (hora == null) hora = citaDoc.getString("hora");
        String tratamiento = citaDoc.getString("tratamiento");
        String idPaciente  = citaDoc.getString("idPaciente");
        String estado      = citaDoc.getString("estado");
        String estadoPago  = citaDoc.getString("estadoPago");

        Boolean triajeOk    = citaDoc.getBoolean("completado");
        String nivelDolor   = citaDoc.getString("nivelDolor");
        String primeraVis   = citaDoc.getString("primeraVisita");
        String medicamentos = citaDoc.getString("tomaMedicamentos");
        String descripcion  = citaDoc.getString("descripcionMolestia");

        View tarjeta = LayoutInflater.from(this)
                .inflate(R.layout.item_solicitud, contenedorSolicitudes, false);

        TextView tvInfo     = tarjeta.findViewById(R.id.tvInfoCita);
        TextView tvTriaje   = tarjeta.findViewById(R.id.tvTriajeCita);
        TextView tvEstPago  = tarjeta.findViewById(R.id.tvEstadoPagoCita);
        MaterialButton btnVerComp = tarjeta.findViewById(R.id.btnVerComprobante);
        Button btnAceptar   = tarjeta.findViewById(R.id.btnAceptar);
        Button btnRechazar  = tarjeta.findViewById(R.id.btnRechazar);

        final String horaFinal  = hora;
        final String tratFinal  = tratamiento;
        final String fechaFinal = fecha;
        final String idPacFinal = idPaciente;

        // Si es cita ACEPTADA con comprobante, cambiar título de la tarjeta
        boolean soloComprobante = "aceptada".equals(estado) && "pagado".equals(estadoPago);

        tvInfo.setText((soloComprobante ? "💳 " : "🦷 ") +
                (tratamiento != null ? tratamiento : "Cita") +
                "\n📅 " + (fecha != null ? fecha : "--") +
                "  🕐 " + (hora != null ? hora : "--") +
                (soloComprobante ? "\n✅ Cita aceptada — comprobante pendiente" : "\n👤 Cargando..."));

        if (!soloComprobante && idPaciente != null) {
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

        // Triaje (solo en pendientes)
        if (!soloComprobante) {
            if (Boolean.TRUE.equals(triajeOk)) {
                StringBuilder sb = new StringBuilder("📋 Triaje:\n");
                if (nivelDolor != null)
                    sb.append("• Dolor: ").append(nivelDolor).append("\n");
                if (primeraVis != null)
                    sb.append("• Visita: ").append(primeraVis).append("\n");
                if (medicamentos != null)
                    sb.append("• Medicamentos: ").append(medicamentos);
                if (descripcion != null && !descripcion.isEmpty())
                    sb.append("\n• Molestia: ").append(descripcion);
                tvTriaje.setText(sb.toString().trim());
                tvTriaje.setTextColor(0xFF333333);
            } else {
                tvTriaje.setText("⏳ Triaje pendiente del paciente.");
                tvTriaje.setTextColor(0xFF888888);
            }
            tvTriaje.setVisibility(View.VISIBLE);
        }

        // Estado del pago
        if ("pagado".equals(estadoPago)) {
            tvEstPago.setText("📤 Comprobante enviado — pendiente de revisión");
            tvEstPago.setVisibility(View.VISIBLE);
            btnVerComp.setVisibility(View.VISIBLE);
            btnVerComp.setOnClickListener(v -> {
                Intent intent = new Intent(this, ConfirmarPagoActivity.class);
                intent.putExtra("idCita", idCita);
                startActivity(intent);
            });
            // Si solo es comprobante, ocultar aceptar/rechazar
            if (soloComprobante) {
                btnAceptar.setVisibility(View.GONE);
                btnRechazar.setVisibility(View.GONE);
            }
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
        } else if (!soloComprobante) {
            tvEstPago.setText("💰 Anticipo: pendiente de pago");
            tvEstPago.setVisibility(View.VISIBLE);
        }

        // Botones aceptar/rechazar (solo en pendientes)
        if (!soloComprobante) {
            btnAceptar.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Aceptar cita")
                            .setMessage("¿Confirmas aceptar esta solicitud?")
                            .setPositiveButton("Sí, aceptar", (d, w) -> {
                                db.collection("citas").document(idCita)
                                        .update("estado", "aceptada")
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "✅ Cita aceptada",
                                                    Toast.LENGTH_SHORT).show();
                                            if (idPacFinal != null) {
                                                NtfyHelper.citaAceptada(idPacFinal,
                                                        tratFinal != null ? tratFinal : "tu cita",
                                                        fechaFinal != null ? fechaFinal : "--",
                                                        horaFinal != null ? horaFinal : "--");
                                                if (fechaFinal != null)
                                                    RecordatorioReceiver.programar(this,
                                                            idCita, idPacFinal,
                                                            tratFinal != null ? tratFinal : "Cita",
                                                            fechaFinal,
                                                            horaFinal != null ? horaFinal : "--");
                                            }
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this, "Error al aceptar",
                                                        Toast.LENGTH_SHORT).show());
                            })
                            .setNegativeButton("Cancelar", null).show()
            );

            btnRechazar.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Rechazar solicitud")
                            .setMessage("¿Rechazar? Rechazar NO suma falta al paciente.")
                            .setPositiveButton("Sí, rechazar", (d, w) -> {
                                db.collection("citas").document(idCita)
                                        .update("estado", "rechazada")
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "❌ Solicitud rechazada",
                                                    Toast.LENGTH_SHORT).show();
                                            if (idPacFinal != null)
                                                NtfyHelper.citaRechazada(idPacFinal,
                                                        tratFinal != null ? tratFinal : "tu cita");
                                            RecordatorioReceiver.cancelar(this, idCita);
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this, "Error al rechazar",
                                                        Toast.LENGTH_SHORT).show());
                            })
                            .setNegativeButton("Cancelar", null).show()
            );
        }

        contenedorSolicitudes.addView(tarjeta);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerPendientes != null) listenerPendientes.remove();
        if (listenerComprobantes != null) listenerComprobantes.remove();
    }
}
