SunchuTV

Launcher para Android TV (API 30 o superior) que reúne en una sola pantalla las apps instaladas, el catálogo de películas y series de TMDB, el clima local y un protector de pantalla propio.

Objetivo

Reemplazar la pantalla de inicio estándar de Android TV por una interfaz de un solo vistazo: accesos a las aplicaciones de streaming favoritas, tendencias y estrenos de TMDB con tráiler y datos de disponibilidad por plataforma, una lista personal ("Mi lista"), clima actual y pronóstico corto, y un protector de pantalla con imágenes en tendencia.

La app no reproduce contenido por sí misma: abre las aplicaciones de terceros instaladas en el televisor (YouTube TV, Netflix, Disney+, etc.) o un sitio web configurable para buscar dónde ver un título.

Funcionalidades
Pestañas Inicio, Películas, Series, Apps y System Apps.
Hero con backdrop rotativo según la plataforma enfocada, con reproducción, tráiler y acceso a "Mi lista".
Top semanal calculado a partir de tendencias de TMDB (filtrado por puntuación y cantidad de votos mínima).
Clima desde Open-Meteo, sin clave de API, con panel de detalle.
Acceso rápido a los ajustes de red WiFi.
Protector de pantalla (DreamService) con carrusel de imágenes, reloj y clima.
Animación de arranque única por sesión de proceso.
Lenguaje y framework
Lenguaje: Java 17.
Plataforma: Android SDK, sin dependencias de terceros. El proyecto no usa librerías externas para HTTP, JSON o imágenes; todo se implementa con HttpURLConnection, org.json, LruCache y SharedPreferences.
Build: Gradle 8.7 (wrapper incluido) con Android Gradle Plugin 8.5.2.
SDK: compileSdk 34, targetSdk 34, minSdk 30.
Componentes Android: Activity (Home y Boot), DreamService (protector de pantalla), VideoView (animación de inicio).
Servicios externos: TMDB API v3 (autenticación Bearer con Read Access Token) y Open-Meteo para el clima.
Requisitos
Android Studio Ladybug o superior, o JDK 17 y Android SDK con platform-tools y platforms;android-34.
Un dispositivo Android TV o emulador con API 30 o superior.
Depuración por ADB habilitada en el televisor (Ajustes > Preferencias del dispositivo > Información > tocar "Compilación" varias veces para activar Opciones de desarrollador, y luego activar "Depuración por ADB" o "Depuración de red").
Un token de lectura de TMDB (Read Access Token) de una cuenta gratuita en themoviedb.org. No es la API Key v3.
Instalación
1. Clonar el repositorio
bash
git clone https://github.com/Mutenatt/sunchuTV.git
cd sunchuTV
2. Configurar local.properties

Crear el archivo local.properties en la raíz del proyecto. Este archivo no se versiona.

properties
sdk.dir=/ruta/al/Android/Sdk
tmdb.token=TU_READ_ACCESS_TOKEN_DE_TMDB

Android Studio genera sdk.dir automáticamente. Si compilás desde la línea de comandos, debe apuntar a la ruta real del SDK.

Propiedades opcionales (los valores entre paréntesis son los predeterminados):

Propiedad	Descripción
tmdb.language	Idioma de las respuestas de TMDB (es-AR)
tmdb.region	Región para disponibilidad por plataforma (AR)
weather.latitude	Latitud para el clima (-31.3980)
weather.longitude	Longitud para el clima (-64.1850)
weather.place	Nombre de la localidad mostrado en pantalla (Cofico)
where.url	Sitio que abre el botón "Dónde ver" (https://framextv.cc)

Las propiedades weather.* tienen valores por defecto orientados a Córdoba, Argentina. Cambiarlas si el clima se necesita en otra ubicación.

3. Compilar

Con Android Studio: seleccionar el módulo app y ejecutar Run (▶) con el dispositivo elegido.

Desde la línea de comandos:

bash
# Linux / macOS
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug

El APK queda en app/build/outputs/apk/debug/app-debug.apk.

4. Instalar en el televisor

Conectar el televisor por red y autorizar la sesión ADB desde el propio televisor:

bash
adb connect <IP_DEL_TV>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk

La opción -r reemplaza la versión instalada conservando los datos.

5. Usar SunchuTV como launcher (opcional y reversible)

Antes de cambiar el launcher, registrar el actual para poder volver atrás:

bash
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME

Activar SunchuTV:

bash
adb shell cmd package set-home-activity tv.sunchu.launcher/.HomeActivity

Para restaurar el launcher original, usar el paquete y la actividad obtenidos en el primer paso. En Android TV de Google, normalmente es:

bash
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
6. Protector de pantalla (opcional)

En el televisor: Ajustes > Preferencias del dispositivo > Protector de pantalla > SunchuTV.

También se puede iniciar desde ADB:

bash
adb shell am start -n com.android.systemui/.Somnambulator
Estructura del proyecto
app/src/main/java/tv/sunchu/launcher/
  HomeActivity.java     Interfaz principal: pestañas, hero, filas y navegación
  Tmdb.java             Cliente de la API de TMDB (listas, tráilers, proveedores, Top semanal)
  Weather.java          Cliente de Open-Meteo y modelo del clima
  SunchuDream.java      Protector de pantalla
  Apps.java             Listado de apps instaladas con entrada de Android TV
  MyList.java           Persistencia de "Mi lista" en SharedPreferences
  ImageLoader.java      Descarga de imágenes con caché LRU en memoria
  Net.java              HTTP mínimo y ejecución en pool de hilos
  BootActivity.java     Animación de inicio
  WhereToWatch.java     Construcción de la URL de búsqueda
app/src/main/res/       Layouts, temas, colores, fuentes y recursos gráficos
Limitaciones conocidas
El proyecto no incluye pruebas automatizadas.
El token de TMDB se incluye en el APK mediante BuildConfig. Es un token de solo lectura, pero no debe publicarse un APK firmado que lo contenga para distribución pública.
La interfaz está diseñada para control remoto (D-pad). Las pantallas táctiles son opcionales en el manifiesto, pero no es el caso de uso principal.
Créditos y avisos
Datos de películas y series: TMDB. Esta aplicación utiliza la API de TMDB, pero no está avalada ni certificada por TMDB.
Clima: Open-Meteo.
Tipografías: Outfit y Plus Jakarta Sans, bajo SIL Open Font License (ver app/src/main/assets/fonts/).
