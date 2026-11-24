package cl.plexmusica.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.load

/**
 * Indicador de reproducción para la parte superior de las pantallas de biblioteca,
 * al estilo de la tarjeta de Android TV: carátula, canción, artista · álbum, y botones
 * "Abrir" y "Pausar/Reanudar". Solo se muestra si hay algo en la cola.
 */
class NowPlayingBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val art: ImageView
    private val title: TextView
    private val sub: TextView
    private val btnToggle: ImageButton
    private var controller: MediaController? = null
    private var lastArt: Uri? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            update()
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.now_bar_bg)
        clipToOutline = true
        LayoutInflater.from(context).inflate(R.layout.view_now_playing_bar, this, true)
        art = findViewById(R.id.barArt)
        title = findViewById(R.id.barTitle)
        sub = findViewById(R.id.barSub)
        btnToggle = findViewById(R.id.barToggle)
        visibility = GONE
        art.setOnClickListener {
            context.startActivity(Intent(context, NowPlayingActivity::class.java))
        }
        findViewById<ImageButton>(R.id.barPrev).setOnClickListener { controller?.seekToPrevious() }
        findViewById<ImageButton>(R.id.barNext).setOnClickListener { controller?.seekToNext() }
        btnToggle.setOnClickListener {
            controller?.let { c ->
                if (c.playWhenReady) {
                    c.pause()
                } else {
                    if (c.playbackState == Player.STATE_IDLE) c.prepare()
                    c.play()
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode) return
        PlayerConnection.get(context) { c ->
            if (!isAttachedToWindow) return@get
            controller = c
            c.addListener(listener)
            update()
        }
    }

    override fun onDetachedFromWindow() {
        controller?.removeListener(listener)
        controller = null
        super.onDetachedFromWindow()
    }

    private fun update() {
        val c = controller ?: return
        if (c.mediaItemCount == 0 || c.currentMediaItem == null) {
            visibility = GONE
            return
        }
        visibility = VISIBLE
        val md = c.mediaMetadata
        title.text = md.title ?: ""
        val parts = listOfNotNull(md.artist?.toString(), md.albumTitle?.toString()).filter { it.isNotEmpty() }
        sub.text = (listOf(context.getString(R.string.app_name)) + parts).joinToString(" · ")
        if (md.artworkUri != lastArt || lastArt == null) {
            lastArt = md.artworkUri
            art.load(md.artworkUri) {
                crossfade(true)
                placeholder(R.drawable.ic_music)
                error(R.drawable.ic_music)
            }
        }
        btnToggle.setImageResource(if (c.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play)
    }
}
