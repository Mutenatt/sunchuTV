# SunchuTV (etapa 2)

Launcher para Android TV 11 (API 30): apps, películas y series de TMDB, clima de Cofico y protector de pantalla.

## Configuración (local.properties, no va a git)
Copiá de `local.properties.example` al menos:

    tmdb.token=TU_API_READ_ACCESS_TOKEN

Opcionales: `tmdb.language`, `tmdb.region`, `weather.latitude`, `weather.longitude`, `weather.place`, `where.url` (sitio que abre el botón del globo).

## Instalar
Run (▶) en Android Studio con el TV seleccionado, o:

    .\adb install -r app\build\outputs\apk\debug\app-debug.apk

## Protector de pantalla
En el TV: Ajustes > Preferencias del dispositivo > Protector de pantalla > SunchuTV.
Probarlo ya mismo:

    .\adb shell am start -n com.android.systemui/.Somnambulator

## Usarlo como launcher (reversible)
1. Anotá el launcher actual:

       .\adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME

2. Activá SunchuTV:

       .\adb shell cmd package set-home-activity tv.sunchu.launcher/.HomeActivity

3. Para volver, usá el paquete/actividad que anotaste en el paso 1:

       .\adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity

## Créditos
Datos de películas y series: TMDB (esta app usa la API de TMDB pero no está avalada ni certificada por TMDB).
Clima: Open-Meteo. Fuentes: Outfit y Plus Jakarta Sans (SIL Open Font License, ver assets/fonts).
