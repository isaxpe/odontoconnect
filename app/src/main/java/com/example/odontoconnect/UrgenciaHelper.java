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
 * Maneja la lógica de urgencia médica.
 *
 * Cuando un paciente reporta dolor FUERTE o INSOPORTABLE en el triaje:
 * 1. Busca citas del mismo día o mañana con pacientes de dolor leve/ninguno
 * 2. Marca esas citas como "reagendada_por_urgencia"
 * 3. Notifica a esos pacientes con opción de reagendar (+20% costo)
 * 4. Asigna el slot al paciente urgente
 * 5. Notifica al doctor
 */
public class UrgenciaHelper {

    private static final String TAG = "URGENCIA";

    // Porcentaje de aumento si el paciente desplazado quiere reagendar
    private static final double PORCENTAJE_AUMENTO = 0.20;

    /**
     * Verifica si un nivel de dolor indica urgencia.
     * Busca palabras clave "fuerte" o "insoportable" sin importar emojis,
     * mayusculas o texto adicional.
     */
    public static boolean esUrgente(String nivelDolor) {
        if (nivelDolor == null) return false;
        String lower = nivelDolor.toLowerCase(Locale.ROOT);
        return lower.contains("fuerte") || lower.contains("insoportable");
    }

    /**
     * Verifica si el dolor es leve o ninguno (candidato a ser reagendado)
     */
    public static boolean esLeveONinguno(String nivelDolor) {
        if (nivelDolor == null) return false;
        String lower = nivelDolor.toLowerCase(Locale.ROOT);
        return lower.contains("sin dolor") ||
               lower.contains("ningun") ||
               lower.contains("leve");
    }

    /**
     * Llama esto después de guardar el triaje si nivelDolor es urgente
     */
    public static void procesarUrgencia(String idCitaUrgente, String idPacienteUrgente,
                                         String idDoctor, String tratamiento) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Log.d(TAG, "Procesando urgencia para cita: " + idCitaUrgente);

        // Obtener las próximas 2 fechas (hoy y mañana) en formato d-M-yyyy
        SimpleDateFormat sdf = new SimpleDateFormat("d-M-yyyy", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        String hoy    = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_MONTH, 1);
        String manana = sdf.format(cal.getTime());

        // Marcar la cita como urgente ANTES de buscar candidatos
        Map<String, Object> marcarUrgente = new HashMap<>();
        marcarUrgente.put("esUrgente", true);
        db.collection("citas").document(idCitaUrgente)
                .update(marcarUrgente)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Cita marcada como urgente"))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error marcando urgente: " + e.getMessage()));

        // Buscar citas pendientes o aceptadas en esas fechas con doctor correcto
        db.collection("citas")
                .whereEqualTo("idDoctor", idDoctor)
                .whereIn("estado", Arrays.asList("pendiente", "aceptada"))
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        Log.d(TAG, "No hay citas cercanas para reagendar");
                        notificarDoctorUrgencia(idDoctor, idPacienteUrgente,
                                tratamiento, null);
                        return;
                    }

                    // Buscar candidato a reagendar (dolor leve o ninguno, hoy o manana)
                    QueryDocumentSnapshot candidato = null;
                    for (QueryDocumentSnapshot doc : snap) {
                        String fecha      = doc.getString("fecha");
                        String nivelDolor = doc.getString("nivelDolor");
                        String idPac      = doc.getString("idPaciente");

                        // No reagendar al mismo paciente urgente
                        if (idPacienteUrgente.equals(idPac)) continue;

                        // Solo hoy o manana
                        if (!hoy.equals(fecha) && !manana.equals(fecha)) continue;

                        // Solo si su dolor es leve o ninguno
                        if (esLeveONinguno(nivelDolor)) {
                            candidato = doc;
                            break;
                        }
                    }

                    if (candidato != null) {
                        reagendarCita(candidato, idCitaUrgente,
                                idDoctor, idPacienteUrgente, tratamiento);
                    } else {
                        Log.d(TAG, "No se encontro candidato para reagendar");
                        // Notificar al doctor igual, el decide
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
                    Log.d(TAG, "Cita reagendada correctamente");

                    // 2. Mover la hora de la cita urgente a la hora liberada
                    Map<String, Object> urgente = new HashMap<>();
                    urgente.put("fecha",       fechaOriginal);
                    urgente.put("hora",        citaReagendada.getString("hora"));
                    urgente.put("horaDisplay", horaOriginal);
                    urgente.put("esUrgente",   true);

                    db.collection("citas").document(idCitaUrgente)
                            .update(urgente)
                            .addOnSuccessListener(v -> {
                                Log.d(TAG, "Cita urgente asignada al slot liberado");

                                // 3. Notificar al paciente afectado
                                if (idPacAfectado != null) {
                                    NtfyHelper.notificarPaciente(
                                            idPacAfectado,
                                            "Tu cita fue reagendada",
                                            "Tu cita del " + fechaOriginal +
                                            " a las " + horaOriginal +
                                            " fue cedida a un paciente con urgencia medica.\n\n" +
                                            "Si deseas reagendar en otra fecha, el costo sera " +
                                            "$" + String.format(Locale.getDefault(),
                                                    "%.2f", precioAumento) +
                                            " (+20%). Abre la app para ver tus opciones.",
                                            "!");
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
            msg = "Un paciente reporto dolor FUERTE para " + tratamiento + ".\n" +
                  "Se libero un slot cercano automaticamente.\n" +
                  "Revisa la agenda en la app.";
        } else {
            msg = "Un paciente reporto dolor FUERTE para " + tratamiento + ".\n" +
                  "No se encontro slot cercano disponible.\n" +
                  "Revisa la solicitud y aceptala con recargo si aplica.";
        }

        NtfyHelper.notificarDoctor(idDoctor,
                "Paciente urgente",
                msg,
                "!");
    }
}
