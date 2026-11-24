package cl.plexmusica.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max

/** Imagen siempre cuadrada: el alto se iguala al ancho. */
class SquareImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredWidth)
    }
}

/**
 * Forma de onda minimalista y animada:
 * - las barras "crecen" de izquierda a derecha al cargar,
 * - el avance se desliza suave entre actualizaciones,
 * - mientras suena, las barras alrededor del punto de reproducción laten levemente.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var levels = FloatArray(0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var appear = 1f          // 0 → 1 durante la animación de entrada
    private var appearStart = 0L
    private var shown = 0f           // avance dibujado (persigue a `progress`)
    private var lastFrame = 0L

    /** Avance real 0..1; el dibujo se acerca suavemente a este valor. */
    var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            // Salto grande (cambio de canción o adelantar mucho): se acompaña más rápido.
            invalidate()
        }

    var playing = false
        set(value) {
            field = value
            invalidate()
        }

    var playedColor = 0xFFE5A00D.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val restColor = 0x33FFFFFF

    private var loading = false

    /** Estado de espera: una línea de puntos con un brillo que la recorre. */
    fun showLoading() {
        levels = FloatArray(0)
        loading = true
        invalidate()
    }

    fun setLevels(values: FloatArray) {
        loading = false
        levels = values
        appear = 0f
        appearStart = 0L
        shown = progress
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (loading) {
            drawLoading(canvas)
            return
        }
        val n = levels.size
        if (n == 0) return
        val now = System.nanoTime() / 1_000_000L
        val dt = if (lastFrame == 0L) 16L else (now - lastFrame).coerceIn(1L, 100L)
        lastFrame = now

        // Animación de entrada (700 ms).
        if (appear < 1f) {
            if (appearStart == 0L) appearStart = now
            appear = ((now - appearStart) / 700f).coerceIn(0f, 1f)
        }
        // Avance suave hacia el valor real.
        val diff = progress - shown
        shown += diff * (1f - Math.exp(-dt / 120.0).toFloat())
        if (kotlin.math.abs(diff) < 0.0005f) shown = progress

        val w = width.toFloat()
        val h = height.toFloat()
        val slot = w / n
        val bar = max(2f, slot * 0.42f)
        val radius = bar / 2f
        val t = now / 1000f
        for (i in 0 until n) {
            val pos = (i + 0.5f) / n
            // entrada escalonada: cada barra arranca un poco después que la anterior
            val local = ((appear * 1.6f) - (i.toFloat() / n) * 0.6f).coerceIn(0f, 1f)
            val ease = 1f - (1f - local) * (1f - local) * (1f - local)
            var level = levels[i] * ease
            // latido suave cerca del punto de reproducción
            if (playing) {
                val d = kotlin.math.abs(pos - shown) * n
                if (d < 4f) {
                    val k = 1f - d / 4f
                    level *= 1f + 0.18f * k * (0.5f + 0.5f * kotlin.math.sin(t * 6.5f + i * 0.9f))
                }
            }
            val bh = max(bar, level.coerceAtMost(1f) * h)
            val x = i * slot + (slot - bar) / 2f
            val top = (h - bh) / 2f
            paint.color = if (pos <= shown) playedColor else restColor
            rect.set(x, top, x + bar, top + bh)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        if (appear < 1f || playing || shown != progress) postInvalidateOnAnimation()
    }

    private fun drawLoading(canvas: Canvas) {
        val n = 72
        val w = width.toFloat()
        val h = height.toFloat()
        val slot = w / n
        val bar = max(2f, slot * 0.42f)
        val t = (System.nanoTime() / 1_000_000L) / 1000f
        val head = (t * 0.6f) % 1.4f - 0.2f // posición del brillo, recorre de izquierda a derecha
        for (i in 0 until n) {
            val pos = (i + 0.5f) / n
            val glow = (1f - kotlin.math.abs(pos - head) / 0.12f).coerceIn(0f, 1f)
            val bh = bar + glow * h * 0.22f
            val alpha = (0x33 + (0x99 - 0x33) * glow).toInt()
            paint.color = (alpha shl 24) or (playedColor and 0x00FFFFFF)
            val x = i * slot + (slot - bar) / 2f
            val top = (h - bh) / 2f
            rect.set(x, top, x + bar, top + bh)
            canvas.drawRoundRect(rect, bar / 2f, bar / 2f, paint)
        }
        postInvalidateOnAnimation()
    }
}
