package com.example.odontoconnect;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BroadcastReceiver que dispara el recordatorio 24h antes de la cita.
 * Se registra con AlarmManager al aceptar la cita.
 */
public class RecordatorioReceiver extends BroadcastReceiver {

    private static final String TAG = "NTFY";

    @Override
    public void onReceive(Context context, Intent intent) {
        String idPaciente  = intent.getStringExtra("idPaciente");
        String tratamiento = intent.getStringExtra("tratamiento");
        String fecha       = intent.getStringExtra("fecha");
        String hora        = intent.getStringExtra("hora");

        Log.d(TAG, "⏰ Recordatorio disparado para: " + idPaciente);

        // Enviar notificación ntfy
        if (idPaciente != null && tratamiento != null) {
            NtfyHelper.recordatorio24h(idPaciente, tratamiento,
                    fecha != null ? fecha : "--",
                    hora != null ? hora : "--");
        }
    }

    /**
     * Programa un recordatorio para 24 horas antes de la cita.
     * Llamar esto cuando el doctor acepta una cita.
     */
    public static void programar(Context context, String idCita, String idPaciente,
                                   String tratamiento, String fecha, String hora) {
        try {
            // Parsear fecha de la cita (formato d-M-yyyy)
            String[] partes = fecha.split("-");
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(Integer.parseInt(partes[2]),  // año
                    Integer.parseInt(partes[1]) - 1, // mes (0-indexed)
                    Integer.parseInt(partes[0]),  // día
                    0, 0, 0);

            // Restar 24 horas
            long tiempoRecordatorio = cal.getTimeInMillis() - (24 * 60 * 60 * 1000);

            // Solo programar si el recordatorio es en el futuro
            if (tiempoRecordatorio <= System.currentTimeMillis()) {
                Log.d(TAG, "Recordatorio no programado — cita es en menos de 24h");
                return;
            }

            Intent intent = new Intent(context, RecordatorioReceiver.class);
            intent.putExtra("idPaciente",  idPaciente);
            intent.putExtra("tratamiento", tratamiento);
            intent.putExtra("fecha",       fecha);
            intent.putExtra("hora",        hora);

            // Usar idCita como requestCode para poder cancelarlo si es necesario
            int requestCode = idCita.hashCode();

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager alarmManager =
                    (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            if (alarmManager != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, tiempoRecordatorio, pendingIntent);
                } else {
                    alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP, tiempoRecordatorio, pendingIntent);
                }
                Log.d(TAG, "✅ Recordatorio programado para: " +
                        new java.util.Date(tiempoRecordatorio));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error programando recordatorio: " + e.getMessage());
        }
    }

    /**
     * Cancela el recordatorio si la cita es cancelada o rechazada.
     */
    public static void cancelar(Context context, String idCita) {
        Intent intent = new Intent(context, RecordatorioReceiver.class);
        int requestCode = idCita.hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            Log.d(TAG, "Recordatorio cancelado para cita: " + idCita);
        }
    }
}
