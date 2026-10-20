# SunchuTV (etapa 1)

Launcher para Android TV 11 (API 30). Boot con animación + home con apps instaladas.

## Build
Abrir la carpeta en Android Studio (Hedgehog o superior), dejar que sincronice Gradle y
ejecutar Build → Build APK(s). El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Instalar en el TV
    .\adb connect 192.168.1.9:5555
    .\adb install -r app-debug.apk

## Probar como launcher
    .\adb shell cmd package set-home-activity tv.sunchu.launcher/.HomeActivity

Para volver al home original:
    .\adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
