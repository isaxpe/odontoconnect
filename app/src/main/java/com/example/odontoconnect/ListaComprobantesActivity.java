package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class ListaComprobantesActivity extends AppCompatActivity {

    private LinearLayout contenedor;
    private TextView tvSinComprobantes;
    private FirebaseFirestore db;
    private String miUid;
    private ListenerRegistration listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_comprobantes);

        db    = FirebaseFirestore.getInstance();
        miUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        contenedor        = findViewById(R.id.contenedorComprobantes);
        tvSinComprobantes = findViewById(R.id.tvSinComprobantes);

        cargarComprobantes();
    }

    private void cargarComprobantes() {
        listener = db.collection("citas")
                .whereEqualTo("idDoctor", miUid)
                .whereEqualTo("estadoPago", "comprobante_enviado")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    contenedor.removeAllViews();

                    if (snap.isEmpty()) {
                        tvSinComprobantes.setVisibility(View.VISIBLE);
                        return;
                    }
                    tvSinComprobantes.setVisibility(View.GONE);

                    for (QueryDocumentSnapshot doc : snap) {
                        String idCita      = doc.getId();
                        String tratamiento = doc.getString("tratamiento");
                        String fecha       = doc.getString("fecha");
                        String idPaciente  = doc.getString("idPaciente");
                        Double precio      = doc.getDouble("precioTratamiento");

                        View tarjeta = LayoutInflater.from(this)
                                .inflate(R.layout.item_comprobante,
                                        contenedor, false);

                        TextView tvTrat   = tarjeta.findViewById(R.id.tvTratComp);
                        TextView tvFecha  = tarjeta.findViewById(R.id.tvFechaComp2);
                        TextView tvPac    = tarjeta.findViewById(R.id.tvPacienteComp);
                        TextView tvMonto  = tarjeta.findViewById(R.id.tvMontoComp2);
                        MaterialButton btnVer = tarjeta.findViewById(R.id.btnVerComp);

                        tvTrat.setText(tratamiento != null ? tratamiento : "Cita");
                        tvFecha.setText("📅 " + (fecha != null ? fecha : "--"));
                        if (precio != null && precio > 0)
                            tvMonto.setText(String.format("💲 Anticipo: $%.2f",
                                    precio * 0.20));

                        if (idPaciente != null) {
                            db.collection("usuarios").document(idPaciente).get()
                                    .addOnSuccessListener(pacDoc -> {
                                        String nombre = pacDoc.getString("Nombre");
                                        if (nombre == null)
                                            nombre = pacDoc.getString("nombre");
                                        tvPac.setText("👤 " +
                                                (nombre != null ? nombre : "Paciente"));
                                    });
                        }

                        btnVer.setOnClickListener(v -> {
                            Intent intent = new Intent(this,
                                    VerComprobanteActivity.class);
                            intent.putExtra("idCita", idCita);
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
