package com.example.odontoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private RadioGroup rgRolUsuario;
    private EditText etCorreo, etPassword;
    private Button btnIniciarSesion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        rgRolUsuario = findViewById(R.id.rgRolUsuario);
        etCorreo = findViewById(R.id.etCorreo);
        etPassword = findViewById(R.id.etPassword);
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion);

        btnIniciarSesion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String correo = etCorreo.getText().toString();
                String password = etPassword.getText().toString();

                // Validación rápida para que no dejen campos vacíos
                if (correo.isEmpty() || password.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "Por favor, llena todos los campos", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Aquí en el futuro Firebase verificará el correo y contraseña.
                // Por ahora, simulamos el inicio de sesión exitoso.

                int seleccionId = rgRolUsuario.getCheckedRadioButtonId();

                if (seleccionId == R.id.rbOdontologo) {
                    // ¡Es el doctor! Lo mandamos a TU MainActivity
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish(); // Cerramos la pantalla de login para que no pueda volver atrás

                } else if (seleccionId == R.id.rbPaciente) {
                    // Es un paciente.
                    Toast.makeText(LoginActivity.this, "Abriendo interfaz de Paciente...", Toast.LENGTH_LONG).show();

                    // Cuando tus compañeros te pasen su código, activarás esto:
                    // Intent intent = new Intent(LoginActivity.this, NombreDeSuPantallaActivity.class);
                    // startActivity(intent);
                    // finish();
                }
            }
        });
    }
}