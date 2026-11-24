package cl.plexmusica.tv

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlin.math.abs

/**
 * Colores sacados de la carátula, compartidos por Reproduciendo y el visualizador
 * para que ambas pantallas tengan exactamente el mismo fondo.
 */
object ArtColors {
    const val DEFAULT_BG = 0xFF121212.toInt()
    const val DEFAULT_BG2 = 0xFF0A0A0A.toInt()
    const val DEFAULT_ACCENT = 0xFFE5A00D.toInt()

    /** bg1/bg2: degradado del fondo (oscuros). accent/accent2: colores vivos para texto y gráficos. */
    data class Scheme(val bg1: Int, val bg2: Int, val accent: Int, val accent2: Int)

    val DEFAULT = Scheme(DEFAULT_BG, DEFAULT_BG2, DEFAULT_ACCENT, 0xFFFF6A3D.toInt())

    fun from(p: Palette): Scheme {
        val base = p.getDarkVibrantColor(p.getDarkMutedColor(p.getDominantColor(DEFAULT_BG)))
        val second = secondColor(p, base)
        val accent = p.getLightVibrantColor(p.getVibrantColor(p.getLightMutedColor(DEFAULT_ACCENT)))
        var accent2 = readableAccent(second)
        if (accent2 == readableAccent(accent)) accent2 = readableAccent(p.getLightMutedColor(0xFFFF6A3D.toInt()))
        return Scheme(readableBg(base), readableBg(second), readableAccent(accent), accent2)
    }

    /** Degradado diagonal: color principal arriba a la izquierda, segundo color abajo a la derecha. */
    fun gradient(bg1: Int, bg2: Int): IntArray =
        intArrayOf(bg1, ColorUtils.blendARGB(bg1, bg2, 0.6f), ColorUtils.blendARGB(bg2, Color.BLACK, 0.35f))

    /**
     * Segundo color del degradado: el tono de la carátula más distinto al principal
     * (se ignoran grises). Si todos son parecidos, usa el tono apagado de la carátula.
     */
    fun secondColor(p: Palette, base: Int): Int {
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
    fun readableBg(color: Int): Int {
        var c = color
        var i = 0
        while (ColorUtils.calculateLuminance(c) > 0.12 && i < 10) {
            c = ColorUtils.blendARGB(c, Color.BLACK, 0.2f)
            i++
        }
        return c
    }

    /** Aclara el acento para que destaque sobre el fondo oscuro. */
    fun readableAccent(color: Int): Int {
        var c = color
        var i = 0
        while (ColorUtils.calculateLuminance(c) < 0.35 && i < 10) {
            c = ColorUtils.blendARGB(c, Color.WHITE, 0.2f)
            i++
        }
        return c
    }
}
