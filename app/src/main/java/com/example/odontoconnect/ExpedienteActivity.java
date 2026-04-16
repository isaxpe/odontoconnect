package com.example.odontoconnect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExpedienteActivity extends AppCompatActivity {

    private LinearLayout contenedorExpediente;
    private TextView tvMensajeVacio, tvNombrePacienteExp;
    private MaterialButton btnAgregarNota;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String idPaciente;
    private boolean esDoctor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expediente);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        idPaciente = getIntent().getStringExtra("idPaciente");
        esDoctor   = getIntent().getBooleanExtra("esDoctor", false);

        if (idPaciente == null && mAuth.getCurrentUser() != null)
            idPaciente = mAuth.getCurrentUser().getUid();
        if (idPaciente == null) { finish(); return; }

        contenedorExpediente = findViewById(R.id.contenedorExpediente);
        tvMensajeVacio       = findViewById(R.id.tvMensajeVacioExpediente);
        tvNombrePacienteExp  = findViewById(R.id.tvNombrePacienteExp);
        btnAgregarNota       = findViewById(R.id.btnAgregarNota);

        if (btnAgregarNota != null) {
            if (esDoctor) {
                btnAgregarNota.setVisibility(View.VISIBLE);
                btnAgregarNota.setOnClickListener(v -> mostrarDialogoNota());
            } else {
                btnAgregarNota.setVisibility(View.GONE);
            }
        }

        db.collection("usuarios").document(idPaciente).get()
                .addOnSuccessListener(doc -> {
                    String nombre = doc.getString("Nombre");
                    if (nombre == null) nombre = doc.getString("nombre");
                    if (tvNombrePacienteExp != null)
                        tvNombrePacienteExp.setText(
                                nombre != null ? nombre : "Expediente clínico");
                });

        cargarExpediente();
    }

    private void cargarExpediente() {
        // FIX: Sin orderBy — ordenamos manualmente para evitar crash por índice
        db.collection("usuarios").document(idPaciente)
                .collection("expediente")
                .get()
                .addOnSuccessListener(snap -> {
                    contenedorExpediente.removeAllViews();

                    if (snap.isEmpty()) {
                        if (tvMensajeVacio != null)
                            tvMensajeVacio.setVisibility(View.VISIBLE);
                        return;
                    }
                    if (tvMensajeVacio != null)
                        tvMensajeVacio.setVisibility(View.GONE);

                    // Ordenar por timestamp descendente manualmente
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) docs.add(doc);
                    Collections.sort(docs, (a, b) -> {
                        Long ta = a.getLong("timestamp");
                        Long tb = b.getLong("timestamp");
                        if (ta == null) ta = 0L;
                        if (tb == null) tb = 0L;
                        return Long.compare(tb, ta); // descendente
                    });

                    for (QueryDocumentSnapshot doc : docs) {
                        crearTarjeta(doc);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al cargar expediente",
                                Toast.LENGTH_SHORT).show()
                );
    }

    private void crearTarjeta(QueryDocumentSnapshot doc) {
        String tipo    = doc.getString("tipo");
        String titulo  = doc.getString("titulo");
        String desc    = doc.getString("descripcion");
        String fecha   = doc.getString("fecha");
        String notas   = doc.getString("notasGenerales");

        View tarjeta = LayoutInflater.from(this)
                .inflate(R.layout.item_expediente, contenedorExpediente, false);

        TextView tvTitulo = tarjeta.findViewById(R.id.tvTituloExpediente);
        TextView tvDesc   = tarjeta.findViewById(R.id.tvDescExpediente);
        TextView tvFecha  = tarjeta.findViewById(R.id.tvFechaExpediente);
        TextView tvTipo   = tarjeta.findViewById(R.id.tvTipoExpediente);

        String emoji = "odontograma".equals(tipo) ? "🦷"
                : "receta".equals(tipo) ? "💊"
                : "nota".equals(tipo)   ? "📝" : "📋";

        if (tvTitulo != null)
            tvTitulo.setText(emoji + " " + (titulo != null ? titulo : "Entrada"));
        if (tvFecha != null)
            tvFecha.setText(fecha != null ? fecha : "--");
        if (tvTipo != null)
            tvTipo.setText(tipo != null ? tipo.toUpperCase() : "--");

        String contenido = desc != null ? desc : (notas != null ? notas : "");
        if (tvDesc != null)
            tvDesc.setText(!contenido.isEmpty() ? contenido : "Sin descripción");

        contenedorExpediente.addView(tarjeta);
    }

    private void mostrarDialogoNota() {
        View form = LayoutInflater.from(this)
                .inflate(R.layout.dialog_expediente, null);
        EditText etTitulo = form.findViewById(R.id.etTituloExpediente);
        EditText etDesc   = form.findViewById(R.id.etDescripcionExpediente);

        new AlertDialog.Builder(this)
                .setTitle("Agregar nota al expediente")
                .setView(form)
                .setPositiveButton("Guardar", (d, w) -> {
                    String titulo = etTitulo != null
                            ? etTitulo.getText().toString().trim() : "";
                    String desc = etDesc != null
                            ? etDesc.getText().toString().trim() : "";
                    if (titulo.isEmpty()) {
                        Toast.makeText(this, "Escribe un título",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    guardarNota(titulo, desc);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void guardarNota(String titulo, String descripcion) {
        if (mAuth.getCurrentUser() == null) return;
        String fecha = new SimpleDateFormat("d-M-yyyy",
                Locale.getDefault()).format(new Date());

        Map<String, Object> nota = new HashMap<>();
        nota.put("tipo",        "nota");
        nota.put("titulo",      titulo);
        nota.put("descripcion", descripcion);
        nota.put("fecha",       fecha);
        nota.put("timestamp",   System.currentTimeMillis());
        nota.put("idDoctor",    mAuth.getCurrentUser().getUid());

        db.collection("usuarios").document(idPaciente)
                .collection("expediente").add(nota)
                .addOnSuccessListener(r -> {
                    Toast.makeText(this, "✅ Nota agregada",
                            Toast.LENGTH_SHORT).show();
                    cargarExpediente(); // recargar lista
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }
}
