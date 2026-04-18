# OdontoConnect Smartie - Diseño Final

## Instalación

Extrae las carpetas de `smartie/res/` dentro de `app/src/main/res/` de tu proyecto Android Studio:

```
smartie/res/layout/    →  app/src/main/res/layout/    (reemplaza los existentes)
smartie/res/drawable/  →  app/src/main/res/drawable/  (agrega a los existentes)
smartie/res/values/    →  app/src/main/res/values/    (reemplaza colors.xml y themes.xml)
smartie/res/anim/      →  app/src/main/res/anim/      (reemplaza los existentes)
```

## Animaciones incluidas

### Automáticas (ya aplicadas en los layouts)
- **Transiciones entre pantallas**: deslizamiento lateral con fade y curva decelerate_quint (350ms)
- **Listas con efecto cascada**: los items aparecen uno por uno desde abajo con delay del 15% (layout_fall_down)
- **animateLayoutChanges**: transiciones suaves cuando aparecen/desaparecen elementos

### Disponibles para usar desde Java

Llámalas en tus activities cuando quieras animar elementos específicos:

```java
// Animar un botón al tocarlo (feedback táctil premium)
btnGuardar.setOnClickListener(v -> {
    Animation pressAnim = AnimationUtils.loadAnimation(this, R.anim.button_press);
    v.startAnimation(pressAnim);
    // tu lógica aquí...
});

// Animar la entrada de una card (pop in con overshoot)
cardInfo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.card_pop_in));

// Fade in simple
view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in));

// Slide up con fade (para banners, toasts personalizados)
banner.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up_fade));

// Slide down desde arriba (notificaciones, alertas)
alerta.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_down_fade));

// Pulse infinito (para llamar la atención a un botón importante)
Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
btnImportante.startAnimation(pulse);

// Shake (para error de campo inválido)
Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
shake.setInterpolator(new CycleInterpolator(4));
etCampo.startAnimation(shake);
```

## Paleta de colores Smartie

- **Navy** `#1B3A6B` - Principal, headers, botones primarios
- **Gold** `#E8A830` - Acento, CTAs destacados, acciones importantes
- **Teal** `#3DBFAC` - Estados positivos (pagado, asistió, activo)
- **Fondo app** `#F5F7FA` - Gris muy claro de fondo

## Elementos visuales

- **Header navy con ola inferior blanca** (40dp corners top) en todas las pantallas
- **Avatar circular dorado** con iniciales (pacientes, doctor, familiares)
- **Cards blancas** con borde gris claro de 1dp (sin sombra)
- **Tags de color** para estados (dorado=pendiente, teal=completado, rojo=rechazado)
- **Inputs con label MAYÚSCULA** en navy arriba del campo
- **BottomNav blanco** con íconos navy (más moderno que el navy sólido)

## Poppins (opcional)

Si quieres fuente Poppins:

1. Descarga de https://fonts.google.com/specimen/Poppins los archivos:
   - `Poppins-Regular.ttf` → rename a `poppins_regular.ttf`
   - `Poppins-Medium.ttf` → `poppins_medium.ttf`
   - `Poppins-SemiBold.ttf` → `poppins_semibold.ttf`
   - `Poppins-Bold.ttf` → `poppins_bold.ttf`
2. Colócalos en `app/src/main/res/font/` (crea la carpeta si no existe)
3. Agrega esta línea dentro del estilo `Theme.Odontoconnect` en `themes.xml`:
   ```xml
   <item name="android:fontFamily">@font/poppins_regular</item>
   ```

## Dependencias necesarias en build.gradle.kts

```kotlin
implementation("androidx.core:core:1.12.0")
implementation("androidx.recyclerview:recyclerview:1.3.2")
implementation("com.google.android.material:material:1.11.0")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")
```
