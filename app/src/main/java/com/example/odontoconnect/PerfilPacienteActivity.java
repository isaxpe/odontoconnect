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
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class PerfilPacienteActivity extends AppCompatActivity {

    private TextView tvNombrePerfilPaciente, tvIdPerfilPaciente,
            tvCorreoPerfilPaciente, tvTelefonoPerfil, tvFechaNacPerfil,
            tvTipoSangrePerfil, tvAlergiasPerfil, tvFaltasNumeroPerfil,
            tvTotalCitasPerfil, tvEstadoNumeroPerfil, tvEstadoTextoPerfil,
            tvProximaCitaPerfil, tvFamiliaresPerfil;
    private Button btnCerrarSesionPaciente, btnEliminarCuenta;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userId;

    // C: SnapshotListener para actualizaciones en tiempo real
    private ListenerRegistration listenerPerfil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_paciente);

        mAuth  = FirebaseAuth.getInstance();
        db     = FirebaseFirestore.getInstance();
        userId = mAuth.getCurrentUser().getUid();

        tvNombrePerfilPaciente = findViewById(R.id.tvNombrePerfilPaciente);
        tvIdPerfilPaciente     = findViewById(R.id.tvIdPerfilPaciente);
        tvCorreoPerfilPaciente = findViewById(R.id.tvCorreoPerfilPaciente);
        tvTelefonoPerfil       = findViewById(R.id.tvTelefonoPerfil);
        tvFechaNacPerfil       = findViewById(R.id.tvFechaNacPerfil);
        tvTipoSangrePerfil     = findViewById(R.id.tvTipoSangrePerfil);
        tvAlergiasPerfil       = findViewById(R.id.tvAlergiasPerfil);
        tvFaltasNumeroPerfil   = findViewById(R.id.tvFaltasNumeroPerfil);
        tvTotalCitasPerfil     = findViewById(R.id.tvTotalCitasPerfil);
        tvEstadoNumeroPerfil   = findViewById(R.id.tvEstadoNumeroPerfil);
        tvEstadoTextoPerfil    = findViewById(R.id.tvEstadoTextoPerfil);
        tvProximaCitaPerfil    = findViewById(R.id.tvProximaCitaPerfil);
        tvFamiliaresPerfil     = findViewById(R.id.tvFamiliaresPerfil);
        btnCerrarSesionPaciente = findViewById(R.id.btnCerrarSesionPaciente);
        btnEliminarCuenta       = findViewById(R.id.btnEliminarCuenta);

        if (mAuth.getCurrentUser() != null &&
                mAuth.getCurrentUser().getEmail() != null) {
            tvCorreoPerfilPaciente.setText(mAuth.getCurrentUser().getEmail());
        }

        // C: SnapshotListener en lugar de .get() — se actualiza si el doctor cambia faltas
        cargarPerfilEnTiempoReal();
        cargarCitas();
        cargarFamiliares();
        configurarEdicion();

        View btnMiExpediente = findViewById(R.id.btnMiExpediente);
        if (btnMiExpediente != null) {
            btnMiExpediente.setOnClickListener(v -> {
                Intent intent = new Intent(this, ExpedienteActivity.class);
                intent.putExtra("esDoctor", false);
                startActivity(intent);
            });
        }

        View btnCambiarPassword = findViewById(R.id.btnCambiarPassword);
        if (btnCambiarPassword != null) {
            btnCambiarPassword.setOnClickListener(v -> {
                if (mAuth.getCurrentUser() != null &&
                        mAuth.getCurrentUser().getEmail() != null) {
                    mAuth.sendPasswordResetEmail(mAuth.getCurrentUser().getEmail())
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(this,
                                            "📧 Revisa tu correo",
                                            Toast.LENGTH_LONG).show());
                }
            });
        }

        btnCerrarSesionPaciente.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        btnEliminarCuenta.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("⚠️ Eliminar cuenta")
                        .setMessage("Se eliminarán todos tus datos, citas, " +
                                "familiares y expediente. ¿Seguro?")
                        .setPositiveButton("Sí, eliminar todo",
                                (d, w) -> eliminarCuentaCompleta())
                        .setNegativeButton("Cancelar", null)
                        .show()
        );

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationPaciente);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_paciente_perfil);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_paciente_inicio) {
                    startActivity(new Intent(this, PacienteMainActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                } else if (id == R.id.nav_paciente_citas) {
                    startActivity(new Intent(this, MisCitasActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                } else if (id == R.id.nav_paciente_agendar) {
                    startActivity(new Intent(this, AgendarCitaActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                } else if (id == R.id.nav_paciente_perfil) {
                    return true;
                }
                return false;
            });
        }
    }

    // C: FIX — SnapshotListener para actualizaciones en tiempo real
    private void cargarPerfilEnTiempoReal() {
        listenerPerfil = db.collection("usuarios").document(userId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists()) return;

                    String nombre = doc.getString("Nombre");
                    if (nombre == null) nombre = doc.getString("nombre");
                    String idPac = doc.getString("paciente_id");
                    if (idPac == null) idPac = userId.substring(0, 6).toUpperCase();

                    Long faltas = doc.getLong("faltas");
                    Boolean bloq = doc.getBoolean("bloqueado");
                    Long nivel  = doc.getLong("nivelPenalizacion");
                    Long rest   = doc.getLong("restriccionHasta");
                    if (faltas == null) faltas = 0L;
                    if (bloq == null) bloq = false;
                    if (nivel == null) nivel = 0L;

                    String tel  = doc.getString("telefono");
                    String fnac = doc.getString("fechaNacimiento");
                    String sang = doc.getString("tipoSangre");
                    String aler = doc.getString("alergias");

                    tvNombrePerfilPaciente.setText(nombre != null ? nombre : "Paciente");
                    tvIdPerfilPaciente.setText("ID: " + idPac);
                    tvTelefonoPerfil.setText(
                            tel != null && !tel.isEmpty() ? tel : "No registrado");
                    tvFechaNacPerfil.setText(
                            fnac != null && !fnac.isEmpty() ? fnac : "No registrada");
                    tvTipoSangrePerfil.setText(
                            sang != null && !sang.isEmpty() ? sang : "No registrado");
                    tvAlergiasPerfil.setText(
                            aler != null && !aler.isEmpty() ? aler : "Ninguna registrada");
                    tvFaltasNumeroPerfil.setText(String.valueOf(faltas));

                    if (bloq || faltas >= 3) {
                        tvEstadoNumeroPerfil.setText("✗");
                        tvEstadoNumeroPerfil.setTextColor(0xFFB71C1C);
                        tvEstadoTextoPerfil.setText("Bloqueado");
                        tvFaltasNumeroPerfil.setTextColor(0xFFB71C1C);
                    } else if (nivel >= 2 && rest != null &&
                            System.currentTimeMillis() < rest) {
                        tvEstadoNumeroPerfil.setText("!");
                        tvEstadoNumeroPerfil.setTextColor(0xFFF57C00);
                        tvEstadoTextoPerfil.setText("Restringido");
                        tvFaltasNumeroPerfil.setTextColor(0xFFF57C00);
                    } else {
                        tvEstadoNumeroPerfil.setText("✓");
                        tvEstadoNumeroPerfil.setTextColor(0xFF1565C0);
                        tvEstadoTextoPerfil.setText("Activo");
                        tvFaltasNumeroPerfil.setTextColor(
                                faltas > 0 ? 0xFFF57C00 : 0xFF1565C0);
                    }
                });
    }

    private void configurarEdicion() {
        tvNombrePerfilPaciente.setOnClickListener(v ->
                mostrarDialogoEditar("Nombre", "nombre",
                        tvNombrePerfilPaciente.getText().toString()));

        View btnEditTel = findViewById(R.id.btnEditarTelefono);
        if (btnEditTel != null) btnEditTel.setOnClickListener(v ->
                mostrarDialogoEditar("Teléfono", "telefono",
                        tvTelefonoPerfil.getText().toString()));

        View btnEditFecha = findViewById(R.id.btnEditarFechaNac);
        if (btnEditFecha != null) btnEditFecha.setOnClickListener(v ->
                mostrarDialogoEditar("Fecha de nacimiento", "fechaNacimiento",
                        tvFechaNacPerfil.getText().toString()));

        View btnEditSangre = findViewById(R.id.btnEditarSangre);
        if (btnEditSangre != null) btnEditSangre.setOnClickListener(v ->
                mostrarDialogoEditarOpciones("Tipo de sangre", "tipoSangre",
                        new String[]{"A+","A-","B+","B-","AB+","AB-","O+","O-"}));

        View btnEditAlergias = findViewById(R.id.btnEditarAlergias);
        if (btnEditAlergias != null) btnEditAlergias.setOnClickListener(v ->
                mostrarDialogoEditar("Alergias", "alergias",
                        tvAlergiasPerfil.getText().toString()));
    }

    private void mostrarDialogoEditar(String titulo, String campo,
                                       String valorActual) {
        EditText input = new EditText(this);
        boolean esDefault = valorActual.equals("No registrado") ||
                valorActual.equals("No registrada") ||
                valorActual.equals("Ninguna registrada");
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

    private void mostrarDialogoEditarOpciones(String titulo, String campo,
                                               String[] opciones) {
        new AlertDialog.Builder(this)
                .setTitle("Selecciona " + titulo)
                .setItems(opciones, (d, w) -> guardarCampo(campo, opciones[w]))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void guardarCampo(String campo, String valor) {
        db.collection("usuarios").document(userId)
                .update(campo, valor)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Actualizado", Toast.LENGTH_SHORT).show();
                    // SnapshotListener actualiza la UI automáticamente
                    if ("nombre".equals(campo)) {
                        db.collection("usuarios").document(userId)
                                .update("Nombre", valor);
                    }
                });
    }

    private void cargarCitas() {
        db.collection("citas").whereEqualTo("idPaciente", userId).get()
                .addOnSuccessListener(snap ->
                        tvTotalCitasPerfil.setText(String.valueOf(snap.size())));

        db.collection("citas").whereEqualTo("idPaciente", userId)
                .whereEqualTo("estado", "aceptada").limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        QueryDocumentSnapshot d =
                                (QueryDocumentSnapshot) snap.getDocuments().get(0);
                        String trat = d.getString("tratamiento");
                        String fech = d.getString("fecha");
                        String hora = d.getString("horaDisplay");
                        if (hora == null) hora = d.getString("hora");
                        tvProximaCitaPerfil.setText(
                                "🦷 " + (trat != null ? trat : "Cita") +
                                "\n📅 " + (fech != null ? fech : "--") +
                                "  🕐 " + (hora != null ? hora : "--"));
                        tvProximaCitaPerfil.setTextColor(0xFF1565C0);
                    } else {
                        tvProximaCitaPerfil.setText("No tienes citas próximas.");
                    }
                });
    }

    private void cargarFamiliares() {
        db.collection("usuarios").document(userId)
                .collection("familiares").get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        tvFamiliaresPerfil.setText("No tienes familiares registrados.");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (QueryDocumentSnapshot d : snap) {
                        String nom = d.getString("nombre");
                        String par = d.getString("parentesco");
                        sb.append("👤 ").append(nom != null ? nom : "—")
                          .append(" (").append(par != null ? par : "—").append(")\n");
                    }
                    tvFamiliaresPerfil.setText(sb.toString().trim());
                });
    }

    private void eliminarCuentaCompleta() {
        // FIX: eliminar citas propias Y citas de familiares (idPacienteTitular)
        db.collection("citas").whereEqualTo("idPaciente", userId).get()
                .addOnSuccessListener(citasSnap -> {
                    for (QueryDocumentSnapshot doc : citasSnap) doc.getReference().delete();

                    db.collection("citas")
                            .whereEqualTo("idPacienteTitular", userId).get()
                            .addOnSuccessListener(titularSnap -> {
                                for (QueryDocumentSnapshot doc : titularSnap)
                                    doc.getReference().delete();

                                db.collection("usuarios").document(userId)
                                        .collection("familiares").get()
                                        .addOnSuccessListener(famSnap -> {
                                            for (QueryDocumentSnapshot doc : famSnap)
                                                doc.getReference().delete();
                                            db.collection("usuarios").document(userId)
                                                    .collection("expediente").get()
                                                    .addOnSuccessListener(expSnap -> {
                                                        for (QueryDocumentSnapshot doc : expSnap)
                                                            doc.getReference().delete();
                                                        db.collection("usuarios").document(userId)
                                                                .delete()
                                                                .addOnSuccessListener(aVoid -> {
                                                                    if (mAuth.getCurrentUser() != null) {
                                                                        mAuth.getCurrentUser().delete()
                                                                                .addOnSuccessListener(av -> {
                                                                                    Toast.makeText(this,
                                                                                            "Cuenta eliminada",
                                                                                            Toast.LENGTH_SHORT).show();
                                                                                    startActivity(new Intent(
                                                                                            this, LoginActivity.class));
                                                                                    finish();
                                                                                });
                                                                    }
                                                                });
                                                    });
                                        });
                            });
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // C: cancelar listener al salir
        if (listenerPerfil != null) listenerPerfil.remove();
    }
}
