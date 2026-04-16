package com.example.odontoconnect;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Clase auxiliar para enviar notificaciones push via ntfy.sh
 *
 * SETUP PARA LA DEMO:
 * 1. Instalar la app "ntfy" en los teléfonos de prueba (Play Store, gratis)
 * 2. El paciente debe suscribirse al topic: "odontoconnect-{sus primeros 8 chars del UID}"
 * 3. El doctor debe suscribirse a: "odontoconnect-doctor-{sus primeros 8 chars del UID}"
 * 4. No se necesita cuenta ni configuración — funciona de inmediato
 *
 * USO:
 * NtfyHelper.notificarPaciente(idPaciente, "Título", "Mensaje", "✅");
 * NtfyHelper.notificarDoctor(idDoctor, "Título", "Mensaje", "📋");
 */
public class NtfyHelper {

    private static final String TAG          = "NTFY";
    private static final String NTFY_BASE    = "https://ntfy.sh/";
    private static final String APP_PREFIX   = "odontoconnect-";

    private static final ExecutorService executor =
            Executors.newCachedThreadPool();
    private static final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    // ── Generar topic del paciente ──────────────────────────────────────────
    public static String topicPaciente(String uid) {
        if (uid == null || uid.length() < 8) return APP_PREFIX + "paciente";
        return APP_PREFIX + uid.substring(0, 8).toLowerCase();
    }

    // ── Generar topic del doctor ────────────────────────────────────────────
    public static String topicDoctor(String uid) {
        if (uid == null || uid.length() < 8) return APP_PREFIX + "doctor";
        return APP_PREFIX + "doctor-" + uid.substring(0, 8).toLowerCase();
    }

    // ── Notificar al paciente ───────────────────────────────────────────────
    public static void notificarPaciente(String idPaciente, String titulo,
                                          String mensaje, String emoji) {
        String topic = topicPaciente(idPaciente);
        enviar(topic, emoji + " " + titulo, mensaje, "high");
    }

    // ── Notificar al doctor ─────────────────────────────────────────────────
    public static void notificarDoctor(String idDoctor, String titulo,
                                        String mensaje, String emoji) {
        String topic = topicDoctor(idDoctor);
        enviar(topic, emoji + " " + titulo, mensaje, "default");
    }

    // ── Envío HTTP en hilo secundario ───────────────────────────────────────
    private static void enviar(String topic, String titulo,
                                String mensaje, String prioridad) {
        executor.execute(() -> {
            try {
                URL url = new URL(NTFY_BASE + topic);
                HttpURLConnection conn =
                        (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Title",    titulo);
                conn.setRequestProperty("Priority", prioridad);
                conn.setRequestProperty("Tags",     "tooth");
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");

                byte[] body = mensaje.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    Log.d(TAG, "✅ Notificación enviada a topic: " + topic);
                } else {
                    Log.e(TAG, "❌ Error ntfy HTTP " + code + " en topic: " + topic);
                }
                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "❌ Error enviando notificación: " + e.getMessage());
                // Las notificaciones son opcionales — no interrumpir el flujo
            }
        });
    }

    // ── Métodos de conveniencia para cada evento ────────────────────────────

    public static void citaAceptada(String idPaciente, String tratamiento,
                                     String fecha, String hora) {
        notificarPaciente(idPaciente,
                "Cita aceptada",
                "Tu cita de " + tratamiento + " fue aceptada.\n" +
                "📅 " + fecha + "  🕐 " + hora + "\n" +
                "Recuerda pagar tu anticipo en la app.",
                "✅");
    }

    public static void citaRechazada(String idPaciente, String tratamiento) {
        notificarPaciente(idPaciente,
                "Solicitud rechazada",
                "Tu solicitud de " + tratamiento + " no pudo ser aceptada.\n" +
                "Puedes agendar en otra fecha desde la app.",
                "❌");
    }

    public static void anticipoConfirmado(String idPaciente, String tratamiento) {
        notificarPaciente(idPaciente,
                "Pago confirmado",
                "El doctor confirmó tu anticipo para " + tratamiento + ".\n" +
                "¡Estás listo para tu cita!",
                "💰");
    }

    public static void comprobanteRecibido(String idDoctor, String nombrePaciente,
                                            String tratamiento) {
        notificarDoctor(idDoctor,
                "Comprobante recibido",
                nombrePaciente + " envió su comprobante de pago para " +
                tratamiento + ".\nRevísalo en la app.",
                "💳");
    }

    public static void nuevaSolicitud(String idDoctor, String nombrePaciente,
                                       String tratamiento, String fecha) {
        notificarDoctor(idDoctor,
                "Nueva solicitud de cita",
                nombrePaciente + " quiere agendar " + tratamiento + "\n" +
                "📅 " + fecha + "\nRevisa y acepta en la app.",
                "📋");
    }

    public static void recordatorio24h(String idPaciente, String tratamiento,
                                        String fecha, String hora) {
        notificarPaciente(idPaciente,
                "Recordatorio de cita",
                "Tienes una cita mañana:\n" +
                "🦷 " + tratamiento + "\n" +
                "📅 " + fecha + "  🕐 " + hora + "\n" +
                "¡No olvides asistir!",
                "⏰");
    }
}
