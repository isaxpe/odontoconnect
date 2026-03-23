package com.example.odontoconnect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class MisCitasActivity extends AppCompatActivity {

    private LinearLayout contenedor;
    private TextView tvMensajeVacio;
    private FirebaseFirestore db;
    private String userId;
    private ListenerRegistration listenerCitas; // para limpiar al salir

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_citas);

        contenedor      = findViewById(R.id.contenedorMisCitas);
        tvMensajeVacio  = findViewById(R.id.tvMensajeVacio);
        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        cargarMisCitas();
    }

    private void cargarMisCitas() {
        // CORRECCIÓN: usamos SnapshotListener para que la lista se actualice en tiempo real
        // cuando el doctor acepta o rechaza una cita
        listenerCitas = db.collection("citas")
                .whereEqualTo("idPaciente", userId)
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null || queryDocumentSnapshots == null) return;

                    contenedor.removeAllViews(); // Limpiar antes de redibujar

                    if (queryDocumentSnapshots.isEmpty()) {
                        if (tvMensajeVacio != null) {
                            tvMensajeVacio.setVisibility(View.VISIBLE);
                        }
                        return;
                    }

                    if (tvMensajeVacio != null) {
                        tvMensajeVacio.setVisibility(View.GONE);
                    }

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String tratamiento = doc.getString("tratamiento");
                        String fecha       = doc.getString("fecha");
                        String hora        = doc.getString("hora");
                        String estado      = doc.getString("estado"); // minúscula: "pendiente","aceptada","rechazada"
                        añadirTarjetaCita(tratamiento, fecha, hora, estado);
                    }
                });
    }

    private void añadirTarjetaCita(String tratamiento, String fecha, String hora, String estado) {
        View tarjeta = LayoutInflater.from(this).inflate(R.layout.item_cita_paciente, contenedor, false);

        TextView tvTratamiento = tarjeta.findViewById(R.id.tvTratamientoPaciente);
        TextView tvFecha       = tarjeta.findViewById(R.id.tvFechaHoraPaciente);
        TextView tvEstado      = tarjeta.findViewById(R.id.tvEstadoCita);

        tvTratamiento.setText(tratamiento != null ? tratamiento : "Sin tratamiento");
        // Mostrar fecha y hora juntas si hay hora disponible
        String fechaTexto = fecha != null ? fecha : "--";
        if (hora != null && !hora.isEmpty()) {
            fechaTexto += " a las " + hora;
        }
        tvFecha.setText(fechaTexto);
        tvEstado.setText(estado != null ? estado.toUpperCase() : "PENDIENTE");

        // CORRECCIÓN: comparamos en minúscula, coherente con el valor guardado en Firebase
        if ("aceptada".equals(estado)) {
            tvEstado.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
        } else if ("rechazada".equals(estado)) {
            tvEstado.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
        } else {
            // pendiente — color ámbar
            tvEstado.setBackgroundColor(0xFFFBC02D);
        }

        contenedor.addView(tarjeta);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Buena práctica: liberar el listener al cerrar la Activity
        if (listenerCitas != null) {
            listenerCitas.remove();
        }
    }
}
