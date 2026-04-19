package com.example.odontoconnect;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SolicitudUrgenciaActivity extends AppCompatActivity {

    private static final String TAG = "SOLICITUD_URGENCIA";

    private TextView tvFechaPreferida, tvHoraPreferida;
    private EditText etDescripcionUrgencia;
    private MaterialButton btnSeleccionarFecha, btnSeleccionarHora,
            btnEnviarSolicitud, btnCancelar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private Calendar fechaElegida;
    private String horaElegidaDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_solicitud_urgencia);

            mAuth = FirebaseAuth.getInstance();
            db    = FirebaseFirestore.getInstance();

            tvFechaPreferida      = findViewById(R.id.tvFechaPreferida);
            tvHoraPreferida       = findViewById(R.id.tvHoraPreferida);
            etDescripcionUrgencia = findViewById(R.id.etDescripcionUrgencia);
            btnSeleccionarFecha   = findViewById(R.id.btnSeleccionarFecha);
            btnSeleccionarHora    = findViewById(R.id.btnSeleccionarHora);
            btnEnviarSolicitud    = findViewById(R.id.btnEnviarSolicitud);
            btnCancelar           = findViewById(R.id.btnCancelar);

            if (btnSeleccionarFecha != null)
                btnSeleccionarFecha.setOnClickListener(v -> mostrarPickerFecha());
            if (btnSeleccionarHora != null)
                btnSeleccionarHora.setOnClickListener(v -> mostrarPickerHora());
            if (btnEnviarSolicitud != null)
                btnEnviarSolicitud.setOnClickListener(v -> enviarSolicitud());
            if (btnCancelar != null)
                btnCancelar.setOnClickListener(v -> finish());

            Log.d(TAG, "onCreate OK");
        } catch (Exception e) {
            Log.e(TAG, "CRASH onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error al abrir: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void mostrarPickerFecha() {
        try {
            Calendar hoy = Calendar.getInstance();
            DatePickerDialog dp = new DatePickerDialog(this,
                    (view, year, month, day) -> {
                        fechaElegida = Calendar.getInstance();
                        fechaElegida.set(year, month, day);
                        String txt = String.format(Locale.getDefault(),
                                "%d-%d-%d", day, month + 1, year);
                        if (tvFechaPreferida != null)
                            tvFechaPreferida.setText(txt);
                    }, hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH),
                    hoy.get(Calendar.DAY_OF_MONTH));
            dp.getDatePicker().setMinDate(hoy.getTimeInMillis());
            Calendar max = (Calendar) hoy.clone();
            max.add(Calendar.DAY_OF_MONTH, 7);
            dp.getDatePicker().setMaxDate(max.getTimeInMillis());
            dp.show();
        } catch (Exception e) {
            Log.e(TAG, "Error picker fecha", e);
        }
    }

    private void mostrarPickerHora() {
        try {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(this, (view, hour, minute) -> {
                horaElegidaDisplay = String.format(Locale.getDefault(),
                        "%02d:%02d", hour, minute);
                if (tvHoraPreferida != null)
                    tvHoraPreferida.setText(horaElegidaDisplay);
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        } catch (Exception e) {
            Log.e(TAG, "Error picker hora", e);
        }
    }

    private void enviarSolicitud() {
        try {
            String descripcion = etDescripcionUrgencia != null
                    ? etDescripcionUrgencia.getText().toString().trim() : "";
            if (descripcion.isEmpty()) {
                Toast.makeText(this, "Describe tu urgencia",
                        Toast.LENGTH_SHORT).show();
                if (etDescripcionUrgencia != null)
                    etDescripcionUrgencia.requestFocus();
                return;
            }
            if (fechaElegida == null) {
                Toast.makeText(this, "Selecciona una fecha preferida",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (horaElegidaDisplay == null) {
                Toast.makeText(this, "Selecciona una hora preferida",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (mAuth.getCurrentUser() == null) {
                Toast.makeText(this, "Sesion expirada",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            String idPaciente = mAuth.getCurrentUser().getUid();

            btnEnviarSolicitud.setEnabled(false);
            btnEnviarSolicitud.setText("Enviando...");

            db.collection("configuracion").document("consultorio").get()
                    .addOnSuccessListener(configDoc -> {
                        if (!configDoc.exists()) {
                            Toast.makeText(this, "No hay consultorio configurado",
                                    Toast.LENGTH_SHORT).show();
                            btnEnviarSolicitud.setEnabled(true);
                            btnEnviarSolicitud.setText("Enviar solicitud de urgencia");
                            return;
                        }
                        String idDoctor = configDoc.getString("idDoctor");
                        if (idDoctor == null) {
                            Toast.makeText(this, "No hay doctor configurado",
                                    Toast.LENGTH_SHORT).show();
                            btnEnviarSolicitud.setEnabled(true);
                            btnEnviarSolicitud.setText("Enviar solicitud de urgencia");
                            return;
                        }
                        crearSolicitud(idPaciente, idDoctor, descripcion);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error leer config", e);
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        btnEnviarSolicitud.setEnabled(true);
                        btnEnviarSolicitud.setText("Enviar solicitud de urgencia");
                    });
        } catch (Exception e) {
            Log.e(TAG, "CRASH enviar", e);
            Toast.makeText(this, "Error: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void crearSolicitud(String idPaciente, String idDoctor,
                                 String descripcion) {
        db.collection("usuarios").document(idPaciente).get()
                .addOnSuccessListener(pacDoc -> {
                    String nombrePac = pacDoc.getString("Nombre");
                    if (nombrePac == null) nombrePac = pacDoc.getString("nombre");
                    if (nombrePac == null) nombrePac = "Paciente";

                    String fechaTxt = tvFechaPreferida != null
                            ? tvFechaPreferida.getText().toString() : "--";

                    Map<String, Object> solicitud = new HashMap<>();
                    solicitud.put("idPaciente",           idPaciente);
                    solicitud.put("idPacienteTitular",    idPaciente);
                    solicitud.put("nombrePaciente",       nombrePac);
                    solicitud.put("idDoctor",             idDoctor);
                    solicitud.put("fechaPreferida",       fechaTxt);
                    solicitud.put("horaPreferida",        horaElegidaDisplay);
                    solicitud.put("descripcionUrgencia",  descripcion);
                    solicitud.put("tratamiento",          "Urgencia");
                    solicitud.put("estado",               "urgencia_pendiente");
                    solicitud.put("esUrgente",            true);
                    solicitud.put("esFamiliar",           false);
                    solicitud.put("timestamp",            System.currentTimeMillis());

                    db.collection("citas").add(solicitud)
                            .addOnSuccessListener(ref -> {
                                try {
                                    NtfyHelper.notificarDoctor(idDoctor,
                                            "URGENCIA solicitada",
                                            "Paciente con urgencia pide atencion para " +
                                                    fechaTxt + " a las " + horaElegidaDisplay,
                                            "!");
                                } catch (Exception e) {
                                    Log.e(TAG, "Error ntfy", e);
                                }

                                new AlertDialog.Builder(this)
                                        .setTitle("Solicitud enviada")
                                        .setMessage("Tu solicitud fue enviada al doctor. " +
                                                "Recibiras una notificacion cuando responda.\n\n" +
                                                "Si acepta, el costo tendra recargo del 20%.")
                                        .setCancelable(false)
                                        .setPositiveButton("Entendido", (d, w) -> {
                                            Intent intent = new Intent(this,
                                                    MisCitasActivity.class);
                                            intent.setFlags(
                                                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                            startActivity(intent);
                                            finish();
                                        })
                                        .show();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error creando cita", e);
                                Toast.makeText(this, "Error: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                                btnEnviarSolicitud.setEnabled(true);
                                btnEnviarSolicitud.setText("Enviar solicitud de urgencia");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error leer usuario", e);
                    Toast.makeText(this, "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    btnEnviarSolicitud.setEnabled(true);
                    btnEnviarSolicitud.setText("Enviar solicitud de urgencia");
                });
    }
}
