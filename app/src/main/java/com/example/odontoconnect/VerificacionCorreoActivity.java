package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class VerificacionCorreoActivity extends AppCompatActivity {

    private TextView tvCorreoDestino, tvTiempoRestante, tvEstado;
    private MaterialButton btnYaVerifique, btnReenviar;

    private FirebaseAuth mAuth;
    private String uid, correo, nombre;
    private CountDownTimer temporizador;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verificacion_correo);

        mAuth  = FirebaseAuth.getInstance();
        uid    = getIntent().getStringExtra("uid");
        correo = getIntent().getStringExtra("correo");
        nombre = getIntent().getStringExtra("nombre");

        if (uid == null || correo == null) { finish(); return; }

        tvCorreoDestino  = findViewById(R.id.tvCorreoDestino);
        tvTiempoRestante = findViewById(R.id.tvTiempoRestante);
        tvEstado         = findViewById(R.id.tvEstadoVerificacion);
        btnYaVerifique   = findViewById(R.id.btnYaVerifique);
        btnReenviar      = findViewById(R.id.btnReenviarCorreo);

        tvCorreoDestino.setText("Enviamos un enlace de verificacion a:\n" + correo);

        btnYaVerifique.setOnClickListener(v -> verificarSiConfirmo());
        btnReenviar.setOnClickListener(v -> reenviarCorreo());

        iniciarTemporizador();
    }

    /**
     * El paciente abre su correo, toca el enlace de Firebase,
     * regresa a la app y toca "Ya verifiqué".
     * Firebase recarga el usuario y revisa si emailVerified == true.
     */
    private void verificarSiConfirmo() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { finish(); return; }

        btnYaVerifique.setEnabled(false);
        btnYaVerifique.setText("Verificando...");

        // Recargar el estado del usuario desde Firebase
        user.reload().addOnSuccessListener(aVoid -> {
            if (user.isEmailVerified()) {
                // Correo verificado ✅
                if (temporizador != null) temporizador.cancel();
                Toast.makeText(this, "Correo verificado. Bienvenido!",
                        Toast.LENGTH_SHORT).show();

                // Actualizar en Firestore
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("usuarios").document(uid)
                        .update("correoVerificado", true);

                // Ir al cuestionario de bienvenida
                Intent intent = new Intent(this, CuestionarioBienvenidaActivity.class);
                intent.putExtra("uid",     uid);
                intent.putExtra("esNuevo", true);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            } else {
                // Aún no verificado
                btnYaVerifique.setEnabled(true);
                btnYaVerifique.setText("Ya verifique mi correo");
                if (tvEstado != null) {
                    tvEstado.setText(
                            "Aun no detectamos la verificacion.\n" +
                            "Revisa tu bandeja de entrada (y spam).\n" +
                            "Luego vuelve aqui y toca el boton.");
                    tvEstado.setTextColor(0xFFB71C1C);
                    tvEstado.setVisibility(View.VISIBLE);
                }
            }
        }).addOnFailureListener(e -> {
            btnYaVerifique.setEnabled(true);
            btnYaVerifique.setText("Ya verifique mi correo");
            Toast.makeText(this, "Sin conexion. Verifica tu internet.",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void reenviarCorreo() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        btnReenviar.setEnabled(false);
        user.sendEmailVerification()
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Correo reenviado a " + correo,
                            Toast.LENGTH_SHORT).show();
                    iniciarTemporizador();
                    if (tvEstado != null) {
                        tvEstado.setText("Correo reenviado. Revisa tu bandeja.");
                        tvEstado.setTextColor(0xFF1565C0);
                        tvEstado.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error al reenviar: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }

    private void iniciarTemporizador() {
        if (temporizador != null) temporizador.cancel();
        btnReenviar.setEnabled(false);

        temporizador = new CountDownTimer(60_000, 1000) {
            @Override public void onTick(long ms) {
                if (tvTiempoRestante != null)
                    tvTiempoRestante.setText(
                            "Puedes reenviar en " + (ms / 1000) + " segundos");
            }
            @Override public void onFinish() {
                if (tvTiempoRestante != null) tvTiempoRestante.setText("");
                btnReenviar.setEnabled(true);
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (temporizador != null) temporizador.cancel();
    }
}
