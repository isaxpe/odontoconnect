package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class UrgenciasPendientesActivity extends AppCompatActivity {

    private static final String TAG = "URG_PENDIENTES";

    private LinearLayout contenedorUrgencias;
    private TextView tvSinUrgencias;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_urgencias_pendientes);
        } catch (Exception e) {
            Log.e(TAG, "Error inflar layout", e);
            Toast.makeText(this, "ERROR LAYOUT: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            contenedorUrgencias = findViewById(R.id.contenedorUrgencias);
            tvSinUrgencias      = findViewById(R.id.tvSinUrgencias);
            if (contenedorUrgencias == null) {
                Toast.makeText(this, "Falta contenedorUrgencias en XML",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error findViewById", e);
            Toast.makeText(this, "ERROR VIEWS: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            db    = FirebaseFirestore.getInstance();
            mAuth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Error Firebase", e);
            Toast.makeText(this, "ERROR FIREBASE: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        cargarUrgencias();
    }

    private void cargarUrgencias() {
        try {
            if (mAuth.getCurrentUser() == null) {
                Toast.makeText(this, "Sesion expirada",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            String miUid = mAuth.getCurrentUser().getUid();

            listener = db.collection("citas")
                    .whereEqualTo("idDoctor", miUid)
                    .whereEqualTo("estado", "urgencia_pendiente")
                    .addSnapshotListener((snap, error) -> {
                        if (error != null) {
                            Log.e(TAG, "Error snapshot", error);
                            Toast.makeText(this,
                                    "Error leer: " + error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (snap == null || contenedorUrgencias == null) return;

                        try {
                            contenedorUrgencias.removeAllViews();

                            if (snap.isEmpty()) {
                                if (tvSinUrgencias != null)
                                    tvSinUrgencias.setVisibility(View.VISIBLE);
                                return;
                            }

                            if (tvSinUrgencias != null)
                                tvSinUrgencias.setVisibility(View.GONE);

                            for (DocumentSnapshot doc : snap.getDocuments()) {
                                try {
                                    crearTarjeta(doc);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error tarjeta", e);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error procesar snap", e);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "CRASH cargar", e);
            Toast.makeText(this, "ERROR: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void crearTarjeta(DocumentSnapshot doc) {
        String idCita      = doc.getId();
        String nombrePac   = doc.getString("nombrePaciente");
        String fechaPref   = doc.getString("fechaPreferida");
        String horaPref    = doc.getString("horaPreferida");
        String descripcion = doc.getString("descripcionUrgencia");

        View tarjeta;
        try {
            tarjeta = LayoutInflater.from(this)
                    .inflate(R.layout.item_urgencia, contenedorUrgencias, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflate item", e);
            Toast.makeText(this,
                    "ERROR item_urgencia: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }

        TextView tvNombre      = tarjeta.findViewById(R.id.tvNombreUrgencia);
        TextView tvFechaHora   = tarjeta.findViewById(R.id.tvFechaHoraUrgencia);
        TextView tvDescripcion = tarjeta.findViewById(R.id.tvDescripcionUrgencia);
        View btnResponder      = tarjeta.findViewById(R.id.btnResponderUrgencia);

        if (tvNombre != null)
            tvNombre.setText(nombrePac != null ? nombrePac : "Paciente");
        if (tvFechaHora != null)
            tvFechaHora.setText("Pide: " +
                    (fechaPref != null ? fechaPref : "--") + " a las " +
                    (horaPref != null ? horaPref : "--"));
        if (tvDescripcion != null)
            tvDescripcion.setText(
                    descripcion != null ? descripcion : "Sin descripcion");

        if (btnResponder != null) {
            btnResponder.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(this,
                            ResponderUrgenciaActivity.class);
                    intent.putExtra("idCita", idCita);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error abrir responder", e);
                    Toast.makeText(this,
                            "Agrega ResponderUrgenciaActivity al Manifest",
                            Toast.LENGTH_LONG).show();
                }
            });
        }

        contenedorUrgencias.addView(tarjeta);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}
