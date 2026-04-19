package com.example.odontoconnect;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Doctor responde una solicitud de urgencia.
 *
 * Tres opciones:
 *  1. Aceptar en fecha/hora que elija el doctor -> cita confirmada, +20% recargo
 *  2. Desplazar paciente: si la fecha/hora elegida colisiona con otro paciente,
 *     ese paciente se reagenda con 10% descuento
 *  3. Rechazar: la urgencia se marca rechazada, paciente busca otra opcion
 */
public class ResponderUrgenciaActivity extends AppCompatActivity {

    // Porcentaje que paga el paciente urgente (+20% sobre precio tratamiento)
    private static final double RECARGO_URGENCIA = 0.20;
    // Porcentaje de descuento para paciente desplazado (-10%)
    private static final double DESCUENTO_DESPLAZADO = 0.10;

    private TextView tvNombrePacUrg, tvDescripcionUrg, tvFechaPedida,
            tvFechaElegida, tvHoraElegida, tvPrecioSugerido, tvAvisoColision;
    private EditText etPrecioUrgencia, etMotivoRechazo;
    private MaterialButton btnElegirFecha, btnElegirHora,
            btnAceptarUrgencia, btnRechazarUrgencia;
    private View seccionAceptar, seccionRechazar;

    private FirebaseFirestore db;
    private String idCita;
    private DocumentSnapshot citaDoc;

    private Calendar fechaElegida;
    private String horaElegidaStr;
    private double precioTratamientoBase = 0;

    // Si al elegir fecha/hora detectamos que hay otro paciente, lo guardamos aqui
    private QueryDocumentSnapshot citaEnConflicto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_responder_urgencia);

        db = FirebaseFirestore.getInstance();
        idCita = getIntent().getStringExtra("idCita");

        if (idCita == null) {
            Toast.makeText(this, "Urgencia no encontrada",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Refs
        tvNombrePacUrg     = findViewById(R.id.tvNombrePacUrg);
        tvDescripcionUrg   = findViewById(R.id.tvDescripcionUrg);
        tvFechaPedida      = findViewById(R.id.tvFechaPedida);
        tvFechaElegida     = findViewById(R.id.tvFechaElegida);
        tvHoraElegida      = findViewById(R.id.tvHoraElegida);
        tvPrecioSugerido   = findViewById(R.id.tvPrecioSugerido);
        tvAvisoColision    = findViewById(R.id.tvAvisoColision);
        etPrecioUrgencia   = findViewById(R.id.etPrecioUrgencia);
        etMotivoRechazo    = findViewById(R.id.etMotivoRechazo);
        btnElegirFecha     = findViewById(R.id.btnElegirFecha);
        btnElegirHora      = findViewById(R.id.btnElegirHora);
        btnAceptarUrgencia = findViewById(R.id.btnAceptarUrgencia);
        btnRechazarUrgencia = findViewById(R.id.btnRechazarUrgencia);
        seccionAceptar     = findViewById(R.id.seccionAceptar);
        seccionRechazar    = findViewById(R.id.seccionRechazar);

        btnElegirFecha.setOnClickListener(v -> mostrarPickerFecha());
        btnElegirHora.setOnClickListener(v -> mostrarPickerHora());
        btnAceptarUrgencia.setOnClickListener(v -> aceptarUrgencia());
        btnRechazarUrgencia.setOnClickListener(v -> rechazarUrgencia());

        cargarUrgencia();
    }

    private void cargarUrgencia() {
        db.collection("citas").document(idCita).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        finish();
                        return;
                    }
                    citaDoc = doc;

                    String nombre      = doc.getString("nombrePaciente");
                    String descripcion = doc.getString("descripcionUrgencia");
                    String fechaPref   = doc.getString("fechaPreferida");
                    String horaPref    = doc.getString("horaPreferida");

                    tvNombrePacUrg.setText(nombre != null ? nombre : "Paciente");
                    tvDescripcionUrg.setText(
                            descripcion != null ? descripcion : "Sin descripcion");
                    tvFechaPedida.setText("Pide: " +
                            (fechaPref != null ? fechaPref : "--") + " a las " +
                            (horaPref != null ? horaPref : "--"));

                    // Cargar precio de "Urgencia" del catalogo (si existe)
                    // si no, sugerimos un precio basico
                    buscarPrecioUrgencia();
                });
    }

    private void buscarPrecioUrgencia() {
        db.collection("tratamientos")
                .whereEqualTo("nombre", "Urgencia").get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        Double precio = snap.getDocuments().get(0).getDouble("precio");
                        if (precio != null) precioTratamientoBase = precio;
                    }
                    // Si no hay tratamiento "Urgencia", dejar precio 0
                    // y el doctor lo ingresa manualmente
                    actualizarSugerenciaPrecio();
                });
    }

    private void actualizarSugerenciaPrecio() {
        if (precioTratamientoBase > 0) {
            double conRecargo = precioTratamientoBase * (1 + RECARGO_URGENCIA);
            tvPrecioSugerido.setText(String.format(Locale.getDefault(),
                    "Sugerido: $%.2f base + 20%% = $%.2f total",
                    precioTratamientoBase, conRecargo));
            etPrecioUrgencia.setText(String.format(Locale.getDefault(),
                    "%.2f", conRecargo));
        } else {
            tvPrecioSugerido.setText("No hay tratamiento 'Urgencia' registrado. Ingresa el precio total (ya incluido recargo).");
        }
    }

    private void mostrarPickerFecha() {
        Calendar hoy = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            fechaElegida = Calendar.getInstance();
            fechaElegida.set(year, month, day);
            String txt = String.format(Locale.getDefault(),
                    "%d-%d-%d", day, month + 1, year);
            tvFechaElegida.setText(txt);
            verificarColision();
        }, hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH),
                hoy.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void mostrarPickerHora() {
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(this, (view, hour, minute) -> {
            horaElegidaStr = String.format(Locale.getDefault(),
                    "%02d:%02d", hour, minute);
            tvHoraElegida.setText(horaElegidaStr);
            verificarColision();
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
    }

    // Busca si ya existe una cita activa en esa fecha/hora con ese doctor
    private void verificarColision() {
        if (fechaElegida == null || horaElegidaStr == null) return;
        if (citaDoc == null) return;

        String fechaTxt = String.format(Locale.getDefault(),
                "%d-%d-%d",
                fechaElegida.get(Calendar.DAY_OF_MONTH),
                fechaElegida.get(Calendar.MONTH) + 1,
                fechaElegida.get(Calendar.YEAR));
        String idDoctor = citaDoc.getString("idDoctor");

        tvAvisoColision.setVisibility(View.GONE);
        citaEnConflicto = null;

        db.collection("citas")
                .whereEqualTo("idDoctor", idDoctor)
                .whereEqualTo("fecha", fechaTxt)
                .whereIn("estado", Arrays.asList("pendiente", "aceptada"))
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) {
                        // No considerarse a si misma
                        if (doc.getId().equals(idCita)) continue;

                        String hora = doc.getString("horaDisplay");
                        if (hora == null) hora = doc.getString("hora");

                        if (horaElegidaStr.equals(hora)) {
                            // COLISION
                            citaEnConflicto = doc;
                            String nombre = doc.getString("nombrePaciente");
                            if (nombre == null) {
                                String idPac = doc.getString("idPaciente");
                                if (idPac != null) {
                                    db.collection("usuarios").document(idPac).get()
                                            .addOnSuccessListener(u -> {
                                                String n = u.getString("Nombre");
                                                if (n == null) n = u.getString("nombre");
                                                mostrarAvisoColision(n);
                                            });
                                }
                            } else {
                                mostrarAvisoColision(nombre);
                            }
                            return;
                        }
                    }
                });
    }

    private void mostrarAvisoColision(String nombrePac) {
        tvAvisoColision.setVisibility(View.VISIBLE);
        tvAvisoColision.setText(
                "ATENCION: Ya hay cita con " +
                (nombrePac != null ? nombrePac : "otro paciente") +
                " en esa fecha/hora. Si aceptas, se reagendara con 10% de descuento.");
    }

    private void aceptarUrgencia() {
        if (fechaElegida == null || horaElegidaStr == null) {
            Toast.makeText(this,
                    "Selecciona fecha y hora para la urgencia",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        double precio;
        try {
            precio = Double.parseDouble(etPrecioUrgencia.getText().toString());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Ingresa un precio valido",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (citaEnConflicto != null) {
            // Hay colision - confirmar reagenda del paciente desplazado
            mostrarDialogoColision(precio);
        } else {
            // Sin colision - aceptar directo
            confirmarAceptar(precio, null);
        }
    }

    private void mostrarDialogoColision(double precioUrgencia) {
        String nombreDesplazado = citaEnConflicto.getString("nombrePaciente");
        if (nombreDesplazado == null) nombreDesplazado = "El paciente";
        Double precioOrig = citaEnConflicto.getDouble("precioTratamiento");
        double precioConDescuento = precioOrig != null
                ? precioOrig * (1 - DESCUENTO_DESPLAZADO) : 0;

        String msg = "Se desplazara la cita de " + nombreDesplazado + ".\n\n";
        if (precioOrig != null) {
            msg += String.format(Locale.getDefault(),
                    "Precio original: $%.2f\n" +
                    "Descuento -10%%: $%.2f\n" +
                    "Nuevo precio: $%.2f\n\n",
                    precioOrig, precioOrig * DESCUENTO_DESPLAZADO,
                    precioConDescuento);
        }
        msg += "El paciente desplazado recibira la notificacion y podra elegir nueva fecha.\n\nConfirmas?";

        new AlertDialog.Builder(this)
                .setTitle("Desplazar cita")
                .setMessage(msg)
                .setPositiveButton("Si, desplazar", (d, w) ->
                        confirmarAceptar(precioUrgencia, citaEnConflicto))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarAceptar(double precioUrgencia,
                                    QueryDocumentSnapshot citaDesplazar) {
        btnAceptarUrgencia.setEnabled(false);
        btnAceptarUrgencia.setText("Guardando...");

        String fechaTxt = String.format(Locale.getDefault(),
                "%d-%d-%d",
                fechaElegida.get(Calendar.DAY_OF_MONTH),
                fechaElegida.get(Calendar.MONTH) + 1,
                fechaElegida.get(Calendar.YEAR));

        // 1. Actualizar la cita urgente: pasa a aceptada con fecha/hora
        Map<String, Object> urgData = new HashMap<>();
        urgData.put("estado",             "aceptada");
        urgData.put("fecha",              fechaTxt);
        urgData.put("hora",               horaElegidaStr);
        urgData.put("horaDisplay",        horaElegidaStr);
        urgData.put("esUrgente",          true);
        urgData.put("recargoAplicado",    true);
        urgData.put("porcentajeRecargo",  RECARGO_URGENCIA * 100);
        urgData.put("precioTratamiento",  precioUrgencia / (1 + RECARGO_URGENCIA));
        urgData.put("montoFinal",         precioUrgencia);

        db.collection("citas").document(idCita)
                .update(urgData)
                .addOnSuccessListener(aVoid -> {
                    // Notificar al paciente urgente
                    String idPacUrg = citaDoc.getString("idPaciente");
                    if (idPacUrg != null) {
                        NtfyHelper.notificarPaciente(idPacUrg,
                                "Urgencia aceptada",
                                "El doctor acepto tu urgencia para " + fechaTxt +
                                        " a las " + horaElegidaStr +
                                        ". Monto a pagar: $" +
                                        String.format(Locale.getDefault(), "%.2f",
                                                precioUrgencia) +
                                        " (incluye recargo del 20%). " +
                                        "Realiza el pago desde la app.",
                                "OK");
                    }

                    // 2. Si hay cita a desplazar, reagendarla
                    if (citaDesplazar != null) {
                        desplazarCita(citaDesplazar);
                    } else {
                        finalizarAceptacion();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    btnAceptarUrgencia.setEnabled(true);
                    btnAceptarUrgencia.setText("Aceptar urgencia");
                });
    }

    private void desplazarCita(QueryDocumentSnapshot citaDesplazar) {
        String idDesplazada = citaDesplazar.getId();
        Double precioOrig   = citaDesplazar.getDouble("precioTratamiento");
        double precioConDesc = precioOrig != null
                ? precioOrig * (1 - DESCUENTO_DESPLAZADO) : 0;
        String idPacDespl   = citaDesplazar.getString("idPaciente");

        Map<String, Object> cambios = new HashMap<>();
        cambios.put("estado",              "reagendada_por_urgencia");
        cambios.put("motivoReagenda",      "Paciente con urgencia tomo tu horario");
        cambios.put("precioNuevoFecha",    precioConDesc);
        cambios.put("descuentoAplicado",   DESCUENTO_DESPLAZADO * 100);
        cambios.put("ofertaReagenda",      true);

        db.collection("citas").document(idDesplazada)
                .update(cambios)
                .addOnSuccessListener(aVoid -> {
                    if (idPacDespl != null) {
                        NtfyHelper.notificarPaciente(idPacDespl,
                                "Tu cita fue reagendada",
                                "Otro paciente con urgencia tomo tu horario. " +
                                        "Como disculpa, te ofrecemos 10% de descuento " +
                                        "en tu cita. Nuevo precio: $" +
                                        String.format(Locale.getDefault(), "%.2f",
                                                precioConDesc) +
                                        ". Abre la app para elegir nueva fecha.",
                                "!");
                    }
                    finalizarAceptacion();
                });
    }

    private void finalizarAceptacion() {
        Toast.makeText(this, "Urgencia aceptada correctamente",
                Toast.LENGTH_LONG).show();
        finish();
    }

    private void rechazarUrgencia() {
        String motivo = etMotivoRechazo.getText().toString().trim();
        if (motivo.isEmpty()) {
            Toast.makeText(this,
                    "Escribe un motivo (ej: no puedo, busca otra clinica)",
                    Toast.LENGTH_SHORT).show();
            etMotivoRechazo.requestFocus();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Rechazar urgencia")
                .setMessage("Estas seguro? El paciente recibira tu mensaje y sabra que debe buscar otra opcion.")
                .setPositiveButton("Si, rechazar", (d, w) -> {
                    Map<String, Object> cambios = new HashMap<>();
                    cambios.put("estado",         "urgencia_rechazada");
                    cambios.put("motivoRechazo",  motivo);

                    db.collection("citas").document(idCita)
                            .update(cambios)
                            .addOnSuccessListener(aVoid -> {
                                String idPac = citaDoc.getString("idPaciente");
                                if (idPac != null) {
                                    NtfyHelper.notificarPaciente(idPac,
                                            "Urgencia no disponible",
                                            "El doctor no puede atender tu urgencia. " +
                                                    "Motivo: " + motivo + "\n\n" +
                                                    "Te sugerimos buscar atencion en otra clinica o servicio de urgencias.",
                                            "X");
                                }
                                Toast.makeText(this, "Urgencia rechazada",
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
