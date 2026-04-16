package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CuestionarioBienvenidaActivity extends AppCompatActivity {

    private EditText etFechaNac, etTelefono, etTipoSangre, etAlergias,
            etMedicamentos, etEnfermedades, etContactoEmergencia;
    private Button btnGuardar, btnOmitir;
    private TextView tvSubtitulo;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String uid;
    private boolean esNuevo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cuestionario_bienvenida);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        uid     = getIntent().getStringExtra("uid");
        esNuevo = getIntent().getBooleanExtra("esNuevo", false);

        if (uid == null && mAuth.getCurrentUser() != null) {
            uid = mAuth.getCurrentUser().getUid();
        }

        etFechaNac           = findViewById(R.id.etFechaNacBienvenida);
        etTelefono           = findViewById(R.id.etTelefonoBienvenida);
        etTipoSangre         = findViewById(R.id.etTipoSangreBienvenida);
        etAlergias           = findViewById(R.id.etAlergiasBienvenida);
        etMedicamentos       = findViewById(R.id.etMedicamentosBienvenida);
        etEnfermedades       = findViewById(R.id.etEnfermedadesBienvenida);
        etContactoEmergencia = findViewById(R.id.etContactoEmergenciaBienvenida);
        btnGuardar           = findViewById(R.id.btnGuardarBienvenida);
        btnOmitir            = findViewById(R.id.btnOmitirBienvenida);
        tvSubtitulo          = findViewById(R.id.tvSubtituloBienvenida);

        if (esNuevo) {
            tvSubtitulo.setText(
                    "¡Bienvenido! Completa tu perfil médico básico. " +
                    "Puedes hacerlo ahora o más tarde.");
        } else {
            tvSubtitulo.setText(
                    "Completa tu información médica para que el doctor " +
                    "pueda atenderte mejor.");
        }

        btnGuardar.setOnClickListener(v -> guardarInformacion());
        btnOmitir.setOnClickListener(v -> {
            if (uid != null) {
                db.collection("usuarios").document(uid)
                        .update("cuestionarioCompletado", false);
            }
            irAInicio();
        });

        // FIX 10: usar OnBackPressedCallback en lugar de onBackPressed() deprecado
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (esNuevo) {
                            Toast.makeText(CuestionarioBienvenidaActivity.this,
                                    "Usa el botón \"Omitir\" para continuar",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }

    private void guardarInformacion() {
        if (uid == null) { irAInicio(); return; }

        String fechaNac     = etFechaNac.getText().toString().trim();
        String telefono     = etTelefono.getText().toString().trim();
        String tipoSangre   = etTipoSangre.getText().toString().trim();
        String alergias     = etAlergias.getText().toString().trim();
        String medicamentos = etMedicamentos.getText().toString().trim();
        String enfermedades = etEnfermedades.getText().toString().trim();
        String contactoEmer = etContactoEmergencia.getText().toString().trim();

        btnGuardar.setEnabled(false);
        btnGuardar.setText("Guardando...");

        Map<String, Object> datos = new HashMap<>();
        datos.put("fechaNacimiento",      fechaNac);
        datos.put("telefono",             telefono);
        datos.put("tipoSangre",           tipoSangre);
        datos.put("alergias",             alergias);
        datos.put("medicamentos",         medicamentos);
        datos.put("enfermedadesCronicas", enfermedades);
        datos.put("contactoEmergencia",   contactoEmer);
        datos.put("cuestionarioCompletado", true);

        db.collection("usuarios").document(uid)
                .update(datos)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Perfil guardado",
                            Toast.LENGTH_SHORT).show();
                    irAInicio();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al guardar. Intenta de nuevo.",
                            Toast.LENGTH_SHORT).show();
                    btnGuardar.setEnabled(true);
                    btnGuardar.setText("GUARDAR Y CONTINUAR");
                });
    }

    private void irAInicio() {
        startActivity(new Intent(this, PacienteMainActivity.class));
        finish();
    }
}
