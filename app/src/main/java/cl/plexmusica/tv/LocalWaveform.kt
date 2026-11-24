package cl.plexmusica.tv

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Forma de onda calculada en la TV cuando Plex no tiene analizado el volumen.
 *
 * En vez de decodificar la canción entera (lento: había que bajarla completa), salta a
 * [POINTS] puntos repartidos en la canción y decodifica un fragmento corto en cada uno.
 * Los resultados se guardan en memoria y en disco, así que al repetir una canción la
 * onda aparece al instante.
 */
object LocalWaveform {
    private const val POINTS = 40
    private val memory = LruCache<String, FloatArray>(120)
    private var dir: File? = null

    fun init(context: Context) {
        dir = File(context.filesDir, "waves").apply { mkdirs() }
    }

    /** Onda ya calculada (memoria o disco), o null. */
    fun cached(key: String): FloatArray? {
        memory.get(key)?.let { return it }
        val f = dir?.let { File(it, "$key.bin") } ?: return null
        if (!f.exists()) return null
        return try {
            DataInputStream(f.inputStream().buffered()).use { inp ->
                FloatArray(inp.readInt()) { inp.readFloat() }
            }.also { memory.put(key, it) }
        } catch (e: Exception) {
            null
        }
    }

    fun store(key: String, levels: FloatArray) {
        memory.put(key, levels)
        val f = dir?.let { File(it, "$key.bin") } ?: return
        try {
            DataOutputStream(f.outputStream().buffered()).use { out ->
                out.writeInt(levels.size)
                levels.forEach { out.writeFloat(it) }
            }
        } catch (_: Exception) {
        }
    }

    suspend fun compute(key: String, url: String, durationMs: Long, bars: Int = 72): FloatArray? {
        cached(key)?.let { return it }
        val result = withContext(Dispatchers.IO) { sampled(url, durationMs) } ?: return null
        val levels = resample(result, bars)
        store(key, levels)
        return levels
    }

    private suspend fun sampled(url: String, durationMs: Long): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(url, emptyMap<String, String>())
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    format = f
                    break
                }
            }
            if (format == null) return null
            val durUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION)
            else durationMs * 1000
            if (durUs <= 0) return null

            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            val info = MediaCodec.BufferInfo()
            val values = FloatArray(POINTS)
            var goodSeeks = 0
            for (p in 0 until POINTS) {
                coroutineContext.ensureActive()
                val t = ((p + 0.5) / POINTS * durUs).toLong()
                extractor.seekTo(t, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                if (abs(extractor.sampleTime - t) < durUs / POINTS) goodSeeks++
                c.flush()

                var sum = 0.0
                var count = 0
                var decoded = 0
                var guard = 0
                var inputEnded = false
                while (decoded < 3 && guard < 80) {
                    guard++
                    if (!inputEnded) {
                        val ii = c.dequeueInputBuffer(5_000)
                        if (ii >= 0) {
                            val n = extractor.readSampleData(c.getInputBuffer(ii)!!, 0)
                            if (n < 0) {
                                c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                c.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val oi = c.dequeueOutputBuffer(info, 5_000)
                    if (oi >= 0) {
                        if (info.size > 0) {
                            val out = c.getOutputBuffer(oi)!!
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val s = out.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                            var i = 0
                            while (i < s.limit()) {
                                sum += abs(s.get(i).toInt())
                                count++
                                i += 8
                            }
                            decoded++
                        }
                        c.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
                values[p] = if (count > 0) (sum / count).toFloat() else 0f
            }
            // Si el archivo no permite saltar, todos los fragmentos serían el comienzo: no sirve.
            if (goodSeeks < POINTS / 2) return null
            return values
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    /** Lleva los puntos medidos a la cantidad de barras, suavizando y normalizando. */
    private fun resample(values: FloatArray, bars: Int): FloatArray {
        val max = values.maxOrNull() ?: 0f
        if (max <= 0f) return FloatArray(bars) { 0.08f }
        val out = FloatArray(bars)
        for (b in 0 until bars) {
            val x = b.toFloat() / (bars - 1) * (values.size - 1)
            val i = x.toInt().coerceAtMost(values.size - 2)
            val f = x - i
            val v = values[i] * (1 - f) + values[i + 1] * f
            out[b] = (v / max).coerceIn(0.08f, 1f)
        }
        return out
    }
}
