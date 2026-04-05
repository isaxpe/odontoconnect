package com.example.odontoconnect;

import android.content.Intent;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AgendarCitaActivity extends AppCompatActivity {

    private CalendarView calendarViewPaciente;
    private TextView tvInfoDisponibilidad, tvTituloAgendar;
    private Button btnConfirmarCita;
    private Spinner spinnerTratamiento, spinnerHoras;
    private LinearLayout layoutHoras;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private String fechaSeleccionada  = "";
    private String idDoctorEncontrado = "";
    private int duracionTratamientoActual = 1;

    private boolean esFamiliar    = false;
    private String idFamiliar     = null;
    private String nombreFamiliar = null;

    private final List<String> horasParaMostrar = new ArrayList<>();
    private final List<String> horasParaGuardar = new ArrayList<>();
    private final List<String> nombresTratamientos = new ArrayList<>();
    private final List<Integer> horasTratamientos  = new ArrayList<>();
    private ArrayAdapter<String> adapterTratamientos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agendar_cita);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (getIntent() != null) {
            esFamiliar    = getIntent().getBooleanExtra("esFamiliar", false);
            idFamiliar    = getIntent().getStringExtra("idFamiliar");
            nombreFamiliar = getIntent().getStringExtra("nombreFamiliar");
        }

        calendarViewPaciente = findViewById(R.id.calendarViewPaciente);
        tvInfoDisponibilidad = findViewById(R.id.tvInfoDisponibilidad);
        tvTituloAgendar      = findViewById(R.id.tvTituloAgendar);
        btnConfirmarCita     = findViewById(R.id.btnConfirmarCita);
        spinnerTratamiento   = findViewById(R.id.spinnerTratamiento);
        spinnerHoras         = findViewById(R.id.spinnerHoras);
        layoutHoras          = findViewById(R.id.layoutHoras);

        if (esFamiliar && nombreFamiliar != null && tvTituloAgendar != null) {
            tvTituloAgendar.setText("Agendar Cita\npara: " + nombreFamiliar);
        }

        verificarPenalizacionYConfigurar();
    }

    private void verificarPenalizacionYConfigurar() {
        if (mAuth.getCurrentUser() == null) { finish(); return; }
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(doc ->
                        PenalizacionHelper.verificarPuedeAgendar(doc, this,
                                new PenalizacionHelper.ResultadoVerificacion() {
                                    @Override
                                    public void onPermitido() { configurarCalendario(); }
                                    @Override
                                    public void onBloqueado(String mensaje) {
                                        new AlertDialog.Builder(AgendarCitaActivity.this)
                                                .setTitle("Acceso restringido")
                                                .setMessage(mensaje)
                                                .setPositiveButton("Entendido",
                                                        (d, w) -> finish())
                                                .setCancelable(false)
                                                .show();
                                    }
                                })
                );
    }

    private void configurarCalendario() {
        calendarViewPaciente.setMinDate(System.currentTimeMillis());

        adapterTratamientos = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                nombresTratamientos);
        spinnerTratamiento.setAdapter(adapterTratamientos);

        spinnerTratamiento.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v,
                                               int pos, long id) {
                        if (!horasTratamientos.isEmpty() &&
                                pos < horasTratamientos.size()) {
                            duracionTratamientoActual = horasTratamientos.get(pos);
                        }
                        if (!fechaSeleccionada.isEmpty() &&
                                !idDoctorEncontrado.isEmpty()) {
                            verificarDia(fechaSeleccionada);
                        }
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                });

        cargarTratamientosDesdeFirebase();
        buscarDoctor(() -> {
            if (!fechaSeleccionada.isEmpty()) verificarDia(fechaSeleccionada);
        });

        calendarViewPaciente.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar hoy = Calendar.getInstance();
            hoy.set(Calendar.HOUR_OF_DAY, 0);
            hoy.set(Calendar.MINUTE, 0);
            hoy.set(Calendar.SECOND, 0);
            hoy.set(Calendar.MILLISECOND, 0);

            Calendar sel = Calendar.getInstance();
            sel.set(year, month, dayOfMonth, 0, 0, 0);
            sel.set(Calendar.MILLISECOND, 0);

            if (sel.before(hoy)) {
                tvInfoDisponibilidad.setText("⚠️ No puedes agendar en fecha pasada.");
                tvInfoDisponibilidad.setTextColor(0xFFB71C1C);
                layoutHoras.setVisibility(View.GONE);
                btnConfirmarCita.setEnabled(false);
                fechaSeleccionada = "";
                return;
            }

            fechaSeleccionada = dayOfMonth + "-" + (month + 1) + "-" + year;
            tvInfoDisponibilidad.setTextColor(0xFF666666);
            tvInfoDisponibilidad.setText("Buscando disponibilidad...");
            btnConfirmarCita.setEnabled(false);
            layoutHoras.setVisibility(View.GONE);

            if (!idDoctorEncontrado.isEmpty()) {
                verificarDia(fechaSeleccionada);
            }
        });

        btnConfirmarCita.setOnClickListener(v -> {
            if (horasParaGuardar.isEmpty()) {
                Toast.makeText(this, "Selecciona un horario",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            int pos = spinnerHoras.getSelectedItemPosition();
            if (pos < 0 || pos >= horasParaGuardar.size()) return;

            String horaGuardar = horasParaGuardar.get(pos);
            String horaMostrar = horasParaMostrar.get(pos);
            String tratamiento = spinnerTratamiento.getSelectedItem() != null
                    ? spinnerTratamiento.getSelectedItem().toString() : "";

            btnConfirmarCita.setEnabled(false);
            btnConfirmarCita.setText("Verificando...");
            verificarDuplicadoYGuardar(horaGuardar, horaMostrar, tratamiento);
        });
    }

    private void buscarDoctor(Runnable onTerminado) {
        db.collection("usuarios")
                .whereIn("rol", Arrays.asList("odontologo", "doctor"))
                .limit(1)
                .get(Source.SERVER)
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        idDoctorEncontrado = snap.getDocuments().get(0).getId();
                        if (onTerminado != null) onTerminado.run();
                    } else {
                        tvInfoDisponibilidad.setText("No se encontró odontólogo.");
                    }
                })
                .addOnFailureListener(e ->
                        db.collection("usuarios")
                                .whereIn("rol", Arrays.asList("odontologo", "doctor"))
                                .limit(1)
                                .get(Source.CACHE)
                                .addOnSuccessListener(snap -> {
                                    if (!snap.isEmpty()) {
                                        idDoctorEncontrado =
                                                snap.getDocuments().get(0).getId();
                                        if (onTerminado != null) onTerminado.run();
                                    } else {
                                        tvInfoDisponibilidad.setText(
                                                "Sin conexión. Verifica tu internet.");
                                    }
                                })
                                .addOnFailureListener(e2 ->
                                        tvInfoDisponibilidad.setText(
                                                "Sin conexión. Verifica tu internet.")
                                )
                );
    }

    private void cargarTratamientosDesdeFirebase() {
        db.collection("tratamientos")
                .get(Source.SERVER)
                .addOnSuccessListener(snap -> {
                    nombresTratamientos.clear();
                    horasTratamientos.clear();

                    if (snap.isEmpty()) { cargarTratamientosPorDefecto(); return; }

                    for (QueryDocumentSnapshot doc : snap) {
                        String nombre = doc.getString("nombre");
                        Long hrs = doc.getLong("duracion_horas");
                        if (hrs == null) {
                            Long min = doc.getLong("duracion_minutos");
                            hrs = min != null ? (long) Math.ceil(min / 60.0) : 1L;
                        }
                        if (nombre != null) {
                            nombresTratamientos.add(nombre + " (" + hrs + " hr)");
                            horasTratamientos.add(hrs.intValue());
                        }
                    }
                    adapterTratamientos.notifyDataSetChanged();
                    tvInfoDisponibilidad.setText("Selecciona un día en el calendario.");
                    if (!horasTratamientos.isEmpty())
                        duracionTratamientoActual = horasTratamientos.get(0);
                })
                .addOnFailureListener(e -> cargarTratamientosPorDefecto());
    }

    private void cargarTratamientosPorDefecto() {
        nombresTratamientos.clear();
        horasTratamientos.clear();
        nombresTratamientos.add("Limpieza General (1 hr)");
        nombresTratamientos.add("Consulta de Revisión (1 hr)");
        nombresTratamientos.add("Extracción Muela (2 hrs)");
        nombresTratamientos.add("Endodoncia Completa (3 hrs)");
        horasTratamientos.add(1); horasTratamientos.add(1);
        horasTratamientos.add(2); horasTratamientos.add(3);
        adapterTratamientos.notifyDataSetChanged();
        tvInfoDisponibilidad.setText("Selecciona un día en el calendario.");
        duracionTratamientoActual = 1;
    }

    private void verificarDia(String fecha) {
        String[] p = fecha.split("-");
        String fechaAgenda = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                Integer.parseInt(p[2]),
                Integer.parseInt(p[1]),
                Integer.parseInt(p[0]));

        db.collection("usuarios").document(idDoctorEncontrado)
                .collection("agenda_doctor").document(fechaAgenda)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        tvInfoDisponibilidad.setText(
                                "El doctor no tiene agenda para este día.");
                        layoutHoras.setVisibility(View.GONE);
                        btnConfirmarCita.setEnabled(false);
                        return;
                    }

                    List<String> bloques = null;
                    if (doc.contains("turnos")) {
                        bloques = (List<String>) doc.get("turnos");
                    } else if (doc.contains("horas")) {
                        List<String> h = (List<String>) doc.get("horas");
                        if (h != null) bloques = convertirHorasABloques(h);
                    } else {
                        String ini = doc.getString("hora_inicio");
                        String fin = doc.getString("hora_fin");
                        if (ini != null && fin != null) {
                            bloques = new ArrayList<>();
                            bloques.add(ini + " a " + fin);
                        }
                    }

                    if (bloques == null || bloques.isEmpty()) {
                        tvInfoDisponibilidad.setText(
                                "El doctor no tiene agenda para este día.");
                        layoutHoras.setVisibility(View.GONE);
                        btnConfirmarCita.setEnabled(false);
                        return;
                    }

                    verificarHorasOcupadas(fecha, bloques);
                })
                .addOnFailureListener(e -> {
                    tvInfoDisponibilidad.setText("Sin conexión. Verifica tu internet.");
                    layoutHoras.setVisibility(View.GONE);
                    btnConfirmarCita.setEnabled(false);
                });
    }

    private void verificarHorasOcupadas(String fecha, List<String> bloques) {
        db.collection("citas")
                .whereEqualTo("idDoctor", idDoctorEncontrado)
                .whereEqualTo("fecha", fecha)
                .whereIn("estado", Arrays.asList("pendiente", "aceptada"))
                .get(Source.SERVER)
                .addOnSuccessListener(snap -> {
                    List<String> ocupadas = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        String hora = doc.getString("hora");
                        Long dur    = doc.getLong("duracionHoras");
                        if (hora != null) {
                            int ini = Integer.parseInt(hora.split(":")[0]);
                            int d   = dur != null ? dur.intValue() : 1;
                            for (int i = 0; i < d; i++) {
                                ocupadas.add(String.format(Locale.getDefault(),
                                        "%02d:00", ini + i));
                            }
                        }
                    }
                    calcularHorasDisponibles(bloques, ocupadas);
                })
                .addOnFailureListener(e ->
                        calcularHorasDisponibles(bloques, new ArrayList<>())
                );
    }

    private void calcularHorasDisponibles(List<String> turnos,
                                          List<String> ocupadas) {
        horasParaMostrar.clear();
        horasParaGuardar.clear();

        for (String bloque : turnos) {
            String[] p = bloque.split(" a ");
            if (p.length == 2) {
                int ini = Integer.parseInt(p[0].split(":")[0]);
                int fin = Integer.parseInt(p[1].split(":")[0]);

                for (int h = ini; h <= fin - duracionTratamientoActual; h++) {
                    String horaStr    = String.format(Locale.getDefault(),
                            "%02d:00", h);
                    String horaFinStr = String.format(Locale.getDefault(),
                            "%02d:00", h + duracionTratamientoActual);

                    boolean ok = true;
                    for (int i = 0; i < duracionTratamientoActual; i++) {
                        if (ocupadas.contains(String.format(Locale.getDefault(),
                                "%02d:00", h + i))) {
                            ok = false; break;
                        }
                    }
                    if (ok) {
                        horasParaMostrar.add(horaStr + " — " + horaFinStr);
                        horasParaGuardar.add(horaStr);
                    }
                }
            }
        }

        if (!horasParaMostrar.isEmpty()) {
            tvInfoDisponibilidad.setText("¡Horarios disponibles!");
            layoutHoras.setVisibility(View.VISIBLE);
            btnConfirmarCita.setEnabled(true);
            spinnerHoras.setAdapter(new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_dropdown_item,
                    horasParaMostrar));
        } else {
            tvInfoDisponibilidad.setText(
                    "No hay horarios disponibles para este tratamiento.");
            layoutHoras.setVisibility(View.GONE);
            btnConfirmarCita.setEnabled(false);
        }
    }

    private List<String> convertirHorasABloques(List<String> horas) {
        List<String> ord = new ArrayList<>(horas);
        java.util.Collections.sort(ord);
        List<String> bloques = new ArrayList<>();
        if (ord.isEmpty()) return bloques;
        String ini = ord.get(0), ant = ord.get(0);
        for (int i = 1; i < ord.size(); i++) {
            String act = ord.get(i);
            if (Integer.parseInt(act.split(":")[0]) !=
                    Integer.parseInt(ant.split(":")[0]) + 1) {
                bloques.add(ini + " a " + ant);
                ini = act;
            }
            ant = act;
        }
        bloques.add(ini + " a " + ant);
        return bloques;
    }

    private void verificarDuplicadoYGuardar(String horaGuardar,
                                            String horaMostrar,
                                            String tratamiento) {
        if (mAuth.getCurrentUser() == null) return;
        String idPaciente = mAuth.getCurrentUser().getUid();

        String idParaVerificar = esFamiliar && idFamiliar != null
                ? idFamiliar : idPaciente;

        db.collection("citas")
                .whereEqualTo("idPaciente", idParaVerificar)
                .whereEqualTo("fecha", fechaSeleccionada)
                .get(Source.SERVER)
                .addOnSuccessListener(snap -> {
                    boolean activa = false;
                    for (QueryDocumentSnapshot doc : snap) {
                        String estado = doc.getString("estado");
                        if ("pendiente".equals(estado) ||
                                "aceptada".equals(estado)) {
                            activa = true; break;
                        }
                    }
                    if (activa) {
                        String quien = esFamiliar
                                ? (nombreFamiliar + " ya tiene")
                                : "Ya tienes";
                        Toast.makeText(this,
                                "⚠️ " + quien + " una cita para este día.",
                                Toast.LENGTH_LONG).show();
                        btnConfirmarCita.setEnabled(true);
                        btnConfirmarCita.setText("CONFIRMAR CITA");
                    } else {
                        guardarCitaEnFirebase(idPaciente, horaGuardar,
                                horaMostrar, tratamiento);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Error al verificar. Intenta de nuevo.",
                            Toast.LENGTH_SHORT).show();
                    btnConfirmarCita.setEnabled(true);
                    btnConfirmarCita.setText("CONFIRMAR CITA");
                });
    }

    private void guardarCitaEnFirebase(String idPaciente, String horaGuardar,
                                       String horaMostrar, String tratamiento) {
        Map<String, Object> cita = new HashMap<>();

        if (esFamiliar && idFamiliar != null) {
            cita.put("idPaciente",        idFamiliar);
            cita.put("idPacienteTitular", idPaciente);
            cita.put("esFamiliar",        true);
            cita.put("nombreFamiliar",    nombreFamiliar);
        } else {
            cita.put("idPaciente",        idPaciente);
            cita.put("idPacienteTitular", idPaciente);
            cita.put("esFamiliar",        false);
        }

        cita.put("idDoctor",      idDoctorEncontrado);
        cita.put("fecha",         fechaSeleccionada);
        cita.put("hora",          horaGuardar);
        cita.put("horaDisplay",   horaMostrar);
        cita.put("tratamiento",   tratamiento);
        cita.put("duracionHoras", duracionTratamientoActual);
        cita.put("estado",        "pendiente");
        cita.put("asistencia",    "pendiente");

        db.collection("citas").add(cita)
                .addOnSuccessListener(documentReference -> {
                    String msg = esFamiliar
                            ? "¡Solicitud enviada para " + nombreFamiliar + "!"
                            : "¡Solicitud enviada al doctor!";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

                    // Notificar al doctor via ntfy
                    db.collection("usuarios").document(idPaciente).get()
                            .addOnSuccessListener(pacDoc -> {
                                String nombre = pacDoc.getString("Nombre");
                                if (nombre == null)
                                    nombre = pacDoc.getString("nombre");
                                final String nombreFinal =
                                        nombre != null ? nombre : "Un paciente";
                                NtfyHelper.nuevaSolicitud(
                                        idDoctorEncontrado,
                                        esFamiliar && nombreFamiliar != null
                                                ? nombreFinal + " (para " +
                                                nombreFamiliar + ")"
                                                : nombreFinal,
                                        tratamiento,
                                        fechaSeleccionada);
                            });

                    // Ir al triaje
                    Intent intent = new Intent(this, TriajeActivity.class);
                    intent.putExtra("idCita", documentReference.getId());
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Error al enviar. Verifica tu internet.",
                            Toast.LENGTH_SHORT).show();
                    btnConfirmarCita.setEnabled(true);
                    btnConfirmarCita.setText("CONFIRMAR CITA");
                });
    }
}