package com.example.odontoconnect;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class TriajeActivity extends AppCompatActivity {

    private RadioGroup rgDolor, rgVisita, rgMedicamentos;
    private RadioButton rbMedicamentosSi;
    private View layoutDetalleMedicamentos;
    private android.widget.EditText etDescripcionMolestia, etMedicamentosDetalle,
            etOtraInformacion;
    private ImageView ivFotoMolestia;
    private TextView tvFotoSeleccionada;
    private MaterialButton btnSeleccionarFoto, btnEnviarTriaje;

    // Banner de urgencia
    private View bannerUrgencia;
    private TextView tvMensajeUrgencia;

    private FirebaseFirestore db;
    private String idCita;
    private String fotoBase64 = null;

    private final ActivityResultLauncher<Intent> seleccionarFoto =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK &&
                                result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                ivFotoMolestia.setImageURI(uri);
                                ivFotoMolestia.setVisibility(View.VISIBLE);
                                tvFotoSeleccionada.setText("✅ Comprobante seleccionado");
                                tvFotoSeleccionada.setTextColor(0xFF1565C0);
                                fotoBase64 = convertirABase64(uri);
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_triaje);

        db     = FirebaseFirestore.getInstance();
        idCita = getIntent().getStringExtra("idCita");
        if (idCita == null) { finish(); return; }

        rgDolor               = findViewById(R.id.rgDolor);
        rgVisita              = findViewById(R.id.rgVisita);
        rgMedicamentos        = findViewById(R.id.rgMedicamentos);
        rbMedicamentosSi      = findViewById(R.id.rbMedicamentosSi);
        layoutDetalleMedicamentos = findViewById(R.id.layoutDetalleMedicamentos);
        etDescripcionMolestia = findViewById(R.id.etDescripcionMolestia);
        etMedicamentosDetalle = findViewById(R.id.etMedicamentosDetalle);
        etOtraInformacion     = findViewById(R.id.etOtraInformacion);
        ivFotoMolestia        = findViewById(R.id.ivFotoMolestia);
        tvFotoSeleccionada    = findViewById(R.id.tvFotoSeleccionada);
        btnSeleccionarFoto    = findViewById(R.id.btnSeleccionarFoto);
        btnEnviarTriaje       = findViewById(R.id.btnEnviarTriaje);
        bannerUrgencia        = findViewById(R.id.bannerUrgencia);
        tvMensajeUrgencia     = findViewById(R.id.tvMensajeUrgencia);

        // Mostrar/ocultar detalle medicamentos
        rgMedicamentos.setOnCheckedChangeListener((group, checkedId) -> {
            if (layoutDetalleMedicamentos != null)
                layoutDetalleMedicamentos.setVisibility(
                        checkedId == R.id.rbMedicamentosSi
                                ? View.VISIBLE : View.GONE);
        });

        // URGENCIA: detectar cuando selecciona dolor fuerte
        rgDolor.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton rb = findViewById(checkedId);
            if (rb != null && bannerUrgencia != null) {
                String texto = rb.getText().toString();
                if (UrgenciaHelper.esUrgente(texto)) {
                    bannerUrgencia.setVisibility(View.VISIBLE);
                    if (tvMensajeUrgencia != null)
                        tvMensajeUrgencia.setText(
                                "🚨 Detectamos que tienes dolor fuerte.\n" +
                                "Al enviar este formulario, el sistema buscará " +
                                "automáticamente el horario más cercano disponible.");
                } else {
                    bannerUrgencia.setVisibility(View.GONE);
                }
            }
        });

        btnSeleccionarFoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            seleccionarFoto.launch(intent);
        });

        btnEnviarTriaje.setOnClickListener(v -> guardarTriaje());
    }

    private void guardarTriaje() {
        if (rgDolor.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, "Por favor responde la pregunta sobre el dolor",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (rgVisita.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, "Por favor indica si es tu primera visita",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (rgMedicamentos.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, "Por favor indica si tomas medicamentos",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton rbDolor = findViewById(rgDolor.getCheckedRadioButtonId());
        RadioButton rbVisita = findViewById(rgVisita.getCheckedRadioButtonId());
        RadioButton rbMed   = findViewById(rgMedicamentos.getCheckedRadioButtonId());

        String nivelDolor      = rbDolor.getText().toString();
        String primeraVisita   = rbVisita.getText().toString();
        String tomaMedicamentos = rbMed.getText().toString();
        String descripcion     = etDescripcionMolestia.getText().toString().trim();
        String detalleMed      = etMedicamentosDetalle.getText().toString().trim();
        String otraInfo        = etOtraInformacion.getText().toString().trim();

        btnEnviarTriaje.setEnabled(false);
        btnEnviarTriaje.setText("Enviando...");

        Map<String, Object> triaje = new HashMap<>();
        triaje.put("nivelDolor",          nivelDolor);
        triaje.put("primeraVisita",       primeraVisita);
        triaje.put("tomaMedicamentos",    tomaMedicamentos);
        triaje.put("descripcionMolestia", descripcion);
        triaje.put("medicamentosDetalle", detalleMed);
        triaje.put("otraInformacion",     otraInfo);
        triaje.put("completado",          true);

        if (fotoBase64 != null) triaje.put("fotoBase64", fotoBase64);

        // Detectar urgencia ANTES de guardar
        final boolean esUrgente = UrgenciaHelper.esUrgente(nivelDolor);

        db.collection("citas").document(idCita)
                .update(triaje)
                .addOnSuccessListener(aVoid -> {
                    if (esUrgente) {
                        // Mostrar mensaje especial y activar reagendado
                        Toast.makeText(this,
                                "🚨 Urgencia detectada — buscando horario cercano...",
                                Toast.LENGTH_LONG).show();

                        // Leer idPaciente, idDoctor y tratamiento de la cita
                        db.collection("citas").document(idCita).get()
                                .addOnSuccessListener(citaDoc -> {
                                    String idPaciente  = citaDoc.getString("idPaciente");
                                    String idDoctor    = citaDoc.getString("idDoctor");
                                    String tratamiento = citaDoc.getString("tratamiento");

                                    // Activar lógica de urgencia automáticamente
                                    UrgenciaHelper.procesarUrgencia(
                                            idCita, idPaciente, idDoctor,
                                            tratamiento != null ? tratamiento : "Cita");

                                    irAInicio();
                                });
                    } else {
                        Toast.makeText(this,
                                "✅ Información enviada al doctor",
                                Toast.LENGTH_SHORT).show();
                        irAInicio();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Error al enviar. Intenta de nuevo.",
                            Toast.LENGTH_SHORT).show();
                    btnEnviarTriaje.setEnabled(true);
                    btnEnviarTriaje.setText("ENVIAR AL DOCTOR");
                });
    }

    private void irAInicio() {
        Intent intent = new Intent(this, PacienteMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private String convertirABase64(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) bos.write(buf, 0, len);
            byte[] bytes = bos.toByteArray();
            if (bytes.length > 800_000) {
                Toast.makeText(this,
                        "Imagen muy grande. Usa una más pequeña.",
                        Toast.LENGTH_LONG).show();
                return null;
            }
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
    }
}
