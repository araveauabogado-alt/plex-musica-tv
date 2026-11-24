package cl.plexmusica.tv

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Funciones nativas de projectM (ver app/src/main/cpp/visualizer.cpp). */
object VisualizerNative {
    init {
        System.loadLibrary("visualizer")
    }

    @JvmStatic external fun create(width: Int, height: Int, meshW: Int, meshH: Int, fps: Int): Long
    @JvmStatic external fun resize(handle: Long, width: Int, height: Int)
    @JvmStatic external fun loadPreset(handle: Long, data: String, smooth: Boolean)
    @JvmStatic external fun addPcm(handle: Long, samples: FloatArray, frames: Int)
    @JvmStatic external fun render(handle: Long)
    @JvmStatic external fun destroy(handle: Long)
}

/**
 * Visualizador estilo MilkDrop a pantalla completa.
 * Rendimiento antes que calidad: se dibuja a 960×540 (la TV lo escala), malla 32×24 y 30 fps.
 *
 * Control: ◀ ▶ cambia de preset, OK fija o suelta el preset actual, ▲ muestra la información,
 * Atrás vuelve. Si no se fija, cambia solo cada [AUTO_SECONDS] segundos.
 */
class VisualizerActivity : AppCompatActivity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var info: TextView
    private lateinit var title: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var presets: List<String> = emptyList()
    private var index = 0
    private var locked = false
    private var controller: MediaController? = null

    @Volatile private var handle = 0L
    private var pendingPreset: String? = null

    private val renderer = object : GLSurfaceView.Renderer {
        private val pcm = FloatArray(2048 * 2)

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            handle = 0L // contexto nuevo: la instancia anterior ya no sirve
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            if (handle == 0L) {
                handle = VisualizerNative.create(width, height, MESH_W, MESH_H, FPS)
                pendingPreset = currentPresetText() // al crear, cargar el preset actual
            } else {
                VisualizerNative.resize(handle, width, height)
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            val h = handle
            if (h == 0L) return
            pendingPreset?.let {
                VisualizerNative.loadPreset(h, it, false)
                pendingPreset = null
            }
            val frames = PcmTap.read(pcm)
            if (frames > 0) VisualizerNative.addPcm(h, pcm, frames)
            VisualizerNative.render(h)
        }
    }

    // Limita a 30 fps pidiendo un cuadro cada dos refrescos de pantalla.
    private var frameToggle = false
    private var running = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            frameToggle = !frameToggle
            if (frameToggle) glView.requestRender()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val autoNext = object : Runnable {
        override fun run() {
            if (!locked) showPreset(index + 1, smooth = true, announce = false)
            handler.postDelayed(this, AUTO_SECONDS * 1000L)
        }
    }

    private val hideInfo = Runnable {
        info.animate().alpha(0f).setDuration(600).start()
        title.animate().alpha(0f).setDuration(600).start()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                updateTitle()
                flashInfo()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visualizer)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        info = findViewById(R.id.presetInfo)
        title = findViewById(R.id.songInfo)

        presets = (assets.list("presets") ?: emptyArray()).filter { it.endsWith(".milk") }.sorted()
        val prefs = getSharedPreferences("visualizer", Context.MODE_PRIVATE)
        index = prefs.getInt("index", 0).coerceIn(0, (presets.size - 1).coerceAtLeast(0))

        glView = findViewById(R.id.gl)
        glView.setEGLContextClientVersion(3)
        glView.setEGLConfigChooser(8, 8, 8, 0, 0, 0)
        glView.preserveEGLContextOnPause = false
        // Se dibuja a resolución reducida y el hardware lo escala a la pantalla completa.
        glView.holder.setFixedSize(RENDER_W, RENDER_H)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        updateInfo()
    }

    override fun onStart() {
        super.onStart()
        PlayerConnection.get(this) { c ->
            controller = c
            c.addListener(listener)
            updateTitle()
        }
        flashInfo()
    }

    override fun onResume() {
        super.onResume()
        PcmTap.active = true
        glView.onResume()
        running = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
        handler.removeCallbacks(autoNext)
        handler.postDelayed(autoNext, AUTO_SECONDS * 1000L)
    }

    override fun onPause() {
        running = false
        handler.removeCallbacks(autoNext)
        PcmTap.active = false
        val h = handle
        if (h != 0L) {
            handle = 0L
            glView.queueEvent { VisualizerNative.destroy(h) }
        }
        glView.onPause()
        getSharedPreferences("visualizer", Context.MODE_PRIVATE).edit().putInt("index", index).apply()
        super.onPause()
    }

    override fun onStop() {
        controller?.removeListener(listener)
        controller = null
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> showPreset(index + 1, smooth = false, announce = true)
            KeyEvent.KEYCODE_DPAD_LEFT -> showPreset(index - 1, smooth = false, announce = true)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                locked = !locked
                updateInfo()
                flashInfo()
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_INFO -> flashInfo()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun currentPresetText(): String? {
        val name = presets.getOrNull(index) ?: return null
        return try {
            assets.open("presets/$name").bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun showPreset(newIndex: Int, smooth: Boolean, announce: Boolean) {
        if (presets.isEmpty()) return
        index = (newIndex + presets.size) % presets.size
        val text = currentPresetText() ?: return
        glView.queueEvent {
            if (handle != 0L) VisualizerNative.loadPreset(handle, text, smooth)
        }
        updateInfo()
        if (announce) flashInfo()
        // Al cambiar a mano, el cambio automático vuelve a contar desde cero.
        handler.removeCallbacks(autoNext)
        handler.postDelayed(autoNext, AUTO_SECONDS * 1000L)
    }

    private fun updateInfo() {
        val name = presets.getOrNull(index)?.removeSuffix(".milk")?.substringAfter(" - ") ?: ""
        val state = getString(if (locked) R.string.vis_locked else R.string.vis_auto)
        info.text = getString(R.string.vis_info, index + 1, presets.size, name, state)
    }

    private fun updateTitle() {
        val md = controller?.mediaMetadata
        title.text = if (md?.title != null) "${md.title} — ${md.artist ?: ""}" else ""
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

    companion object {
        private const val RENDER_W = 960
        private const val RENDER_H = 540
        private const val MESH_W = 32
        private const val MESH_H = 24
        private const val FPS = 30
        private const val AUTO_SECONDS = 30
    }
}
