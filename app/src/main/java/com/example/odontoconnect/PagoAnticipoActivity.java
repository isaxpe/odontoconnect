package com.example.odontoconnect;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PagoAnticipoActivity extends AppCompatActivity {

    // IDs exactos del activity_pago_anticipo.xml
    private TextView tvNombreTratamiento, tvMontoPagar,
                     tvDatosBancarios, tvEstadoPago, tvFotoEstado;
    private ImageView ivComprobante;
    private MaterialButton btnSeleccionarFoto, btnEnviarComprobante;

    private FirebaseFirestore db;
    private String idCita   = null;
    private String fotoBase64 = null;

    // FIX: GetContent funciona en todos los Android sin permisos extras
    private final ActivityResultLauncher<String> seleccionarFoto =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            String b64 = comprimirYConvertir(uri);
                            if (b64 != null) {
                                fotoBase64 = b64;
                                ivComprobante.setImageURI(uri);
                                ivComprobante.setVisibility(View.VISIBLE);
                                if (tvFotoEstado != null) {
                                    tvFotoEstado.setText("✅ Foto lista para enviar");
                                    tvFotoEstado.setTextColor(0xFF1565C0);
                                    tvFotoEstado.setVisibility(View.VISIBLE);
                                }
                                btnEnviarComprobante.setEnabled(true);
                            }
                        }
                    });

    private final ActivityResultLauncher<String> pedirPermiso =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) abrirGaleria();
                        else Toast.makeText(this,
                                "Se necesita permiso para acceder a las fotos",
                                Toast.LENGTH_LONG).show();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pago_anticipo);

        db     = FirebaseFirestore.getInstance();
        idCita = getIntent().getStringExtra("idCita");
        if (idCita == null) { finish(); return; }

        // Usar los IDs que SÍ existen en el XML
        tvNombreTratamiento  = findViewById(R.id.tvNombreTratamiento);
        tvMontoPagar         = findViewById(R.id.tvMontoPagar);
        tvDatosBancarios     = findViewById(R.id.tvDatosBancarios);
        tvEstadoPago         = findViewById(R.id.tvEstadoPago);
        tvFotoEstado         = findViewById(R.id.tvFotoEstado);
        ivComprobante        = findViewById(R.id.ivComprobante);
        btnSeleccionarFoto   = findViewById(R.id.btnSeleccionarFoto);
        btnEnviarComprobante = findViewById(R.id.btnEnviarComprobante);

        btnEnviarComprobante.setEnabled(false);
        btnSeleccionarFoto.setOnClickListener(v -> verificarPermisoYAbrir());
        btnEnviarComprobante.setOnClickListener(v -> enviarComprobante());

        cargarDatosCita();
    }

    private void verificarPermisoYAbrir() {
        // Android 13+ usa READ_MEDIA_IMAGES, anteriores READ_EXTERNAL_STORAGE
        String permiso = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permiso)
                == PackageManager.PERMISSION_GRANTED) {
            abrirGaleria();
        } else {
            pedirPermiso.launch(permiso);
        }
    }

    private void abrirGaleria() {
        seleccionarFoto.launch("image/*");
    }

    private void cargarDatosCita() {
        db.collection("citas").document(idCita).get()
                .addOnSuccessListener(citaDoc -> {
                    if (!citaDoc.exists()) return;

                    String estadoPago  = citaDoc.getString("estadoPago");
                    String tratamiento = citaDoc.getString("tratamiento");
                    String idDoctor    = citaDoc.getString("idDoctor");
                    Double precio      = citaDoc.getDouble("precioTratamiento");

                    // Mostrar nombre del tratamiento
                    if (tvNombreTratamiento != null)
                        tvNombreTratamiento.setText(
                                tratamiento != null ? tratamiento : "Consulta");

                    // Calcular y mostrar anticipo (20%)
                    if (tvMontoPagar != null) {
                        if (precio != null && precio > 0) {
                            double anticipo = precio * 0.20;
                            tvMontoPagar.setText("$" + String.format(
                                    Locale.getDefault(), "%.2f", anticipo) +
                                    " MXN (20% de anticipo)");
                        } else {
                            tvMontoPagar.setText("Consulta el monto con el doctor");
                        }
                    }

                    // Cargar datos bancarios del doctor
                    if (idDoctor != null) cargarDatosDoctor(idDoctor);

                    // Estado actual del pago
                    actualizarEstadoPago(estadoPago);
                });
    }

    private void actualizarEstadoPago(String estadoPago) {
        if ("rechazado".equals(estadoPago)) {
            if (tvFotoEstado != null) {
                tvFotoEstado.setText(
                        "❌ Tu comprobante fue rechazado.\n" +
                        "Sube una foto más clara o legible.");
                tvFotoEstado.setTextColor(0xFFB71C1C);
                tvFotoEstado.setVisibility(View.VISIBLE);
            }
            btnSeleccionarFoto.setEnabled(true);
            btnEnviarComprobante.setEnabled(false);
            btnEnviarComprobante.setText("REENVIAR COMPROBANTE");
            if (tvEstadoPago != null) tvEstadoPago.setVisibility(View.GONE);

        } else if ("pagado".equals(estadoPago)) {
            if (tvEstadoPago != null) {
                tvEstadoPago.setText(
                        "⏳ Comprobante enviado\nEsperando confirmación del doctor...");
                tvEstadoPago.setVisibility(View.VISIBLE);
            }
            btnSeleccionarFoto.setEnabled(false);
            btnEnviarComprobante.setEnabled(false);
            btnEnviarComprobante.setText("Enviado ✓");

        } else if ("confirmado".equals(estadoPago)) {
            if (tvEstadoPago != null) {
                tvEstadoPago.setText("✅ Anticipo confirmado por el doctor");
                tvEstadoPago.setVisibility(View.VISIBLE);
            }
            btnSeleccionarFoto.setEnabled(false);
            btnEnviarComprobante.setEnabled(false);
            btnEnviarComprobante.setText("Pago confirmado ✓");
        }
    }

    private void cargarDatosDoctor(String idDoctor) {
        db.collection("usuarios").document(idDoctor).get()
                .addOnSuccessListener(doc -> {
                    String banco   = doc.getString("banco");
                    String clabe   = doc.getString("clabe");
                    String titular = doc.getString("titularCuenta");

                    StringBuilder sb = new StringBuilder();
                    if (banco   != null) sb.append("Banco: ").append(banco).append("\n");
                    if (clabe   != null) sb.append("CLABE: ").append(clabe).append("\n");
                    if (titular != null) sb.append("Titular: ").append(titular);

                    if (tvDatosBancarios != null)
                        tvDatosBancarios.setText(sb.length() > 0
                                ? sb.toString().trim()
                                : "El doctor aún no ha configurado sus datos bancarios.");
                });
    }

    private void enviarComprobante() {
        if (fotoBase64 == null) {
            Toast.makeText(this, "Selecciona una foto primero",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnEnviarComprobante.setEnabled(false);
        btnEnviarComprobante.setText("Enviando...");

        db.collection("citas").document(idCita).get()
                .addOnSuccessListener(citaDoc -> {
                    Double precio = citaDoc.getDouble("precioTratamiento");
                    double anticipo = precio != null ? precio * 0.20 : 0;

                    Map<String, Object> datos = new HashMap<>();
                    datos.put("comprobanteBase64", fotoBase64);
                    datos.put("estadoPago",        "pagado");
                    datos.put("fechaPago",
                            new SimpleDateFormat("d-M-yyyy HH:mm", Locale.getDefault())
                                    .format(new Date()));
                    if (anticipo > 0) datos.put("montoAnticipo",    anticipo);
                    if (precio   != null) datos.put("precioTratamiento", precio);

                    db.collection("citas").document(idCita)
                            .update(datos)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this,
                                        "✅ Comprobante enviado. El doctor lo revisará pronto.",
                                        Toast.LENGTH_LONG).show();

                                // FIX: notificar al doctor que llegó el comprobante
                                db.collection("citas").document(idCita).get()
                                        .addOnSuccessListener(citaActualizada -> {
                                            String idDoctor = citaActualizada.getString("idDoctor");
                                            String trat     = citaActualizada.getString("tratamiento");
                                            String idPac    = citaActualizada.getString("idPaciente");
                                            if (idDoctor != null && idPac != null) {
                                                // Obtener nombre del paciente
                                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                        .collection("usuarios").document(idPac).get()
                                                        .addOnSuccessListener(pacDoc -> {
                                                            String nombre = pacDoc.getString("Nombre");
                                                            if (nombre == null) nombre = pacDoc.getString("nombre");
                                                            NtfyHelper.comprobanteRecibido(
                                                                    idDoctor,
                                                                    nombre != null ? nombre : "Un paciente",
                                                                    trat != null ? trat : "una cita");
                                                        });
                                            }
                                        });
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this,
                                        "Error al enviar: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                                btnEnviarComprobante.setEnabled(true);
                                btnEnviarComprobante.setText("ENVIAR COMPROBANTE");
                            });
                });
    }

    /**
     * Comprime la imagen a máx 800x800 px y calidad 70%
     * Resultado típico: 40-100 KB → Base64 ~130 KB (muy bajo para Firestore)
     */
    private String comprimirYConvertir(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap original = BitmapFactory.decodeStream(is);
            if (original == null) {
                Toast.makeText(this, "No se pudo leer la imagen",
                        Toast.LENGTH_SHORT).show();
                return null;
            }

            // Escalar manteniendo proporción
            int maxDim = 800;
            int w = original.getWidth(), h = original.getHeight();
            float escala = (w > maxDim || h > maxDim)
                    ? (w > h ? (float) maxDim / w : (float) maxDim / h)
                    : 1f;

            Bitmap escalado = Bitmap.createScaledBitmap(
                    original, Math.round(w * escala), Math.round(h * escala), true);

            // Comprimir JPEG calidad 70%
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            escalado.compress(Bitmap.CompressFormat.JPEG, 70, bos);
            byte[] bytes = bos.toByteArray();

            // Si sigue grande, intentar calidad 40%
            if (bytes.length > 900_000) {
                bos.reset();
                escalado.compress(Bitmap.CompressFormat.JPEG, 40, bos);
                bytes = bos.toByteArray();
            }

            if (bytes.length > 900_000) {
                Toast.makeText(this,
                        "Imagen demasiado grande. Usa una foto más pequeña.",
                        Toast.LENGTH_LONG).show();
                return null;
            }

            Toast.makeText(this,
                    "✅ Foto procesada (" + (bytes.length / 1024) + " KB)",
                    Toast.LENGTH_SHORT).show();

            return Base64.encodeToString(bytes, Base64.DEFAULT);

        } catch (Exception e) {
            Toast.makeText(this, "Error al procesar imagen: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            return null;
        }
    }
}
