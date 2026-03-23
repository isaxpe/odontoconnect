package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Button btnConfigurarAgenda, btnVerCitas, btnCerrarSesion, btnGestionTratamientos;
    private TextView tvBienvenida, tvCitasHoy, tvTotalPacientes, tvSolicitudes;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tvBienvenida           = findViewById(R.id.tvBienvenida);
        tvCitasHoy             = findViewById(R.id.tvCitasConfirmadasValor);
        tvTotalPacientes       = findViewById(R.id.tvPacientesTotalesValor);
        tvSolicitudes          = findViewById(R.id.tvSolicitudesPendientesValor);
        btnConfigurarAgenda    = findViewById(R.id.btnConfigurarAgenda);
        btnVerCitas            = findViewById(R.id.btnVerCitas);
        btnCerrarSesion        = findViewById(R.id.btnCerrarSesion);
        btnGestionTratamientos = findViewById(R.id.btnGestionTratamientos);

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

        // ── BottomNavigation del doctor ──
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        // Marcar "Inicio" como seleccionado al entrar
        bottomNav.setSelectedItemId(R.id.nav_inicio);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_inicio) {
                // Ya estamos aquí, no hacer nada
                return true;

            } else if (id == R.id.nav_agenda) {
                startActivity(new Intent(this, AgendaActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;

            } else if (id == R.id.nav_pacientes) {
                startActivity(new Intent(this, PacientesActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;

            } else if (id == R.id.nav_perfil) {
                startActivity(new Intent(this, PerfilDoctorActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }

            return false;
        });
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
