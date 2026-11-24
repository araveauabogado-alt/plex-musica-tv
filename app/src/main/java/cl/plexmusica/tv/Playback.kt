package cl.plexmusica.tv

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Servicio que sigue reproduciendo aunque salgas de la app o se active el protector de pantalla. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())

    // Avisa a Plex cuando una canción se escuchó al menos la mitad (o 4 minutos), una vez por reproducción.
    private var trackedId: String? = null
    private var trackedIndex = -1
    private var scrobbled = false
    private val scrobbleCheck = object : Runnable {
        override fun run() {
            checkScrobble()
            handler.postDelayed(this, 5000)
        }
    }

    private fun checkScrobble() {
        val p = session?.player ?: return
        val item = p.currentMediaItem ?: return
        if (item.mediaId != trackedId || p.currentMediaItemIndex != trackedIndex) {
            trackedId = item.mediaId
            trackedIndex = p.currentMediaItemIndex
            scrobbled = false
        }
        if (scrobbled) return
        val dur = p.duration
        val pos = p.currentPosition
        if (dur <= 0 || dur == C.TIME_UNSET) return
        if (pos >= dur / 2 || pos >= 4 * 60_000L) {
            scrobbled = true
            val key = item.mediaId
            scope.launch {
                try {
                    Plex.scrobble(key)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // sin conexión: no importa, se pierde solo esta marca
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        // El audio decodificado pasa también por PcmTap, que alimenta al visualizador.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink? = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(TeeAudioProcessor(PcmTap)))
                .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                        item.buildUpon().setUri(uri).build()
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                // Al repetir la misma canción se puede volver a contar.
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) trackedId = null
            }
        })
        handler.post(scrobbleCheck)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        handler.removeCallbacks(scrobbleCheck)
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/** Conexión compartida al reproductor desde las pantallas. */
object PlayerConnection {
    private var future: ListenableFuture<MediaController>? = null

    fun get(context: Context, onReady: (MediaController) -> Unit) {
        val app = context.applicationContext
        var existing = future
        if (existing != null && existing.isDone) {
            val ok = try { existing.get().isConnected } catch (e: Exception) { false }
            if (!ok) existing = null
        }
        val ready: ListenableFuture<MediaController> = existing
            ?: MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
                .buildAsync()
                .also { future = it }
        ready.addListener({
            try {
                onReady(ready.get())
            } catch (e: Exception) {
                future = null
            }
        }, ContextCompat.getMainExecutor(app))
    }

    /** El reproductor ya conectado, si lo hay (sin esperar). */
    fun current(): MediaController? {
        val f = future ?: return null
        if (!f.isDone) return null
        return try {
            f.get().takeIf { it.isConnected }
        } catch (e: Exception) {
            null
        }
    }

    fun play(context: Context, tracks: List<PlexItem>, startIndex: Int, shuffle: Boolean = false) {
        val start = tracks.getOrNull(startIndex)
        val playable = tracks.filter { it.partKey != null }
        if (playable.isEmpty()) return
        val idx = if (shuffle) playable.indices.random()
        else (start?.let { playable.indexOf(it) } ?: 0).coerceAtLeast(0)
        val items = playable.map { toMediaItem(it) }
        get(context) { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(items, idx, 0L)
            c.prepare()
            c.play()
        }
    }

    /** Agrega canciones a la cola: a continuación de la actual, o al final. */
    fun enqueue(context: Context, tracks: List<PlexItem>, next: Boolean, onDone: (Int) -> Unit = {}) {
        val items = tracks.filter { it.partKey != null }.map { toMediaItem(it) }
        if (items.isEmpty()) return
        get(context) { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(items)
                c.prepare()
                c.play()
            } else {
                val at = if (next) c.currentMediaItemIndex + 1 else c.mediaItemCount
                c.addMediaItems(at, items)
            }
            onDone(items.size)
        }
    }

    private fun toMediaItem(t: PlexItem): MediaItem {
        val url = Plex.streamUrl(t.partKey ?: "")
        val art = Plex.thumbUrl(t.thumb, 600)?.let { Uri.parse(it) }
        return MediaItem.Builder()
            .setMediaId(t.ratingKey)
            .setUri(url)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(url)).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(art)
                    .setExtras(
                        Bundle().apply {
                            putLong("duration", t.duration)
                            putString("codec", t.codec)
                            putInt("bitrate", t.bitrate)
                        }
                    )
                    .build()
            )
            .build()
    }
}
