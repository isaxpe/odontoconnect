package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class PacientesActivity extends AppCompatActivity {

    private LinearLayout contenedor;
    private TextView tvSinPacientes;
    private FirebaseFirestore db;
    private ListenerRegistration listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pacientes);

        db             = FirebaseFirestore.getInstance();
        contenedor     = findViewById(R.id.contenedorPacientes);
        tvSinPacientes = findViewById(R.id.tvSinPacientes);

        cargarPacientes();

        BottomNavHelper.setupDoctor(this, BottomNavHelper.DoctorTab.PACIENTES);

    }

    private void cargarPacientes() {
        listener = db.collection("usuarios")
                .whereEqualTo("rol", "paciente")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    contenedor.removeAllViews();

                    if (snap.isEmpty()) {
                        tvSinPacientes.setVisibility(View.VISIBLE);
                        return;
                    }
                    tvSinPacientes.setVisibility(View.GONE);

                    for (QueryDocumentSnapshot doc : snap) {
                        String uid    = doc.getId();
                        String nombre = doc.getString("Nombre");
                        if (nombre == null) nombre = doc.getString("nombre");
                        Long faltas   = doc.getLong("faltas");
                        Boolean bloq  = doc.getBoolean("bloqueado");
                        String idPac  = doc.getString("paciente_id");

                        if (faltas == null) faltas = 0L;
                        if (bloq == null) bloq = false;

                        View tarjeta = LayoutInflater.from(this)
                                .inflate(R.layout.item_paciente, contenedor, false);

                        TextView tvNombre = tarjeta.findViewById(R.id.tvNombrePacienteItem);
                        TextView tvId     = tarjeta.findViewById(R.id.tvIdPacienteItem);
                        TextView tvFaltas = tarjeta.findViewById(R.id.tvFaltasPacienteItem);
                        TextView tvEstado = tarjeta.findViewById(R.id.tvEstadoPacienteItem);

                        tvNombre.setText(nombre != null ? nombre : "Sin nombre");
                        tvId.setText("ID: " + (idPac != null
                                ? idPac : uid.substring(0, 6).toUpperCase()));
                        tvFaltas.setText("Faltas: " + faltas + "/3");

                        if (bloq || faltas >= 3) {
                            tvEstado.setText("🔴 Bloqueado");
                            tvEstado.setTextColor(0xFFB71C1C);
                            tvFaltas.setTextColor(0xFFB71C1C);
                        } else if (faltas == 2) {
                            tvEstado.setText("🟠 2 faltas");
                            tvEstado.setTextColor(0xFFF57C00);
                            tvFaltas.setTextColor(0xFFF57C00);
                        } else if (faltas == 1) {
                            tvEstado.setText("🟡 1 falta");
                            tvEstado.setTextColor(0xFFF59E0B);
                        } else {
                            tvEstado.setText("🟢 Activo");
                            tvEstado.setTextColor(0xFF1565C0);
                        }

                        // NUEVO: tocar la tarjeta abre el detalle del paciente
                        final String uidFinal = uid;
                        tarjeta.setOnClickListener(v -> {
                            Intent intent = new Intent(this,
                                    DetallePacienteActivity.class);
                            intent.putExtra("idPaciente", uidFinal);
                            startActivity(intent);
                        });

                        contenedor.addView(tarjeta);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}
