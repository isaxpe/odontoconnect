package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class PerfilPacienteActivity extends AppCompatActivity {

    private TextView tvNombrePerfilPaciente, tvIdPerfilPaciente,
            tvCorreoPerfilPaciente, tvTelefonoPerfil, tvFechaNacPerfil,
            tvTipoSangrePerfil, tvAlergiasPerfil,
            tvMedicamentosPerfil, tvEnfermedadesPerfil, tvEmergenciaPerfil,
            tvFaltasNumeroPerfil, tvTotalCitasPerfil,
            tvEstadoNumeroPerfil, tvEstadoTextoPerfil,
            tvProximaCitaPerfil, tvFamiliaresPerfil;
    private Button btnCerrarSesionPaciente, btnEliminarCuenta;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userId;

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
        tvMedicamentosPerfil   = findViewById(R.id.tvMedicamentosPerfil);
        tvEnfermedadesPerfil   = findViewById(R.id.tvEnfermedadesPerfil);
        tvEmergenciaPerfil     = findViewById(R.id.tvEmergenciaPerfil);
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
                                            "Revisa tu correo",
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
                        .setTitle("Eliminar cuenta")
                        .setMessage("Se eliminaran todos tus datos, citas, " +
                                "familiares y expediente. Seguro?")
                        .setPositiveButton("Si, eliminar todo",
                                (d, w) -> eliminarCuentaCompleta())
                        .setNegativeButton("Cancelar", null)
                        .show()
        );

        BottomNavHelper.setupPaciente(this, BottomNavHelper.PacienteTab.PERFIL);

    }

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
                    if (faltas == null) faltas = 0L;
                    if (bloq == null) bloq = false;

                    String tel  = doc.getString("telefono");
                    String fnac = doc.getString("fechaNacimiento");
                    String sang = doc.getString("tipoSangre");
                    String aler = doc.getString("alergias");
                    String meds = doc.getString("medicamentos");
                    String enfr = doc.getString("enfermedadesCronicas");
                    String emer = doc.getString("contactoEmergencia");

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

                    if (tvMedicamentosPerfil != null)
                        tvMedicamentosPerfil.setText(
                                meds != null && !meds.isEmpty() ? meds : "Ninguno");
                    if (tvEnfermedadesPerfil != null)
                        tvEnfermedadesPerfil.setText(
                                enfr != null && !enfr.isEmpty() ? enfr : "Ninguna");
                    if (tvEmergenciaPerfil != null)
                        tvEmergenciaPerfil.setText(
                                emer != null && !emer.isEmpty() ? emer : "No registrado");

                    tvFaltasNumeroPerfil.setText(String.valueOf(faltas));

                    if (bloq) {
                        tvEstadoNumeroPerfil.setText("!");
                        tvEstadoNumeroPerfil.setTextColor(0xFFD32F2F);
                        tvEstadoTextoPerfil.setText("Bloqueado");
                        tvFaltasNumeroPerfil.setTextColor(0xFFD32F2F);
                    } else {
                        tvEstadoNumeroPerfil.setText("OK");
                        tvEstadoNumeroPerfil.setTextColor(0xFF1B3A6B);
                        tvEstadoTextoPerfil.setText("Activo");
                        tvFaltasNumeroPerfil.setTextColor(
                                faltas > 0 ? 0xFFF57C00 : 0xFF1B3A6B);
                    }
                });
    }

    private void configurarEdicion() {
        // Nombre se edita tocando el texto
        tvNombrePerfilPaciente.setOnClickListener(v ->
                mostrarDialogoEditar("Nombre", "nombre",
                        tvNombrePerfilPaciente.getText().toString(),
                        InputType.TYPE_TEXT_FLAG_CAP_WORDS));

        View btnEditTel = findViewById(R.id.btnEditarTelefono);
        if (btnEditTel != null) btnEditTel.setOnClickListener(v ->
                mostrarDialogoEditar("Telefono", "telefono",
                        tvTelefonoPerfil.getText().toString(),
                        InputType.TYPE_CLASS_PHONE));

        View btnEditFecha = findViewById(R.id.btnEditarFechaNac);
        if (btnEditFecha != null) btnEditFecha.setOnClickListener(v ->
                mostrarDialogoEditar("Fecha de nacimiento (DD/MM/AAAA)",
                        "fechaNacimiento",
                        tvFechaNacPerfil.getText().toString(),
                        InputType.TYPE_CLASS_DATETIME));

        View btnEditSangre = findViewById(R.id.btnEditarSangre);
        if (btnEditSangre != null) btnEditSangre.setOnClickListener(v ->
                mostrarDialogoEditarOpciones("Tipo de sangre", "tipoSangre",
                        new String[]{"A+","A-","B+","B-","AB+","AB-","O+","O-"}));

        View btnEditAlergias = findViewById(R.id.btnEditarAlergias);
        if (btnEditAlergias != null) btnEditAlergias.setOnClickListener(v ->
                mostrarDialogoEditar("Alergias", "alergias",
                        tvAlergiasPerfil.getText().toString(),
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));

        View btnEditMeds = findViewById(R.id.btnEditarMedicamentos);
        if (btnEditMeds != null) btnEditMeds.setOnClickListener(v ->
                mostrarDialogoEditar("Medicamentos", "medicamentos",
                        tvMedicamentosPerfil != null ?
                                tvMedicamentosPerfil.getText().toString() : "",
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));

        View btnEditEnf = findViewById(R.id.btnEditarEnfermedades);
        if (btnEditEnf != null) btnEditEnf.setOnClickListener(v ->
                mostrarDialogoEditar("Enfermedades cronicas",
                        "enfermedadesCronicas",
                        tvEnfermedadesPerfil != null ?
                                tvEnfermedadesPerfil.getText().toString() : "",
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));

        View btnEditEmer = findViewById(R.id.btnEditarEmergencia);
        if (btnEditEmer != null) btnEditEmer.setOnClickListener(v ->
                mostrarDialogoEditar("Contacto de emergencia",
                        "contactoEmergencia",
                        tvEmergenciaPerfil != null ?
                                tvEmergenciaPerfil.getText().toString() : "",
                        InputType.TYPE_TEXT_FLAG_CAP_WORDS));
    }

    private void mostrarDialogoEditar(String titulo, String campo,
                                       String valorActual, int inputType) {
        EditText input = new EditText(this);
        input.setInputType(inputType);
        boolean esDefault = valorActual.equals("No registrado") ||
                valorActual.equals("No registrada") ||
                valorActual.equals("Ninguna registrada") ||
                valorActual.equals("Ninguna") ||
                valorActual.equals("Ninguno");
        input.setText(esDefault ? "" : valorActual);
        input.setHint(titulo);
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(this)
                .setTitle("Editar " + titulo)
                .setView(input)
                .setPositiveButton("Guardar", (d, w) -> {
                    String val = input.getText().toString().trim();
                    guardarCampo(campo, val);
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
                    Toast.makeText(this, "Actualizado", Toast.LENGTH_SHORT).show();
                    // SnapshotListener actualiza la UI automaticamente
                    if ("nombre".equals(campo)) {
                        db.collection("usuarios").document(userId)
                                .update("Nombre", valor);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
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
                                (trat != null ? trat : "Cita") +
                                "\n" + (fech != null ? fech : "--") +
                                " - " + (hora != null ? hora : "--"));
                        tvProximaCitaPerfil.setTextColor(0xFF1B3A6B);
                    } else {
                        tvProximaCitaPerfil.setText("No tienes citas proximas.");
                    }
                });
    }

    private void cargarFamiliares() {
        db.collection("usuarios").document(userId)
                .collection("familiares").get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        tvFamiliaresPerfil.setText("0 familiares registrados");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(snap.size()).append(" familiar(es) registrado(s)\n\n");
                    for (QueryDocumentSnapshot d : snap) {
                        String nom = d.getString("nombre");
                        String par = d.getString("parentesco");
                        sb.append(nom != null ? nom : "--")
                          .append(" (").append(par != null ? par : "--").append(")\n");
                    }
                    tvFamiliaresPerfil.setText(sb.toString().trim());
                });
    }

    private void eliminarCuentaCompleta() {
        db.collection("citas").whereEqualTo("idPaciente", userId).get()
                .addOnSuccessListener(citasSnap -> {
                    for (QueryDocumentSnapshot doc : citasSnap) doc.getReference().delete();
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerPerfil != null) listenerPerfil.remove();
    }
}
