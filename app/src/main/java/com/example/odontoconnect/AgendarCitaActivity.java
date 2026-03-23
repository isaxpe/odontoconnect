package com.example.odontoconnect;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AgendarCitaActivity extends AppCompatActivity {

    private CalendarView calendarViewPaciente;
    private TextView tvInfoDisponibilidad;
    private Button btnConfirmarCita;
    private Spinner spinnerTratamiento, spinnerHoras;
    private LinearLayout layoutHoras;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private String fechaSeleccionada = "";
    private String idDoctorEncontrado = "";
    private int duracionTratamientoActual = 1;

    private final String[] nombresTratamientos = {
            "Limpieza General (1 hr)",
            "Consulta de Revisión (1 hr)",
            "Extracción Muela (2 hrs)",
            "Endodoncia Completa (3 hrs)"
    };
    private final int[] horasTratamientos = {1, 1, 2, 3};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agendar_cita);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        calendarViewPaciente = findViewById(R.id.calendarViewPaciente);
        tvInfoDisponibilidad = findViewById(R.id.tvInfoDisponibilidad);
        btnConfirmarCita     = findViewById(R.id.btnConfirmarCita);
        spinnerTratamiento   = findViewById(R.id.spinnerTratamiento);
        spinnerHoras         = findViewById(R.id.spinnerHoras);
        layoutHoras          = findViewById(R.id.layoutHoras);

        // Bloquear fechas pasadas en el calendario
        calendarViewPaciente.setMinDate(System.currentTimeMillis());

        // Spinner de tratamientos
        ArrayAdapter<String> adapterTratamientos = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, nombresTratamientos);
        spinnerTratamiento.setAdapter(adapterTratamientos);

        spinnerTratamiento.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                duracionTratamientoActual = horasTratamientos[position];
                if (!fechaSeleccionada.isEmpty() && !idDoctorEncontrado.isEmpty()) {
                    verificarDia(fechaSeleccionada);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        buscarDoctor();

        calendarViewPaciente.setOnDateChangeListener((view, year, month, dayOfMonth) -> {

            // Validación de fecha pasada
            Calendar hoy = Calendar.getInstance();
            hoy.set(Calendar.HOUR_OF_DAY, 0);
            hoy.set(Calendar.MINUTE, 0);
            hoy.set(Calendar.SECOND, 0);
            hoy.set(Calendar.MILLISECOND, 0);

            Calendar seleccionada = Calendar.getInstance();
            seleccionada.set(year, month, dayOfMonth, 0, 0, 0);
            seleccionada.set(Calendar.MILLISECOND, 0);

            if (seleccionada.before(hoy)) {
                tvInfoDisponibilidad.setText("⚠️ No puedes agendar en una fecha pasada.");
                tvInfoDisponibilidad.setTextColor(
                        getResources().getColor(android.R.color.holo_red_dark));
                layoutHoras.setVisibility(View.GONE);
                btnConfirmarCita.setEnabled(false);
                fechaSeleccionada = "";
                return;
            }

            tvInfoDisponibilidad.setTextColor(
                    getResources().getColor(android.R.color.darker_gray));
            fechaSeleccionada = dayOfMonth + "-" + (month + 1) + "-" + year;
            tvInfoDisponibilidad.setText("Buscando disponibilidad...");
            btnConfirmarCita.setEnabled(false);
            layoutHoras.setVisibility(View.GONE);

            if (!idDoctorEncontrado.isEmpty()) {
                verificarDia(fechaSeleccionada);
            }
        });

        btnConfirmarCita.setOnClickListener(v -> {
            if (spinnerHoras.getSelectedItem() == null) {
                Toast.makeText(this, "Selecciona un horario primero", Toast.LENGTH_SHORT).show();
                return;
            }
            String horaElegida        = spinnerHoras.getSelectedItem().toString();
            String tratamientoElegido = spinnerTratamiento.getSelectedItem().toString();

            btnConfirmarCita.setEnabled(false);
            btnConfirmarCita.setText("Verificando disponibilidad...");

            // NUEVO: verificar duplicados ANTES de guardar
            verificarDuplicadoYGuardar(horaElegida, tratamientoElegido);
        });
    }

    private void buscarDoctor() {
        db.collection("usuarios")
                .whereIn("rol", Arrays.asList("odontologo", "doctor"))
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        idDoctorEncontrado =
                                queryDocumentSnapshots.getDocuments().get(0).getId();
                    } else {
                        tvInfoDisponibilidad.setText("No se encontró un odontólogo disponible.");
                    }
                })
                .addOnFailureListener(e ->
                        tvInfoDisponibilidad.setText("Error al buscar doctor.")
                );
    }

    private void verificarDia(String fecha) {
        String[] partes = fecha.split("-");
        String fechaAgenda = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                Integer.parseInt(partes[2]),
                Integer.parseInt(partes[1]),
                Integer.parseInt(partes[0]));

        db.collection("usuarios").document(idDoctorEncontrado)
                .collection("agenda_doctor").document(fechaAgenda)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("turnos")) {
                        List<String> turnosDelDoctor =
                                (List<String>) documentSnapshot.get("turnos");
                        calcularHorasDisponibles(turnosDelDoctor);
                    } else {
                        tvInfoDisponibilidad.setText("El doctor no tiene agenda para este día.");
                        layoutHoras.setVisibility(View.GONE);
                        btnConfirmarCita.setEnabled(false);
                    }
                })
                .addOnFailureListener(e ->
                        tvInfoDisponibilidad.setText("Error al verificar disponibilidad.")
                );
    }

    private void calcularHorasDisponibles(List<String> turnos) {
        List<String> horasValidas = new ArrayList<>();

        for (String bloque : turnos) {
            String[] partes = bloque.split(" a ");
            if (partes.length == 2) {
                int horaInicio = Integer.parseInt(partes[0].split(":")[0]);
                int horaFin    = Integer.parseInt(partes[1].split(":")[0]);

                for (int h = horaInicio; h <= horaFin - duracionTratamientoActual; h++) {
                    horasValidas.add(String.format(Locale.getDefault(), "%02d:00", h));
                }
            }
        }

        if (!horasValidas.isEmpty()) {
            tvInfoDisponibilidad.setText("¡Horarios disponibles encontrados!");
            layoutHoras.setVisibility(View.VISIBLE);
            btnConfirmarCita.setEnabled(true);
            spinnerHoras.setAdapter(new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_dropdown_item, horasValidas));
        } else {
            tvInfoDisponibilidad.setText(
                    "No hay tiempo disponible para este tratamiento en este día.");
            layoutHoras.setVisibility(View.GONE);
            btnConfirmarCita.setEnabled(false);
        }
    }

    // ── NUEVO: Verificar si ya existe una cita en esa fecha y hora ──
    private void verificarDuplicadoYGuardar(String hora, String tratamiento) {
        // Buscamos si ya hay una cita pendiente o aceptada para el mismo doctor,
        // misma fecha y misma hora
        db.collection("citas")
                .whereEqualTo("idDoctor", idDoctorEncontrado)
                .whereEqualTo("fecha", fechaSeleccionada)
                .whereEqualTo("hora", hora)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    // Revisamos si alguna de las citas encontradas está pendiente o aceptada
                    boolean hayConflicto = false;
                    for (com.google.firebase.firestore.DocumentSnapshot doc :
                            queryDocumentSnapshots.getDocuments()) {
                        String estadoExistente = doc.getString("estado");
                        if ("pendiente".equals(estadoExistente) ||
                                "aceptada".equals(estadoExistente)) {
                            hayConflicto = true;
                            break;
                        }
                    }

                    if (hayConflicto) {
                        // Ya está ocupado ese horario
                        Toast.makeText(this,
                                "⚠️ Ese horario ya está ocupado. Elige otra hora.",
                                Toast.LENGTH_LONG).show();
                        btnConfirmarCita.setEnabled(true);
                        btnConfirmarCita.setText("CONFIRMAR CITA");

                        // Resaltar el mensaje de disponibilidad en rojo
                        tvInfoDisponibilidad.setText(
                                "❌ Horario no disponible. Selecciona otra hora.");
                        tvInfoDisponibilidad.setTextColor(
                                getResources().getColor(android.R.color.holo_red_dark));
                    } else {
                        // No hay conflicto — proceder a guardar
                        guardarCitaEnFirebase(hora, tratamiento);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al verificar disponibilidad.",
                            Toast.LENGTH_SHORT).show();
                    btnConfirmarCita.setEnabled(true);
                    btnConfirmarCita.setText("CONFIRMAR CITA");
                });
    }

    private void guardarCitaEnFirebase(String hora, String tratamiento) {
        if (mAuth.getCurrentUser() == null) return;

        String idPaciente = mAuth.getCurrentUser().getUid();

        Map<String, Object> cita = new HashMap<>();
        cita.put("idPaciente", idPaciente);
        cita.put("idDoctor", idDoctorEncontrado);
        cita.put("fecha", fechaSeleccionada);
        cita.put("hora", hora);
        cita.put("tratamiento", tratamiento);
        cita.put("duracionHoras", duracionTratamientoActual);
        cita.put("estado", "pendiente");

        db.collection("citas").add(cita)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "¡Solicitud enviada al doctor!",
                            Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al enviar la solicitud.",
                            Toast.LENGTH_SHORT).show();
                    btnConfirmarCita.setEnabled(true);
                    btnConfirmarCita.setText("CONFIRMAR CITA");
                });
    }
}