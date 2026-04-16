package com.example.odontoconnect;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AgendarCitaActivity extends AppCompatActivity {

    // Vistas
    private GridLayout gridCalendario;
    private TextView tvMesActual, tvInfoDisponibilidad, tvTituloAgendar;
    private ImageButton btnMesAnterior, btnMesSiguiente;
    private LinearLayout layoutHoras;
    private Spinner spinnerTratamiento, spinnerHoras;
    private com.google.android.material.button.MaterialButton btnConfirmarCita;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Estado del calendario
    private Calendar calMes = Calendar.getInstance(); // mes mostrado
    private int diaSeleccionado = -1;
    private String fechaSeleccionada = "";
    private TextView celdaSeleccionada = null;

    // Días del doctor con agenda disponible (formato "yyyy-MM-dd")
    private final Set<String> diasDisponibles = new HashSet<>();

    private String idDoctorEncontrado = "";
    private int duracionTratamientoActual = 1;

    private boolean esFamiliar    = false;
    private String idFamiliar     = null;
    private String nombreFamiliar = null;

    private final List<String> horasParaMostrar    = new ArrayList<>();
    private final List<String> horasParaGuardar    = new ArrayList<>();
    private final List<String> nombresTratamientos = new ArrayList<>();
    private final List<Integer> horasTratamientos  = new ArrayList<>();
    private ArrayAdapter<String> adapterTratamientos;

    // Colores del calendario
    private static final int COLOR_DISPONIBLE     = 0xFF1565C0; // azul
    private static final int COLOR_SIN_AGENDA     = 0xFFE0E0E0; // gris
    private static final int COLOR_SELECCIONADO   = 0xFF2E7D32; // verde
    private static final int COLOR_PASADO         = 0xFFF5F5F5; // gris muy claro
    private static final int COLOR_HOY            = 0xFFFFF176; // amarillo suave

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agendar_cita);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (getIntent() != null) {
            esFamiliar     = getIntent().getBooleanExtra("esFamiliar", false);
            idFamiliar     = getIntent().getStringExtra("idFamiliar");
            nombreFamiliar = getIntent().getStringExtra("nombreFamiliar");
        }

        gridCalendario       = findViewById(R.id.gridCalendario);
        tvMesActual          = findViewById(R.id.tvMesActual);
        tvInfoDisponibilidad = findViewById(R.id.tvInfoDisponibilidad);
        tvTituloAgendar      = findViewById(R.id.tvTituloAgendar);
        btnMesAnterior       = findViewById(R.id.btnMesAnterior);
        btnMesSiguiente      = findViewById(R.id.btnMesSiguiente);
        layoutHoras          = findViewById(R.id.layoutHoras);
        spinnerTratamiento   = findViewById(R.id.spinnerTratamiento);
        spinnerHoras         = findViewById(R.id.spinnerHoras);
        btnConfirmarCita     = findViewById(R.id.btnConfirmarCita);

        if (esFamiliar && nombreFamiliar != null && tvTituloAgendar != null)
            tvTituloAgendar.setText("Agendar Cita\npara: " + nombreFamiliar);

        // El mes mínimo es el actual
        calMes = Calendar.getInstance();
        calMes.set(Calendar.DAY_OF_MONTH, 1);

        adapterTratamientos = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, nombresTratamientos);
        spinnerTratamiento.setAdapter(adapterTratamientos);

        spinnerTratamiento.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!horasTratamientos.isEmpty() && pos < horasTratamientos.size())
                    duracionTratamientoActual = horasTratamientos.get(pos);
                if (!fechaSeleccionada.isEmpty() && !idDoctorEncontrado.isEmpty())
                    verificarDia(fechaSeleccionada);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        btnMesAnterior.setOnClickListener(v -> {
            Calendar hoy = Calendar.getInstance();
            hoy.set(Calendar.DAY_OF_MONTH, 1);
            hoy.set(Calendar.HOUR_OF_DAY, 0);
            // No retroceder antes del mes actual
            if (calMes.after(hoy)) {
                calMes.add(Calendar.MONTH, -1);
                diaSeleccionado = -1;
                fechaSeleccionada = "";
                celdaSeleccionada = null;
                layoutHoras.setVisibility(View.GONE);
                btnConfirmarCita.setEnabled(false);
                tvInfoDisponibilidad.setText("Selecciona un día en el calendario.");
                cargarAgendaYRenderizar();
            }
        });

        btnMesSiguiente.setOnClickListener(v -> {
            calMes.add(Calendar.MONTH, 1);
            diaSeleccionado = -1;
            fechaSeleccionada = "";
            celdaSeleccionada = null;
            layoutHoras.setVisibility(View.GONE);
            btnConfirmarCita.setEnabled(false);
            tvInfoDisponibilidad.setText("Selecciona un día en el calendario.");
            cargarAgendaYRenderizar();
        });

        btnConfirmarCita.setEnabled(false);
        btnConfirmarCita.setOnClickListener(v -> {
            if (horasParaGuardar.isEmpty()) {
                Toast.makeText(this, "Selecciona un horario", Toast.LENGTH_SHORT).show();
                return;
            }
            int pos = spinnerHoras.getSelectedItemPosition();
            if (pos < 0 || pos >= horasParaGuardar.size()) return;
            String horaGuardar = horasParaGuardar.get(pos);
            String horaMostrar = horasParaMostrar.get(pos);
            String trat = spinnerTratamiento.getSelectedItem() != null
                    ? spinnerTratamiento.getSelectedItem().toString() : "";
            btnConfirmarCita.setEnabled(false);
            btnConfirmarCita.setText("Verificando...");
            verificarDuplicadoYGuardar(horaGuardar, horaMostrar, trat);
        });

        verificarPenalizacion();
    }

    private void verificarPenalizacion() {
        if (mAuth.getCurrentUser() == null) { finish(); return; }
        db.collection("usuarios").document(mAuth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(doc ->
                        PenalizacionHelper.verificarPuedeAgendar(doc, this,
                                new PenalizacionHelper.ResultadoVerificacion() {
                                    @Override public void onPermitido() { iniciar(); }
                                    @Override public void onBloqueado(String msg) {
                                        new AlertDialog.Builder(AgendarCitaActivity.this)
                                                .setTitle("Acceso restringido").setMessage(msg)
                                                .setPositiveButton("Entendido", (d,w) -> finish())
                                                .setCancelable(false).show();
                                    }
                                }));
    }

    private void iniciar() {
        cargarTratamientos();
        buscarDoctorDesdeConfig(() -> cargarAgendaYRenderizar());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CARGA DE AGENDA Y RENDERIZADO DEL CALENDARIO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Carga TODOS los documentos de agenda_doctor del mes actual
     * y los meses siguientes (hasta 2 meses adelante), luego renderiza el grid.
     */
    private void cargarAgendaYRenderizar() {
        if (idDoctorEncontrado.isEmpty()) {
            renderizarCalendario(); // renderizar aunque sea sin disponibilidad
            return;
        }

        tvInfoDisponibilidad.setText("Cargando disponibilidad...");
        diasDisponibles.clear();

        // Prefijos de fechas para el mes mostrado y el siguiente
        // Formato del doc en Firestore: yyyy-MM-dd
        db.collection("usuarios").document(idDoctorEncontrado)
                .collection("agenda_doctor")
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) {
                        String fecha = doc.getId(); // "2026-04-24"
                        // Solo incluir si tiene horas disponibles
                        List<String> horas = (List<String>) doc.get("horas");
                        Boolean disponible = doc.getBoolean("disponible");
                        if ((horas != null && !horas.isEmpty()) ||
                                Boolean.TRUE.equals(disponible)) {
                            diasDisponibles.add(fecha);
                        }
                    }
                    tvInfoDisponibilidad.setText("Selecciona un día azul para ver horarios.");
                    renderizarCalendario();
                })
                .addOnFailureListener(e -> {
                    tvInfoDisponibilidad.setText("Selecciona un día en el calendario.");
                    renderizarCalendario();
                });
    }

    /**
     * Construye el grid del mes mostrando colores según disponibilidad
     */
    private void renderizarCalendario() {
        gridCalendario.removeAllViews();

        // Nombre del mes
        String[] meses = {"Enero","Febrero","Marzo","Abril","Mayo","Junio",
                          "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
        tvMesActual.setText(meses[calMes.get(Calendar.MONTH)] +
                " " + calMes.get(Calendar.YEAR));

        Calendar hoy = Calendar.getInstance();

        // Primer día de la semana del mes (0=Dom, 6=Sáb)
        Calendar primerDia = (Calendar) calMes.clone();
        primerDia.set(Calendar.DAY_OF_MONTH, 1);
        int offsetInicio = primerDia.get(Calendar.DAY_OF_WEEK) - 1; // Calendar.SUNDAY = 1

        int diasEnMes = calMes.getActualMaximum(Calendar.DAY_OF_MONTH);
        int totalCeldas = offsetInicio + diasEnMes;
        // Completar hasta múltiplo de 7
        if (totalCeldas % 7 != 0) totalCeldas += 7 - (totalCeldas % 7);

        int anio = calMes.get(Calendar.YEAR);
        int mes  = calMes.get(Calendar.MONTH); // 0-indexed

        for (int i = 0; i < totalCeldas; i++) {
            TextView celda = new TextView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width  = 0;
            params.height = dpToPx(44);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
            celda.setLayoutParams(params);
            celda.setGravity(android.view.Gravity.CENTER);
            celda.setTextSize(14f);

            int dia = i - offsetInicio + 1;

            if (i < offsetInicio || dia > diasEnMes) {
                // Celda vacía
                celda.setText("");
                celda.setBackgroundColor(Color.TRANSPARENT);
            } else {
                celda.setText(String.valueOf(dia));

                // Construir fecha en formato yyyy-MM-dd para comparar con Firestore
                String fechaDoc = String.format(Locale.getDefault(),
                        "%04d-%02d-%02d", anio, mes + 1, dia);

                // Construir fecha en formato d-M-yyyy para guardar en cita
                String fechaCita = dia + "-" + (mes + 1) + "-" + anio;

                Calendar esteDia = Calendar.getInstance();
                esteDia.set(anio, mes, dia, 0, 0, 0);
                esteDia.set(Calendar.MILLISECOND, 0);

                boolean esPasado = esteDia.before(hoy) &&
                        !(esteDia.get(Calendar.YEAR)  == hoy.get(Calendar.YEAR) &&
                          esteDia.get(Calendar.MONTH) == hoy.get(Calendar.MONTH) &&
                          esteDia.get(Calendar.DAY_OF_MONTH) == hoy.get(Calendar.DAY_OF_MONTH));

                boolean esHoy = esteDia.get(Calendar.YEAR)  == hoy.get(Calendar.YEAR) &&
                                esteDia.get(Calendar.MONTH) == hoy.get(Calendar.MONTH) &&
                                esteDia.get(Calendar.DAY_OF_MONTH) == hoy.get(Calendar.DAY_OF_MONTH);

                boolean esDiaSeleccionado = (dia == diaSeleccionado &&
                        mes  == Integer.parseInt(fechaSeleccionada.split("-")[1]) - 1 &&
                        anio == Integer.parseInt(fechaSeleccionada.split("-")[2]))
                        && !fechaSeleccionada.isEmpty();

                boolean tieneAgenda = diasDisponibles.contains(fechaDoc);

                // Aplicar estilo
                if (esDiaSeleccionado) {
                    celda.setBackgroundColor(COLOR_SELECCIONADO);
                    celda.setTextColor(Color.WHITE);
                    celda.setTypeface(null, Typeface.BOLD);
                    celdaSeleccionada = celda;
                } else if (esPasado) {
                    celda.setBackgroundColor(COLOR_PASADO);
                    celda.setTextColor(0xFFCCCCCC);
                    celda.setEnabled(false);
                } else if (esHoy) {
                    celda.setBackgroundColor(tieneAgenda ? COLOR_DISPONIBLE : COLOR_HOY);
                    celda.setTextColor(tieneAgenda ? Color.WHITE : 0xFF333333);
                    celda.setTypeface(null, Typeface.BOLD);
                } else if (tieneAgenda) {
                    celda.setBackgroundColor(COLOR_DISPONIBLE);
                    celda.setTextColor(Color.WHITE);
                    celda.setTypeface(null, Typeface.BOLD);
                } else {
                    celda.setBackgroundColor(COLOR_SIN_AGENDA);
                    celda.setTextColor(0xFF888888);
                }

                // Click solo en días no pasados
                if (!esPasado) {
                    final int diaFinal = dia;
                    final String fechaCitaFinal = fechaCita;
                    final TextView celdaFinal = celda;
                    final boolean tieneAgendaFinal = tieneAgenda;

                    celda.setOnClickListener(v -> {
                        // Restaurar color anterior de la celda seleccionada
                        if (celdaSeleccionada != null && celdaSeleccionada != celdaFinal) {
                            String fechaAnterior = diaSeleccionado + "-" +
                                    (mes + 1) + "-" + anio;
                            String fechaDocAnterior = String.format(
                                    Locale.getDefault(), "%04d-%02d-%02d",
                                    anio, mes + 1, diaSeleccionado);
                            boolean teAgendaAnterior = diasDisponibles.contains(fechaDocAnterior);

                            Calendar diaAnt = Calendar.getInstance();
                            diaAnt.set(anio, mes, diaSeleccionado);
                            boolean esHoyAnt = diaAnt.get(Calendar.DAY_OF_MONTH) ==
                                    hoy.get(Calendar.DAY_OF_MONTH) &&
                                    diaAnt.get(Calendar.MONTH) == hoy.get(Calendar.MONTH) &&
                                    diaAnt.get(Calendar.YEAR) == hoy.get(Calendar.YEAR);

                            if (esHoyAnt) {
                                celdaSeleccionada.setBackgroundColor(
                                        teAgendaAnterior ? COLOR_DISPONIBLE : COLOR_HOY);
                                celdaSeleccionada.setTextColor(
                                        teAgendaAnterior ? Color.WHITE : 0xFF333333);
                            } else if (teAgendaAnterior) {
                                celdaSeleccionada.setBackgroundColor(COLOR_DISPONIBLE);
                                celdaSeleccionada.setTextColor(Color.WHITE);
                            } else {
                                celdaSeleccionada.setBackgroundColor(COLOR_SIN_AGENDA);
                                celdaSeleccionada.setTextColor(0xFF888888);
                            }
                            celdaSeleccionada.setTypeface(null, Typeface.NORMAL);
                        }

                        // Marcar nueva celda como seleccionada
                        celdaFinal.setBackgroundColor(COLOR_SELECCIONADO);
                        celdaFinal.setTextColor(Color.WHITE);
                        celdaFinal.setTypeface(null, Typeface.BOLD);
                        celdaSeleccionada = celdaFinal;

                        diaSeleccionado   = diaFinal;
                        fechaSeleccionada = fechaCitaFinal;
                        layoutHoras.setVisibility(View.GONE);
                        btnConfirmarCita.setEnabled(false);
                        horasParaMostrar.clear();
                        horasParaGuardar.clear();

                        if (!tieneAgendaFinal) {
                            tvInfoDisponibilidad.setText(
                                    "El doctor no tiene agenda para este día.");
                        } else if (!idDoctorEncontrado.isEmpty()) {
                            tvInfoDisponibilidad.setText("Buscando horarios...");
                            verificarDia(fechaCitaFinal);
                        } else {
                            tvInfoDisponibilidad.setText("⏳ Cargando doctor...");
                            esperarDoctorYVerificar();
                        }
                    });
                }
            }
            gridCalendario.addView(celda);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BÚSQUEDA DE DOCTOR
    // ─────────────────────────────────────────────────────────────────────────

    private void buscarDoctorDesdeConfig(Runnable onTerminado) {
        db.collection("configuracion").document("consultorio").get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.contains("idDoctor")) {
                        idDoctorEncontrado = doc.getString("idDoctor");
                        if (onTerminado != null) onTerminado.run();
                    } else {
                        buscarDoctorPorRol(onTerminado);
                    }
                })
                .addOnFailureListener(e -> buscarDoctorPorRol(onTerminado));
    }

    private void buscarDoctorPorRol(Runnable onTerminado) {
        db.collection("usuarios")
                .whereIn("rol", Arrays.asList("odontologo", "doctor"))
                .limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        idDoctorEncontrado = snap.getDocuments().get(0).getId();
                        if (onTerminado != null) onTerminado.run();
                    } else {
                        tvInfoDisponibilidad.setText("No se encontró odontólogo.");
                    }
                })
                .addOnFailureListener(e ->
                        tvInfoDisponibilidad.setText("Sin conexión. Verifica tu internet."));
    }

    private void esperarDoctorYVerificar() { esperarDoctorYVerificar(0); }
    private void esperarDoctorYVerificar(int intento) {
        if (intento > 10) {
            tvInfoDisponibilidad.setText("Sin conexión. Verifica tu internet.");
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!idDoctorEncontrado.isEmpty() && !fechaSeleccionada.isEmpty()) {
                tvInfoDisponibilidad.setText("Buscando horarios...");
                verificarDia(fechaSeleccionada);
            } else if (!fechaSeleccionada.isEmpty()) {
                esperarDoctorYVerificar(intento + 1);
            }
        }, 300);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TRATAMIENTOS
    // ─────────────────────────────────────────────────────────────────────────

    private void cargarTratamientos() {
        db.collection("tratamientos").get()
                .addOnSuccessListener(snap -> {
                    nombresTratamientos.clear(); horasTratamientos.clear();
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
                    if (!horasTratamientos.isEmpty())
                        duracionTratamientoActual = horasTratamientos.get(0);
                })
                .addOnFailureListener(e -> cargarTratamientosPorDefecto());
    }

    private void cargarTratamientosPorDefecto() {
        nombresTratamientos.clear(); horasTratamientos.clear();
        nombresTratamientos.add("Limpieza General (1 hr)");
        nombresTratamientos.add("Consulta de Revisión (1 hr)");
        nombresTratamientos.add("Extracción Muela (2 hrs)");
        nombresTratamientos.add("Endodoncia Completa (3 hrs)");
        horasTratamientos.add(1); horasTratamientos.add(1);
        horasTratamientos.add(2); horasTratamientos.add(3);
        adapterTratamientos.notifyDataSetChanged();
        duracionTratamientoActual = 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VERIFICAR HORAS DISPONIBLES
    // ─────────────────────────────────────────────────────────────────────────

    private void verificarDia(String fecha) {
        String[] p = fecha.split("-");
        String fechaDoc = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));

        db.collection("usuarios").document(idDoctorEncontrado)
                .collection("agenda_doctor").document(fechaDoc).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        tvInfoDisponibilidad.setText("El doctor no tiene agenda para este día.");
                        layoutHoras.setVisibility(View.GONE);
                        btnConfirmarCita.setEnabled(false);
                        return;
                    }
                    List<String> horasDoc = (List<String>) doc.get("horas");
                    if (horasDoc != null && !horasDoc.isEmpty()) {
                        calcularDesdeHorasSueltas(fecha, horasDoc);
                    } else {
                        List<String> bloques = (List<String>) doc.get("turnos");
                        if (bloques != null && !bloques.isEmpty()) {
                            calcularDesdeBloques(fecha, bloques);
                        } else {
                            tvInfoDisponibilidad.setText("El doctor no tiene agenda para este día.");
                            layoutHoras.setVisibility(View.GONE);
                            btnConfirmarCita.setEnabled(false);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    tvInfoDisponibilidad.setText("Sin conexión. Verifica tu internet.");
                    layoutHoras.setVisibility(View.GONE);
                    btnConfirmarCita.setEnabled(false);
                });
    }

    private void calcularDesdeHorasSueltas(String fecha, List<String> horasDoc) {
        db.collection("citas")
                .whereEqualTo("idDoctor", idDoctorEncontrado)
                .whereEqualTo("fecha", fecha)
                .whereIn("estado", Arrays.asList("pendiente", "aceptada"))
                .get()
                .addOnSuccessListener(snap -> {
                    List<Integer> ocupadas = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        String hora = doc.getString("hora");
                        Long dur    = doc.getLong("duracionHoras");
                        if (hora != null) {
                            int ini = Integer.parseInt(hora.split(":")[0]);
                            int d   = dur != null ? dur.intValue() : 1;
                            for (int i = 0; i < d; i++) ocupadas.add(ini + i);
                        }
                    }
                    List<Integer> disponibles = new ArrayList<>();
                    for (String h : horasDoc) {
                        try {
                            int hora = Integer.parseInt(h.split(":")[0]);
                            if (!disponibles.contains(hora)) disponibles.add(hora);
                        } catch (Exception ignored) {}
                    }
                    Collections.sort(disponibles);
                    horasParaMostrar.clear(); horasParaGuardar.clear();
                    for (int horaInicio : disponibles) {
                        boolean ok = true;
                        for (int d = 0; d < duracionTratamientoActual; d++) {
                            if (!disponibles.contains(horaInicio + d) ||
                                    ocupadas.contains(horaInicio + d)) {
                                ok = false; break;
                            }
                        }
                        if (ok) {
                            horasParaMostrar.add(String.format(Locale.getDefault(),
                                    "%02d:00 — %02d:00",
                                    horaInicio, horaInicio + duracionTratamientoActual));
                            horasParaGuardar.add(String.format(Locale.getDefault(),
                                    "%02d:00", horaInicio));
                        }
                    }
                    mostrarResultadoHoras();
                })
                .addOnFailureListener(e -> mostrarResultadoHoras());
    }

    private void calcularDesdeBloques(String fecha, List<String> bloques) {
        db.collection("citas")
                .whereEqualTo("idDoctor", idDoctorEncontrado)
                .whereEqualTo("fecha", fecha)
                .whereIn("estado", Arrays.asList("pendiente", "aceptada"))
                .get()
                .addOnSuccessListener(snap -> {
                    List<Integer> todasHoras = new ArrayList<>();
                    for (String bloque : bloques) {
                        String[] parts = bloque.split(" a ");
                        if (parts.length == 2) {
                            int ini = Integer.parseInt(parts[0].split(":")[0]);
                            int fin = Integer.parseInt(parts[1].split(":")[0]);
                            for (int h = ini; h <= fin; h++)
                                if (!todasHoras.contains(h)) todasHoras.add(h);
                        }
                    }
                    Collections.sort(todasHoras);
                    List<Integer> ocupadas = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        String hora = doc.getString("hora");
                        Long dur    = doc.getLong("duracionHoras");
                        if (hora != null) {
                            int ini = Integer.parseInt(hora.split(":")[0]);
                            int d   = dur != null ? dur.intValue() : 1;
                            for (int i = 0; i < d; i++) ocupadas.add(ini + i);
                        }
                    }
                    horasParaMostrar.clear(); horasParaGuardar.clear();
                    for (int horaInicio : todasHoras) {
                        boolean ok = true;
                        for (int d = 0; d < duracionTratamientoActual; d++) {
                            if (!todasHoras.contains(horaInicio + d) ||
                                    ocupadas.contains(horaInicio + d)) {
                                ok = false; break;
                            }
                        }
                        if (ok) {
                            horasParaMostrar.add(String.format(Locale.getDefault(),
                                    "%02d:00 — %02d:00",
                                    horaInicio, horaInicio + duracionTratamientoActual));
                            horasParaGuardar.add(String.format(Locale.getDefault(),
                                    "%02d:00", horaInicio));
                        }
                    }
                    mostrarResultadoHoras();
                })
                .addOnFailureListener(e -> mostrarResultadoHoras());
    }

    private void mostrarResultadoHoras() {
        if (!horasParaMostrar.isEmpty()) {
            tvInfoDisponibilidad.setText("¡Horarios disponibles! Selecciona uno.");
            layoutHoras.setVisibility(View.VISIBLE);
            btnConfirmarCita.setEnabled(true);
            spinnerHoras.setAdapter(new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_dropdown_item, horasParaMostrar));
        } else {
            tvInfoDisponibilidad.setText("No hay horarios disponibles para este tratamiento.");
            layoutHoras.setVisibility(View.GONE);
            btnConfirmarCita.setEnabled(false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GUARDAR CITA
    // ─────────────────────────────────────────────────────────────────────────

    private void verificarDuplicadoYGuardar(String horaGuardar,
                                              String horaMostrar, String tratamiento) {
        if (mAuth.getCurrentUser() == null) return;
        String idPaciente = mAuth.getCurrentUser().getUid();
        String idVerif = esFamiliar && idFamiliar != null ? idFamiliar : idPaciente;

        db.collection("citas")
                .whereEqualTo("idPaciente", idVerif)
                .whereEqualTo("fecha", fechaSeleccionada).get()
                .addOnSuccessListener(snap -> {
                    boolean activa = false;
                    for (QueryDocumentSnapshot doc : snap) {
                        String est = doc.getString("estado");
                        if ("pendiente".equals(est) || "aceptada".equals(est)) {
                            activa = true; break;
                        }
                    }
                    if (activa) {
                        String quien = esFamiliar
                                ? (nombreFamiliar + " ya tiene") : "Ya tienes";
                        Toast.makeText(this,
                                "⚠️ " + quien + " una cita para este día.",
                                Toast.LENGTH_LONG).show();
                        btnConfirmarCita.setEnabled(true);
                        btnConfirmarCita.setText("CONFIRMAR CITA");
                    } else {
                        guardarCita(idPaciente, horaGuardar, horaMostrar, tratamiento);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al verificar. Intenta de nuevo.",
                            Toast.LENGTH_SHORT).show();
                    btnConfirmarCita.setEnabled(true);
                    btnConfirmarCita.setText("CONFIRMAR CITA");
                });
    }

    private void guardarCita(String idPaciente, String horaGuardar,
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
                .addOnSuccessListener(ref -> {
                    String msg = esFamiliar
                            ? "¡Solicitud enviada para " + nombreFamiliar + "!"
                            : "¡Solicitud enviada al doctor!";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    db.collection("usuarios").document(idPaciente).get()
                            .addOnSuccessListener(pacDoc -> {
                                String nombre = pacDoc.getString("Nombre");
                                if (nombre == null) nombre = pacDoc.getString("nombre");
                                final String nf = nombre != null ? nombre : "Un paciente";
                                NtfyHelper.nuevaSolicitud(idDoctorEncontrado,
                                        esFamiliar && nombreFamiliar != null
                                                ? nf + " (para " + nombreFamiliar + ")" : nf,
                                        tratamiento, fechaSeleccionada);
                            });
                    Intent intent = new Intent(this, TriajeActivity.class);
                    intent.putExtra("idCita", ref.getId());
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al enviar. Verifica tu internet.",
                            Toast.LENGTH_SHORT).show();
                    btnConfirmarCita.setEnabled(true);
                    btnConfirmarCita.setText("CONFIRMAR CITA");
                });
    }
}
