package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class PerfilDoctorActivity extends AppCompatActivity {

    private TextView tvNombreDoctor, tvCorreoDoctor, tvRolDoctor;
    private Button btnCerrarSesion;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_doctor);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tvNombreDoctor = findViewById(R.id.tvNombreDoctor);
        tvCorreoDoctor = findViewById(R.id.tvCorreoDoctor);
        tvRolDoctor    = findViewById(R.id.tvRolDoctor);
        btnCerrarSesion = findViewById(R.id.btnCerrarSesionPerfil);

        cargarPerfilDoctor();

        btnCerrarSesion.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // ── BottomNavigation ──
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_perfil);

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
                startActivity(new Intent(this, PacientesActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_perfil) {
                return true; // Ya estamos aquí
            }
            return false;
        });
    }

    private void cargarPerfilDoctor() {
        if (mAuth.getCurrentUser() == null) return;

        String uid    = mAuth.getCurrentUser().getUid();
        String correo = mAuth.getCurrentUser().getEmail();

        if (correo != null) tvCorreoDoctor.setText(correo);

        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String nombre = doc.getString("Nombre");
                        if (nombre == null) nombre = doc.getString("nombre");
                        String rol = doc.getString("rol");

                        tvNombreDoctor.setText(nombre != null ? nombre : "Doctor");
                        tvRolDoctor.setText(rol != null ?
                                rol.substring(0, 1).toUpperCase() + rol.substring(1) : "Odontólogo");
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al cargar perfil", Toast.LENGTH_SHORT).show()
                );
    }
}