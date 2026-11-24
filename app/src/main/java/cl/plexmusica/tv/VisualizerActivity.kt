package cl.plexmusica.tv

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.load
import coil.transform.RoundedCornersTransformation
import coil.request.ImageRequest

/**
 * Visualizador a pantalla completa, liviano: tres estilos dibujados con Canvas
 * (Espectro, Onda y Halo) con los colores de la carátula.
 *
 * Control: ◀ ▶ cambia de estilo, OK pausa o reanuda, ▲ ▼ muestran la información, Atrás vuelve.
 */
class VisualizerActivity : AppCompatActivity() {
    private lateinit var vis: VisualizerView
    private lateinit var info: TextView
    private lateinit var title: TextView
    private lateinit var thumb: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var lastArt: Uri? = null

    private val styles = VisualizerView.Style.values()
    private var styleIndex = 0

    private val hideInfo = Runnable {
        info.animate().alpha(0f).setDuration(600).start()
        title.animate().alpha(0f).setDuration(600).start()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                updateSong()
                flashInfo()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visualizer)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        vis = findViewById(R.id.vis)
        info = findViewById(R.id.presetInfo)
        title = findViewById(R.id.songInfo)
        thumb = findViewById(R.id.thumb)
        styleIndex = getSharedPreferences("visualizer", Context.MODE_PRIVATE)
            .getInt("style", 0).coerceIn(0, styles.size - 1)
        vis.style = styles[styleIndex]
        updateInfo()
    }

    override fun onStart() {
        super.onStart()
        PlayerConnection.get(this) { c ->
            controller = c
            c.addListener(listener)
            updateSong()
        }
        flashInfo()
    }

    override fun onResume() {
        super.onResume()
        PcmTap.active = true
    }

    override fun onPause() {
        PcmTap.active = false
        getSharedPreferences("visualizer", Context.MODE_PRIVATE).edit().putInt("style", styleIndex).apply()
        super.onPause()
    }

    override fun onStop() {
        controller?.removeListener(listener)
        controller = null
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> changeStyle(1)
            KeyEvent.KEYCODE_DPAD_LEFT -> changeStyle(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
                flashInfo()
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_INFO -> flashInfo()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun changeStyle(delta: Int) {
        styleIndex = (styleIndex + delta + styles.size) % styles.size
        vis.style = styles[styleIndex]
        updateInfo()
        flashInfo()
    }

    private fun updateInfo() {
        val name = getString(
            when (styles[styleIndex]) {
                VisualizerView.Style.SPECTRUM -> R.string.vis_spectrum
                VisualizerView.Style.WAVE -> R.string.vis_wave
                VisualizerView.Style.HALO -> R.string.vis_halo
            }
        )
        info.text = getString(R.string.vis_info, name)
    }

    private fun updateSong() {
        val md = controller?.mediaMetadata ?: return
        title.text = if (md.title != null) "${md.title} — ${md.artist ?: ""}" else ""
        val uri = md.artworkUri
        if (uri != lastArt) {
            lastArt = uri
            loadArtwork(uri)
        }
    }

    /** Carátula → miniatura en la esquina y colores del degradado (los mismos de Reproduciendo). */
    private fun loadArtwork(uri: Uri?) {
        thumb.load(uri) {
            crossfade(true)
            transformations(RoundedCornersTransformation(8f * resources.displayMetrics.density))
            placeholder(R.drawable.ic_music)
            error(R.drawable.ic_music)
        }
        if (uri == null) {
            vis.setArtwork(null, ArtColors.DEFAULT)
            return
        }
        val request = ImageRequest.Builder(this)
            .data(uri)
            .allowHardware(false)
            .target(
                onError = { vis.setArtwork(null, ArtColors.DEFAULT) },
                onSuccess = { drawable ->
                    val bmp = (drawable as? BitmapDrawable)?.bitmap
                    if (bmp == null) {
                        vis.setArtwork(null, ArtColors.DEFAULT)
                    } else {
                        Palette.from(bmp).generate { p ->
                            vis.setArtwork(bmp, if (p == null) ArtColors.DEFAULT else ArtColors.from(p))
                        }
                    }
                },
            )
            .build()
        imageLoader.enqueue(request)
    }

    private fun flashInfo() {
        handler.removeCallbacks(hideInfo)
        info.animate().cancel()
        title.animate().cancel()
        info.alpha = 1f
        title.alpha = 1f
        info.visibility = View.VISIBLE
        title.visibility = View.VISIBLE
        handler.postDelayed(hideInfo, 4000)
    }

}
