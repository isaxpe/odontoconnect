package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pantalla para paciente cuya cita fue cedida a un urgente.
 * Muestra el nuevo monto con +20% y deja al paciente elegir:
 *  - Aceptar reagendar (paga mas, pero conserva la cita)
 *  - Cancelar definitivamente (se pierde la cita)
 */
public class ReagendarCitaActivity extends AppCompatActivity {

    private TextView tvTratamientoReagendar, tvFechaOriginalReagendar,
            tvMotivoReagendar, tvPrecioOriginal, tvPrecioNuevo,
            tvDiferencia, tvAnticipoNuevo;
    private MaterialButton btnAceptarReagendar, btnCancelarDefinitivo;
    private FirebaseFirestore db;
    private String idCita;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reagendar_cita);

        db = FirebaseFirestore.getInstance();
        idCita = getIntent().getStringExtra("idCita");

        if (idCita == null) {
            Toast.makeText(this, "Cita no encontrada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvTratamientoReagendar   = findViewById(R.id.tvTratamientoReagendar);
        tvFechaOriginalReagendar = findViewById(R.id.tvFechaOriginalReagendar);
        tvMotivoReagendar        = findViewById(R.id.tvMotivoReagendar);
        tvPrecioOriginal         = findViewById(R.id.tvPrecioOriginal);
        tvPrecioNuevo            = findViewById(R.id.tvPrecioNuevo);
        tvDiferencia             = findViewById(R.id.tvDiferencia);
        tvAnticipoNuevo          = findViewById(R.id.tvAnticipoNuevo);
        btnAceptarReagendar      = findViewById(R.id.btnAceptarReagendar);
        btnCancelarDefinitivo    = findViewById(R.id.btnCancelarDefinitivo);

        cargarDatos();
    }

    private void cargarDatos() {
        db.collection("citas").document(idCita).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Cita no existe", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String tratamiento   = doc.getString("tratamiento");
                    String fecha         = doc.getString("fecha");
                    String hora          = doc.getString("horaDisplay");
                    if (hora == null) hora = doc.getString("hora");
                    String motivo        = doc.getString("motivoReagenda");
                    Double precioOrig    = doc.getDouble("precioTratamiento");
                    Double precioNuevo   = doc.getDouble("precioNuevoFecha");

                    tvTratamientoReagendar.setText(
                            tratamiento != null ? tratamiento : "Tratamiento");
                    tvFechaOriginalReagendar.setText(
                            (fecha != null ? fecha : "--") + " a las " +
                            (hora != null ? hora : "--"));
                    tvMotivoReagendar.setText(
                            motivo != null ? motivo
                                    : "Paciente con urgencia medica");

                    if (precioOrig != null && precioNuevo != null) {
                        double diferencia = precioNuevo - precioOrig;
                        double anticipoNuevo = precioNuevo * 0.20;

                        tvPrecioOriginal.setText(String.format(Locale.getDefault(),
                                "$%.2f MXN", precioOrig));
                        tvPrecioNuevo.setText(String.format(Locale.getDefault(),
                                "$%.2f MXN", precioNuevo));
                        tvDiferencia.setText(String.format(Locale.getDefault(),
                                "+$%.2f MXN (+20%%)", diferencia));
                        tvAnticipoNuevo.setText(String.format(Locale.getDefault(),
                                "$%.2f MXN", anticipoNuevo));
                    } else {
                        tvPrecioOriginal.setText("--");
                        tvPrecioNuevo.setText("--");
                        tvDiferencia.setText("Consulta con el doctor");
                        tvAnticipoNuevo.setText("--");
                    }

                    btnAceptarReagendar.setOnClickListener(v ->
                            mostrarDialogoAceptar(precioNuevo));
                    btnCancelarDefinitivo.setOnClickListener(v ->
                            mostrarDialogoCancelar());
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void mostrarDialogoAceptar(Double precioNuevo) {
        String msg = "Al aceptar, tu cita cambiara de estado a 'pendiente' " +
                "y el doctor la evaluara para reagendarla en otra fecha.\n\n";
        if (precioNuevo != null) {
            msg += String.format(Locale.getDefault(),
                    "Nuevo precio: $%.2f MXN\n" +
                    "Anticipo: $%.2f MXN (20%%)",
                    precioNuevo, precioNuevo * 0.20);
        }

        new AlertDialog.Builder(this)
                .setTitle("Aceptar reagendar")
                .setMessage(msg)
                .setPositiveButton("Si, aceptar", (d, w) ->
                        aceptarReagendar(precioNuevo))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarDialogoCancelar() {
        new AlertDialog.Builder(this)
                .setTitle("Cancelar cita")
                .setMessage("Tu cita se cancelara definitivamente.\n\n" +
                        "No pagaras nada, pero perderas el espacio. " +
                        "Podras agendar una nueva cita cuando quieras.")
                .setPositiveButton("Si, cancelar", (d, w) -> cancelarDefinitivo())
                .setNegativeButton("Volver", null)
                .show();
    }

    private void aceptarReagendar(Double precioNuevo) {
        Map<String, Object> cambios = new HashMap<>();
        // Vuelve a estado pendiente para que el doctor la reprograme
        cambios.put("estado", "pendiente");
        cambios.put("aceptoReagenda", true);
        cambios.put("ofertaReagenda", false);
        if (precioNuevo != null) {
            cambios.put("precioTratamiento", precioNuevo);
            cambios.put("montoFinal", precioNuevo);
            cambios.put("recargoAplicado", true);
            cambios.put("porcentajeRecargo", 20.0);
        }
        // Limpiar fecha/hora para que doctor asigne una nueva
        cambios.put("fecha", null);
        cambios.put("hora", null);
        cambios.put("horaDisplay", null);

        db.collection("citas").document(idCita)
                .update(cambios)
                .addOnSuccessListener(aVoid -> {
                    // Notificar al doctor
                    db.collection("citas").document(idCita).get()
                            .addOnSuccessListener(citaDoc -> {
                                String idDoctor = citaDoc.getString("idDoctor");
                                String trat     = citaDoc.getString("tratamiento");
                                if (idDoctor != null) {
                                    NtfyHelper.notificarDoctor(idDoctor,
                                            "Paciente acepto reagendar",
                                            "Un paciente acepto reagendar su cita de " +
                                                    (trat != null ? trat : "tratamiento") +
                                                    " con recargo del 20%. Revisa tu agenda.",
                                            "OK");
                                }
                            });

                    Toast.makeText(this, "Aceptado. El doctor te contactara pronto.",
                            Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void cancelarDefinitivo() {
        db.collection("citas").document(idCita)
                .update("estado", "cancelada",
                        "motivoCancelacion", "Cedida a urgencia - paciente cancelo")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Cita cancelada",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }
}
