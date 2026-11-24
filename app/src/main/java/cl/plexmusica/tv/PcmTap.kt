package cl.plexmusica.tv

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Copia el audio que está sonando (ya decodificado) para el visualizador.
 * Solo trabaja mientras el visualizador está abierto ([active]); si no, no hace nada.
 * Guarda las últimas muestras en un anillo estéreo de floats.
 */
@OptIn(UnstableApi::class)
object PcmTap : TeeAudioProcessor.AudioBufferSink {
    @Volatile
    var active = false

    private const val CAPACITY = 8192 // cuadros estéreo
    private val ring = FloatArray(CAPACITY * 2)
    private var writeFrame = 0
    private var available = 0
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        synchronized(this) {
            channels = channelCount.coerceAtLeast(1)
            this.encoding = encoding
            available = 0
        }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!active) return
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        synchronized(this) {
            val ch = channels
            when (encoding) {
                C.ENCODING_PCM_16BIT -> {
                    val frames = b.remaining() / (2 * ch)
                    for (f in 0 until frames) {
                        val l = b.short / 32768f
                        val r = if (ch > 1) b.short / 32768f else l
                        for (extra in 2 until ch) b.short
                        put(l, r)
                    }
                }
                C.ENCODING_PCM_FLOAT -> {
                    val frames = b.remaining() / (4 * ch)
                    for (f in 0 until frames) {
                        val l = b.float
                        val r = if (ch > 1) b.float else l
                        for (extra in 2 until ch) b.float
                        put(l, r)
                    }
                }
                else -> return
            }
        }
    }

    private fun put(l: Float, r: Float) {
        ring[writeFrame * 2] = l
        ring[writeFrame * 2 + 1] = r
        writeFrame = (writeFrame + 1) % CAPACITY
        if (available < CAPACITY) available++
    }

    /** Copia a [dest] (estéreo intercalado) las muestras nuevas más recientes; devuelve cuántos cuadros. */
    fun read(dest: FloatArray): Int = synchronized(this) {
        val max = dest.size / 2
        val n = minOf(available, max)
        var start = (writeFrame - n + CAPACITY) % CAPACITY
        for (i in 0 until n) {
            dest[i * 2] = ring[start * 2]
            dest[i * 2 + 1] = ring[start * 2 + 1]
            start = (start + 1) % CAPACITY
        }
        available = 0
        n
    }
}
