package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.Calendar;

public class AgendaActivity extends AppCompatActivity {

    private CalendarView calendarViewAgenda;
    private TextView tvFechaSeleccionada;
    private Switch switchDiaDisponible;
    private LinearLayout layoutHorariosConfig;
    private Button btnGuardarHorario;
    private String fechaActual = ""; // Guardaremos la fecha elegida aquí

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agenda);

        // 1. Enlazar las vistas
        calendarViewAgenda = findViewById(R.id.calendarViewAgenda);
        tvFechaSeleccionada = findViewById(R.id.tvFechaSeleccionada);
        switchDiaDisponible = findViewById(R.id.switchDiaDisponible);
        layoutHorariosConfig = findViewById(R.id.layoutHorariosConfig);
        btnGuardarHorario = findViewById(R.id.btnGuardarHorario);

        configurarLimitesCalendario();

        // 2. Evento: Cuando el doctor toca una fecha
        calendarViewAgenda.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
                fechaActual = dayOfMonth + "/" + (month + 1) + "/" + year;
                tvFechaSeleccionada.setText(fechaActual);

                // Reiniciamos el interruptor para obligar a configurarlo
                switchDiaDisponible.setChecked(false);
                layoutHorariosConfig.setVisibility(View.GONE);
            }
        });

        // 3. Evento: Cuando el doctor enciende o apaga el "Día Laboral"
        switchDiaDisponible.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(fechaActual.isEmpty()){
                    Toast.makeText(AgendaActivity.this, "Primero selecciona una fecha", Toast.LENGTH_SHORT).show();
                    switchDiaDisponible.setChecked(false);
                    return;
                }

                if (isChecked) {
                    // Si activa el switch, mostramos la lista de horas
                    layoutHorariosConfig.setVisibility(View.VISIBLE);
                } else {
                    // Si lo apaga, ocultamos las horas (Día libre)
                    layoutHorariosConfig.setVisibility(View.GONE);
                }
            }
        });

        // 4. Evento: Guardar la disponibilidad
        btnGuardarHorario.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(AgendaActivity.this, "Horarios habilitados para el " + fechaActual, Toast.LENGTH_LONG).show();
                // En el futuro, aquí enviaremos estos datos a tu base de datos
            }
        });

        // 5. Navegación
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationAgenda);
        bottomNavigationView.setSelectedItemId(R.id.nav_agenda);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_inicio) {
                Intent intent = new Intent(AgendaActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.nav_agenda) {
                return true;
            }
            return false;
        });
    }

    private void configurarLimitesCalendario() {
        Calendar minDate = Calendar.getInstance();
        minDate.set(2026, Calendar.JANUARY, 1);
        calendarViewAgenda.setMinDate(minDate.getTimeInMillis());

        Calendar maxDate = Calendar.getInstance();
        maxDate.set(2035, Calendar.DECEMBER, 31);
        calendarViewAgenda.setMaxDate(maxDate.getTimeInMillis());
    }
}