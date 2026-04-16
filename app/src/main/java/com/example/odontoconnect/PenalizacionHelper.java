package com.example.odontoconnect;

import android.content.Context;
import android.widget.Toast;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Clase auxiliar para gestionar el sistema de penalizaciones.
 *
 * REGLA IMPORTANTE:
 * - Las faltas SOLO se suman cuando el paciente tiene cita ACEPTADA y NO asiste.
 * - Rechazar una solicitud NO es una falta.
 */
public class PenalizacionHelper {

    public interface ResultadoVerificacion {
        void onPermitido();
        void onBloqueado(String mensaje);
    }

    public static void verificarPuedeAgendar(DocumentSnapshot doc,
                                              Context context,
                                              ResultadoVerificacion callback) {
        if (!doc.exists()) {
            callback.onPermitido();
            return;
        }

        Long faltas           = doc.getLong("faltas");
        Boolean bloqueado     = doc.getBoolean("bloqueado");
        Long nivelPen         = doc.getLong("nivelPenalizacion");
        Long restriccionHasta = doc.getLong("restriccionHasta");

        if (faltas == null) faltas = 0L;
        if (bloqueado == null) bloqueado = false;
        if (nivelPen == null) nivelPen = 0L;

        // Nivel 3: Bloqueado permanentemente
        if (bloqueado || faltas >= 3) {
            callback.onBloqueado(
                "🔴 Tu cuenta está bloqueada por acumular 3 inasistencias.\n\n" +
                "Contacta al consultorio para resolver tu situación."
            );
            return;
        }

        // Nivel 2: Restricción temporal
        if (nivelPen >= 2 && restriccionHasta != null) {
            long ahora = System.currentTimeMillis();
            if (ahora < restriccionHasta) {
                long diasRestantes =
                        (restriccionHasta - ahora) / (1000 * 60 * 60 * 24);
                callback.onBloqueado(
                    "🚫 Tienes una restricción temporal por 2 inasistencias.\n\n" +
                    "Podrás agendar de nuevo en " + (diasRestantes + 1) + " día(s)."
                );
                return;
            }
        }

        // Nivel 1: Advertencia — puede agendar pero se le avisa
        if (faltas == 1) {
            Toast.makeText(context,
                "⚠️ Tienes 1 inasistencia registrada. " +
                "Con 3 tu cuenta será bloqueada.",
                Toast.LENGTH_LONG).show();
        }

        callback.onPermitido();
    }

    public static String getMensajeEstado(long faltas, boolean bloqueado,
                                           long nivelPen, Long restriccionHasta) {
        if (bloqueado || faltas >= 3) {
            return "🔴 BLOQUEADO — Cuenta suspendida por 3 inasistencias";
        }
        if (nivelPen >= 2 && restriccionHasta != null) {
            long ahora = System.currentTimeMillis();
            if (ahora < restriccionHasta) {
                long dias = (restriccionHasta - ahora) / (1000 * 60 * 60 * 24);
                return "🚫 RESTRINGIDO — Puedes agendar en " + (dias + 1) + " día(s)";
            }
        }
        if (faltas == 1) {
            return "⚠️ ADVERTENCIA — 1 inasistencia acumulada";
        }
        if (faltas == 2) {
            return "⚠️⚠️ ADVERTENCIA — 2 inasistencias acumuladas";
        }
        return "✅ ACTIVO";
    }
}
