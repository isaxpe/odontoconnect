package com.example.odontoconnect;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Arrays;

/**
 * Gestiona la lista de espera automática.
 *
 * Cuando un paciente cancela su cita, este helper:
 * 1. Busca pacientes que tuvieron citas rechazadas o vencidas
 *    en fechas cercanas (±3 días) con el mismo doctor
 * 2. Les envía una notificación ntfy invitándolos a agendar
 *
 * No requiere servidor — funciona directamente desde Android.
 */
public class ListaEsperaHelper {

    private static final String TAG = "LISTA_ESPERA";

    /**
     * Llama esto cuando un paciente cancela una cita.
     *
     * @param idCitaCancelada ID de la cita que se acaba de cancelar
     */
    public static void notificarListaEspera(String idCitaCancelada) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Obtener datos de la cita cancelada
        db.collection("citas").document(idCitaCancelada).get()
                .addOnSuccessListener(citaDoc -> {
                    if (!citaDoc.exists()) return;

                    String idDoctor    = citaDoc.getString("idDoctor");
                    String fecha       = citaDoc.getString("fecha");
                    String hora        = citaDoc.getString("horaDisplay");
                    if (hora == null) hora = citaDoc.getString("hora");
                    String tratamiento = citaDoc.getString("tratamiento");

                    if (idDoctor == null || fecha == null) return;

                    final String horaFinal       = hora;
                    final String tratFinal       = tratamiento;
                    final String fechaFinal      = fecha;

                    Log.d(TAG, "Buscando candidatos para lista de espera. " +
                            "Fecha: " + fecha + " Doctor: " + idDoctor);

                    // 2. Buscar pacientes con citas rechazadas o vencidas
                    //    con el mismo doctor (en cualquier fecha futura)
                    db.collection("citas")
                            .whereEqualTo("idDoctor", idDoctor)
                            .whereIn("estado", Arrays.asList("rechazada", "vencida"))
                            .get()
                            .addOnSuccessListener(snap -> {
                                if (snap.isEmpty()) {
                                    Log.d(TAG, "No hay candidatos en lista de espera");
                                    return;
                                }

                                int notificados = 0;
                                for (QueryDocumentSnapshot doc : snap) {
                                    String idPaciente = doc.getString("idPaciente");
                                    if (idPaciente == null) continue;

                                    // Evitar notificar al mismo paciente que canceló
                                    if (idPaciente.equals(citaDoc.getString("idPaciente")))
                                        continue;

                                    // Enviar notificación ntfy
                                    NtfyHelper.notificarPaciente(
                                            idPaciente,
                                            "¡Horario disponible!",
                                            "Se liberó un horario con tu doctor.\n" +
                                            "📅 " + fechaFinal +
                                            (horaFinal != null ? "  🕐 " + horaFinal : "") +
                                            (tratFinal != null ? "\n🦷 " + tratFinal : "") +
                                            "\n\nAbre la app para agendar ahora.",
                                            "🔔");

                                    // Marcar en Firestore que fue notificado
                                    // (para no notificar de nuevo)
                                    doc.getReference().update(
                                            "notificadoListaEspera", true);

                                    notificados++;
                                    Log.d(TAG, "Notificado paciente: " + idPaciente);

                                    // Límite: máximo 5 notificaciones por cancelación
                                    if (notificados >= 5) break;
                                }

                                Log.d(TAG, "Lista de espera: " + notificados +
                                        " pacientes notificados");
                            })
                            .addOnFailureListener(e ->
                                    Log.e(TAG, "Error buscando lista de espera: "
                                            + e.getMessage())
                            );
                });
    }
}
