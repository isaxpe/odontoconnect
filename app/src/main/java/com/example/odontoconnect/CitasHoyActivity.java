package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CitasHoyActivity extends AppCompatActivity {

    private LinearLayout contenedorCitasHoy;
    private TextView tvMensajeVacio;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration listenerCitas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_citas_hoy);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        contenedorCitasHoy = findViewById(R.id.contenedorCitasHoy);
        tvMensajeVacio     = findViewById(R.id.tvMensajeVacioHoy);

        cargarCitasDeHoy();

        // BottomNavigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
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
                    startActivity(new Intent(this, PerfilDoctorActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                }
                return false;
            });
        }
    }

    private void cargarCitasDeHoy() {
        if (mAuth.getCurrentUser() == null) return;
        String miUid = mAuth.getCurrentUser().getUid();
        String hoy   = new SimpleDateFormat("d-M-yyyy",
                Locale.getDefault()).format(new Date());

        listenerCitas = db.collection("citas")
                .whereEqualTo("idDoctor", miUid)
                .whereEqualTo("fecha", hoy)
                .whereEqualTo("estado", "aceptada")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    contenedorCitasHoy.removeAllViews();

                    int sinMarcar = 0;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String asistencia = doc.getString("asistencia");
                        if (asistencia == null || "pendiente".equals(asistencia)) {
                            crearTarjeta(doc);
                            sinMarcar++;
                        }
                    }

                    if (sinMarcar == 0) {
                        tvMensajeVacio.setText(snap.isEmpty()
                                ? "📅 No tienes citas para hoy."
                                : "✅ Ya marcaste la asistencia de todas las citas.");
                        tvMensajeVacio.setVisibility(View.VISIBLE);
                    } else {
                        tvMensajeVacio.setVisibility(View.GONE);
                    }
                });
    }

    private void crearTarjeta(DocumentSnapshot doc) {
        String idCita      = doc.getId();
        String tratamiento = doc.getString("tratamiento");
        String hora        = doc.getString("horaDisplay");
        if (hora == null) hora = doc.getString("hora");
        String idPaciente  = doc.getString("idPaciente");

        View tarjeta = LayoutInflater.from(this)
                .inflate(R.layout.item_cita_hoy, contenedorCitasHoy, false);

        TextView tvInfo     = tarjeta.findViewById(R.id.tvInfoCitaHoy);
        TextView tvNombre   = tarjeta.findViewById(R.id.tvNombrePacienteHoy);
        Button btnAsistio   = tarjeta.findViewById(R.id.btnAsistio);
        Button btnNoAsistio = tarjeta.findViewById(R.id.btnNoAsistio);

        final String horaFinal = hora;
        tvInfo.setText("🦷 " + (tratamiento != null ? tratamiento : "Cita") +
                "   🕐 " + (horaFinal != null ? horaFinal : "--"));

        if (idPaciente != null) {
            db.collection("usuarios").document(idPaciente).get()
                    .addOnSuccessListener(pac -> {
                        String nombre = pac.getString("Nombre");
                        if (nombre == null) nombre = pac.getString("nombre");
                        String idPac  = pac.getString("paciente_id");
                        tvNombre.setText("👤 " + (nombre != null ? nombre : "Paciente") +
                                (idPac != null ? "  •  ID: " + idPac : ""));
                    });
        }

        btnAsistio.setOnClickListener(v ->
                marcarAsistencia(idCita, idPaciente, true));

        btnNoAsistio.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("⚠️ Confirmar inasistencia")
                        .setMessage("¿El paciente NO asistió?\n" +
                                "Esto sumará 1 falta a su cuenta.")
                        .setPositiveButton("Sí, no asistió", (d, w) ->
                                marcarAsistencia(idCita, idPaciente, false))
                        .setNegativeButton("Cancelar", null)
                        .show()
        );

        contenedorCitasHoy.addView(tarjeta);
    }

    private void marcarAsistencia(String idCita, String idPaciente,
                                   boolean asistio) {
        db.collection("citas").document(idCita)
                .update("asistencia", asistio ? "asistio" : "no_asistio")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this,
                            asistio ? "✅ Asistencia confirmada"
                                    : "❌ Inasistencia registrada",
                            Toast.LENGTH_SHORT).show();
                    if (!asistio && idPaciente != null) {
                        aplicarPenalizacion(idPaciente);
                    }
                });
    }

    private void aplicarPenalizacion(String idPaciente) {
        db.collection("usuarios").document(idPaciente)
                .update("faltas", FieldValue.increment(1))
                .addOnSuccessListener(aVoid ->
                        db.collection("usuarios").document(idPaciente).get()
                                .addOnSuccessListener(doc -> {
                                    if (!doc.exists()) return;
                                    Long faltas = doc.getLong("faltas");
                                    if (faltas == null) faltas = 1L;
                                    if (faltas >= 3) {
                                        doc.getReference().update(
                                                "bloqueado", true,
                                                "nivelPenalizacion", 3,
                                                "advertencia",
                                                "Bloqueado por 3 faltas.");
                                        Toast.makeText(this,
                                                "🔴 Paciente BLOQUEADO (3 faltas).",
                                                Toast.LENGTH_LONG).show();
                                    } else if (faltas == 2) {
                                        long rest = System.currentTimeMillis()
                                                + (7L * 24 * 60 * 60 * 1000);
                                        doc.getReference().update(
                                                "nivelPenalizacion", 2,
                                                "restriccionHasta", rest,
                                                "advertencia",
                                                "Restringido 7 días (2 faltas).");
                                        Toast.makeText(this,
                                                "🚫 Restringido 7 días (2 faltas).",
                                                Toast.LENGTH_LONG).show();
                                    } else {
                                        doc.getReference().update(
                                                "nivelPenalizacion", 1,
                                                "advertencia",
                                                "1 falta. Con 3 serás bloqueado.");
                                        Toast.makeText(this,
                                                "⚠️ 1 falta registrada.",
                                                Toast.LENGTH_LONG).show();
                                    }
                                })
                );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerCitas != null) listenerCitas.remove();
    }
}
