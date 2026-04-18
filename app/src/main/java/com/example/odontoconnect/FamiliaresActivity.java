package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FamiliaresActivity extends AppCompatActivity {

    private RecyclerView rvFamiliares;
    private TextView tvSinFamiliares;
    private FloatingActionButton fabAgregarFamiliar;
    private FirebaseFirestore db;
    private String userId;
    private final List<Map<String, Object>> listaFamiliares = new ArrayList<>();
    private FamiliarAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_familiares);

        db     = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        rvFamiliares       = findViewById(R.id.rvFamiliares);
        tvSinFamiliares    = findViewById(R.id.tvSinFamiliares);
        fabAgregarFamiliar = findViewById(R.id.fabAgregarFamiliar);

        adapter = new FamiliarAdapter(listaFamiliares, this::agendarParaFamiliar);
        rvFamiliares.setLayoutManager(new LinearLayoutManager(this));
        rvFamiliares.setAdapter(adapter);

        cargarFamiliares();

        fabAgregarFamiliar.setOnClickListener(v ->
                startActivity(new Intent(this, RegistrarFamiliarActivity.class))
        );

        BottomNavHelper.setupPaciente(this, BottomNavHelper.PacienteTab.FAMILIARES);

    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarFamiliares();
    }

    private void cargarFamiliares() {
        db.collection("usuarios").document(userId)
                .collection("familiares").get()
                .addOnSuccessListener(snap -> {
                    listaFamiliares.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        Map<String, Object> item = new HashMap<>(doc.getData());
                        item.put("id", doc.getId());
                        listaFamiliares.add(item);
                    }
                    if (listaFamiliares.isEmpty()) {
                        rvFamiliares.setVisibility(View.GONE);
                        tvSinFamiliares.setVisibility(View.VISIBLE);
                    } else {
                        rvFamiliares.setVisibility(View.VISIBLE);
                        tvSinFamiliares.setVisibility(View.GONE);
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void agendarParaFamiliar(Map<String, Object> familiar) {
        String idDocFamiliar   = String.valueOf(familiar.get("id"));
        String nombreFamiliar  = String.valueOf(familiar.get("nombre"));

        Intent intent = new Intent(this, AgendarCitaActivity.class);
        intent.putExtra("esFamiliar",     true);
        intent.putExtra("idFamiliar",     idDocFamiliar);
        intent.putExtra("nombreFamiliar", nombreFamiliar);
        intent.putExtra("idTitular",      userId);
        startActivity(intent);
    }

    static class FamiliarAdapter extends RecyclerView.Adapter<FamiliarAdapter.VH> {
        private final List<Map<String, Object>> data;
        private final OnAgendarListener listener;

        interface OnAgendarListener {
            void onAgendar(Map<String, Object> familiar);
        }

        FamiliarAdapter(List<Map<String, Object>> data, OnAgendarListener listener) {
            this.data = data;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_familiar, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> item = data.get(pos);
            h.tvNombre.setText(String.valueOf(item.get("nombre")));
            String par  = String.valueOf(item.get("parentesco"));
            String edad = item.get("edad") != null
                    ? item.get("edad") + " años" : "";
            h.tvParentesco.setText(par + (edad.isEmpty() ? "" : " · " + edad));
            h.btnAgendar.setOnClickListener(v -> listener.onAgendar(item));
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvNombre, tvParentesco;
            Button btnAgendar;

            VH(@NonNull View v) {
                super(v);
                tvNombre     = v.findViewById(R.id.tvNombreFamiliar);
                tvParentesco = v.findViewById(R.id.tvParentescoFamiliar);
                btnAgendar   = v.findViewById(R.id.btnAgendarFamiliar);
            }
        }
    }
}
