package com.example.odontoconnect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

public class VerComprobanteActivity extends AppCompatActivity {

    private ImageView ivComprobante;
    private TextView tvNombrePaciente, tvTratamiento, tvFecha, tvMonto, tvEstado;
    private MaterialButton btnConfirmarPago, btnRechazarPago;

    private FirebaseFirestore db;
    private String idCita;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ver_comprobante);

        db     = FirebaseFirestore.getInstance();
        idCita = getIntent().getStringExtra("idCita");

        if (idCita == null) { finish(); return; }

        ivComprobante    = findViewById(R.id.ivComprobanteDoctor);
        tvNombrePaciente = findViewById(R.id.tvNombrePacienteComp);
        tvTratamiento    = findViewById(R.id.tvTratamientoComp);
        tvFecha          = findViewById(R.id.tvFechaComp);
        tvMonto          = findViewById(R.id.tvMontoComp);
        tvEstado         = findViewById(R.id.tvEstadoComp);
        btnConfirmarPago = findViewById(R.id.btnConfirmarPago);
        btnRechazarPago  = findViewById(R.id.btnRechazarPago);

        cargarDatos();

        btnConfirmarPago.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Confirmar pago")
                        .setMessage("¿Confirmas que recibiste el anticipo correctamente?")
                        .setPositiveButton("Sí, confirmar", (d, w) ->
                                actualizarPago("pagado",
                                        "✅ Pago confirmado"))
                        .setNegativeButton("Cancelar", null)
                        .show()
        );

        btnRechazarPago.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Rechazar comprobante")
                        .setMessage("¿El comprobante no es válido? " +
                                "El paciente deberá subir uno nuevo.")
                        .setPositiveButton("Sí, rechazar", (d, w) ->
                                actualizarPago("pago_rechazado",
                                        "❌ Comprobante rechazado"))
                        .setNegativeButton("Cancelar", null)
                        .show()
        );
    }

    private void cargarDatos() {
        db.collection("citas").document(idCita).get()
                .addOnSuccessListener(citaDoc -> {
                    if (!citaDoc.exists()) return;

                    String tratamiento = citaDoc.getString("tratamiento");
                    String fecha       = citaDoc.getString("fecha");
                    String hora        = citaDoc.getString("horaDisplay");
                    if (hora == null) hora = citaDoc.getString("hora");
                    String idPaciente  = citaDoc.getString("idPaciente");
                    Double precio      = citaDoc.getDouble("precioTratamiento");
                    String estadoPago  = citaDoc.getString("estadoPago");
                    String comprobante = citaDoc.getString("comprobanteBase64");

                    tvTratamiento.setText(tratamiento != null ? tratamiento : "Cita");
                    String horaFinal = hora;
                    tvFecha.setText((fecha != null ? fecha : "--") +
                            "  🕐 " + (horaFinal != null ? horaFinal : "--"));

                    if (precio != null && precio > 0) {
                        tvMonto.setText(String.format("Anticipo: $%.2f MXN",
                                precio * 0.20));
                    }

                    // Estado del pago
                    if ("pagado".equals(estadoPago)) {
                        tvEstado.setText("✅ Pago confirmado");
                        tvEstado.setTextColor(0xFF1565C0);
                        btnConfirmarPago.setEnabled(false);
                        btnRechazarPago.setEnabled(false);
                    } else if ("pago_rechazado".equals(estadoPago)) {
                        tvEstado.setText("❌ Comprobante rechazado");
                        tvEstado.setTextColor(0xFFB71C1C);
                    } else {
                        tvEstado.setText("⏳ Esperando confirmación");
                        tvEstado.setTextColor(0xFFF57C00);
                    }

                    // Mostrar imagen del comprobante
                    if (comprobante != null && !comprobante.isEmpty()) {
                        try {
                            byte[] bytes = Base64.decode(comprobante, Base64.DEFAULT);
                            Bitmap bmp   = BitmapFactory.decodeByteArray(
                                    bytes, 0, bytes.length);
                            ivComprobante.setImageBitmap(bmp);
                            ivComprobante.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            ivComprobante.setVisibility(View.GONE);
                        }
                    }

                    // Cargar nombre del paciente
                    if (idPaciente != null) {
                        db.collection("usuarios").document(idPaciente).get()
                                .addOnSuccessListener(pacDoc -> {
                                    String nombre = pacDoc.getString("Nombre");
                                    if (nombre == null)
                                        nombre = pacDoc.getString("nombre");
                                    tvNombrePaciente.setText(
                                            "👤 " + (nombre != null ? nombre : "Paciente"));
                                });
                    }
                });
    }

    private void actualizarPago(String nuevoEstado, String mensaje) {
        db.collection("citas").document(idCita)
                .update("estadoPago", nuevoEstado)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al actualizar",
                                Toast.LENGTH_SHORT).show()
                );
    }
}
