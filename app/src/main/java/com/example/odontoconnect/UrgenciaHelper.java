package com.example.odontoconnect;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maneja la lógica de urgencia médica:
 *
 * Cuando un paciente reporta dolor FUERTE en el triaje:
 * 1. Busca citas del mismo día o mañana con pacientes de dolor Ninguno o Leve
 * 2. Marca esas citas como "reagendada_por_urgencia"
 * 3. Notifica a esos pacientes via ntfy con opción de reagendar (+20% costo)
 * 4. Asigna el slot al paciente urgente
 * 5. Notifica al doctor
 */
public class UrgenciaHelper {

    private static final String TAG = "URGENCIA";

    // Niveles que activan urgencia
    private static final String DOLOR_FUERTE = "😖 Fuerte — necesito atención urgente";

    // Niveles que pueden ser reagendados
    private static final String DOLOR_NINGUNO  = "😊 No, ningún dolor";
    private static final String DOLOR_LEVE     = "😐 Leve — no interfiere con el día";

    // Porcentaje de aumento si el paciente quiere reagendar
    private static final double PORCENTAJE_AUMENTO = 0.20;

    /**
     * Llama esto después de guardar el triaje si nivelDolor == FUERTE
     */
    public static void procesarUrgencia(String idCitaUrgente, String idPacienteUrgente,
                                         String idDoctor, String tratamiento) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Log.d(TAG, "🚨 Procesando urgencia para cita: " + idCitaUrgente);

        // Obtener las próximas 2 fechas (hoy y mañana) en formato d-M-yyyy
        SimpleDateFormat sdf = new SimpleDateFormat("d-M-yyyy", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        String hoy    = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_MONTH, 1);
        String manana = sdf.format(cal.getTime());

        // Buscar citas pendientes o aceptadas en esas fechas con doctor correcto
        db.collection("citas")
                .whereEqualTo("idDoctor", idDoctor)
                .whereIn("estado", Arrays.asList("pendiente", "aceptada"))
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        Log.d(TAG, "No hay citas cercanas para reagendar");
                        // Aun así notificar al doctor de la urgencia
                        notificarDoctorUrgencia(idDoctor, idPacienteUrgente,
                                tratamiento, null);
                        return;
                    }

                    // Buscar candidato a reagendar (dolor Ninguno o Leve, hoy o mañana)
                    QueryDocumentSnapshot candidato = null;
                    for (QueryDocumentSnapshot doc : snap) {
                        String fecha      = doc.getString("fecha");
                        String nivelDolor = doc.getString("nivelDolor");
                        String idPac      = doc.getString("idPaciente");

                        // No reagendar al mismo paciente urgente
                        if (idPacienteUrgente.equals(idPac)) continue;

                        // Solo hoy o mañana
                        if (!hoy.equals(fecha) && !manana.equals(fecha)) continue;

                        // Solo si su dolor es Ninguno o Leve
                        if (DOLOR_NINGUNO.equals(nivelDolor) ||
                                DOLOR_LEVE.equals(nivelDolor)) {
                            candidato = doc;
                            break; // Tomar el primero que cumpla
                        }
                    }

                    if (candidato != null) {
                        reagendarCita(candidato, idCitaUrgente,
                                idDoctor, idPacienteUrgente, tratamiento);
                    } else {
                        Log.d(TAG, "No se encontró candidato para reagendar");
                        // Notificar al doctor igual — él decide
                        notificarDoctorUrgencia(idDoctor, idPacienteUrgente,
                                tratamiento, null);
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error buscando citas: " + e.getMessage())
                );
    }

    private static void reagendarCita(QueryDocumentSnapshot citaReagendada,
                                       String idCitaUrgente, String idDoctor,
                                       String idPacienteUrgente, String tratamiento) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String idCitaAfectada  = citaReagendada.getId();
        String idPacAfectado   = citaReagendada.getString("idPaciente");
        String fechaOriginal   = citaReagendada.getString("fecha");
        String horaOriginal    = citaReagendada.getString("horaDisplay");
        String tratOriginal    = citaReagendada.getString("tratamiento");
        Double precioOriginal  = citaReagendada.getDouble("precioTratamiento");

        // Calcular precio con aumento del 20%
        double precioAumento = precioOriginal != null
                ? precioOriginal * (1 + PORCENTAJE_AUMENTO) : 0;

        Log.d(TAG, "Reagendando cita: " + idCitaAfectada +
                " del paciente: " + idPacAfectado);

        // 1. Marcar la cita reagendada con estado especial
        Map<String, Object> cambios = new HashMap<>();
        cambios.put("estado",            "reagendada_por_urgencia");
        cambios.put("motivoReagenda",    "Paciente urgente con dolor fuerte");
        cambios.put("precioNuevoFecha",  precioAumento);
        cambios.put("ofertaReagenda",    true);

        db.collection("citas").document(idCitaAfectada)
                .update(cambios)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Cita reagendada correctamente");

                    // 2. Mover la hora de la cita urgente a la hora liberada
                    Map<String, Object> urgente = new HashMap<>();
                    urgente.put("fecha",       fechaOriginal);
                    urgente.put("hora",        citaReagendada.getString("hora"));
                    urgente.put("horaDisplay", horaOriginal);
                    urgente.put("esUrgente",   true);

                    db.collection("citas").document(idCitaUrgente)
                            .update(urgente)
                            .addOnSuccessListener(v -> {
                                Log.d(TAG, "✅ Cita urgente asignada al slot liberado");

                                // 3. Notificar al paciente afectado
                                if (idPacAfectado != null) {
                                    NtfyHelper.notificarPaciente(
                                            idPacAfectado,
                                            "Tu cita fue reagendada",
                                            "Tu cita del " + fechaOriginal +
                                            " a las " + horaOriginal +
                                            " fue cedida a un paciente con urgencia médica.\n\n" +
                                            "Si deseas reagendar en otra fecha, el costo será " +
                                            "$" + String.format(Locale.getDefault(),
                                                    "%.2f", precioAumento) +
                                            " (+20%).\n\nAbre la app para ver tus opciones.",
                                            "📅");
                                }

                                // 4. Notificar al doctor
                                notificarDoctorUrgencia(idDoctor, idPacienteUrgente,
                                        tratamiento, idPacAfectado);
                            });
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error al reagendar: " + e.getMessage())
                );
    }

    private static void notificarDoctorUrgencia(String idDoctor,
                                                  String idPacienteUrgente,
                                                  String tratamiento,
                                                  String idPacAfectado) {
        String msg;
        if (idPacAfectado != null) {
            msg = "Un paciente reportó dolor FUERTE para " + tratamiento + ".\n" +
                  "Se liberó un slot cercano automáticamente.\n" +
                  "Revisa la agenda en la app.";
        } else {
            msg = "Un paciente reportó dolor FUERTE para " + tratamiento + ".\n" +
                  "No se encontró slot cercano disponible.\n" +
                  "Considera agendar manualmente una cita urgente.";
        }

        NtfyHelper.notificarDoctor(idDoctor,
                "🚨 Paciente urgente",
                msg,
                "🚨");
    }

    /**
     * Verifica si el nivel de dolor es urgente
     */
    public static boolean esUrgente(String nivelDolor) {
        return DOLOR_FUERTE.equals(nivelDolor);
    }
}
