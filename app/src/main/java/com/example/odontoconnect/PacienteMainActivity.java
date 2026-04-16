package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class PacienteMainActivity extends AppCompatActivity {

    private TextView tvNombrePaciente, tvIdPaciente, tvEstadoPaciente,
            tvContadorFaltas, tvDetallesProximaCita, tvAdvertencia;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration listenerPerfil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paciente_main);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        tvNombrePaciente      = findViewById(R.id.tvNombrePaciente);
        tvIdPaciente          = findViewById(R.id.tvIdPaciente);
        tvEstadoPaciente      = findViewById(R.id.tvEstadoPaciente);
        tvContadorFaltas      = findViewById(R.id.tvContadorFaltas);
        tvDetallesProximaCita = findViewById(R.id.tvDetallesProximaCita);
        tvAdvertencia         = findViewById(R.id.tvAdvertenciaPenalizacion);

        configurarBotones();

        FirebaseUser usuarioActual = mAuth.getCurrentUser();
        if (usuarioActual != null) {
            String uid = usuarioActual.getUid();
            cargarPerfilPaciente(uid, usuarioActual.getEmail());
            cargarProximaCita(uid);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationPaciente);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_paciente_inicio);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_paciente_inicio) {
                    return true;
                } else if (id == R.id.nav_paciente_citas) {
                    startActivity(new Intent(this, MisCitasActivity.class));
                    overridePendingTransition(0, 0);
                    return true;
                } else if (id == R.id.nav_paciente_agendar) {
                    intentarAgendar();
                    return true;
                } else if (id == R.id.nav_paciente_perfil) {
                    startActivity(new Intent(this, PerfilPacienteActivity.class));
                    overridePendingTransition(0, 0);
                    return true;
                }
                return false;
            });
        }
    }

    private void configurarBotones() {
        View btnAgendar = findViewById(R.id.btnAbrirAgendar);
        if (btnAgendar != null) {
            btnAgendar.setOnClickListener(v -> intentarAgendar());
        }

        View btnMisCitas = findViewById(R.id.btnMisCitas);
        if (btnMisCitas != null) {
            btnMisCitas.setOnClickListener(v ->
                    startActivity(new Intent(this, MisCitasActivity.class))
            );
        }

        View btnFamiliares = findViewById(R.id.btnFamiliares);
        if (btnFamiliares != null) {
            btnFamiliares.setOnClickListener(v ->
                    startActivity(new Intent(this, FamiliaresActivity.class))
            );
        }
    }

    // MEJORA: verificar penalización antes de permitir agendar
    private void intentarAgendar() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(doc -> {
                    PenalizacionHelper.verificarPuedeAgendar(doc, this,
                            new PenalizacionHelper.ResultadoVerificacion() {
                                @Override
                                public void onPermitido() {
                                    startActivity(new Intent(PacienteMainActivity.this,
                                            AgendarCitaActivity.class));
                                }

                                @Override
                                public void onBloqueado(String mensaje) {
                                    new AlertDialog.Builder(PacienteMainActivity.this)
                                            .setTitle("Acceso restringido")
                                            .setMessage(mensaje)
                                            .setPositiveButton("Entendido", null)
                                            .show();
                                }
                            });
                });
    }

    private void cargarPerfilPaciente(String uid, String email) {
        // SnapshotListener para que el estado se actualice en tiempo real
        listenerPerfil = db.collection("usuarios").document(uid)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null || documentSnapshot == null) return;
                    if (documentSnapshot.exists()) {
                        mostrarDatos(documentSnapshot);
                    } else {
                        crearPerfilPorDefecto(uid, email);
                    }
                });
    }

    private void mostrarDatos(DocumentSnapshot document) {
        String nombre = document.getString("Nombre");
        if (nombre == null) nombre = document.getString("nombre");

        String idPaciente = document.getString("paciente_id");
        if (idPaciente == null)
            idPaciente = document.getId().substring(0, 6).toUpperCase();

        Long faltas       = document.getLong("faltas");
        Boolean bloqueado = document.getBoolean("bloqueado");
        Long nivelPen     = document.getLong("nivelPenalizacion");
        Long restriccion  = document.getLong("restriccionHasta");
        String advertencia = document.getString("advertencia");

        if (faltas == null) faltas = 0L;
        if (bloqueado == null) bloqueado = false;
        if (nivelPen == null) nivelPen = 0L;

        tvNombrePaciente.setText(nombre != null ? nombre : "Paciente");
        tvIdPaciente.setText("ID: " + idPaciente);

        // MEJORA: usar PenalizacionHelper para el mensaje de estado
        String mensajeEstado = PenalizacionHelper.getMensajeEstado(
                faltas, bloqueado, nivelPen, restriccion);

        boolean puedeAgendar = !bloqueado && faltas < 3;
        boolean restringido  = nivelPen >= 2 && restriccion != null
                && System.currentTimeMillis() < restriccion;

        if (bloqueado || faltas >= 3) {
            tvEstadoPaciente.setText("BLOQUEADO");
            tvEstadoPaciente.setTextColor(0xFFB71C1C);
            tvContadorFaltas.setText("⚠️ Faltas: " + faltas + " / 3 — Cuenta suspendida");
            tvContadorFaltas.setTextColor(0xFFB71C1C);
            bloquearBotonAgendar(false);
        } else if (restringido) {
            tvEstadoPaciente.setText("RESTRINGIDO");
            tvEstadoPaciente.setTextColor(0xFFF57C00);
            tvContadorFaltas.setText("🚫 Faltas: " + faltas + " / 3 — Temporalmente restringido");
            tvContadorFaltas.setTextColor(0xFFF57C00);
            bloquearBotonAgendar(false);
        } else {
            tvEstadoPaciente.setText("ACTIVO");
            tvEstadoPaciente.setTextColor(0xFF2E7D32);
            tvContadorFaltas.setText("Faltas: " + faltas + " / 3");
            tvContadorFaltas.setTextColor(0xFF666666);
            bloquearBotonAgendar(true);
        }

        // Mostrar advertencia si existe
        if (tvAdvertencia != null) {
            if (advertencia != null && !advertencia.isEmpty() && nivelPen > 0) {
                tvAdvertencia.setText(mensajeEstado);
                tvAdvertencia.setVisibility(View.VISIBLE);
            } else {
                tvAdvertencia.setVisibility(View.GONE);
            }
        }
    }

    private void bloquearBotonAgendar(boolean habilitado) {
        View btnAgendar = findViewById(R.id.btnAbrirAgendar);
        if (btnAgendar != null) {
            btnAgendar.setEnabled(habilitado);
            btnAgendar.setAlpha(habilitado ? 1.0f : 0.4f);
        }
    }

    private void cargarProximaCita(String uid) {
        if (tvDetallesProximaCita == null) return;

        db.collection("citas")
                .whereEqualTo("idPaciente", uid)
                .whereEqualTo("estado", "aceptada")
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot proximaCita =
                                queryDocumentSnapshots.getDocuments().get(0);
                        String tratamiento = proximaCita.getString("tratamiento");
                        String fecha       = proximaCita.getString("fecha");
                        String hora        = proximaCita.getString("horaDisplay");
                        if (hora == null) hora = proximaCita.getString("hora");
                        tvDetallesProximaCita.setText(
                                "🦷 " + (tratamiento != null ? tratamiento : "Cita") +
                                "\n" + (fecha != null ? fecha : "--") +
                                "  " + (hora != null ? hora : "--"));
                        tvDetallesProximaCita.setTextColor(0xFF1565C0);
                    } else {
                        tvDetallesProximaCita.setText("No tienes citas próximas agendadas.");
                    }
                });
    }

    private void crearPerfilPorDefecto(String uid, String email) {
        Map<String, Object> nuevoPaciente = new HashMap<>();
        nuevoPaciente.put("nombre",      "Paciente Nuevo");
        nuevoPaciente.put("Nombre",      "Paciente Nuevo");
        nuevoPaciente.put("email",       email);
        nuevoPaciente.put("rol",         "paciente");
        nuevoPaciente.put("faltas",      0);
        nuevoPaciente.put("bloqueado",   false);
        nuevoPaciente.put("nivelPenalizacion", 0);
        nuevoPaciente.put("paciente_id", uid.substring(0, 6).toUpperCase());

        db.collection("usuarios").document(uid).set(nuevoPaciente);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerPerfil != null) listenerPerfil.remove();
    }
}
