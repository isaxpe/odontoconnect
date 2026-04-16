package com.example.odontoconnect;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegistrarFamiliarActivity extends AppCompatActivity {

    private EditText etNombre, etFechaNacimiento, etParentesco, etEdad,
            etTelefono, etDireccion, etOcupacion, etTelefonoEmergencia,
            etAlergias;
    private CheckBox cbResponsableLegal, cbContactoEmergencia;
    private Button btnGuardarFamiliar;

    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registrar_familiar);

        db     = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        etNombre             = findViewById(R.id.etNombreFamiliarReg);
        etFechaNacimiento    = findViewById(R.id.etFechaNacFamiliar);
        etParentesco         = findViewById(R.id.etParentescoFamiliar);
        etEdad               = findViewById(R.id.etEdadFamiliar);
        etTelefono           = findViewById(R.id.etTelefonoFamiliar);
        etDireccion          = findViewById(R.id.etDireccionFamiliar);
        etOcupacion          = findViewById(R.id.etOcupacionFamiliar);
        etTelefonoEmergencia = findViewById(R.id.etTelefonoEmergencia);
        etAlergias           = findViewById(R.id.etAlergiasFamiliar);
        cbResponsableLegal   = findViewById(R.id.cbResponsableLegal);
        cbContactoEmergencia = findViewById(R.id.cbContactoEmergencia);
        btnGuardarFamiliar   = findViewById(R.id.btnGuardarFamiliar);

        btnGuardarFamiliar.setOnClickListener(v -> guardarFamiliar());
    }

    private void guardarFamiliar() {
        String nombre        = etNombre.getText().toString().trim();
        String fechaNac      = etFechaNacimiento.getText().toString().trim();
        String parentesco    = etParentesco.getText().toString().trim();
        String edadStr       = etEdad.getText().toString().trim();
        String telefono      = etTelefono.getText().toString().trim();
        String direccion     = etDireccion.getText().toString().trim();
        String ocupacion     = etOcupacion.getText().toString().trim();
        String telEmergencia = etTelefonoEmergencia.getText().toString().trim();
        String alergias      = etAlergias.getText().toString().trim();
        boolean esResponsable = cbResponsableLegal.isChecked();
        boolean esEmergencia  = cbContactoEmergencia.isChecked();

        if (nombre.isEmpty()) {
            Toast.makeText(this, "El nombre es obligatorio", Toast.LENGTH_SHORT).show();
            return;
        }
        if (parentesco.isEmpty()) {
            Toast.makeText(this, "El parentesco es obligatorio", Toast.LENGTH_SHORT).show();
            return;
        }
        if (telefono.isEmpty()) {
            Toast.makeText(this, "El teléfono es obligatorio", Toast.LENGTH_SHORT).show();
            return;
        }

        btnGuardarFamiliar.setEnabled(false);
        btnGuardarFamiliar.setText("Guardando...");

        Integer edad = null;
        if (!edadStr.isEmpty()) {
            try {
                edad = Integer.parseInt(edadStr);
            } catch (NumberFormatException e) {
                edad = null;
            }
        }

        Map<String, Object> familiar = new HashMap<>();
        familiar.put("nombre",               nombre);
        familiar.put("fechaNacimiento",      fechaNac);
        familiar.put("parentesco",           parentesco);
        familiar.put("edad",                 edad);
        familiar.put("telefono",             telefono);
        familiar.put("direccion",            direccion);
        familiar.put("ocupacion",            ocupacion);
        familiar.put("telefonoEmergencia",   telEmergencia);
        familiar.put("alergias",             alergias);
        familiar.put("esResponsableLegal",   esResponsable);
        familiar.put("esContactoEmergencia", esEmergencia);
        familiar.put("idPacienteTitular",    userId);

        db.collection("usuarios").document(userId)
                .collection("familiares")
                .add(familiar)
                .addOnSuccessListener(documentReference -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                "Familiar registrado exitosamente",
                                Toast.LENGTH_LONG).show();
                        btnGuardarFamiliar.setEnabled(true);
                        btnGuardarFamiliar.setText("GUARDAR FAMILIAR");
                        finish();
                    });
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                "Error al registrar: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        btnGuardarFamiliar.setEnabled(true);
                        btnGuardarFamiliar.setText("GUARDAR FAMILIAR");
                    });
                });
    }
}