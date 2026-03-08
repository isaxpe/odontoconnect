package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    // Contadores de la parte superior
    private TextView tvCitasConfirmadasValor;
    private TextView tvSolicitudesPendientesValor;
    private TextView tvPacientesTotalesValor;

    // Elementos de la lista y la tarjeta de próxima cita
    private RecyclerView rvSolicitudesRecientes;
    private LinearLayout layoutProximaCita;
    private TextView tvMensajeVacio;

    // Menú de navegación inferior
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Enlazar las variables con la vista (los IDs que usaremos en el XML)
        inicializarVistas();

        // 2. Configurar la pantalla para un consultorio sin registros aún
        cargarEstadoVacio();

        // 3. Preparar los clics del menú de navegación
        configurarNavegacion();
    }

    private void inicializarVistas()
    {
        tvCitasConfirmadasValor = findViewById(R.id.tvCitasConfirmadasValor);
        tvSolicitudesPendientesValor = findViewById(R.id.tvSolicitudesPendientesValor);
        tvPacientesTotalesValor = findViewById(R.id.tvPacientesTotalesValor);

        rvSolicitudesRecientes = findViewById(R.id.rvSolicitudesRecientes);
        layoutProximaCita = findViewById(R.id.layoutProximaCita);
        tvMensajeVacio = findViewById(R.id.tvMensajeVacio);

        bottomNavigationView = findViewById(R.id.bottomNavigationView);
    }

    private void cargarEstadoVacio() {
        // Inicializamos los contadores en 0
        tvCitasConfirmadasValor.setText("0");
        tvSolicitudesPendientesValor.setText("0");
        tvPacientesTotalesValor.setText("0");

        // Ocultamos el RecyclerView de solicitudes y la tarjeta de próxima cita
        rvSolicitudesRecientes.setVisibility(View.GONE);
        layoutProximaCita.setVisibility(View.GONE);

        // Mostramos un texto que avise que la bandeja está limpia
        tvMensajeVacio.setVisibility(View.VISIBLE);
        tvMensajeVacio.setText("No hay solicitudes ni citas programadas por el momento.");
    }

    private void configurarNavegacion() {
        // Aseguramos que el ícono de Inicio esté seleccionado al abrir
        bottomNavigationView.setSelectedItemId(R.id.nav_inicio);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_inicio) {
                // Ya estamos aquí
                return true;
            } else if (itemId == R.id.nav_agenda) {
                // Abrir pantalla de agenda
                Intent intent = new Intent(MainActivity.this, AgendaActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            // Aquí agregaremos Pacientes y Perfil después
            return false;
        });
    }

}