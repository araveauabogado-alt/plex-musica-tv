package cl.plexmusica.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Visualizadores livianos dibujados con Canvas (acelerado por la GPU), con los colores de la carátula.
 * Un análisis de frecuencias (FFT de 1024 muestras) por cuadro alimenta los tres estilos.
 */
class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Style { SPECTRUM, WAVE, HALO }

    var style = Style.SPECTRUM
        set(value) {
            field = value
            invalidate()
        }

    private val bands = 48
    private val level = FloatArray(bands)      // nivel suavizado por banda (0..1)
    private val peak = FloatArray(bands)       // "tapitas" que caen lento
    private var bass = 0f
    private var energy = 0f

    private val n = 1024
    private val samples = FloatArray(n)
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }
    private val wave = FloatArray(160)
    private val waveSmooth = FloatArray(160)
    private var runningMax = 1e-3f

    private var color1 = 0xFFE5A00D.toInt()
    private var color2 = 0xFFFF6A3D.toInt()
    private var background: Bitmap? = null
    private var art: Bitmap? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val matrix = Matrix()

    /** Colores y carátula de la canción actual. [bg] es una versión muy pequeña: al estirarla queda difuminada. */
    fun setArtwork(art: Bitmap?, bg: Bitmap?, c1: Int, c2: Int) {
        this.art = art
        background = bg
        color1 = c1
        color2 = c2
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postInvalidateOnAnimation()
    }

    // ---------- Análisis ----------

    private fun analyze() {
        val hasAudio = PcmTap.window(samples)
        if (!hasAudio) {
            for (b in 0 until bands) {
                level[b] *= 0.9f
                peak[b] = max(0f, peak[b] - 0.01f)
            }
            for (i in wave.indices) waveSmooth[i] *= 0.9f
            bass *= 0.9f
            energy *= 0.9f
            return
        }
        // Forma de onda (para el estilo "Onda")
        val step = n / wave.size
        for (i in wave.indices) {
            wave[i] = samples[i * step]
            waveSmooth[i] += (wave[i] - waveSmooth[i]) * 0.5f
        }
        // FFT
        for (i in 0 until n) {
            re[i] = samples[i] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        // Bandas logarítmicas de ~40 Hz a ~16 kHz (aprox. con 44,1 kHz)
        var frameMax = 0f
        val mags = FloatArray(bands)
        for (b in 0 until bands) {
            val lo = (2.0 * (256.0).pow(b.toDouble() / bands)).toInt().coerceIn(1, n / 2 - 1)
            val hi = (2.0 * (256.0).pow((b + 1).toDouble() / bands)).toInt().coerceIn(lo + 1, n / 2)
            var sum = 0f
            for (k in lo until hi) sum += hypot(re[k], im[k])
            val m = sum / (hi - lo)
            mags[b] = m
            if (m > frameMax) frameMax = m
        }
        runningMax = max(runningMax * 0.995f, frameMax)
        var e = 0f
        for (b in 0 until bands) {
            // Normalización adaptable + compresión suave para que no todo quede arriba o abajo.
            val target = sqrt((mags[b] / runningMax).coerceIn(0f, 1f)) * (0.75f + 0.5f * b / bands).coerceAtMost(1f)
            level[b] += (target - level[b]) * if (target > level[b]) 0.55f else 0.14f
            peak[b] = if (level[b] > peak[b]) level[b] else max(0f, peak[b] - 0.008f)
            e += level[b]
        }
        val newBass = (level[0] + level[1] + level[2] + level[3]) / 4f
        bass += (newBass - bass) * if (newBass > bass) 0.5f else 0.12f
        energy += (e / bands - energy) * 0.2f
    }

    // ---------- Dibujo ----------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        analyze()
        val w = width.toFloat()
        val h = height.toFloat()

        // Fondo: carátula muy ampliada (difuminada) y oscurecida.
        val bg = background
        if (bg != null) {
            rect.set(0f, 0f, w, h)
            canvas.drawBitmap(bg, null, rect, bgPaint)
            canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (150 - bass * 40).toInt().coerceIn(90, 170)))
        } else {
            canvas.drawColor(Color.BLACK)
        }

        when (style) {
            Style.SPECTRUM -> drawSpectrum(canvas, w, h)
            Style.WAVE -> drawWave(canvas, w, h)
            Style.HALO -> drawHalo(canvas, w, h)
        }
        postInvalidateOnAnimation()
    }

    /** Barras simétricas desde el centro, con tapitas que caen. */
    private fun drawSpectrum(canvas: Canvas, w: Float, h: Float) {
        val total = bands * 2
        val areaW = w * 0.78f
        val left = (w - areaW) / 2f
        val slot = areaW / total
        val bar = slot * 0.56f
        val base = h * 0.72f
        val maxH = h * 0.5f
        paint.shader = LinearGradient(0f, base - maxH, 0f, base, color2, color1, Shader.TileMode.CLAMP)
        for (i in 0 until total) {
            // espejo: graves al centro, agudos hacia los costados
            val b = if (i < bands) bands - 1 - i else i - bands
            val x = left + i * slot + (slot - bar) / 2f
            val bh = max(bar, level[b] * maxH)
            rect.set(x, base - bh, x + bar, base)
            canvas.drawRoundRect(rect, bar / 2f, bar / 2f, paint)
            // reflejo tenue bajo la línea base
            paint.alpha = 50
            rect.set(x, base + 6f, x + bar, base + 6f + bh * 0.25f)
            canvas.drawRoundRect(rect, bar / 2f, bar / 2f, paint)
            paint.alpha = 255
        }
        paint.shader = null
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, 200)
        for (i in 0 until total) {
            val b = if (i < bands) bands - 1 - i else i - bands
            val x = left + i * slot + (slot - bar) / 2f
            val y = base - max(bar, peak[b] * maxH) - bar * 1.6f
            canvas.drawCircle(x + bar / 2f, y, bar * 0.32f, paint)
        }
    }

    /** Tres líneas de onda superpuestas que respiran con el volumen. */
    private fun drawWave(canvas: Canvas, w: Float, h: Float) {
        val mid = h * 0.5f
        val amp = h * (0.18f + energy * 0.35f)
        val layers = arrayOf(
            Triple(color2, 1.0f, 0.35f),
            Triple(color1, 0.7f, 0.6f),
            Triple(Color.WHITE, 0.45f, 0.9f),
        )
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val t = System.nanoTime() / 1e9f
        for ((li, layer) in layers.withIndex()) {
            val (color, scale, alpha) = layer
            path.reset()
            val count = waveSmooth.size
            for (i in 0 until count) {
                val x = w * 0.06f + (w * 0.88f) * i / (count - 1)
                // ventana suave en los extremos + leve desfase por capa
                val edge = sin(PI * i / (count - 1)).toFloat()
                val drift = sin(t * (0.8f + li * 0.3f) + i * 0.08f) * 0.06f
                val y = mid + (waveSmooth[i] * 2.2f + drift * energy) * amp * scale * edge
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // brillo: trazo ancho y transparente, luego el fino
            paint.color = ColorUtils.setAlphaComponent(color, (alpha * 70).toInt())
            paint.strokeWidth = 14f
            canvas.drawPath(path, paint)
            paint.color = ColorUtils.setAlphaComponent(color, (alpha * 255).toInt())
            paint.strokeWidth = 4f
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
    }

    /** Carátula al centro que late con los graves, rodeada de un espectro circular. */
    private fun drawHalo(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) * 0.2f * (1f + bass * 0.08f)
        val rays = bands * 2
        paint.shader = SweepGradient(cx, cy, intArrayOf(color1, color2, color1), null)
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (2 * PI * radius / rays * 0.5f).toFloat()
        for (i in 0 until rays) {
            val b = if (i < bands) i else rays - 1 - i
            val angle = (2 * PI * i / rays - PI / 2).toFloat()
            val len = radius * (0.06f + level[b] * 0.7f)
            val r0 = radius * 1.12f
            val c = cos(angle)
            val s = sin(angle)
            canvas.drawLine(cx + c * r0, cy + s * r0, cx + c * (r0 + len), cy + s * (r0 + len), paint)
        }
        paint.shader = null
        paint.style = Paint.Style.FILL

        // anillo de brillo con los graves
        paint.color = ColorUtils.setAlphaComponent(color1, (40 + bass * 120).toInt().coerceAtMost(200))
        canvas.drawCircle(cx, cy, radius * 1.06f, paint)

        val a = art
        if (a != null) {
            val shader = BitmapShader(a, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = radius * 2f / min(a.width, a.height)
            matrix.reset()
            matrix.setScale(scale, scale)
            matrix.postTranslate(cx - a.width * scale / 2f, cy - a.height * scale / 2f)
            shader.setLocalMatrix(matrix)
            artPaint.shader = shader
            canvas.drawCircle(cx, cy, radius, artPaint)
        } else {
            paint.color = ColorUtils.blendARGB(color1, Color.BLACK, 0.5f)
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    // ---------- FFT radix-2 ----------

    private fun fft(re: FloatArray, im: FloatArray) {
        val size = re.size
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= size) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < size) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
