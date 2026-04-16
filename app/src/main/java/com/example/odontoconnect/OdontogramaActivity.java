package com.example.odontoconnect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class OdontogramaActivity extends AppCompatActivity {

    private OdontogramaView odontogramaView;
    private TextView tvNombrePaciente, tvFechaOdonto;
    private EditText etNotasGenerales;
    private MaterialButton btnGuardarOdonto;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String idPaciente;

    private static final String[] CONDICIONES = {
            "✅ Sano",
            "🔴 Caries",
            "🔵 Obturado (empaste)",
            "⬛ Ausente (extraído)",
            "🟡 Corona",
            "🟠 Fractura",
            "🟢 En tratamiento"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_odontograma);

        db         = FirebaseFirestore.getInstance();
        mAuth      = FirebaseAuth.getInstance();
        idPaciente = getIntent().getStringExtra("idPaciente");
        if (idPaciente == null) { finish(); return; }

        odontogramaView  = findViewById(R.id.odontogramaView);
        tvNombrePaciente = findViewById(R.id.tvNombrePacienteOdonto);
        tvFechaOdonto    = findViewById(R.id.tvFechaOdonto);
        etNotasGenerales = findViewById(R.id.etNotasGeneralesOdonto);
        btnGuardarOdonto = findViewById(R.id.btnGuardarOdonto);

        tvFechaOdonto.setText("📅 " + new SimpleDateFormat(
                "d-M-yyyy HH:mm", Locale.getDefault()).format(new Date()));

        // Cargar nombre del paciente
        db.collection("usuarios").document(idPaciente).get()
                .addOnSuccessListener(doc -> {
                    String nombre = doc.getString("Nombre");
                    if (nombre == null) nombre = doc.getString("nombre");
                    tvNombrePaciente.setText(
                            "Paciente: " + (nombre != null ? nombre : "--"));
                });

        // FIX: cargar odontograma SIN orderBy para evitar crash por índice faltante
        cargarExistente();

        odontogramaView.setOnDienteClickListener(this::mostrarDialogo);
        btnGuardarOdonto.setOnClickListener(v -> guardar());
    }

    private void mostrarDialogo(int num, int condActual) {
        String[] opciones = new String[CONDICIONES.length];
        for (int i = 0; i < CONDICIONES.length; i++)
            opciones[i] = (i == condActual ? "● " : "  ") + CONDICIONES[i];

        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_diente, null);
        EditText etNota = dv.findViewById(R.id.etNotaDiente);

        String notaExistente = odontogramaView.getNotas().get(num);
        if (notaExistente != null) etNota.setText(notaExistente);

        new AlertDialog.Builder(this)
                .setTitle("🦷 Diente " + num)
                .setView(dv)
                .setSingleChoiceItems(opciones, condActual, (d, w) ->
                        odontogramaView.setCondicion(num, w))
                .setPositiveButton("Guardar", (d, w) -> {
                    String nota = etNota.getText().toString().trim();
                    odontogramaView.setNota(num, nota);
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void cargarExistente() {
        // FIX: Sin orderBy para evitar error de índice compuesto en Firestore
        // Simplemente tomamos todos los odontogramas y buscamos el más reciente manualmente
        db.collection("usuarios").document(idPaciente)
                .collection("expediente")
                .whereEqualTo("tipo", "odontograma")
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) return;

                    // Buscar el más reciente manualmente (sin orderBy)
                    QueryDocumentSnapshot masReciente = null;
                    long maxTimestamp = 0;
                    for (QueryDocumentSnapshot doc : snap) {
                        Long ts = doc.getLong("timestamp");
                        if (ts != null && ts > maxTimestamp) {
                            maxTimestamp = ts;
                            masReciente = doc;
                        }
                    }

                    if (masReciente == null) return;
                    Map<String, Object> data = masReciente.getData();
                    if (data == null) return;

                    if (data.containsKey("estadoDientes")) {
                        odontogramaView.cargarEstado(
                                (Map<String, Object>) data.get("estadoDientes"));
                    }
                    if (data.containsKey("notasGenerales")) {
                        etNotasGenerales.setText((String) data.get("notasGenerales"));
                    }
                    Toast.makeText(this, "Odontograma previo cargado",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        // Error al cargar previo — continuar con odontograma vacío
                        Toast.makeText(this, "Nuevo odontograma",
                                Toast.LENGTH_SHORT).show()
                );
    }

    private void guardar() {
        if (mAuth.getCurrentUser() == null) return;
        String idDoctor = mAuth.getCurrentUser().getUid();

        Map<String, Object> estadoStr = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : odontogramaView.getEstado().entrySet())
            estadoStr.put(String.valueOf(e.getKey()), e.getValue());

        Map<String, Object> notasStr = new HashMap<>();
        for (Map.Entry<Integer, String> e : odontogramaView.getNotas().entrySet())
            notasStr.put(String.valueOf(e.getKey()), e.getValue());

        String fecha = new SimpleDateFormat("d-M-yyyy",
                Locale.getDefault()).format(new Date());

        Map<String, Object> entrada = new HashMap<>();
        entrada.put("tipo",           "odontograma");
        entrada.put("titulo",         "Odontograma - " + fecha);
        entrada.put("fecha",          fecha);
        entrada.put("timestamp",      System.currentTimeMillis());
        entrada.put("idDoctor",       idDoctor);
        entrada.put("estadoDientes",  estadoStr);
        entrada.put("notasDientes",   notasStr);
        entrada.put("notasGenerales",
                etNotasGenerales.getText().toString().trim());

        btnGuardarOdonto.setEnabled(false);
        btnGuardarOdonto.setText("Guardando...");

        db.collection("usuarios").document(idPaciente)
                .collection("expediente").add(entrada)
                .addOnSuccessListener(r -> {
                    Toast.makeText(this, "✅ Odontograma guardado",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    btnGuardarOdonto.setEnabled(true);
                    btnGuardarOdonto.setText("GUARDAR ODONTOGRAMA");
                });
    }
}
