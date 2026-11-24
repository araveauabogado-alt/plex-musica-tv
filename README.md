# Plex Música TV

App de música para Android TV (probada para Xiaomi Mi Box) que se conecta a tu servidor Plex.

## Qué hace
- Se vincula con tu cuenta mediante un código en **plex.tv/link** (no tienes que escribir la contraseña en la TV).
- Busca sola la mejor conexión con tu servidor (primero la red local; si no, la remota o el relay).
- Navegas por **Artistas, Álbumes y Playlists**, y puedes **buscar**.
- Reproduces un álbum, una playlist o un artista completo, en orden o en aleatorio.
- La pantalla **Reproduciendo** muestra la carátula grande, la barra de progreso y la canción que viene.
- La música sigue sonando aunque salgas de la app o se active el protector de pantalla. Los botones de reproducir/pausar del control remoto funcionan en cualquier momento.
- La música se reproduce en su calidad original, sin transcodificar (FLAC incluido).

## Compilar el APK
1. Instala **Android Studio** (gratis): https://developer.android.com/studio
2. Abre Android Studio → **Open** → elige la carpeta `PlexMusicaTV`.
3. Espera a que termine la sincronización de Gradle (la primera vez descarga componentes y tarda unos minutos).
4. Menú **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Instalar en el Mi Box
**Opción A, por ADB (la más directa):**
1. En el Mi Box: Configuración → Preferencias del dispositivo → Información → presiona **Compilación** 7 veces para activar las Opciones de desarrollador.
2. En Opciones de desarrollador, activa **Depuración USB** (o "Depuración de red", si aparece).
3. Busca la IP del Mi Box en Configuración → Red.
4. En el computador (la carpeta `platform-tools` del SDK de Android Studio):
   ```
   adb connect 192.168.X.X:5555
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
   Acepta el aviso que aparece en la TV. También puedes presionar ▶ **Run** en Android Studio con el Mi Box conectado.

**Opción B, sin cables ni comandos:**
1. Instala **Send Files to TV** en el teléfono y en el Mi Box (desde Google Play).
2. Envía el APK a la TV y ábrelo con un administrador de archivos (por ejemplo, X-plore).
3. Si te lo pide, permite "instalar apps desconocidas" para ese administrador.

## Uso
1. Abre **Plex Música TV** desde la fila de apps.
2. En el teléfono entra a plex.tv/link e ingresa el código que aparece en la TV.
3. Si tienes más de un servidor, elige el que tiene la música.
4. Si tienes varias bibliotecas de música, el botón **Biblioteca** cambia entre ellas.

## Estructura del código
- `Plex.kt`: API de Plex (vinculación, servidores, biblioteca, búsqueda, URLs).
- `Playback.kt`: servicio de reproducción (Media3/ExoPlayer) y conexión desde las pantallas.
- `MainActivity`, `BrowseActivity`, `TracksActivity`, `NowPlayingActivity`, `LoginActivity`: las pantallas.
