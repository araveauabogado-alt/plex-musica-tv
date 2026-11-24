package cl.plexmusica.tv

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Calcula la forma de onda en la propia TV decodificando el audio, para cuando Plex
 * no tiene analizado el volumen de la pista. Se guarda en memoria para no repetirlo.
 */
object LocalWaveform {
    private val cache = LruCache<String, FloatArray>(60)

    fun cached(key: String): FloatArray? = cache.get(key)

    suspend fun compute(key: String, url: String, durationMs: Long, bars: Int = 72): FloatArray? =
        withContext(Dispatchers.Default) {
            cache.get(key)?.let { return@withContext it }
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(url, emptyMap<String, String>())
                var track = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        track = i
                        format = f
                        break
                    }
                }
                if (track < 0 || format == null) return@withContext null
                extractor.selectTrack(track)
                val durUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION)
                else durationMs * 1000
                if (durUs <= 0) return@withContext null

                val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
                codec = c
                c.configure(format, null, null, 0)
                c.start()

                val sums = DoubleArray(bars)
                val counts = IntArray(bars)
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    ensureActive()
                    if (!inputDone) {
                        val ii = c.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val buf = c.getInputBuffer(ii)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0) {
                                c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                c.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val oi = c.dequeueOutputBuffer(info, 10_000)
                    if (oi >= 0) {
                        if (info.size > 0) {
                            val out = c.getOutputBuffer(oi)!!
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val samples = out.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                            val b = ((info.presentationTimeUs.toDouble() / durUs) * bars).toInt().coerceIn(0, bars - 1)
                            var i = 0
                            val total = samples.limit()
                            while (i < total) {
                                sums[b] += abs(samples.get(i).toInt()).toDouble()
                                counts[b]++
                                i += 16 // basta una muestra de cada 16 para el dibujo
                            }
                        }
                        c.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }

                val avg = FloatArray(bars) { if (counts[it] > 0) (sums[it] / counts[it]).toFloat() else 0f }
                val max = avg.maxOrNull() ?: 0f
                if (max <= 0f) return@withContext null
                val result = FloatArray(bars) { (avg[it] / max).coerceIn(0.08f, 1f) }
                cache.put(key, result)
                result
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            } finally {
                try { codec?.stop() } catch (_: Exception) {}
                try { codec?.release() } catch (_: Exception) {}
                extractor.release()
            }
        }
}
