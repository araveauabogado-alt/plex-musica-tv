package cl.plexmusica.tv

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Copia el audio que está sonando (ya decodificado) para el visualizador.
 * Solo trabaja mientras el visualizador está abierto ([active]).
 *
 * El audio llega aquí un poco antes de salir por los parlantes (el reproductor
 * guarda un búfer), así que [window] entrega las muestras con [LATENCY_MS] de atraso
 * para que la imagen calce con lo que se escucha.
 */
@OptIn(UnstableApi::class)
object PcmTap : TeeAudioProcessor.AudioBufferSink {
    @Volatile
    var active = false

    private const val LATENCY_MS = 250
    private const val CAPACITY = 32768 // cuadros (mono, mezcla de ambos canales)
    private val ring = FloatArray(CAPACITY)
    private var writeFrame = 0
    private var filled = 0
    private var channels = 2
    private var sampleRate = 44100
    private var encoding = C.ENCODING_PCM_16BIT
    private var lastWriteNanos = 0L

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        synchronized(this) {
            channels = channelCount.coerceAtLeast(1)
            sampleRate = sampleRateHz.coerceAtLeast(8000)
            this.encoding = encoding
            filled = 0
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
                        var sum = 0f
                        for (c in 0 until ch) sum += b.short / 32768f
                        put(sum / ch)
                    }
                }
                C.ENCODING_PCM_FLOAT -> {
                    val frames = b.remaining() / (4 * ch)
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until ch) sum += b.float
                        put(sum / ch)
                    }
                }
                else -> return
            }
            lastWriteNanos = System.nanoTime()
        }
    }

    private fun put(v: Float) {
        ring[writeFrame] = v
        writeFrame = (writeFrame + 1) % CAPACITY
        if (filled < CAPACITY) filled++
    }

    /**
     * Llena [dest] con las últimas muestras (mono) ya atrasadas según la latencia.
     * Devuelve false si no hay audio reciente (pausa o silencio de la fuente).
     */
    fun window(dest: FloatArray): Boolean = synchronized(this) {
        if (System.nanoTime() - lastWriteNanos > 400_000_000L) return false
        val delay = (sampleRate * LATENCY_MS / 1000).coerceAtMost(CAPACITY - dest.size - 1)
        if (filled < dest.size + delay) return false
        var start = (writeFrame - delay - dest.size + CAPACITY * 2) % CAPACITY
        for (i in dest.indices) {
            dest[i] = ring[start]
            start = (start + 1) % CAPACITY
        }
        true
    }
}
