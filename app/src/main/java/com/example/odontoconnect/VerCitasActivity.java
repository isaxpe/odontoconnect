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

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class VerCitasActivity extends AppCompatActivity {

    // Porcentaje extra que se cobra por una cita urgente (+20%)
    private static final double RECARGO_URGENCIA = 0.20;

    private LinearLayout contenedorSolicitudes;
    private TextView tvMensajeVacio;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration listenerPendientes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ver_citas);

        contenedorSolicitudes = findViewById(R.id.contenedorSolicitudes);
        tvMensajeVacio        = findViewById(R.id.tvMensajeVacio);
        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        cargarCitas();

        BottomNavHelper.setupDoctor(this, BottomNavHelper.DoctorTab.INICIO);
    }

    private void cargarCitas() {
        if (mAuth.getCurrentUser() == null) return;
        String miUid = mAuth.getCurrentUser().getUid();

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
        Double precioTrat   = citaDoc.getDouble("precioTratamiento");

        // Detectar urgencia por nivelDolor (del triaje) o por flag esUrgente
        Boolean flagUrgente = citaDoc.getBoolean("esUrgente");
        boolean esUrgenteCita = Boolean.TRUE.equals(flagUrgente) ||
                UrgenciaHelper.esUrgente(nivelDolor);

        View tarjeta = LayoutInflater.from(this)
                .inflate(R.layout.item_solicitud, contenedorSolicitudes, false);

        TextView tagUrgente = tarjeta.findViewById(R.id.tagUrgente);
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
        final boolean esUrgente = esUrgenteCita;
        final Double precioFinal = precioTrat;

        boolean soloComprobante = "aceptada".equals(estado) && "pagado".equals(estadoPago);

        // MOSTRAR TAG URGENTE si corresponde y es solicitud pendiente
        if (tagUrgente != null && esUrgente && !soloComprobante) {
            tagUrgente.setVisibility(View.VISIBLE);
        } else if (tagUrgente != null) {
            tagUrgente.setVisibility(View.GONE);
        }

        tvInfo.setText((soloComprobante ? "Comprobante: " : "") +
                (tratamiento != null ? tratamiento : "Cita") +
                "\n" + (fecha != null ? fecha : "--") +
                " - " + (hora != null ? hora : "--") +
                (soloComprobante ? "\nCita aceptada - comprobante pendiente" : "\nCargando..."));

        if (!soloComprobante && idPaciente != null) {
            db.collection("usuarios").document(idPaciente).get()
                    .addOnSuccessListener(pac -> {
                        String nombre = pac.getString("Nombre");
                        if (nombre == null) nombre = pac.getString("nombre");
                        tvInfo.setText((tratFinal != null ? tratFinal : "Cita") +
                                "\n" + (fechaFinal != null ? fechaFinal : "--") +
                                " - " + (horaFinal != null ? horaFinal : "--") +
                                "\n" + (nombre != null ? nombre : "Paciente"));
                    });
        }

        // Triaje
        if (!soloComprobante) {
            if (Boolean.TRUE.equals(triajeOk)) {
                StringBuilder sb = new StringBuilder("Triaje:\n");
                if (nivelDolor != null)
                    sb.append("- Dolor: ").append(nivelDolor).append("\n");
                if (primeraVis != null)
                    sb.append("- Visita: ").append(primeraVis).append("\n");
                if (medicamentos != null)
                    sb.append("- Medicamentos: ").append(medicamentos);
                if (descripcion != null && !descripcion.isEmpty())
                    sb.append("\n- Molestia: ").append(descripcion);
                tvTriaje.setText(sb.toString().trim());
                tvTriaje.setTextColor(0xFF333333);
            } else {
                tvTriaje.setText("Triaje pendiente del paciente.");
                tvTriaje.setTextColor(0xFF888888);
            }
            tvTriaje.setVisibility(View.VISIBLE);
        }

        // Estado del pago
        if ("pagado".equals(estadoPago)) {
            tvEstPago.setText("Comprobante enviado - pendiente de revision");
            tvEstPago.setVisibility(View.VISIBLE);
            btnVerComp.setVisibility(View.VISIBLE);
            btnVerComp.setOnClickListener(v -> {
                Intent intent = new Intent(this, ConfirmarPagoActivity.class);
                intent.putExtra("idCita", idCita);
                startActivity(intent);
            });
            if (soloComprobante) {
                btnAceptar.setVisibility(View.GONE);
                btnRechazar.setVisibility(View.GONE);
            }
        } else if ("confirmado".equals(estadoPago)) {
            tvEstPago.setText("Anticipo confirmado");
            tvEstPago.setBackgroundColor(0xFFE8F5E9);
            tvEstPago.setTextColor(0xFF2E7D32);
            tvEstPago.setVisibility(View.VISIBLE);
        } else if ("rechazado".equals(estadoPago)) {
            tvEstPago.setText("Comprobante rechazado - paciente debe reenviar");
            tvEstPago.setBackgroundColor(0xFFFFEBEE);
            tvEstPago.setTextColor(0xFFB71C1C);
            tvEstPago.setVisibility(View.VISIBLE);
        } else if (!soloComprobante) {
            if (esUrgente && precioTrat != null) {
                double precioUrgente = precioTrat * (1 + RECARGO_URGENCIA);
                tvEstPago.setText(String.format(Locale.getDefault(),
                        "Anticipo URGENTE: $%.2f (+20%% sobre $%.2f)",
                        precioUrgente, precioTrat));
            } else {
                tvEstPago.setText("Anticipo: pendiente de pago");
            }
            tvEstPago.setVisibility(View.VISIBLE);
        }

        // Botones aceptar/rechazar
        if (!soloComprobante) {
            btnAceptar.setOnClickListener(v -> {
                if (esUrgente) {
                    mostrarDialogoAceptarUrgencia(idCita, tratFinal, fechaFinal,
                            horaFinal, idPacFinal, precioFinal);
                } else {
                    mostrarDialogoAceptarNormal(idCita, tratFinal, fechaFinal,
                            horaFinal, idPacFinal);
                }
            });

            btnRechazar.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Rechazar solicitud")
                            .setMessage("Rechazar NO suma falta al paciente.")
                            .setPositiveButton("Si, rechazar", (d, w) -> {
                                db.collection("citas").document(idCita)
                                        .update("estado", "rechazada")
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Solicitud rechazada",
                                                    Toast.LENGTH_SHORT).show();
                                            if (idPacFinal != null)
                                                NtfyHelper.citaRechazada(idPacFinal,
                                                        tratFinal != null ? tratFinal : "tu cita");
                                            RecordatorioReceiver.cancelar(this, idCita);
                                        });
                            })
                            .setNegativeButton("Cancelar", null).show()
            );
        }

        contenedorSolicitudes.addView(tarjeta);
    }

    // Dialogo especial para aceptar urgencia - muestra el recargo +20%
    private void mostrarDialogoAceptarUrgencia(String idCita, String trat, String fecha,
                                                 String hora, String idPac,
                                                 Double precioBase) {
        double precioFinal = precioBase != null
                ? precioBase * (1 + RECARGO_URGENCIA) : 0;

        String msg = "Esta cita esta marcada como URGENTE.\n\n";
        if (precioBase != null) {
            msg += String.format(Locale.getDefault(),
                    "Precio normal: $%.2f\nRecargo urgencia (+20%%): $%.2f\n" +
                    "Total a cobrar: $%.2f\n\n",
                    precioBase, precioBase * RECARGO_URGENCIA, precioFinal);
        } else {
            msg += "No se encontro el precio base del tratamiento.\n\n";
        }
        msg += "Al aceptar, el paciente recibira el nuevo monto a pagar.";

        new AlertDialog.Builder(this)
                .setTitle("Aceptar cita URGENTE")
                .setMessage(msg)
                .setPositiveButton("Aceptar urgencia", (d, w) ->
                        aceptarCita(idCita, trat, fecha, hora, idPac,
                                true, precioFinal))
                .setNeutralButton("Aceptar SIN recargo", (d, w) ->
                        aceptarCita(idCita, trat, fecha, hora, idPac,
                                false, precioBase != null ? precioBase : 0))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarDialogoAceptarNormal(String idCita, String trat, String fecha,
                                               String hora, String idPac) {
        new AlertDialog.Builder(this)
                .setTitle("Aceptar cita")
                .setMessage("Confirmas aceptar esta solicitud?")
                .setPositiveButton("Si, aceptar", (d, w) ->
                        aceptarCita(idCita, trat, fecha, hora, idPac, false, 0))
                .setNegativeButton("Cancelar", null).show();
    }

    private void aceptarCita(String idCita, String trat, String fecha, String hora,
                              String idPac, boolean conRecargo, double montoFinal) {
        Map<String, Object> cambios = new HashMap<>();
        cambios.put("estado", "aceptada");
        if (conRecargo) {
            cambios.put("esUrgente", true);
            cambios.put("recargoAplicado", true);
            cambios.put("montoFinal", montoFinal);
            cambios.put("porcentajeRecargo", RECARGO_URGENCIA * 100);
        } else {
            cambios.put("montoFinal", montoFinal);
        }

        db.collection("citas").document(idCita)
                .update(cambios)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this,
                            conRecargo ? "Cita urgente aceptada (+20%)" : "Cita aceptada",
                            Toast.LENGTH_SHORT).show();
                    if (idPac != null) {
                        if (conRecargo) {
                            NtfyHelper.notificarPaciente(idPac,
                                    "Urgencia aprobada",
                                    "Tu cita urgente fue aceptada. Nuevo monto: $" +
                                            String.format(Locale.getDefault(), "%.2f",
                                                    montoFinal) +
                                            " (incluye recargo del 20%). " +
                                            "Realiza el pago desde la app.",
                                    "!");
                        } else {
                            NtfyHelper.citaAceptada(idPac,
                                    trat != null ? trat : "tu cita",
                                    fecha != null ? fecha : "--",
                                    hora != null ? hora : "--");
                        }
                        if (fecha != null)
                            RecordatorioReceiver.programar(this,
                                    idCita, idPac,
                                    trat != null ? trat : "Cita",
                                    fecha, hora != null ? hora : "--");
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al aceptar",
                                Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerPendientes != null) listenerPendientes.remove();
    }
}
