package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class GestionTratamientosActivity extends AppCompatActivity {

    private LinearLayout contenedorTratamientos;
    private TextView tvSinTratamientos;
    private Button btnAgregarTratamiento;
    private FirebaseFirestore db;
    private ListenerRegistration listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gestion_tratamientos);

        db = FirebaseFirestore.getInstance();

        contenedorTratamientos = findViewById(R.id.contenedorTratamientos);
        tvSinTratamientos      = findViewById(R.id.tvSinTratamientos);
        btnAgregarTratamiento  = findViewById(R.id.btnAgregarTratamiento);

        btnAgregarTratamiento.setOnClickListener(v ->
                mostrarDialogoTratamiento(null, null, 0, 1, 0.0));

        cargarTratamientos();

        BottomNavHelper.setupDoctor(this, BottomNavHelper.DoctorTab.INICIO);

    }

    private void cargarTratamientos() {
        listener = db.collection("tratamientos")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    contenedorTratamientos.removeAllViews();

                    if (snap.isEmpty()) {
                        tvSinTratamientos.setVisibility(View.VISIBLE);
                        return;
                    }
                    tvSinTratamientos.setVisibility(View.GONE);

                    for (QueryDocumentSnapshot doc : snap) {
                        String nombre  = doc.getString("nombre");
                        Long minutos   = doc.getLong("duracion_minutos");
                        Long horas     = doc.getLong("duracion_horas");
                        Double precio  = doc.getDouble("precio");

                        if (nombre == null) continue;
                        if (minutos == null) minutos = horas != null ? horas * 60 : 60L;
                        if (horas == null)   horas   = (long) Math.ceil(minutos / 60.0);
                        if (precio == null)  precio  = 0.0;

                        crearTarjeta(doc.getId(), nombre, minutos, horas, precio);
                    }
                });
    }

    private void crearTarjeta(String id, String nombre,
                               long minutos, long horas, double precio) {
        View tarjeta = LayoutInflater.from(this)
                .inflate(R.layout.item_tratamiento, contenedorTratamientos, false);

        TextView tvNombre   = tarjeta.findViewById(R.id.tvNombreTratItem);
        TextView tvDuracion = tarjeta.findViewById(R.id.tvDuracionTratItem);
        Button btnEditar    = tarjeta.findViewById(R.id.btnEditarTrat);
        Button btnEliminar  = tarjeta.findViewById(R.id.btnEliminarTrat);

        tvNombre.setText(nombre);
        String dur = horas + " hr(s) · " + minutos + " min";
        dur += precio > 0
                ? "  💲 $" + String.format("%.2f", precio)
                : "  💲 Sin precio";
        tvDuracion.setText(dur);

        final long mFinal = minutos, hFinal = horas;
        final double pFinal = precio;

        btnEditar.setOnClickListener(v ->
                mostrarDialogoTratamiento(id, nombre, mFinal, hFinal, pFinal));

        btnEliminar.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Eliminar tratamiento")
                        .setMessage("¿Eliminar \"" + nombre + "\"?")
                        .setPositiveButton("Eliminar", (d, w) ->
                                db.collection("tratamientos").document(id).delete()
                                        .addOnSuccessListener(aVoid ->
                                                Toast.makeText(this,
                                                        "Eliminado", Toast.LENGTH_SHORT).show()
                                        )
                        )
                        .setNegativeButton("Cancelar", null)
                        .show()
        );

        contenedorTratamientos.addView(tarjeta);
    }

    private void mostrarDialogoTratamiento(String id, String nombreActual,
                                            long minActual, long horActual,
                                            double precioActual) {
        View form = LayoutInflater.from(this)
                .inflate(R.layout.dialog_tratamiento, null);

        EditText etNombre  = form.findViewById(R.id.etNombreTrat);
        EditText etMinutos = form.findViewById(R.id.etMinutosTrat);
        EditText etHoras   = form.findViewById(R.id.etHorasTrat);
        EditText etPrecio  = form.findViewById(R.id.etPrecioTrat);

        if (id != null) {
            etNombre.setText(nombreActual);
            etMinutos.setText(String.valueOf(minActual));
            etHoras.setText(String.valueOf(horActual));
            if (precioActual > 0)
                etPrecio.setText(String.format("%.2f", precioActual));
        }

        new AlertDialog.Builder(this)
                .setTitle(id == null ? "Nuevo Tratamiento" : "Editar Tratamiento")
                .setView(form)
                .setPositiveButton("Guardar", (d, w) -> {
                    String nombre = etNombre.getText().toString().trim();
                    String minStr = etMinutos.getText().toString().trim();
                    String horStr = etHoras.getText().toString().trim();
                    String precStr = etPrecio.getText().toString().trim();

                    if (nombre.isEmpty()) {
                        Toast.makeText(this, "El nombre es obligatorio",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    long min  = minStr.isEmpty() ? 60L : Long.parseLong(minStr);
                    long hora = horStr.isEmpty()
                            ? (long) Math.ceil(min / 60.0) : Long.parseLong(horStr);
                    double precio = precStr.isEmpty() ? 0.0 : Double.parseDouble(precStr);

                    guardarTratamiento(id, nombre, min, hora, precio);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void guardarTratamiento(String id, String nombre,
                                     long minutos, long horas, double precio) {
        if (id == null) {
            db.collection("tratamientos")
                    .whereEqualTo("nombre", nombre).get()
                    .addOnSuccessListener(snap -> {
                        if (!snap.isEmpty()) {
                            Toast.makeText(this, "Ya existe ese tratamiento",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            escribir(null, nombre, minutos, horas, precio);
                        }
                    });
        } else {
            escribir(id, nombre, minutos, horas, precio);
        }
    }

    private void escribir(String id, String nombre,
                           long minutos, long horas, double precio) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre",           nombre);
        datos.put("duracion_minutos", minutos);
        datos.put("duracion_horas",   horas);
        datos.put("precio",           precio);

        if (id == null) {
            db.collection("tratamientos").add(datos)
                    .addOnSuccessListener(r ->
                            Toast.makeText(this, "✅ Tratamiento agregado",
                                    Toast.LENGTH_SHORT).show());
        } else {
            db.collection("tratamientos").document(id).update(datos)
                    .addOnSuccessListener(aVoid ->
                            Toast.makeText(this, "✅ Tratamiento actualizado",
                                    Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}
