package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MisCitasActivity extends AppCompatActivity {

    private LinearLayout contenedor;
    private TextView tvMensajeVacio, tvBannerPago;
    private FirebaseFirestore db;
    private String userId;
    private ListenerRegistration listenerCitas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_citas);

        contenedor     = findViewById(R.id.contenedorMisCitas);
        tvMensajeVacio = findViewById(R.id.tvMensajeVacio);
        tvBannerPago   = findViewById(R.id.tvBannerPago);
        db     = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // NUEVO: Botón "Tengo urgencia" — buscar por ID o crear programáticamente
        configurarBotonUrgencia();

        cerrarCitasVencidas();
        cargarMisCitas();

        BottomNavHelper.setupPaciente(this, BottomNavHelper.PacienteTab.CITAS);

    }

    private void configurarBotonUrgencia() {
        // Si existe en el layout (por id "btnTengoUrgencia"), lo usamos.
        // Usamos getIdentifier para no romper la compilacion si el id no existe.
        int idBtn = getResources().getIdentifier(
                "btnTengoUrgencia", "id", getPackageName());
        if (idBtn != 0) {
            View existente = findViewById(idBtn);
            if (existente != null) {
                existente.setOnClickListener(v -> abrirSolicitudUrgencia());
                return;
            }
        }

        // Si no existe, lo creamos programaticamente arriba del contenedor
        if (contenedor == null) return;

        MaterialButton btnUrg = new MaterialButton(this);
        btnUrg.setText("Tengo urgencia");
        btnUrg.setTextColor(0xFFFFFFFF);
        btnUrg.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFDC2626));
        btnUrg.setCornerRadius(
                (int) (14 * getResources().getDisplayMetrics().density));
        btnUrg.setInsetTop(0);
        btnUrg.setInsetBottom(0);
        btnUrg.setStateListAnimator(null);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (54 * getResources().getDisplayMetrics().density));
        int margin = (int) (14 * getResources().getDisplayMetrics().density);
        lp.setMargins(0, 0, 0, margin);
        btnUrg.setLayoutParams(lp);

        btnUrg.setOnClickListener(v -> abrirSolicitudUrgencia());

        contenedor.addView(btnUrg, 0); // al inicio
    }

    private void abrirSolicitudUrgencia() {
        new AlertDialog.Builder(this)
                .setTitle("Solicitar urgencia")
                .setMessage("Vas a crear una solicitud de atencion urgente. " +
                        "El doctor revisara tu caso y respondera lo antes posible.\n\n" +
                        "AVISO: Las urgencias tienen un recargo del 20% por atencion prioritaria.\n\n" +
                        "Deseas continuar?")
                .setPositiveButton("Si, solicitar", (d, w) ->
                        startActivity(new Intent(this, SolicitudUrgenciaActivity.class)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // Auto-cerrar citas pendientes cuya fecha ya pasó
    private void cerrarCitasVencidas() {
        SimpleDateFormat sdf = new SimpleDateFormat("d-M-yyyy", Locale.getDefault());
        String hoy = sdf.format(new Date());

        db.collection("citas")
                .whereEqualTo("idPaciente", userId)
                .whereEqualTo("estado", "pendiente")
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) {
                        String fecha = doc.getString("fecha");
                        if (fecha == null) continue;
                        try {
                            Date fechaCita = sdf.parse(fecha);
                            Date hoyDate   = sdf.parse(hoy);
                            if (fechaCita != null && fechaCita.before(hoyDate)) {
                                doc.getReference().update("estado", "vencida");
                            }
                        } catch (ParseException ignored) {}
                    }
                });
    }

    private void cargarMisCitas() {
        listenerCitas = db.collection("citas")
                .whereEqualTo("idPaciente", userId)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    contenedor.removeAllViews();

                    List<QueryDocumentSnapshot> activas  = new ArrayList<>();
                    List<QueryDocumentSnapshot> pasadas  = new ArrayList<>();
                    boolean tienePagoPendiente = false;

                    for (QueryDocumentSnapshot doc : snap) {
                        String estado     = doc.getString("estado");
                        String estadoPago = doc.getString("estadoPago");

                        if ("pendiente".equals(estado) ||
                                "aceptada".equals(estado) ||
                                "reagendada_por_urgencia".equals(estado) ||
                                "urgencia_pendiente".equals(estado) ||
                                "urgencia_rechazada".equals(estado)) {
                            activas.add(doc);
                            if ("aceptada".equals(estado) &&
                                    (estadoPago == null ||
                                     "rechazado".equals(estadoPago))) {
                                tienePagoPendiente = true;
                            }
                        } else {
                            pasadas.add(doc);
                        }
                    }

                    // Banner pago pendiente
                    if (tvBannerPago != null) {
                        tvBannerPago.setVisibility(
                                tienePagoPendiente ? View.VISIBLE : View.GONE);
                    }

                    if (activas.isEmpty() && pasadas.isEmpty()) {
                        if (tvMensajeVacio != null)
                            tvMensajeVacio.setVisibility(View.VISIBLE);
                        return;
                    }
                    if (tvMensajeVacio != null)
                        tvMensajeVacio.setVisibility(View.GONE);

                    if (!activas.isEmpty()) {
                        agregarEncabezado("📋 Citas Activas");
                        for (QueryDocumentSnapshot doc : activas) añadirTarjeta(doc);
                    }
                    if (!pasadas.isEmpty()) {
                        agregarEncabezado("🕐 Historial");
                        for (QueryDocumentSnapshot doc : pasadas) añadirTarjeta(doc);
                    }
                });
    }

    private void agregarEncabezado(String titulo) {
        TextView tv = new TextView(this);
        tv.setText(titulo);
        tv.setTextSize(14);
        tv.setTextColor(0xFF888888);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, 20, 0, 8);
        contenedor.addView(tv);
    }

    private void añadirTarjeta(QueryDocumentSnapshot doc) {
        String idCita      = doc.getId();
        String tratamiento = doc.getString("tratamiento");
        String fecha       = doc.getString("fecha");
        String hora        = doc.getString("horaDisplay");
        if (hora == null) hora = doc.getString("hora");
        if (hora == null) hora = "--";
        String estado      = doc.getString("estado");
        String estadoPago  = doc.getString("estadoPago");
        boolean esFamiliar = Boolean.TRUE.equals(doc.getBoolean("esFamiliar"));
        String nomFamiliar = doc.getString("nombreFamiliar");

        View tarjeta = LayoutInflater.from(this)
                .inflate(R.layout.item_cita_paciente, contenedor, false);

        TextView tvTrat    = tarjeta.findViewById(R.id.tvTratamientoPaciente);
        TextView tvFecha   = tarjeta.findViewById(R.id.tvFechaHoraPaciente);
        TextView tvEstado  = tarjeta.findViewById(R.id.tvEstadoCita);
        MaterialButton btnCancelar = tarjeta.findViewById(R.id.btnCancelarCita);
        MaterialButton btnPagar    = tarjeta.findViewById(R.id.btnPagarAnticipo);

        String tituloCita = tratamiento != null ? tratamiento : "Cita";
        if (esFamiliar && nomFamiliar != null)
            tituloCita += "\n👨‍👩‍👧 Para: " + nomFamiliar;
        tvTrat.setText(tituloCita);

        tvFecha.setText((fecha != null ? "📅 " + fecha : "--") + "  🕐 " + hora);

        tvEstado.setText(estado != null ? estado.toUpperCase() : "PENDIENTE");
        switch (estado != null ? estado : "") {
            case "aceptada":  tvEstado.setBackgroundColor(0xFF1565C0); break;
            case "rechazada": tvEstado.setBackgroundColor(0xFFB71C1C); break;
            case "cancelada":
            case "vencida":   tvEstado.setBackgroundColor(0xFF757575); break;
            case "reagendada_por_urgencia":
                tvEstado.setText("CEDIDA");
                tvEstado.setBackgroundColor(0xFFD32F2F);
                break;
            case "urgencia_pendiente":
                tvEstado.setText("URGENCIA EN REVISION");
                tvEstado.setBackgroundColor(0xFFDC2626);
                break;
            case "urgencia_rechazada":
                tvEstado.setText("URGENCIA RECHAZADA");
                tvEstado.setBackgroundColor(0xFF757575);
                break;
            default:          tvEstado.setBackgroundColor(0xFFF59E0B); break;
        }

        // Botón cancelar
        if (btnCancelar != null) {
            if ("pendiente".equals(estado) || "aceptada".equals(estado)) {
                btnCancelar.setVisibility(View.VISIBLE);
                final String fechaFinal = fecha;
                btnCancelar.setOnClickListener(v ->
                        mostrarDialogoCancelar(idCita, fechaFinal));
            } else {
                btnCancelar.setVisibility(View.GONE);
            }
        }

        // Botón pagar anticipo
        if (btnPagar != null) {
            if ("aceptada".equals(estado)) {
                btnPagar.setVisibility(View.VISIBLE);
                if (estadoPago == null) {
                    btnPagar.setText("💳 Pagar anticipo");
                    btnPagar.setEnabled(true);
                    btnPagar.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFF1565C0));
                } else if ("rechazado".equals(estadoPago)) {
                    btnPagar.setText("❌ Comprobante rechazado — Reenviar");
                    btnPagar.setEnabled(true);
                    btnPagar.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFFB71C1C));
                } else if ("pagado".equals(estadoPago)) {
                    btnPagar.setText("📤 Comprobante enviado — en revisión");
                    btnPagar.setEnabled(false);
                    btnPagar.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFF757575));
                } else if ("confirmado".equals(estadoPago)) {
                    btnPagar.setText("✅ Anticipo confirmado");
                    btnPagar.setEnabled(false);
                    btnPagar.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFF2E7D32));
                }
                btnPagar.setOnClickListener(v -> {
                    Intent intent = new Intent(this, PagoAnticipoActivity.class);
                    intent.putExtra("idCita", idCita);
                    startActivity(intent);
                });
            } else {
                btnPagar.setVisibility(View.GONE);
            }
        }

        contenedor.addView(tarjeta);
    }

    private void mostrarDialogoCancelar(String idCita, String fecha) {
        boolean dentro24h = esDentroD24Horas(fecha);
        String msg = dentro24h
                ? "⚠️ Esta cita es en menos de 24 horas.\n\n" +
                  "Cancelar a última hora sumará 1 falta. ¿Continuar?"
                : "¿Cancelar esta cita?\n\n" +
                  "Con más de 24h de anticipación NO se suma falta.";

        new AlertDialog.Builder(this)
                .setTitle("Cancelar cita")
                .setMessage(msg)
                .setPositiveButton("Sí, cancelar", (d, w) ->
                        cancelarCita(idCita, dentro24h))
                .setNegativeButton("No, mantener", null)
                .show();
    }

    private void cancelarCita(String idCita, boolean sumarFalta) {
        db.collection("citas").document(idCita)
                .update("estado", "cancelada")
                .addOnSuccessListener(aVoid -> {
                    if (sumarFalta) {
                        aplicarFalta();
                        android.widget.Toast.makeText(this,
                                "Cita cancelada. Se registró 1 falta.",
                                android.widget.Toast.LENGTH_LONG).show();
                    } else {
                        android.widget.Toast.makeText(this,
                                "✅ Cita cancelada sin penalización.",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }

                    // LISTA DE ESPERA: notificar automáticamente a otros pacientes
                    ListaEsperaHelper.notificarListaEspera(idCita);

                    // Cancelar recordatorio si existía
                    RecordatorioReceiver.cancelar(this, idCita);
                });
    }

    private void aplicarFalta() {
        db.collection("usuarios").document(userId)
                .update("faltas", FieldValue.increment(1))
                .addOnSuccessListener(aVoid ->
                        db.collection("usuarios").document(userId).get()
                                .addOnSuccessListener(doc -> {
                                    if (!doc.exists()) return;
                                    Long faltas = doc.getLong("faltas");
                                    if (faltas == null) faltas = 1L;
                                    if (faltas >= 3) {
                                        doc.getReference().update(
                                                "bloqueado", true,
                                                "nivelPenalizacion", 3);
                                    } else if (faltas == 2) {
                                        long rest = System.currentTimeMillis()
                                                + (7L * 24 * 60 * 60 * 1000);
                                        doc.getReference().update(
                                                "nivelPenalizacion", 2,
                                                "restriccionHasta", rest);
                                    } else {
                                        doc.getReference().update(
                                                "nivelPenalizacion", 1);
                                    }
                                })
                );
    }

    private boolean esDentroD24Horas(String fechaStr) {
        if (fechaStr == null) return false;
        try {
            SimpleDateFormat sdf =
                    new SimpleDateFormat("d-M-yyyy", Locale.getDefault());
            Date fechaCita = sdf.parse(fechaStr);
            if (fechaCita == null) return false;
            long dif = fechaCita.getTime() - System.currentTimeMillis();
            return dif >= 0 && dif < 24 * 60 * 60 * 1000;
        } catch (ParseException e) { return false; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerCitas != null) listenerCitas.remove();
    }
}
