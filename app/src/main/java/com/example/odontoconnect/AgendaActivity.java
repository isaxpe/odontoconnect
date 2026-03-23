package com.example.odontoconnect;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.CalendarView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AgendaActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private TextView tvDiaSeleccionado, tvListaHorarios;
    private MaterialButton btnHoraInicio, btnHoraFin, btnAgregarBloque, btnLimpiar, btnGuardarDia;

    private String fechaSeleccionada = "";
    private String horaInicioStr = "09:00";
    private String horaFinStr = "14:00";
    private final List<String> listaDeBloques = new ArrayList<>();

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agenda);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        calendarView      = findViewById(R.id.calendarView);
        tvDiaSeleccionado = findViewById(R.id.tvDiaSeleccionado);
        tvListaHorarios   = findViewById(R.id.tvListaHorarios);
        btnHoraInicio     = findViewById(R.id.btnHoraInicio);
        btnHoraFin        = findViewById(R.id.btnHoraFin);
        btnAgregarBloque  = findViewById(R.id.btnAgregarBloque);
        btnLimpiar        = findViewById(R.id.btnLimpiar);
        btnGuardarDia     = findViewById(R.id.btnGuardarDia);

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            fechaSeleccionada = String.format(Locale.getDefault(),
                    "%04d-%02d-%02d", year, (month + 1), dayOfMonth);
            tvDiaSeleccionado.setText("Horarios para: " +
                    dayOfMonth + "/" + (month + 1) + "/" + year);
            listaDeBloques.clear();
            actualizarVistaLista();
        });

        btnHoraInicio.setOnClickListener(v -> abrirReloj(true));
        btnHoraFin.setOnClickListener(v -> abrirReloj(false));

        btnAgregarBloque.setOnClickListener(v -> {
            if (fechaSeleccionada.isEmpty()) {
                Toast.makeText(this, "Selecciona un día primero", Toast.LENGTH_SHORT).show();
                return;
            }
            listaDeBloques.add(horaInicioStr + " a " + horaFinStr);
            actualizarVistaLista();
            Toast.makeText(this, "Bloque agregado", Toast.LENGTH_SHORT).show();
        });

        btnLimpiar.setOnClickListener(v -> {
            listaDeBloques.clear();
            actualizarVistaLista();
        });

        btnGuardarDia.setOnClickListener(v -> guardarAgendaEnFirebase());

        // ── BottomNavigation ──
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_agenda);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inicio) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_agenda) {
                return true; // Ya estamos aquí
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

    private void abrirReloj(boolean esInicio) {
        int horaPorDefecto = esInicio ? 9 : 14;
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String horaFormateada = String.format(Locale.getDefault(),
                    "%02d:%02d", hourOfDay, minute);
            if (esInicio) {
                horaInicioStr = horaFormateada;
                btnHoraInicio.setText("Inicio\n" + horaFormateada);
            } else {
                horaFinStr = horaFormateada;
                btnHoraFin.setText("Fin\n" + horaFormateada);
            }
        }, horaPorDefecto, 0, true).show();
    }

    private void actualizarVistaLista() {
        if (listaDeBloques.isEmpty()) {
            tvListaHorarios.setText("Aún no has agregado horarios para este día.");
        } else {
            StringBuilder texto = new StringBuilder("Turnos guardados:\n");
            for (int i = 0; i < listaDeBloques.size(); i++) {
                texto.append(i + 1).append(". ").append(listaDeBloques.get(i)).append("\n");
            }
            tvListaHorarios.setText(texto.toString());
        }
    }

    private void guardarAgendaEnFirebase() {
        if (fechaSeleccionada.isEmpty()) {
            Toast.makeText(this, "Toca un día en el calendario", Toast.LENGTH_SHORT).show();
            return;
        }
        if (listaDeBloques.isEmpty()) {
            Toast.makeText(this, "Agrega al menos un horario", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> diaAgenda = new HashMap<>();
        diaAgenda.put("fecha", fechaSeleccionada);
        diaAgenda.put("turnos", listaDeBloques);
        diaAgenda.put("disponible", true);

        btnGuardarDia.setEnabled(false);
        btnGuardarDia.setText("Guardando...");

        db.collection("usuarios").document(uid)
                .collection("agenda_doctor").document(fechaSeleccionada)
                .set(diaAgenda)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "¡Día configurado!", Toast.LENGTH_LONG).show();
                    btnGuardarDia.setEnabled(true);
                    btnGuardarDia.setText("GUARDAR DÍA COMPLETO");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show();
                    btnGuardarDia.setEnabled(true);
                    btnGuardarDia.setText("GUARDAR DÍA COMPLETO");
                });
    }
}
