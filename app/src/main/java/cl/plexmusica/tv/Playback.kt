package cl.plexmusica.tv

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Servicio que sigue reproduciendo aunque salgas de la app o se active el protector de pantalla. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
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
                    .setExtras(Bundle().apply { putLong("duration", t.duration) })
                    .build()
            )
            .build()
    }
}
