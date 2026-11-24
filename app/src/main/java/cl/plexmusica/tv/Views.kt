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

/** Forma de onda de la canción: barras redondeadas; la parte ya escuchada va en color de acento. */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var levels = FloatArray(0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    var progress = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (v != field) {
                field = v
                invalidate()
            }
        }

    var playedColor = 0xFFE5A00D.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val restColor = 0x40FFFFFF

    fun setLevels(values: FloatArray) {
        levels = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = levels.size
        if (n == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val slot = w / n
        val bar = max(1.5f, slot * 0.55f)
        val radius = bar / 2f
        for (i in 0 until n) {
            val x = i * slot + (slot - bar) / 2f
            val bh = max(bar, levels[i] * h)
            val top = (h - bh) / 2f
            paint.color = if ((i + 0.5f) / n <= progress) playedColor else restColor
            rect.set(x, top, x + bar, top + bh)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}
