package com.example.odontoconnect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GestionTratamientosActivity extends AppCompatActivity {

    private EditText etNombre, etDuracion;
    private Button btnGuardar;
    private RecyclerView rvTratamientos;
    private FirebaseFirestore db;

    private final List<Map<String, Object>> listaTratamientos = new ArrayList<>();
    private TratamientoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gestion_tratamientos);

        db = FirebaseFirestore.getInstance();

        etNombre  = findViewById(R.id.etNombreTratamiento);
        etDuracion = findViewById(R.id.etDuracionTratamiento);
        btnGuardar = findViewById(R.id.btnGuardarTratamiento);
        rvTratamientos = findViewById(R.id.rvTratamientos);

        // Configurar RecyclerView
        adapter = new TratamientoAdapter(listaTratamientos);
        rvTratamientos.setLayoutManager(new LinearLayoutManager(this));
        rvTratamientos.setAdapter(adapter);

        btnGuardar.setOnClickListener(v -> guardarTratamiento());

        cargarTratamientos();
    }

    private void guardarTratamiento() {
        String nombre = etNombre.getText().toString().trim();
        String duracionStr = etDuracion.getText().toString().trim();

        if (nombre.isEmpty() || duracionStr.isEmpty()) {
            Toast.makeText(this, "Llena todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        int duracion;
        try {
            duracion = Integer.parseInt(duracionStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "La duración debe ser un número", Toast.LENGTH_SHORT).show();
            return;
        }

        btnGuardar.setEnabled(false);
        btnGuardar.setText("Guardando...");

        Map<String, Object> tratamiento = new HashMap<>();
        tratamiento.put("nombre", nombre);
        tratamiento.put("duracion_minutos", duracion);

        db.collection("tratamientos")
                .add(tratamiento)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Tratamiento registrado", Toast.LENGTH_SHORT).show();
                    etNombre.setText("");
                    etDuracion.setText("");
                    btnGuardar.setEnabled(true);
                    btnGuardar.setText("AGREGAR TRATAMIENTO");
                    cargarTratamientos(); // Recargar lista
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnGuardar.setEnabled(true);
                    btnGuardar.setText("AGREGAR TRATAMIENTO");
                });
    }

    private void cargarTratamientos() {
        db.collection("tratamientos")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    listaTratamientos.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Map<String, Object> item = new HashMap<>(doc.getData());
                        item.put("id", doc.getId());
                        listaTratamientos.add(item);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    // --- Adapter interno simple ---
    static class TratamientoAdapter extends RecyclerView.Adapter<TratamientoAdapter.VH> {
        private final List<Map<String, Object>> data;

        TratamientoAdapter(List<Map<String, Object>> data) { this.data = data; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> item = data.get(position);
            holder.tvNombre.setText(String.valueOf(item.get("nombre")));
            Object dur = item.get("duracion_minutos");
            holder.tvDuracion.setText("Duración: " + (dur != null ? dur + " min" : "—"));
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvNombre, tvDuracion;
            VH(@NonNull View itemView) {
                super(itemView);
                tvNombre   = itemView.findViewById(android.R.id.text1);
                tvDuracion = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}
