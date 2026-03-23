package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class PacientesActivity extends AppCompatActivity {

    private RecyclerView rvPacientes;
    private TextView tvSinPacientes;
    private FirebaseFirestore db;
    private final List<Map<String, Object>> listaPacientes = new ArrayList<>();
    private PacienteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pacientes);

        rvPacientes    = findViewById(R.id.rvPacientes);
        tvSinPacientes = findViewById(R.id.tvSinPacientes);
        db = FirebaseFirestore.getInstance();

        adapter = new PacienteAdapter(listaPacientes);
        rvPacientes.setLayoutManager(new LinearLayoutManager(this));
        rvPacientes.setAdapter(adapter);

        cargarPacientes();

        // ── BottomNavigation ──
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationPacientes);
        bottomNav.setSelectedItemId(R.id.nav_pacientes);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inicio) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_agenda) {
                startActivity(new Intent(this, AgendaActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_pacientes) {
                return true; // Ya estamos aquí
            } else if (id == R.id.nav_perfil) {
                startActivity(new Intent(this, PerfilDoctorActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private void cargarPacientes() {
        db.collection("usuarios")
                .whereEqualTo("rol", "paciente")
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null || queryDocumentSnapshots == null) return;

                    listaPacientes.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Map<String, Object> item = new HashMap<>(doc.getData());
                        item.put("uid", doc.getId());
                        listaPacientes.add(item);
                    }

                    if (listaPacientes.isEmpty()) {
                        rvPacientes.setVisibility(View.GONE);
                        tvSinPacientes.setVisibility(View.VISIBLE);
                    } else {
                        rvPacientes.setVisibility(View.VISIBLE);
                        tvSinPacientes.setVisibility(View.GONE);
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    static class PacienteAdapter extends RecyclerView.Adapter<PacienteAdapter.VH> {
        private final List<Map<String, Object>> data;

        PacienteAdapter(List<Map<String, Object>> data) { this.data = data; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> paciente = data.get(position);
            Object nombre = paciente.get("Nombre");
            if (nombre == null) nombre = paciente.get("nombre");
            holder.tvNombre.setText(nombre != null ? String.valueOf(nombre) : "Paciente");

            Object id     = paciente.get("paciente_id");
            Object faltas = paciente.get("faltas");
            Object bloq   = paciente.get("bloqueado");
            String estadoTexto = Boolean.TRUE.equals(bloq) ? " 🔴 BLOQUEADO" : " 🟢 Activo";
            holder.tvDetalle.setText(
                    "ID: " + (id != null ? id : "—") +
                    "  |  Faltas: " + (faltas != null ? faltas : 0) + "/3" +
                    estadoTexto
            );
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvNombre, tvDetalle;
            VH(@NonNull View itemView) {
                super(itemView);
                tvNombre  = itemView.findViewById(android.R.id.text1);
                tvDetalle = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}
