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

public class ConfirmarPagoActivity extends AppCompatActivity {

    private TextView tvInfoCita, tvMonto, tvEstadoPago;
    private ImageView ivComprobante;
    private MaterialButton btnConfirmarPago, btnRechazarPago;

    private FirebaseFirestore db;
    private String idCita;
    private String idPacienteGuardado;
    private String tratamientoGuardado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirmar_pago);

        db     = FirebaseFirestore.getInstance();
        idCita = getIntent().getStringExtra("idCita");

        if (idCita == null) { finish(); return; }

        tvInfoCita      = findViewById(R.id.tvInfoCitaPago);
        tvMonto         = findViewById(R.id.tvMontoCitaPago);
        tvEstadoPago    = findViewById(R.id.tvEstadoCitaPago);
        ivComprobante   = findViewById(R.id.ivComprobantePago);
        btnConfirmarPago = findViewById(R.id.btnConfirmarPago);
        btnRechazarPago  = findViewById(R.id.btnRechazarPago);

        cargarDatosCita();

        // NTFY: confirmar pago
        btnConfirmarPago.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Confirmar pago")
                        .setMessage("¿Confirmas que el pago es válido?")
                        .setPositiveButton("Sí, confirmar", (d, w) -> {
                            db.collection("citas").document(idCita)
                                    .update("estadoPago", "confirmado")
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this,
                                                "✅ Pago confirmado",
                                                Toast.LENGTH_SHORT).show();
                                        // NTFY al paciente
                                        if (idPacienteGuardado != null) {
                                            NtfyHelper.anticipoConfirmado(
                                                    idPacienteGuardado,
                                                    tratamientoGuardado != null
                                                            ? tratamientoGuardado : "tu cita");
                                        }
                                        finish();
                                    });
                        })
                        .setNegativeButton("Cancelar", null)
                        .show()
        );

        // NTFY: rechazar comprobante
        btnRechazarPago.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Rechazar comprobante")
                        .setMessage("¿El comprobante no es válido?\n" +
                                "El paciente deberá enviarlo de nuevo.")
                        .setPositiveButton("Sí, rechazar", (d, w) -> {
                            db.collection("citas").document(idCita)
                                    .update("estadoPago", "rechazado")
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this,
                                                "❌ Comprobante rechazado",
                                                Toast.LENGTH_SHORT).show();
                                        // NTFY al paciente para que reenvíe
                                        if (idPacienteGuardado != null) {
                                            NtfyHelper.notificarPaciente(
                                                    idPacienteGuardado,
                                                    "Comprobante rechazado",
                                                    "Tu comprobante de pago fue rechazado.\n" +
                                                    "Por favor sube uno nuevo desde la app.",
                                                    "❌");
                                        }
                                        finish();
                                    });
                        })
                        .setNegativeButton("Cancelar", null)
                        .show()
        );
    }

    private void cargarDatosCita() {
        db.collection("citas").document(idCita).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    String tratamiento = doc.getString("tratamiento");
                    String fecha       = doc.getString("fecha");
                    String hora        = doc.getString("horaDisplay");
                    if (hora == null) hora = doc.getString("hora");
                    String estadoPago  = doc.getString("estadoPago");
                    Double monto       = doc.getDouble("montoAnticipo");
                    String comprobante = doc.getString("comprobanteBase64");
                    String idPaciente  = doc.getString("idPaciente");

                    // Guardar para usar en ntfy
                    idPacienteGuardado  = idPaciente;
                    tratamientoGuardado = tratamiento;

                    tvInfoCita.setText(
                            "🦷 " + (tratamiento != null ? tratamiento : "Cita") +
                            "\n📅 " + (fecha != null ? fecha : "--") +
                            "  🕐 " + (hora != null ? hora : "--"));

                    if (monto != null && monto > 0) {
                        tvMonto.setText(String.format(
                                "💰 Anticipo esperado: $%.2f MXN", monto));
                    } else {
                        tvMonto.setText("💰 Monto no definido");
                    }

                    // Estado visual
                    if ("confirmado".equals(estadoPago)) {
                        tvEstadoPago.setText("✅ Pago ya confirmado");
                        tvEstadoPago.setTextColor(0xFF2E7D32);
                        tvEstadoPago.setVisibility(View.VISIBLE);
                        btnConfirmarPago.setEnabled(false);
                        btnRechazarPago.setEnabled(false);
                    } else if ("rechazado".equals(estadoPago)) {
                        tvEstadoPago.setText("❌ Comprobante rechazado");
                        tvEstadoPago.setTextColor(0xFFB71C1C);
                        tvEstadoPago.setVisibility(View.VISIBLE);
                    } else if ("pagado".equals(estadoPago)) {
                        tvEstadoPago.setText("📤 Comprobante enviado — revisión pendiente");
                        tvEstadoPago.setTextColor(0xFF1565C0);
                        tvEstadoPago.setVisibility(View.VISIBLE);
                    } else {
                        tvEstadoPago.setText("⏳ El paciente aún no ha enviado comprobante");
                        tvEstadoPago.setTextColor(0xFF888888);
                        tvEstadoPago.setVisibility(View.VISIBLE);
                        btnConfirmarPago.setEnabled(false);
                        btnRechazarPago.setEnabled(false);
                    }

                    // Mostrar imagen
                    if (comprobante != null && !comprobante.isEmpty()) {
                        try {
                            byte[] bytes = Base64.decode(comprobante, Base64.DEFAULT);
                            Bitmap bmp   = BitmapFactory.decodeByteArray(
                                    bytes, 0, bytes.length);
                            if (bmp != null) {
                                ivComprobante.setImageBitmap(bmp);
                                ivComprobante.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception ignored) {}
                    }
                });
    }
}
