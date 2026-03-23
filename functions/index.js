const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

// ─────────────────────────────────────────────
// FUNCIÓN 1 y 2: Doctor acepta o rechaza una cita → avisa al paciente
// ─────────────────────────────────────────────
exports.notificarCambioEstadoCita = functions.firestore
    .document("citas/{citaId}")
    .onUpdate(async (change, context) => {
        const antes  = change.before.data();
        const despues = change.after.data();

        // Solo actuar si el estado realmente cambió
        if (antes.estado === despues.estado) return null;

        const nuevoEstado = despues.estado;
        const idPaciente  = despues.idPaciente;
        const tratamiento = despues.tratamiento || "tu cita";
        const fecha       = despues.fecha || "";

        // Solo nos interesan estos dos cambios
        if (nuevoEstado !== "aceptada" && nuevoEstado !== "rechazada") return null;

        // Obtener el token FCM del paciente
        const pacienteDoc = await db.collection("usuarios").document(idPaciente).get();
        if (!pacienteDoc.exists) return null;

        const tokenPaciente = pacienteDoc.data().fcmToken;
        if (!tokenPaciente) return null;

        // Armar el mensaje según el estado
        let titulo, cuerpo;
        if (nuevoEstado === "aceptada") {
            titulo = "✅ ¡Cita Confirmada!";
            cuerpo = `Tu cita de ${tratamiento} el ${fecha} fue aceptada por el doctor.`;
        } else {
            titulo = "❌ Cita Rechazada";
            cuerpo = `Tu solicitud de ${tratamiento} el ${fecha} fue rechazada. Puedes agendar otra fecha.`;
        }

        const mensaje = {
            token: tokenPaciente,
            notification: { title: titulo, body: cuerpo },
            android: {
                notification: {
                    sound: "default",
                    channelId: "citas_channel",
                },
            },
        };

        try {
            await messaging.send(mensaje);
            console.log(`Notificación enviada al paciente ${idPaciente}: ${nuevoEstado}`);
        } catch (error) {
            console.error("Error enviando notificación al paciente:", error);
        }

        return null;
    });


// ─────────────────────────────────────────────
// FUNCIÓN 3: Paciente solicita una cita → avisa al doctor
// ─────────────────────────────────────────────
exports.notificarNuevaSolicitudAlDoctor = functions.firestore
    .document("citas/{citaId}")
    .onCreate(async (snapshot, context) => {
        const cita        = snapshot.data();
        const idDoctor    = cita.idDoctor;
        const tratamiento = cita.tratamiento || "una consulta";
        const fecha       = cita.fecha || "";

        if (!idDoctor) return null;

        // Obtener el token FCM del doctor
        const doctorDoc = await db.collection("usuarios").doc(idDoctor).get();
        if (!doctorDoc.exists) return null;

        const tokenDoctor = doctorDoc.data().fcmToken;
        if (!tokenDoctor) return null;

        // Obtener el nombre del paciente
        const pacienteDoc = await db.collection("usuarios").doc(cita.idPaciente).get();
        const nombrePaciente = pacienteDoc.exists
            ? (pacienteDoc.data().Nombre || pacienteDoc.data().nombre || "Un paciente")
            : "Un paciente";

        const mensaje = {
            token: tokenDoctor,
            notification: {
                title: "📋 Nueva Solicitud de Cita",
                body:  `${nombrePaciente} solicita ${tratamiento} para el ${fecha}.`,
            },
            android: {
                notification: {
                    sound: "default",
                    channelId: "citas_channel",
                },
            },
        };

        try {
            await messaging.send(mensaje);
            console.log(`Notificación de nueva cita enviada al doctor ${idDoctor}`);
        } catch (error) {
            console.error("Error enviando notificación al doctor:", error);
        }

        return null;
    });


// ─────────────────────────────────────────────
// FUNCIÓN 4: Recordatorio 24 horas antes de la cita
// Se ejecuta todos los días a las 8:00 AM (hora Ciudad de México)
// ─────────────────────────────────────────────
exports.recordatorio24Horas = functions.pubsub
    .schedule("0 8 * * *")
    .timeZone("America/Mexico_City")
    .onRun(async (context) => {
        // Calcular la fecha de mañana en formato d-M-yyyy
        const manana = new Date();
        manana.setDate(manana.getDate() + 1);
        const dia  = manana.getDate();
        const mes  = manana.getMonth() + 1;
        const anio = manana.getFullYear();
        const fechaManana = `${dia}-${mes}-${anio}`;

        console.log(`Buscando citas para mañana: ${fechaManana}`);

        // Buscar todas las citas aceptadas para mañana
        const citasSnapshot = await db.collection("citas")
            .where("fecha", "==", fechaManana)
            .where("estado", "==", "aceptada")
            .get();

        if (citasSnapshot.empty) {
            console.log("No hay citas mañana.");
            return null;
        }

        const promesas = citasSnapshot.docs.map(async (citaDoc) => {
            const cita        = citaDoc.data();
            const idPaciente  = cita.idPaciente;
            const tratamiento = cita.tratamiento || "tu cita";
            const hora        = cita.hora || "";

            const pacienteDoc = await db.collection("usuarios").doc(idPaciente).get();
            if (!pacienteDoc.exists) return;

            const tokenPaciente = pacienteDoc.data().fcmToken;
            if (!tokenPaciente) return;

            const mensaje = {
                token: tokenPaciente,
                notification: {
                    title: "⏰ Recordatorio de Cita",
                    body:  `Mañana tienes ${tratamiento} a las ${hora}. ¡No olvides asistir!`,
                },
                android: {
                    notification: {
                        sound: "default",
                        channelId: "citas_channel",
                    },
                },
            };

            try {
                await messaging.send(mensaje);
                console.log(`Recordatorio enviado al paciente ${idPaciente}`);
            } catch (error) {
                console.error(`Error enviando recordatorio a ${idPaciente}:`, error);
            }
        });

        await Promise.all(promesas);
        return null;
    });


// ─────────────────────────────────────────────
// FUNCIÓN 5: Paciente cancela una cita → avisar al paciente más cercano en lista de espera
// Lógica: busca pacientes que hayan solicitado la misma fecha pero fueron rechazados,
// y les ofrece el espacio liberado.
// ─────────────────────────────────────────────
exports.notificarEspacioLiberado = functions.firestore
    .document("citas/{citaId}")
    .onUpdate(async (change, context) => {
        const antes  = change.before.data();
        const despues = change.after.data();

        // Solo actuar si la cita pasó a "cancelada"
        if (antes.estado === despues.estado) return null;
        if (despues.estado !== "cancelada") return null;

        const fechaCancelada = despues.fecha;
        const tratamiento    = despues.tratamiento || "una cita";
        const hora           = despues.hora || "";
        const idDoctor       = despues.idDoctor;

        console.log(`Cita cancelada para ${fechaCancelada}. Buscando pacientes en espera...`);

        // Buscar pacientes con citas rechazadas para la misma fecha (lista de espera)
        const enEsperaSnapshot = await db.collection("citas")
            .where("fecha", "==", fechaCancelada)
            .where("idDoctor", "==", idDoctor)
            .where("estado", "==", "rechazada")
            .get();

        if (enEsperaSnapshot.empty) {
            console.log("No hay pacientes en lista de espera para esta fecha.");
            return null;
        }

        // Tomar el primero de la lista (el más antiguo por orden de creación)
        const citaEnEspera   = enEsperaSnapshot.docs[0].data();
        const idPacienteEspera = citaEnEspera.idPaciente;

        const pacienteDoc = await db.collection("usuarios").doc(idPacienteEspera).get();
        if (!pacienteDoc.exists) return null;

        const tokenPaciente = pacienteDoc.data().fcmToken;
        if (!tokenPaciente) return null;

        const mensaje = {
            token: tokenPaciente,
            notification: {
                title: "🎉 ¡Hay un espacio disponible!",
                body:  `Se liberó un lugar para ${tratamiento} el ${fechaCancelada} a las ${hora}. ¡Ingresa a la app para tomarlo!`,
            },
            android: {
                notification: {
                    sound: "default",
                    channelId: "citas_channel",
                },
            },
            data: {
                // Datos extra para que la app pueda abrir la pantalla correcta
                tipo:   "espacio_liberado",
                fecha:  fechaCancelada,
                hora:   hora,
            },
        };

        try {
            await messaging.send(mensaje);
            console.log(`Notificación de espacio liberado enviada a ${idPacienteEspera}`);
        } catch (error) {
            console.error("Error enviando notificación de espacio liberado:", error);
        }

        return null;
    });