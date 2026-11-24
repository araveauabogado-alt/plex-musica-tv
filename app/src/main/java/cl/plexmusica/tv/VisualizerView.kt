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

    // Colores actuales (se acercan suavemente a los de la carátula nueva al cambiar de canción).
    private var color1 = ArtColors.DEFAULT.accent
    private var color2 = ArtColors.DEFAULT.accent2
    private var bg1 = ArtColors.DEFAULT.bg1
    private var bg2 = ArtColors.DEFAULT.bg2
    private var target = ArtColors.DEFAULT
    private var art: Bitmap? = null
    private val bgPaintGrad = Paint()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val matrix = Matrix()

    /** Carátula y colores de la canción actual (los mismos del fondo de Reproduciendo). */
    fun setArtwork(art: Bitmap?, scheme: ArtColors.Scheme) {
        this.art = art
        target = scheme
        invalidate()
    }

    private fun approach(from: Int, to: Int) = ColorUtils.blendARGB(from, to, 0.06f)

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

        // Fondo: el mismo degradado de dos colores de Reproduciendo.
        color1 = approach(color1, target.accent)
        color2 = approach(color2, target.accent2)
        bg1 = approach(bg1, target.bg1)
        bg2 = approach(bg2, target.bg2)
        bgPaintGrad.shader = LinearGradient(0f, 0f, w, h, ArtColors.gradient(bg1, bg2), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, bgPaintGrad)
        // leve pulso de luz con los graves
        canvas.drawColor(ColorUtils.setAlphaComponent(color1, (bass * 18).toInt().coerceIn(0, 18)))

        when (style) {
            Style.SPECTRUM -> drawSpectrum(canvas, w, h)
            Style.WAVE -> drawWave(canvas, w, h)
            Style.HALO -> drawHalo(canvas, w, h)
        }
        postInvalidateOnAnimation()
    }

    // ---------- Estilo LCD retro (display de radio de auto de fines de los 90) ----------
    // Los segmentos apagados ("fantasma") se pintan una sola vez en un bitmap; en cada cuadro
    // solo se dibujan los encendidos, así el costo es bajo para el Mi Box.

    private val ghostColor = 0x09FFFFFF
    private var ghost: Bitmap? = null
    private var ghostKey = ""
    private val ghostPaint = Paint()

    private fun drawGhost(canvas: Canvas, w: Int, h: Int, build: (Canvas) -> Unit) {
        val key = "$style-$w-$h"
        if (ghost == null || ghostKey != key) {
            ghost?.recycle()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            build(Canvas(bmp))
            ghost = bmp
            ghostKey = key
        }
        ghost?.let { canvas.drawBitmap(it, 0f, 0f, ghostPaint) }
    }

    override fun onDetachedFromWindow() {
        ghost?.recycle()
        ghost = null
        super.onDetachedFromWindow()
    }

    // Espectro: ecualizador de 32 barras de segmentos planos, graves a la izquierda, con reflejo.
    private val eqBars = 32
    private val eqSegs = 22
    private val eqReflect = 7

    private fun drawSpectrum(canvas: Canvas, w: Float, h: Float) {
        val areaW = w * 0.74f
        val left = (w - areaW) / 2f
        val slot = areaW / eqBars
        val bw = slot * 0.8f
        val base = h * 0.70f
        val maxH = h * 0.48f
        val sh = maxH / eqSegs
        val gap = sh * 0.38f
        paint.style = Paint.Style.FILL
        paint.shader = null

        drawGhost(canvas, w.toInt(), h.toInt()) { g ->
            paint.color = ghostColor
            for (i in 0 until eqBars) {
                val x = left + i * slot + (slot - bw) / 2f
                for (k in 0 until eqSegs) {
                    val y1 = base - k * sh
                    g.drawRect(x, y1 - (sh - gap), x + bw, y1, paint)
                }
            }
        }

        for (i in 0 until eqBars) {
            // cada barra toma el máximo de su grupo de bandas
            val b0 = i * bands / eqBars
            val b1 = ((i + 1) * bands / eqBars).coerceAtLeast(b0 + 1)
            var lv = 0f
            var pk = 0f
            for (b in b0 until b1) {
                lv = max(lv, level[b])
                pk = max(pk, peak[b])
            }
            val lit = (lv * eqSegs).toInt().coerceIn(0, eqSegs)
            val pkSeg = (pk * eqSegs).toInt().coerceIn(0, eqSegs - 1)
            val x = left + i * slot + (slot - bw) / 2f
            for (k in 0 until lit) {
                val y1 = base - k * sh
                paint.color = ColorUtils.blendARGB(color1, color2, k / (eqSegs - 1f))
                canvas.drawRect(x, y1 - (sh - gap), x + bw, y1, paint)
            }
            if (pkSeg >= lit) {
                val y1 = base - pkSeg * sh
                paint.color = 0xE6FFFFFF.toInt()
                canvas.drawRect(x, y1 - (sh - gap), x + bw, y1, paint)
            }
            // reflejo: los primeros segmentos encendidos, invertidos y cada vez más tenues
            for (k in 0 until minOf(lit, eqReflect)) {
                val fade = 1f - k.toFloat() / eqReflect
                val alpha = (70 * fade * sqrt(fade)).toInt()
                if (alpha < 4) continue
                val y0 = base + gap * 1.6f + k * sh
                paint.color = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(color1, color2, k / (eqSegs - 1f)), alpha)
                canvas.drawRect(x, y0, x + bw, y0 + (sh - gap), paint)
            }
        }
    }

    // Onda: la misma onda, dibujada en una matriz de bloques chicos.
    private fun drawWave(canvas: Canvas, w: Float, h: Float) {
        val px = 5f * resources.displayMetrics.density
        val gap = max(2f, px * 0.24f)
        val cols = (w * 0.86f / px).toInt()
        val rows = (h * 0.5f / px).toInt()
        val x0 = (w - cols * px) / 2f
        val y0 = h * 0.25f
        paint.style = Paint.Style.FILL
        paint.shader = null

        drawGhost(canvas, w.toInt(), h.toInt()) { g ->
            paint.color = 0x06FFFFFF
            for (c in 0 until cols) for (r in 0 until rows) {
                val x = x0 + c * px
                val y = y0 + r * px
                g.drawRect(x, y, x + px - gap, y + px - gap, paint)
            }
        }

        val mid = rows / 2f
        val ampRows = h * (0.18f + energy * 0.35f) / px
        val count = waveSmooth.size
        var prev = -1
        for (c in 0 until cols) {
            // mismo movimiento que antes, muestreado por columna
            val f = c.toFloat() / (cols - 1) * (count - 1)
            val i = f.toInt().coerceAtMost(count - 2)
            val v = waveSmooth[i] + (waveSmooth[i + 1] - waveSmooth[i]) * (f - i)
            val edge = sin(PI * c / (cols - 1)).toFloat()
            val row = (mid + v * 2.2f * ampRows * edge).toInt().coerceIn(0, rows - 1)
            if (prev < 0) prev = row
            // se rellena el tramo vertical entre columnas para que la línea quede continua
            val lo = minOf(prev, row)
            val hi = maxOf(prev, row)
            val x = x0 + c * px
            for (r in lo..hi) {
                val t = (kotlin.math.abs(r - mid) / mid * 1.4f).coerceAtMost(1f)
                paint.color = ColorUtils.blendARGB(color1, color2, t)
                val y = y0 + r * px
                canvas.drawRect(x, y, x + px - gap, y + px - gap, paint)
            }
            prev = row
        }
    }

    // Halo: carátula al centro que late con los graves; rayos hechos de segmentos.
    private val haloSegs = 9

    private fun drawHalo(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val base = min(w, h) * 0.2f
        val radius = base * (1f + bass * 0.08f)
        val rays = bands * 2
        val r0 = base * 1.14f
        val segL = base * 0.075f
        val segG = segL * 0.32f
        val segW = (2 * PI * r0 / rays * 0.62f).toFloat()
        paint.style = Paint.Style.FILL
        paint.shader = null

        drawGhost(canvas, w.toInt(), h.toInt()) { g ->
            paint.color = ghostColor
            for (i in 0 until rays) {
                g.save()
                g.rotate(360f * i / rays, cx, cy)
                for (k in 0 until haloSegs) {
                    val ra = r0 + k * segL
                    g.drawRect(cx - segW / 2f, cy - ra - (segL - segG), cx + segW / 2f, cy - ra, paint)
                }
                g.restore()
            }
        }

        for (i in 0 until rays) {
            val b = if (i < bands) i else rays - 1 - i
            val lit = (level[b] * haloSegs).toInt().coerceIn(0, haloSegs)
            if (lit == 0) continue
            canvas.save()
            canvas.rotate(360f * i / rays, cx, cy)
            for (k in 0 until lit) {
                val ra = r0 + k * segL
                paint.color = ColorUtils.blendARGB(color1, color2, k / (haloSegs - 1f))
                canvas.drawRect(cx - segW / 2f, cy - ra - (segL - segG), cx + segW / 2f, cy - ra, paint)
            }
            canvas.restore()
        }

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
