package cl.plexmusica.tv

import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.palette.graphics.Palette
import coil.imageLoader
import kotlin.math.abs
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Pantalla "Reproduciendo": carátula grande y controles. */
class NowPlayingActivity : AppCompatActivity() {
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastArt: Uri? = null

    private val defaultBg = 0xFF121212.toInt()
    private val defaultAccent = 0xFFE5A00D.toInt()
    private var currentBg = defaultBg
    private var currentBg2 = 0xFF0A0A0A.toInt()
    private var currentAccent = defaultAccent
    private var colorAnimator: ValueAnimator? = null
    private val gradient = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(defaultBg, Color.BLACK),
    )

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
    private lateinit var waveform: WaveformView
    private lateinit var seekHint: TextView
    private var waveKey: String? = null
    private var waveJob: Job? = null

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 250)
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
        findViewById<View>(R.id.root).background = gradient
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
        waveform = findViewById(R.id.waveform)
        seekHint = findViewById(R.id.seekHint)
        val seekArea = findViewById<View>(R.id.seekArea)
        seekArea.setOnFocusChangeListener { _, has ->
            seekHint.visibility = if (has) View.VISIBLE else View.INVISIBLE
        }
        seekArea.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-stepFor(event)); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(stepFor(event)); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { togglePlay(); true }
                else -> false
            }
        }
        findViewById<ImageButton>(R.id.btnVisualizer).setOnClickListener {
            startActivity(Intent(this, VisualizerActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnQueue).setOnClickListener {
            startActivity(Intent(this, QueueActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { controller?.seekToPrevious() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { controller?.seekToNext() }
        btnPlay.setOnClickListener { togglePlay() }
        btnShuffle.setOnClickListener {
            controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
        }
        btnPlay.requestFocus()
        setColors(defaultBg, currentBg2, defaultAccent)
    }

    override fun onDestroy() {
        colorAnimator?.cancel()
        super.onDestroy()
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

    /** 10 s por toque; si se mantiene apretado, avanza cada vez más rápido (hasta 60 s). */
    private fun stepFor(event: KeyEvent): Long =
        (10_000L * (1 + event.repeatCount / 4)).coerceAtMost(60_000L)

    private fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val dur = c.duration
        var target = (c.currentPosition + deltaMs).coerceAtLeast(0)
        if (dur > 0 && dur != C.TIME_UNSET) target = target.coerceAtMost(dur - 1000)
        c.seekTo(target)
        updateProgress()
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
        val item = c.currentMediaItem
        if (item?.mediaId != waveKey) {
            waveKey = item?.mediaId
            loadWaveform(item)
        }
        if (md.artworkUri != lastArt || lastArt == null) {
            lastArt = md.artworkUri
            applyArtwork(md.artworkUri)
        }
        btnPlay.setImageResource(if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) R.drawable.ic_pause else R.drawable.ic_play)
        btnShuffle.alpha = if (c.shuffleModeEnabled) 1f else 0.45f
        waveform.playing = c.isPlaying

        val next = c.nextMediaItemIndex
        nextUp.text = if (next != C.INDEX_UNSET) {
            val n = c.getMediaItemAt(next).mediaMetadata
            getString(R.string.next_up, n.title ?: "", n.artist ?: "")
        } else ""
        updateProgress()
    }

    // ---------- Forma de onda ----------

    private fun loadWaveform(item: MediaItem?) {
        waveJob?.cancel()
        if (item == null || item.mediaId.isEmpty()) {
            waveform.visibility = View.INVISIBLE
            return
        }
        val key = item.mediaId
        waveJob = lifecycleScope.launch {
            val ready = LocalWaveform.cached(key)
            if (ready != null) {
                showWave(ready)
            } else {
                waveform.showLoading()
                waveform.visibility = View.VISIBLE
                val levels = levelsFor(item)
                if (waveKey == key) {
                    if (levels != null) showWave(levels) else waveform.visibility = View.INVISIBLE
                }
            }
            // Mientras suena esta canción, se prepara la onda de la siguiente.
            val c = controller ?: return@launch
            val next = c.nextMediaItemIndex
            if (next != C.INDEX_UNSET) {
                val nextItem = c.getMediaItemAt(next)
                if (LocalWaveform.cached(nextItem.mediaId) == null) levelsFor(nextItem)
            }
        }
    }

    /** Niveles de Plex si los tiene analizados; si no, calculados en la TV. Quedan guardados. */
    private suspend fun levelsFor(item: MediaItem): FloatArray? {
        val key = item.mediaId
        LocalWaveform.cached(key)?.let { return it }
        val fromPlex = try {
            Plex.waveform(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (fromPlex != null) {
            LocalWaveform.store(key, fromPlex)
            return fromPlex
        }
        val url = (item.requestMetadata.mediaUri ?: item.localConfiguration?.uri)?.toString() ?: return null
        val dur = item.mediaMetadata.extras?.getLong("duration") ?: 0L
        return LocalWaveform.compute(key, url, dur)
    }

    private fun showWave(levels: FloatArray) {
        waveform.setLevels(levels)
        waveform.visibility = View.VISIBLE
    }

    // ---------- Fondo según los colores de la carátula ----------

    private fun applyArtwork(uri: Uri?) {
        if (uri == null) {
            cover.setImageResource(R.drawable.ic_music)
            animateColors(defaultBg, 0xFF0A0A0A.toInt(), defaultAccent)
            return
        }
        val request = ImageRequest.Builder(this)
            .data(uri)
            .allowHardware(false) // Palette necesita leer los píxeles
            .target(
                onError = {
                    cover.setImageResource(R.drawable.ic_music)
                    animateColors(defaultBg, 0xFF0A0A0A.toInt(), defaultAccent)
                },
                onSuccess = { drawable ->
                    cover.setImageDrawable(drawable)
                    val bmp = (drawable as? BitmapDrawable)?.bitmap
                    if (bmp == null) {
                        animateColors(defaultBg, 0xFF0A0A0A.toInt(), defaultAccent)
                    } else {
                        Palette.from(bmp).generate { p ->
                            if (p == null) return@generate
                            val base = p.getDarkVibrantColor(p.getDarkMutedColor(p.getDominantColor(defaultBg)))
                            val second = secondColor(p, base)
                            val accent = p.getLightVibrantColor(p.getVibrantColor(p.getLightMutedColor(defaultAccent)))
                            animateColors(readableBg(base), readableBg(second), readableAccent(accent))
                        }
                    }
                },
            )
            .build()
        imageLoader.enqueue(request)
    }

    /**
     * Segundo color del degradado: el tono de la carátula más distinto al principal
     * (se ignoran grises). Si todos son parecidos, usa el tono apagado de la carátula.
     */
    private fun secondColor(p: Palette, base: Int): Int {
        val hslBase = FloatArray(3)
        ColorUtils.colorToHSL(base, hslBase)
        val total = p.swatches.sumOf { it.population }.coerceAtLeast(1)
        var best: Int? = null
        var bestScore = 0f
        for (sw in p.swatches) {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(sw.rgb, hsl)
            if (hsl[1] < 0.15f || hsl[2] < 0.06f) continue // grises y casi negros no aportan
            val dh = abs(hsl[0] - hslBase[0]).let { if (it > 180f) 360f - it else it }
            if (dh < 25f) continue
            val score = dh * hsl[1] * (0.4f + sw.population.toFloat() / total)
            if (score > bestScore) {
                bestScore = score
                best = sw.rgb
            }
        }
        return best ?: p.getMutedColor(p.getDarkMutedColor(ColorUtils.blendARGB(base, Color.BLACK, 0.5f)))
    }

    /** Oscurece el color hasta que el texto blanco se lea bien. */
    private fun readableBg(color: Int): Int {
        var c = color
        var i = 0
        while (ColorUtils.calculateLuminance(c) > 0.12 && i < 10) {
            c = ColorUtils.blendARGB(c, Color.BLACK, 0.2f)
            i++
        }
        return c
    }

    /** Aclara el acento para que destaque sobre el fondo oscuro. */
    private fun readableAccent(color: Int): Int {
        var c = color
        var i = 0
        while (ColorUtils.calculateLuminance(c) < 0.35 && i < 10) {
            c = ColorUtils.blendARGB(c, Color.WHITE, 0.2f)
            i++
        }
        return c
    }

    private fun animateColors(toBg: Int, toBg2: Int, toAccent: Int) {
        val fromBg = currentBg
        val fromBg2 = currentBg2
        val fromAccent = currentAccent
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                setColors(
                    ColorUtils.blendARGB(fromBg, toBg, f),
                    ColorUtils.blendARGB(fromBg2, toBg2, f),
                    ColorUtils.blendARGB(fromAccent, toAccent, f),
                )
            }
            start()
        }
    }

    private fun setColors(bg: Int, bg2: Int, accent: Int) {
        currentBg = bg
        currentBg2 = bg2
        currentAccent = accent
        // Diagonal: color principal arriba a la izquierda, segundo color abajo a la derecha.
        gradient.setColors(intArrayOf(bg, ColorUtils.blendARGB(bg, bg2, 0.6f), ColorUtils.blendARGB(bg2, Color.BLACK, 0.35f)))
        artist.setTextColor(accent)
        progress.progressTintList = ColorStateList.valueOf(accent)
        waveform.playedColor = accent
    }

    private fun updateProgress() {
        val c = controller ?: return
        val dur = c.duration
        val pos = c.currentPosition
        if (dur > 0 && dur != C.TIME_UNSET) {
            progress.max = 1000
            progress.progress = ((pos * 1000) / dur).toInt()
            waveform.progress = pos.toFloat() / dur
            duration.text = Ui.time(dur)
        } else {
            progress.progress = 0
            waveform.progress = 0f
            duration.text = ""
        }
        position.text = Ui.time(pos)
    }
}
