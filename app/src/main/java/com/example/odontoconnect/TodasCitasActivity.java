package com.example.odontoconnect;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Muestra TODAS las citas aceptadas que el doctor va a atender,
 * agrupadas por fecha y ordenadas cronologicamente.
 *
 * Filtros:
 *  - Todas: todas las citas futuras
 *  - Hoy: solo las de hoy
 *  - Semana: proximos 7 dias
 */
public class TodasCitasActivity extends AppCompatActivity {

    private static final String TAG = "TODAS_CITAS";

    private LinearLayout contenedorTodasCitas;
    private TextView tvSinCitas, tvContador;
    private TextView btnFiltroTodas, btnFiltroHoy, btnFiltroSemana;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration listener;

    private enum Filtro { TODAS, HOY, SEMANA }
    private Filtro filtroActual = Filtro.TODAS;

    // Cache de las citas leidas
    private List<DocumentSnapshot> citasCache = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_todas_citas);
        } catch (Exception e) {
            Log.e(TAG, "Error layout", e);
            Toast.makeText(this, "Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        contenedorTodasCitas = findViewById(R.id.contenedorTodasCitas);
        tvSinCitas           = findViewById(R.id.tvSinCitas);
        tvContador           = findViewById(R.id.tvContadorCitas);
        btnFiltroTodas       = findViewById(R.id.btnFiltroTodas);
        btnFiltroHoy         = findViewById(R.id.btnFiltroHoy);
        btnFiltroSemana      = findViewById(R.id.btnFiltroSemana);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (btnFiltroTodas != null)
            btnFiltroTodas.setOnClickListener(v -> cambiarFiltro(Filtro.TODAS));
        if (btnFiltroHoy != null)
            btnFiltroHoy.setOnClickListener(v -> cambiarFiltro(Filtro.HOY));
        if (btnFiltroSemana != null)
            btnFiltroSemana.setOnClickListener(v -> cambiarFiltro(Filtro.SEMANA));

        actualizarBotonesFiltro();
        cargarCitas();
    }

    private void cambiarFiltro(Filtro nuevo) {
        filtroActual = nuevo;
        actualizarBotonesFiltro();
        renderizarCitas();
    }

    private void actualizarBotonesFiltro() {
        // Desactivar todos
        if (btnFiltroTodas != null) {
            btnFiltroTodas.setBackgroundColor(0xFFFFFFFF);
            btnFiltroTodas.setTextColor(0xFF666666);
        }
        if (btnFiltroHoy != null) {
            btnFiltroHoy.setBackgroundColor(0xFFFFFFFF);
            btnFiltroHoy.setTextColor(0xFF666666);
        }
        if (btnFiltroSemana != null) {
            btnFiltroSemana.setBackgroundColor(0xFFFFFFFF);
            btnFiltroSemana.setTextColor(0xFF666666);
        }
        // Activar el seleccionado
        TextView activo = null;
        if (filtroActual == Filtro.TODAS) activo = btnFiltroTodas;
        else if (filtroActual == Filtro.HOY) activo = btnFiltroHoy;
        else if (filtroActual == Filtro.SEMANA) activo = btnFiltroSemana;

        if (activo != null) {
            activo.setBackgroundColor(0xFF1B3A6B);
            activo.setTextColor(0xFFFFFFFF);
        }
    }

    private void cargarCitas() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Sesion expirada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String miUid = mAuth.getCurrentUser().getUid();

        listener = db.collection("citas")
                .whereEqualTo("idDoctor", miUid)
                .whereEqualTo("estado", "aceptada")
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error snapshot", error);
                        Toast.makeText(this,
                                "Error: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snap == null) return;

                    citasCache.clear();
                    citasCache.addAll(snap.getDocuments());

                    // Ordenar por fecha cronologicamente
                    Collections.sort(citasCache, (a, b) -> {
                        long ta = timestampDeCita(a);
                        long tb = timestampDeCita(b);
                        return Long.compare(ta, tb);
                    });

                    renderizarCitas();
                });
    }

    private long timestampDeCita(DocumentSnapshot doc) {
        try {
            String fecha = doc.getString("fecha");
            String hora = doc.getString("hora");
            if (hora == null) hora = doc.getString("horaDisplay");
            if (fecha == null) return Long.MAX_VALUE;

            SimpleDateFormat sdf = new SimpleDateFormat(
                    "d-M-yyyy HH:mm", Locale.getDefault());
            Date d = sdf.parse(fecha + " " + (hora != null ? hora : "00:00"));
            return d != null ? d.getTime() : Long.MAX_VALUE;
        } catch (ParseException e) {
            return Long.MAX_VALUE;
        }
    }

    private void renderizarCitas() {
        if (contenedorTodasCitas == null) return;
        contenedorTodasCitas.removeAllViews();

        // Filtrar segun el filtro activo
        List<DocumentSnapshot> filtradas = filtrar(citasCache);

        if (tvContador != null) {
            tvContador.setText(filtradas.size() + " cita" +
                    (filtradas.size() == 1 ? "" : "s"));
        }

        if (filtradas.isEmpty()) {
            if (tvSinCitas != null) {
                tvSinCitas.setVisibility(View.VISIBLE);
                String msg;
                switch (filtroActual) {
                    case HOY:    msg = "No hay citas para hoy"; break;
                    case SEMANA: msg = "No hay citas esta semana"; break;
                    default:     msg = "No tienes citas aceptadas"; break;
                }
                tvSinCitas.setText(msg);
            }
            return;
        }

        if (tvSinCitas != null) tvSinCitas.setVisibility(View.GONE);

        // Agrupar por fecha
        Map<String, List<DocumentSnapshot>> porFecha = new HashMap<>();
        List<String> ordenFechas = new ArrayList<>();

        for (DocumentSnapshot doc : filtradas) {
            String fecha = doc.getString("fecha");
            if (fecha == null) fecha = "Sin fecha";

            if (!porFecha.containsKey(fecha)) {
                porFecha.put(fecha, new ArrayList<>());
                ordenFechas.add(fecha);
            }
            porFecha.get(fecha).add(doc);
        }

        // Renderizar cada grupo
        for (String fecha : ordenFechas) {
            agregarEncabezadoFecha(fecha);
            for (DocumentSnapshot doc : porFecha.get(fecha)) {
                try {
                    crearTarjetaCita(doc);
                } catch (Exception e) {
                    Log.e(TAG, "Error tarjeta", e);
                }
            }
        }
    }

    private List<DocumentSnapshot> filtrar(List<DocumentSnapshot> todas) {
        if (filtroActual == Filtro.TODAS) {
            // Solo las que son de hoy en adelante
            return soloFuturas(todas);
        }

        SimpleDateFormat sdf = new SimpleDateFormat(
                "d-M-yyyy", Locale.getDefault());
        String hoy = sdf.format(new Date());

        List<DocumentSnapshot> resultado = new ArrayList<>();

        if (filtroActual == Filtro.HOY) {
            for (DocumentSnapshot doc : todas) {
                if (hoy.equals(doc.getString("fecha"))) {
                    resultado.add(doc);
                }
            }
            return resultado;
        }

        // SEMANA: los proximos 7 dias
        long ahora = System.currentTimeMillis();
        long siete = ahora + (7L * 24 * 60 * 60 * 1000);

        for (DocumentSnapshot doc : todas) {
            long t = timestampDeCita(doc);
            if (t >= inicioDeHoy() && t <= siete) {
                resultado.add(doc);
            }
        }
        return resultado;
    }

    private List<DocumentSnapshot> soloFuturas(List<DocumentSnapshot> todas) {
        long inicio = inicioDeHoy();
        List<DocumentSnapshot> resultado = new ArrayList<>();
        for (DocumentSnapshot doc : todas) {
            if (timestampDeCita(doc) >= inicio) {
                resultado.add(doc);
            }
        }
        return resultado;
    }

    private long inicioDeHoy() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "d-M-yyyy", Locale.getDefault());
            String hoy = sdf.format(new Date());
            Date d = sdf.parse(hoy);
            return d != null ? d.getTime() : 0;
        } catch (ParseException e) {
            return 0;
        }
    }

    private void agregarEncabezadoFecha(String fecha) {
        TextView tv = new TextView(this);
        tv.setText(fecha);
        tv.setTextColor(0xFF1B3A6B);
        tv.setTextSize(14);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        float d = getResources().getDisplayMetrics().density;
        tv.setPadding((int)(4*d), (int)(12*d), 0, (int)(8*d));
        contenedorTodasCitas.addView(tv);
    }

    private void crearTarjetaCita(DocumentSnapshot doc) {
        String tratamiento = doc.getString("tratamiento");
        String hora        = doc.getString("horaDisplay");
        if (hora == null) hora = doc.getString("hora");
        String idPaciente  = doc.getString("idPaciente");
        String nombreCache = doc.getString("nombrePaciente");
        Boolean esUrgente  = doc.getBoolean("esUrgente");
        String estadoPago  = doc.getString("estadoPago");

        View tarjeta = LayoutInflater.from(this)
                .inflate(R.layout.item_todas_citas, contenedorTodasCitas, false);

        TextView tvHora    = tarjeta.findViewById(R.id.tvHoraTodasCita);
        TextView tvTrat    = tarjeta.findViewById(R.id.tvTratTodasCita);
        TextView tvNombre  = tarjeta.findViewById(R.id.tvNombreTodasCita);
        TextView tvPago    = tarjeta.findViewById(R.id.tvPagoTodasCita);
        TextView tagUrg    = tarjeta.findViewById(R.id.tagUrgTodasCita);

        if (tvHora != null)
            tvHora.setText(hora != null ? hora : "--:--");
        if (tvTrat != null)
            tvTrat.setText(tratamiento != null ? tratamiento : "Cita");

        // Tag urgente si aplica
        if (tagUrg != null) {
            tagUrg.setVisibility(Boolean.TRUE.equals(esUrgente)
                    ? View.VISIBLE : View.GONE);
        }

        // Estado de pago
        if (tvPago != null) {
            String pagoTxt;
            int colorPago;
            if ("confirmado".equals(estadoPago)) {
                pagoTxt = "Pagado";
                colorPago = 0xFF2E7D32;
            } else if ("pagado".equals(estadoPago)) {
                pagoTxt = "Pendiente revision";
                colorPago = 0xFFF59E0B;
            } else {
                pagoTxt = "Sin pago";
                colorPago = 0xFFB71C1C;
            }
            tvPago.setText(pagoTxt);
            tvPago.setTextColor(colorPago);
        }

        // Cargar nombre del paciente
        if (tvNombre != null) {
            if (nombreCache != null) {
                tvNombre.setText(nombreCache);
            } else if (idPaciente != null) {
                tvNombre.setText("Cargando...");
                final TextView tvRef = tvNombre;
                db.collection("usuarios").document(idPaciente).get()
                        .addOnSuccessListener(u -> {
                            String n = u.getString("Nombre");
                            if (n == null) n = u.getString("nombre");
                            tvRef.setText(n != null ? n : "Paciente");
                        })
                        .addOnFailureListener(e ->
                                tvRef.setText("Paciente"));
            } else {
                tvNombre.setText("Paciente");
            }
        }

        contenedorTodasCitas.addView(tarjeta);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}
