package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PerfilDoctorActivity extends AppCompatActivity {

    private TextView tvNombreDoctor, tvCorreoDoctor, tvEspecialidadDoctor,
            tvTelefonoDoctor, tvDireccionDoctor, tvEspecialidadPerfil,
            tvTotalPacientesDoctor, tvCitasHoyDoctor, tvPendientesDoctor,
            tvBancoDoctor, tvClabeDoctor, tvTitularDoctor;
    private Button btnCerrarSesionPerfil;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_doctor);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        tvNombreDoctor         = findViewById(R.id.tvNombreDoctor);
        tvCorreoDoctor         = findViewById(R.id.tvCorreoDoctor);
        tvEspecialidadDoctor   = findViewById(R.id.tvEspecialidadDoctor);
        tvTelefonoDoctor       = findViewById(R.id.tvTelefonoDoctor);
        tvDireccionDoctor      = findViewById(R.id.tvDireccionDoctor);
        tvEspecialidadPerfil   = findViewById(R.id.tvEspecialidadPerfil);
        tvTotalPacientesDoctor = findViewById(R.id.tvTotalPacientesDoctor);
        tvCitasHoyDoctor       = findViewById(R.id.tvCitasHoyDoctor);
        tvPendientesDoctor     = findViewById(R.id.tvPendientesDoctor);
        tvBancoDoctor          = findViewById(R.id.tvBancoDoctor);
        tvClabeDoctor          = findViewById(R.id.tvClabeDoctor);
        tvTitularDoctor        = findViewById(R.id.tvTitularDoctor);
        btnCerrarSesionPerfil  = findViewById(R.id.btnCerrarSesionPerfil);

        if (mAuth.getCurrentUser() != null &&
                mAuth.getCurrentUser().getEmail() != null) {
            tvCorreoDoctor.setText(mAuth.getCurrentUser().getEmail());
        }

        cargarPerfilDoctor();
        cargarEstadisticas();

        // Editar datos del consultorio
        configurarEdicion();

        // Acciones rápidas
        View btnIrAgenda = findViewById(R.id.btnIrAgenda);
        if (btnIrAgenda != null) btnIrAgenda.setOnClickListener(v ->
                startActivity(new Intent(this, AgendaActivity.class)));

        View btnIrCitas = findViewById(R.id.btnIrCitas);
        if (btnIrCitas != null) btnIrCitas.setOnClickListener(v ->
                startActivity(new Intent(this, VerCitasActivity.class)));

        View btnIrTratamientos = findViewById(R.id.btnIrTratamientos);
        if (btnIrTratamientos != null) btnIrTratamientos.setOnClickListener(v ->
                startActivity(new Intent(this, GestionTratamientosActivity.class)));

        View btnCambiarPasswordDoctor = findViewById(R.id.btnCambiarPasswordDoctor);
        if (btnCambiarPasswordDoctor != null) {
            btnCambiarPasswordDoctor.setOnClickListener(v -> {
                if (mAuth.getCurrentUser() != null &&
                        mAuth.getCurrentUser().getEmail() != null) {
                    mAuth.sendPasswordResetEmail(mAuth.getCurrentUser().getEmail())
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(this,
                                            "📧 Revisa tu correo",
                                            Toast.LENGTH_LONG).show()
                            );
                }
            });
        }

        btnCerrarSesionPerfil.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // BottomNavigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_perfil);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_inicio) {
                    startActivity(new Intent(this, MainActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                } else if (id == R.id.nav_agenda) {
                    startActivity(new Intent(this, AgendaActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                } else if (id == R.id.nav_pacientes) {
                    startActivity(new Intent(this, PacientesActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                } else if (id == R.id.nav_perfil) {
                    return true;
                }
                return false;
            });
        }
    }

    private void configurarEdicion() {
        View btnEditTel = findViewById(R.id.btnEditarTelefonoDoctor);
        if (btnEditTel != null) btnEditTel.setOnClickListener(v ->
                mostrarDialogoEditar("Teléfono", "telefono",
                        tvTelefonoDoctor.getText().toString()));

        View btnEditDir = findViewById(R.id.btnEditarDireccionDoctor);
        if (btnEditDir != null) btnEditDir.setOnClickListener(v ->
                mostrarDialogoEditar("Dirección", "direccion",
                        tvDireccionDoctor.getText().toString()));

        View btnEditEsp = findViewById(R.id.btnEditarEspecialidad);
        if (btnEditEsp != null) btnEditEsp.setOnClickListener(v ->
                mostrarDialogoEditar("Especialidad", "especialidad",
                        tvEspecialidadPerfil.getText().toString()));

        // NUEVO: editar datos bancarios
        View btnEditBanco = findViewById(R.id.btnEditarBanco);
        if (btnEditBanco != null) btnEditBanco.setOnClickListener(v ->
                mostrarDialogoEditar("Nombre del banco", "banco",
                        tvBancoDoctor.getText().toString()));

        View btnEditClabe = findViewById(R.id.btnEditarClabe);
        if (btnEditClabe != null) btnEditClabe.setOnClickListener(v ->
                mostrarDialogoEditar("CLABE interbancaria (18 dígitos)",
                        "clabe", tvClabeDoctor.getText().toString()));

        View btnEditTitular = findViewById(R.id.btnEditarTitular);
        if (btnEditTitular != null) btnEditTitular.setOnClickListener(v ->
                mostrarDialogoEditar("Nombre del titular de la cuenta",
                        "titularCuenta", tvTitularDoctor.getText().toString()));
    }

    private void mostrarDialogoEditar(String titulo, String campo,
                                       String valorActual) {
        EditText input = new EditText(this);
        boolean esDefault = valorActual.equals("No registrado") ||
                valorActual.equals("No registrada");
        input.setText(esDefault ? "" : valorActual);
        input.setHint(titulo);
        input.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(this)
                .setTitle("Editar " + titulo)
                .setView(input)
                .setPositiveButton("Guardar", (d, w) -> {
                    String val = input.getText().toString().trim();
                    if (!val.isEmpty()) guardarCampo(campo, val);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void guardarCampo(String campo, String valor) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("usuarios").document(uid)
                .update(campo, valor)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Actualizado", Toast.LENGTH_SHORT).show();
                    switch (campo) {
                        case "telefono":     tvTelefonoDoctor.setText(valor); break;
                        case "direccion":    tvDireccionDoctor.setText(valor); break;
                        case "especialidad":
                            tvEspecialidadPerfil.setText(valor);
                            tvEspecialidadDoctor.setText(valor); break;
                        case "banco":        tvBancoDoctor.setText(valor); break;
                        case "clabe":        tvClabeDoctor.setText(valor); break;
                        case "titularCuenta": tvTitularDoctor.setText(valor); break;
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }

    private void cargarPerfilDoctor() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    String nombre      = doc.getString("Nombre");
                    if (nombre == null) nombre = doc.getString("nombre");
                    String especialidad = doc.getString("especialidad");
                    String telefono    = doc.getString("telefono");
                    String direccion   = doc.getString("direccion");
                    String banco       = doc.getString("banco");
                    String clabe       = doc.getString("clabe");
                    String titular     = doc.getString("titularCuenta");

                    tvNombreDoctor.setText("Dr. " + (nombre != null ? nombre : "Doctor"));
                    tvEspecialidadDoctor.setText(
                            especialidad != null ? especialidad : "Odontólogo General");
                    tvTelefonoDoctor.setText(
                            telefono != null ? telefono : "No registrado");
                    tvDireccionDoctor.setText(
                            direccion != null ? direccion : "No registrada");
                    tvEspecialidadPerfil.setText(
                            especialidad != null ? especialidad : "No registrada");

                    if (tvBancoDoctor != null)
                        tvBancoDoctor.setText(banco != null ? banco : "No registrado");
                    if (tvClabeDoctor != null)
                        tvClabeDoctor.setText(clabe != null ? clabe : "No registrada");
                    if (tvTitularDoctor != null)
                        tvTitularDoctor.setText(titular != null ? titular : "No registrado");
                });
    }

    private void cargarEstadisticas() {
        String hoy = new SimpleDateFormat("d-M-yyyy",
                Locale.getDefault()).format(new Date());

        db.collection("usuarios").whereEqualTo("rol", "paciente")
                .addSnapshotListener((value, error) -> {
                    if (value != null && tvTotalPacientesDoctor != null)
                        tvTotalPacientesDoctor.setText(String.valueOf(value.size()));
                });

        db.collection("citas").whereEqualTo("fecha", hoy)
                .whereEqualTo("estado", "aceptada")
                .addSnapshotListener((value, error) -> {
                    if (value != null && tvCitasHoyDoctor != null)
                        tvCitasHoyDoctor.setText(String.valueOf(value.size()));
                });

        db.collection("citas").whereEqualTo("estado", "pendiente")
                .addSnapshotListener((value, error) -> {
                    if (value != null && tvPendientesDoctor != null)
                        tvPendientesDoctor.setText(String.valueOf(value.size()));
                });
    }
}
