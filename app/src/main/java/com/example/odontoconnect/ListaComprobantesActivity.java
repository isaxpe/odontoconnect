package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class ListaComprobantesActivity extends AppCompatActivity {

    private LinearLayout contenedor;
    private TextView tvVacio;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Usa el layout de ver_citas como contenedor (existe y tiene contenedorSolicitudes)
        // o crea un layout propio mas adelante. Aqui reusamos el patron estandar.
        setContentView(R.layout.activity_ver_citas);

        db = FirebaseFirestore.getInstance();

        // Usamos el contenedor de solicitudes como lista de comprobantes
        contenedor = findViewById(R.id.contenedorSolicitudes);
        tvVacio    = findViewById(R.id.tvMensajeVacio);

        if (tvVacio != null) {
            tvVacio.setText("No hay comprobantes recibidos");
        }

        cargarComprobantes();
    }

    private void cargarComprobantes() {
        db.collection("citas")
                .whereEqualTo("estadoPago", "esperando_confirmacion")
                .get()
                .addOnSuccessListener(snap -> {
                    if (contenedor == null) return;
                    contenedor.removeAllViews();

                    if (snap.isEmpty()) {
                        if (tvVacio != null) tvVacio.setVisibility(View.VISIBLE);
                        return;
                    }

                    if (tvVacio != null) tvVacio.setVisibility(View.GONE);

                    LayoutInflater inflater = LayoutInflater.from(this);
                    for (QueryDocumentSnapshot doc : snap) {
                        View tarjeta = inflater.inflate(
                                R.layout.item_comprobante, contenedor, false);

                        TextView tvNombre = tarjeta.findViewById(R.id.tvNombreComprobante);
                        TextView tvInfo   = tarjeta.findViewById(R.id.tvInfoComprobante);

                        String trat    = doc.getString("tratamiento");
                        String fecha   = doc.getString("fecha");
                        String hora    = doc.getString("horaDisplay");
                        if (hora == null) hora = doc.getString("hora");
                        String paciente = doc.getString("nombrePaciente");
                        Object monto   = doc.get("montoAnticipo");

                        if (tvNombre != null) {
                            tvNombre.setText(paciente != null ? paciente : "Paciente");
                        }

                        if (tvInfo != null) {
                            StringBuilder sb = new StringBuilder();
                            if (trat  != null) sb.append(trat).append("\n");
                            if (fecha != null) sb.append(fecha);
                            if (hora  != null) sb.append(" - ").append(hora).append("\n");
                            if (monto != null) sb.append("$").append(monto);
                            tvInfo.setText(sb.toString().trim());
                        }

                        // Al tocar el comprobante, abrir la activity de confirmar pago
                        final String citaId = doc.getId();
                        tarjeta.setOnClickListener(v -> {
                            Intent intent = new Intent(this, ConfirmarPagoActivity.class);
                            intent.putExtra("idCita", citaId);
                            startActivity(intent);
                        });

                        contenedor.addView(tarjeta);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recargar al volver de confirmar pago
        cargarComprobantes();
    }
}