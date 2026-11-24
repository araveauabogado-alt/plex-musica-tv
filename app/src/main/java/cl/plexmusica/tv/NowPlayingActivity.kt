package cl.plexmusica.tv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.load

/** Pantalla "Reproduciendo": carátula grande y controles. */
class NowPlayingActivity : AppCompatActivity() {
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastArt: Uri? = null

    private lateinit var cover: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var album: TextView
    private lateinit var progress: ProgressBar
    private lateinit var position: TextView
    private lateinit var duration: TextView
    private lateinit var nextUp: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnShuffle: ImageButton

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateAll()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cover = findViewById(R.id.cover)
        title = findViewById(R.id.title)
        artist = findViewById(R.id.artist)
        album = findViewById(R.id.album)
        progress = findViewById(R.id.progress)
        position = findViewById(R.id.position)
        duration = findViewById(R.id.duration)
        nextUp = findViewById(R.id.nextUp)
        btnPlay = findViewById(R.id.btnPlay)
        btnShuffle = findViewById(R.id.btnShuffleMode)

        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { controller?.seekToPrevious() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { controller?.seekToNext() }
        btnPlay.setOnClickListener { togglePlay() }
        btnShuffle.setOnClickListener {
            controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
        }
        btnPlay.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        PlayerConnection.get(this) { c ->
            controller = c
            c.addListener(listener)
            updateAll()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }
    }

    override fun onStop() {
        controller?.removeListener(listener)
        controller = null
        handler.removeCallbacks(ticker)
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val c = controller
        if (c != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { c.seekTo(c.currentPosition + 15_000); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { c.seekTo((c.currentPosition - 15_000).coerceAtLeast(0)); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    private fun updateAll() {
        val c = controller ?: return
        val md = c.mediaMetadata
        if (c.mediaItemCount == 0) {
            title.text = getString(R.string.nothing_playing)
            artist.text = ""
            album.text = ""
        } else {
            title.text = md.title ?: ""
            artist.text = md.artist ?: ""
            album.text = md.albumTitle ?: ""
        }
        if (md.artworkUri != lastArt || lastArt == null) {
            lastArt = md.artworkUri
            cover.load(md.artworkUri) {
                crossfade(true)
                placeholder(R.drawable.ic_music)
                error(R.drawable.ic_music)
            }
        }
        btnPlay.setImageResource(if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) R.drawable.ic_pause else R.drawable.ic_play)
        btnShuffle.alpha = if (c.shuffleModeEnabled) 1f else 0.45f

        val next = c.nextMediaItemIndex
        nextUp.text = if (next != C.INDEX_UNSET) {
            val n = c.getMediaItemAt(next).mediaMetadata
            getString(R.string.next_up, n.title ?: "", n.artist ?: "")
        } else ""
        updateProgress()
    }

    private fun updateProgress() {
        val c = controller ?: return
        val dur = c.duration
        val pos = c.currentPosition
        if (dur > 0 && dur != C.TIME_UNSET) {
            progress.max = 1000
            progress.progress = ((pos * 1000) / dur).toInt()
            duration.text = Ui.time(dur)
        } else {
            progress.progress = 0
            duration.text = ""
        }
        position.text = Ui.time(pos)
    }
}
