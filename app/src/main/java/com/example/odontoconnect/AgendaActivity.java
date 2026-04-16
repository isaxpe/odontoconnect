package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgendaActivity extends AppCompatActivity {

    private LinearLayout contenedorHoras;
    private TextView tvFechaSeleccionada;
    private Button btnGuardarAgenda;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private String fechaSeleccionada = "";
    private final List<String> horasSeleccionadas = new ArrayList<>();

    private static final List<String> TODAS_LAS_HORAS = Arrays.asList(
            "06:00","07:00","08:00","09:00","10:00",
            "11:00","12:00","13:00","14:00","15:00",
            "16:00","17:00","18:00","19:00","20:00"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agenda);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        contenedorHoras     = findViewById(R.id.contenedorHoras);
        tvFechaSeleccionada = findViewById(R.id.tvFechaSeleccionada);
        btnGuardarAgenda    = findViewById(R.id.btnGuardarAgenda);

        android.widget.CalendarView calendario = findViewById(R.id.calendarViewAgenda);
        if (calendario != null) {
            calendario.setMinDate(System.currentTimeMillis());
            calendario.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
                fechaSeleccionada = String.format("%04d-%02d-%02d",
                        year, month + 1, dayOfMonth);
                if (tvFechaSeleccionada != null)
                    tvFechaSeleccionada.setText("Agenda para: " + fechaSeleccionada);
                horasSeleccionadas.clear();
                cargarAgendaExistente();
                generarBotonesHoras();
            });
        }

        if (btnGuardarAgenda != null) {
            btnGuardarAgenda.setOnClickListener(v -> guardarAgenda());
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_agenda);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_inicio) {
                    startActivity(new Intent(this, MainActivity.class));
                    overridePendingTransition(0, 0); finish(); return true;
                } else if (id == R.id.nav_agenda) {
                    return true;
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

    private void generarBotonesHoras() {
        if (contenedorHoras == null) return;
        contenedorHoras.removeAllViews();
        contenedorHoras.setOrientation(LinearLayout.VERTICAL);

        int cols = 3, total = TODAS_LAS_HORAS.size();
        for (int row = 0; row < Math.ceil((double) total / cols); row++) {
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            fp.setMargins(0, 0, 0, dp(8));
            fila.setLayoutParams(fp);

            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= total) {
                    View sp = new View(this);
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
                    p.setMargins(dp(4), 0, dp(4), 0);
                    sp.setLayoutParams(p);
                    fila.addView(sp);
                    continue;
                }
                String hora = TODAS_LAS_HORAS.get(idx);
                Button btn = new Button(this);
                btn.setText(hora);
                btn.setAllCaps(false);
                btn.setTextSize(13f);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
                p.setMargins(dp(4), 0, dp(4), 0);
                btn.setLayoutParams(p);
                btn.setPadding(0, 0, 0, 0);
                actualizarColorBoton(btn, horasSeleccionadas.contains(hora));
                btn.setOnClickListener(v -> {
                    if (horasSeleccionadas.contains(hora)) {
                        horasSeleccionadas.remove(hora);
                        actualizarColorBoton(btn, false);
                    } else {
                        horasSeleccionadas.add(hora);
                        actualizarColorBoton(btn, true);
                    }
                });
                fila.addView(btn);
            }
            contenedorHoras.addView(fila);
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void actualizarColorBoton(Button btn, boolean sel) {
        btn.setBackgroundColor(sel ? 0xFF1565C0 : 0xFFE3F2FD);
        btn.setTextColor(sel ? 0xFFFFFFFF : 0xFF1565C0);
    }

    private void cargarAgendaExistente() {
        if (mAuth.getCurrentUser() == null || fechaSeleccionada.isEmpty()) return;
        db.collection("usuarios").document(mAuth.getCurrentUser().getUid())
                .collection("agenda_doctor").document(fechaSeleccionada)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        List<String> horas = (List<String>) doc.get("horas");
                        if (horas != null) {
                            horasSeleccionadas.clear();
                            horasSeleccionadas.addAll(horas);
                            generarBotonesHoras();
                        }
                    }
                });
    }

    private void guardarAgenda() {
        if (mAuth.getCurrentUser() == null) return;
        if (fechaSeleccionada.isEmpty()) {
            Toast.makeText(this, "Selecciona un día primero", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        List<String> ordenadas = new ArrayList<>(horasSeleccionadas);
        Collections.sort(ordenadas);
        List<String> turnos = construirTurnos(ordenadas);

        Map<String, Object> agenda = new HashMap<>();
        agenda.put("fecha",      fechaSeleccionada);
        agenda.put("horas",      ordenadas);
        agenda.put("turnos",     turnos);
        agenda.put("disponible", !ordenadas.isEmpty());

        // FIX CRÍTICO: registrar el UID del doctor en configuración global
        // El paciente leerá esto en lugar de hacer whereIn("rol")
        Map<String, Object> config = new HashMap<>();
        config.put("idDoctor",  uid);
        config.put("updatedAt", System.currentTimeMillis());
        db.collection("configuracion").document("consultorio").set(config);

        db.collection("usuarios").document(uid)
                .collection("agenda_doctor").document(fechaSeleccionada)
                .set(agenda)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this,
                                "✅ Agenda guardada para " + fechaSeleccionada,
                                Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private List<String> construirTurnos(List<String> horas) {
        List<String> turnos = new ArrayList<>();
        if (horas.isEmpty()) return turnos;
        String ini = horas.get(0), ant = horas.get(0);
        for (int i = 1; i < horas.size(); i++) {
            String act = horas.get(i);
            if (Integer.parseInt(act.split(":")[0]) !=
                    Integer.parseInt(ant.split(":")[0]) + 1) {
                turnos.add(ini + " a " + ant);
                ini = act;
            }
            ant = act;
        }
        turnos.add(ini + " a " + ant);
        return turnos;
    }
}
