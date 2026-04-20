package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Button btnConfigurarAgenda, btnVerCitas, btnCerrarSesion,
            btnGestionTratamientos, btnCitasHoy;
    private TextView tvBienvenida, tvCitasHoy, tvTotalPacientes, tvSolicitudes;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        tvBienvenida           = findViewById(R.id.tvBienvenida);
        tvCitasHoy             = findViewById(R.id.tvCitasConfirmadasValor);
        tvTotalPacientes       = findViewById(R.id.tvPacientesTotalesValor);
        tvSolicitudes          = findViewById(R.id.tvSolicitudesPendientesValor);
        btnConfigurarAgenda    = findViewById(R.id.btnConfigurarAgenda);
        btnVerCitas            = findViewById(R.id.btnVerCitas);
        btnCerrarSesion        = findViewById(R.id.btnCerrarSesion);
        btnGestionTratamientos = findViewById(R.id.btnGestionTratamientos);
        btnCitasHoy            = findViewById(R.id.btnCitasHoy);

        cargarDatosDoctor();
        actualizarContadoresDinamicos();

        btnCerrarSesion.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        btnVerCitas.setOnClickListener(v ->
                startActivity(new Intent(this, VerCitasActivity.class))
        );

        btnConfigurarAgenda.setOnClickListener(v ->
                startActivity(new Intent(this, AgendaActivity.class))
        );

        btnGestionTratamientos.setOnClickListener(v ->
                startActivity(new Intent(this, GestionTratamientosActivity.class))
        );

        // NUEVO: Botón Citas de Hoy -> ahora abre TodasCitasActivity
        // (muestra todas las citas con filtro Hoy/Semana/Todas)
        if (btnCitasHoy != null) {
            btnCitasHoy.setText("Mis citas por atender");
            btnCitasHoy.setOnClickListener(v ->
                    startActivity(new Intent(this, TodasCitasActivity.class))
            );
        }

        // NUEVO: Botón Urgencias pendientes (solo si existe en el layout)
        View btnUrgencias = findViewById(R.id.btnUrgenciasPendientes);
        if (btnUrgencias != null) {
            btnUrgencias.setOnClickListener(v ->
                    startActivity(new Intent(this, UrgenciasPendientesActivity.class))
            );
        }

        BottomNavHelper.setupDoctor(this, BottomNavHelper.DoctorTab.INICIO);

    }

    private void actualizarContadoresDinamicos() {
        String hoy = new SimpleDateFormat("d-M-yyyy", Locale.getDefault()).format(new Date());

        db.collection("citas")
                .whereEqualTo("fecha", hoy)
                .whereEqualTo("estado", "aceptada")
                .addSnapshotListener((value, error) -> {
                    if (value != null) tvCitasHoy.setText(String.valueOf(value.size()));
                });

        db.collection("citas")
                .whereEqualTo("estado", "pendiente")
                .addSnapshotListener((value, error) -> {
                    if (value != null) tvSolicitudes.setText(String.valueOf(value.size()));
                });

        db.collection("usuarios")
                .whereEqualTo("rol", "paciente")
                .addSnapshotListener((value, error) -> {
                    if (value != null) tvTotalPacientes.setText(String.valueOf(value.size()));
                });

        // NUEVO: Contador de urgencias pendientes
        TextView tvUrgenciasCount = findViewById(R.id.tvUrgenciasCount);
        View badgeUrgencias = findViewById(R.id.badgeUrgencias);
        db.collection("citas")
                .whereEqualTo("estado", "urgencia_pendiente")
                .addSnapshotListener((value, error) -> {
                    if (value == null) return;
                    int n = value.size();
                    if (tvUrgenciasCount != null) {
                        tvUrgenciasCount.setText(String.valueOf(n));
                    }
                    if (badgeUrgencias != null) {
                        badgeUrgencias.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void cargarDatosDoctor() {
        if (mAuth.getCurrentUser() == null) return;
        db.collection("usuarios").document(mAuth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String nombre = doc.getString("Nombre");
                        if (nombre == null) nombre = doc.getString("nombre");
                        tvBienvenida.setText("Bienvenido, Dr. " +
                                (nombre != null ? nombre : "Doctor"));
                    }
                });
    }
}