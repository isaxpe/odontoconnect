package com.example.odontoconnect;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Helper para el bottom nav pill custom.
 * Uso en cada activity:
 *   BottomNavHelper.setupDoctor(this, BottomNavHelper.DoctorTab.INICIO);
 *   BottomNavHelper.setupPaciente(this, BottomNavHelper.PacienteTab.CITAS);
 *
 * El FAB central lleva a CitasHoyActivity (doctor) / AgendarCitaActivity (paciente).
 */
public class BottomNavHelper {

    // Colores (deben existir en res/values/colors.xml)
    private static final int COLOR_ACTIVO    = 0xFF2EA6E8; // celeste
    private static final int COLOR_INACTIVO  = 0xFF4E6299; // navy claro

    public enum DoctorTab   { INICIO, AGENDA, PACIENTES, PERFIL }
    public enum PacienteTab { INICIO, CITAS, FAMILIARES, PERFIL }

    // ============================================================
    // DOCTOR
    // ============================================================
    public static void setupDoctor(Activity act, DoctorTab activa) {
        View inicio    = act.findViewById(R.id.nav_inicio);
        View agenda    = act.findViewById(R.id.nav_agenda);
        View pacientes = act.findViewById(R.id.nav_pacientes);
        View perfil    = act.findViewById(R.id.nav_perfil);
        View fab       = act.findViewById(R.id.nav_fab_central);

        if (inicio == null) return; // este layout no tiene bottomnav

        // Estado activo visual
        pintar(act, R.id.navIconInicio,    R.id.navTxtInicio,    activa == DoctorTab.INICIO);
        pintar(act, R.id.navIconAgenda,    R.id.navTxtAgenda,    activa == DoctorTab.AGENDA);
        pintar(act, R.id.navIconPacientes, R.id.navTxtPacientes, activa == DoctorTab.PACIENTES);
        pintar(act, R.id.navIconPerfil,    R.id.navTxtPerfil,    activa == DoctorTab.PERFIL);

        // Clicks
        inicio.setOnClickListener(v -> ir(act, MainActivity.class, activa == DoctorTab.INICIO));
        agenda.setOnClickListener(v -> ir(act, AgendaActivity.class, activa == DoctorTab.AGENDA));
        pacientes.setOnClickListener(v -> ir(act, PacientesActivity.class, activa == DoctorTab.PACIENTES));
        perfil.setOnClickListener(v -> ir(act, PerfilDoctorActivity.class, activa == DoctorTab.PERFIL));

        // FAB central: acceso rapido a Citas de Hoy
        if (fab != null) fab.setOnClickListener(v ->
                ir(act, CitasHoyActivity.class, false));
    }

    // ============================================================
    // PACIENTE
    // ============================================================
    public static void setupPaciente(Activity act, PacienteTab activa) {
        View inicio     = act.findViewById(R.id.nav_paciente_inicio);
        View citas      = act.findViewById(R.id.nav_paciente_citas);
        View familiares = act.findViewById(R.id.nav_paciente_familiares);
        View perfil     = act.findViewById(R.id.nav_paciente_perfil);
        View fab        = act.findViewById(R.id.nav_paciente_agendar);

        if (inicio == null) return;

        pintar(act, R.id.navPIconInicio,     R.id.navPTxtInicio,     activa == PacienteTab.INICIO);
        pintar(act, R.id.navPIconCitas,      R.id.navPTxtCitas,      activa == PacienteTab.CITAS);
        pintar(act, R.id.navPIconFamiliares, R.id.navPTxtFamiliares, activa == PacienteTab.FAMILIARES);
        pintar(act, R.id.navPIconPerfil,     R.id.navPTxtPerfil,     activa == PacienteTab.PERFIL);

        inicio.setOnClickListener(v -> ir(act, PacienteMainActivity.class, activa == PacienteTab.INICIO));
        citas.setOnClickListener(v -> ir(act, MisCitasActivity.class, activa == PacienteTab.CITAS));
        familiares.setOnClickListener(v -> ir(act, FamiliaresActivity.class, activa == PacienteTab.FAMILIARES));
        perfil.setOnClickListener(v -> ir(act, PerfilPacienteActivity.class, activa == PacienteTab.PERFIL));

        // FAB central: acceso rapido a Agendar Cita
        if (fab != null) fab.setOnClickListener(v ->
                ir(act, AgendarCitaActivity.class, false));
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private static void pintar(Activity act, int iconId, int txtId, boolean activo) {
        ImageView icon = act.findViewById(iconId);
        TextView  txt  = act.findViewById(txtId);
        int color = activo ? COLOR_ACTIVO : COLOR_INACTIVO;
        if (icon != null) icon.setColorFilter(color);
        if (txt  != null) txt.setTextColor(color);
    }

    private static void ir(Activity act, Class<?> destino, boolean yaEstoyAqui) {
        if (yaEstoyAqui) return;
        Intent i = new Intent(act, destino);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        act.startActivity(i);
        act.overridePendingTransition(0, 0);
        act.finish();
    }
}
