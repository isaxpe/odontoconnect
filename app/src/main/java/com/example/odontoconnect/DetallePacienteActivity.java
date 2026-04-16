package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class DetallePacienteActivity extends AppCompatActivity {

    private TextView tvNombreDetalle, tvIdDetalle, tvTelefonoDetalle,
            tvCorreoDetalle, tvFaltasDetalle, tvEstadoDetalle,
            tvSangreDetalle, tvAlergiasDetalle, tvMedicamentosDetalle;
    private MaterialButton btnVerExpediente, btnAbrirOdontograma,
            btnEditarFaltas, btnDesbloquear;

    private FirebaseFirestore db;
    private String idPaciente;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalle_paciente);

        db         = FirebaseFirestore.getInstance();
        idPaciente = getIntent().getStringExtra("idPaciente");
        if (idPaciente == null) { finish(); return; }

        tvNombreDetalle     = findViewById(R.id.tvNombreDetalle);
        tvIdDetalle         = findViewById(R.id.tvIdDetalle);
        tvTelefonoDetalle   = findViewById(R.id.tvTelefonoDetalle);
        tvCorreoDetalle     = findViewById(R.id.tvCorreoDetalle);
        tvFaltasDetalle     = findViewById(R.id.tvFaltasDetalle);
        tvEstadoDetalle     = findViewById(R.id.tvEstadoDetalle);
        tvSangreDetalle     = findViewById(R.id.tvSangreDetalle);
        tvAlergiasDetalle   = findViewById(R.id.tvAlergiasDetalle);
        tvMedicamentosDetalle = findViewById(R.id.tvMedicamentosDetalle);
        btnVerExpediente    = findViewById(R.id.btnVerExpediente);
        btnAbrirOdontograma = findViewById(R.id.btnAbrirOdontograma);
        btnEditarFaltas     = findViewById(R.id.btnEditarFaltas);
        btnDesbloquear      = findViewById(R.id.btnDesbloquear);

        cargarDatosPaciente();

        // Ver expediente
        btnVerExpediente.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExpedienteActivity.class);
            intent.putExtra("idPaciente", idPaciente);
            intent.putExtra("esDoctor", true);
            startActivity(intent);
        });

        // Abrir odontograma
        btnAbrirOdontograma.setOnClickListener(v -> {
            Intent intent = new Intent(this, OdontogramaActivity.class);
            intent.putExtra("idPaciente", idPaciente);
            startActivity(intent);
        });

        // Editar faltas
        btnEditarFaltas.setOnClickListener(v ->
                mostrarDialogoEditarFaltas());

        // Desbloquear
        btnDesbloquear.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Desbloquear paciente")
                        .setMessage("¿Desbloquear y resetear las faltas de este paciente?")
                        .setPositiveButton("Sí, desbloquear", (d, w) ->
                                db.collection("usuarios").document(idPaciente)
                                        .update("bloqueado", false,
                                                "faltas", 0,
                                                "nivelPenalizacion", 0,
                                                "restriccionHasta", null,
                                                "advertencia", null)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this,
                                                    "✅ Paciente desbloqueado",
                                                    Toast.LENGTH_SHORT).show();
                                            cargarDatosPaciente();
                                        }))
                        .setNegativeButton("Cancelar", null)
                        .show()
        );
    }

    private void cargarDatosPaciente() {
        db.collection("usuarios").document(idPaciente).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    String nombre = doc.getString("Nombre");
                    if (nombre == null) nombre = doc.getString("nombre");
                    String id     = doc.getString("paciente_id");
                    String tel    = doc.getString("telefono");
                    String correo = doc.getString("correo");
                    String sangre = doc.getString("tipoSangre");
                    String alerg  = doc.getString("alergias");
                    String meds   = doc.getString("medicamentos");
                    Long faltas   = doc.getLong("faltas");
                    Boolean bloq  = doc.getBoolean("bloqueado");
                    if (faltas == null) faltas = 0L;
                    if (bloq  == null) bloq  = false;

                    tvNombreDetalle.setText(nombre != null ? nombre : "Paciente");
                    tvIdDetalle.setText("ID: " + (id != null ? id :
                            idPaciente.substring(0, 6).toUpperCase()));
                    tvTelefonoDetalle.setText(tel != null ? tel : "No registrado");
                    tvCorreoDetalle.setText(correo != null ? correo : "--");
                    tvSangreDetalle.setText(sangre != null ? sangre : "--");
                    tvAlergiasDetalle.setText(alerg != null ? alerg : "Ninguna");
                    tvMedicamentosDetalle.setText(meds != null ? meds : "Ninguno");
                    tvFaltasDetalle.setText(faltas + " falta(s)");

                    if (bloq || faltas >= 3) {
                        tvEstadoDetalle.setText("🔴 BLOQUEADO");
                        tvEstadoDetalle.setTextColor(0xFFB71C1C);
                        btnDesbloquear.setEnabled(true);
                    } else {
                        tvEstadoDetalle.setText("🟢 Activo");
                        tvEstadoDetalle.setTextColor(0xFF2E7D32);
                        btnDesbloquear.setEnabled(false);
                    }

                    // Historial de citas
                    cargarHistorialCitas();
                });
    }

    private void cargarHistorialCitas() {
        db.collection("citas")
                .whereEqualTo("idPaciente", idPaciente)
                .get()
                .addOnSuccessListener(snap -> {
                    TextView tvHistorial = findViewById(R.id.tvHistorialCitas);
                    if (tvHistorial == null || snap.isEmpty()) return;

                    StringBuilder sb = new StringBuilder();
                    for (QueryDocumentSnapshot doc : snap) {
                        String trat  = doc.getString("tratamiento");
                        String fecha = doc.getString("fecha");
                        String est   = doc.getString("estado");
                        sb.append("• ").append(trat != null ? trat : "Cita")
                          .append(" — ").append(fecha != null ? fecha : "--")
                          .append(" [").append(est != null ? est : "--").append("]\n");
                    }
                    tvHistorial.setText(sb.toString().trim());
                });
    }

    private void mostrarDialogoEditarFaltas() {
        String[] opciones = {"0 faltas","1 falta","2 faltas","3 faltas (bloquear)"};
        new AlertDialog.Builder(this)
                .setTitle("Editar faltas")
                .setItems(opciones, (d, w) -> {
                    db.collection("usuarios").document(idPaciente)
                            .update("faltas", w,
                                    "bloqueado", w >= 3,
                                    "nivelPenalizacion", w)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this,
                                        "✅ Faltas actualizadas",
                                        Toast.LENGTH_SHORT).show();
                                cargarDatosPaciente();
                            });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
