package cl.plexmusica.tv

import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Un elemento de la biblioteca: artista, álbum, playlist o canción. */
data class PlexItem(
    val ratingKey: String,
    val key: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val thumb: String?,
    val partKey: String?,
    val duration: Long,
    val album: String,
    val artist: String,
)

data class PlexServer(
    val id: String,
    val name: String,
    val token: String,
    val owned: Boolean,
    val candidates: List<String>,
)

data class PlexPin(val id: Long, val code: String)

fun JSONObject.str(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null

object Plex {
    private const val PRODUCT = "Plex Musica TV"
    lateinit var prefs: Prefs

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Cliente rápido para probar qué dirección del servidor responde.
    private val quick = http.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun request(url: String, token: String?): Request.Builder {
        val b = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("X-Plex-Product", PRODUCT)
            .header("X-Plex-Version", "1.0")
            .header("X-Plex-Client-Identifier", prefs.clientId)
            .header("X-Plex-Platform", "Android")
            .header("X-Plex-Device", "Android TV")
            .header("X-Plex-Device-Name", Build.MODEL ?: "Android TV")
        if (!token.isNullOrEmpty()) b.header("X-Plex-Token", token)
        return b
    }

    private suspend fun fetch(client: OkHttpClient, req: Request): String = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: ""
        }
    }

    // ---------- Vinculación con plex.tv/link ----------

    suspend fun createPin(): PlexPin {
        val req = request("https://plex.tv/api/v2/pins?strong=false", null)
            .post(FormBody.Builder().build())
            .build()
        val o = JSONObject(fetch(http, req))
        return PlexPin(o.getLong("id"), o.getString("code"))
    }

    suspend fun checkPin(id: Long): String? {
        val o = JSONObject(fetch(http, request("https://plex.tv/api/v2/pins/$id", null).build()))
        return o.str("authToken")?.takeIf { it.isNotEmpty() }
    }

    // ---------- Servidores ----------

    suspend fun servers(): List<PlexServer> {
        val url = "https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1"
        val arr = JSONArray(fetch(http, request(url, prefs.userToken).build()))
        val out = mutableListOf<PlexServer>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (!(r.str("provides") ?: "").contains("server")) continue
            val conns = r.optJSONArray("connections") ?: JSONArray()
            val local = mutableListOf<String>()
            val remote = mutableListOf<String>()
            val relay = mutableListOf<String>()
            for (j in 0 until conns.length()) {
                val c = conns.getJSONObject(j)
                val uri = c.str("uri") ?: continue
                when {
                    c.optBoolean("relay") -> relay += uri
                    c.optBoolean("local") -> {
                        local += uri
                        // Alternativa por IP directa, por si el router bloquea los dominios plex.direct.
                        val addr = c.str("address")
                        val port = c.optInt("port", 32400)
                        if (addr != null) {
                            val host = if (addr.contains(":")) "[$addr]" else addr
                            local += "http://$host:$port"
                        }
                    }
                    else -> remote += uri
                }
            }
            out += PlexServer(
                id = r.getString("clientIdentifier"),
                name = r.str("name") ?: "Servidor",
                token = r.str("accessToken") ?: prefs.userToken ?: "",
                owned = r.optBoolean("owned"),
                candidates = (local + remote + relay).distinct(),
            )
        }
        return out
    }

    /** Prueba las direcciones del servidor (local primero) y devuelve la primera que responde. */
    suspend fun findConnection(server: PlexServer): String? {
        for (uri in server.candidates) {
            try {
                fetch(quick, request("$uri/identity", server.token).build())
                return uri
            } catch (e: Exception) {
                // probar la siguiente
            }
        }
        return null
    }

    // ---------- Biblioteca ----------

    private suspend fun container(path: String): JSONObject {
        val req = request((prefs.serverUri ?: "") + path, prefs.serverToken)
            .header("X-Plex-Container-Start", "0")
            .header("X-Plex-Container-Size", "10000")
            .build()
        return JSONObject(fetch(http, req)).getJSONObject("MediaContainer")
    }

    /** Bibliotecas de música: lista de (id, nombre). */
    suspend fun musicSections(): List<Pair<String, String>> {
        val dirs = container("/library/sections").optJSONArray("Directory") ?: return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until dirs.length()) {
            val d = dirs.getJSONObject(i)
            if (d.str("type") == "artist") out += (d.str("key") ?: continue) to (d.str("title") ?: "Música")
        }
        return out
    }

    suspend fun items(path: String): List<PlexItem> {
        val arr = container(path).optJSONArray("Metadata") ?: return emptyList()
        return (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
    }

    suspend fun search(query: String, sectionId: String?): List<PlexItem> {
        val path = "/hubs/search?query=${Uri.encode(query)}&limit=30" +
            (sectionId?.let { "&sectionId=$it" } ?: "")
        val hubs = container(path).optJSONArray("Hub") ?: return emptyList()
        val byType = mutableMapOf<String, MutableList<PlexItem>>()
        for (i in 0 until hubs.length()) {
            val hub = hubs.getJSONObject(i)
            val type = hub.str("type") ?: continue
            if (type !in listOf("artist", "album", "track", "playlist")) continue
            val md = hub.optJSONArray("Metadata") ?: continue
            for (j in 0 until md.length()) byType.getOrPut(type) { mutableListOf() } += parse(md.getJSONObject(j))
        }
        return listOf("artist", "album", "playlist", "track").flatMap { byType[it] ?: emptyList() }
    }

    private fun parse(o: JSONObject): PlexItem {
        val type = o.str("type") ?: ""
        val artist = when (type) {
            "track" -> o.str("originalTitle") ?: o.str("grandparentTitle")
            "album" -> o.str("parentTitle")
            else -> null
        } ?: ""
        val subtitle = when (type) {
            "album" -> listOfNotNull(artist.ifEmpty { null }, o.str("year")).joinToString(" · ")
            "track" -> artist
            "playlist" -> "${o.optInt("leafCount")} canciones"
            "artist" -> "Artista"
            else -> ""
        }
        val thumb = o.str("thumb") ?: o.str("composite") ?: o.str("parentThumb") ?: o.str("grandparentThumb")
        val part = o.optJSONArray("Media")?.optJSONObject(0)
            ?.optJSONArray("Part")?.optJSONObject(0)?.str("key")
        return PlexItem(
            ratingKey = o.str("ratingKey") ?: "",
            key = o.str("key") ?: "",
            type = type,
            title = o.str("title") ?: "",
            subtitle = subtitle,
            thumb = thumb,
            partKey = part,
            duration = o.optLong("duration"),
            album = if (type == "track") o.str("parentTitle") ?: "" else "",
            artist = artist,
        )
    }

    // ---------- URLs ----------

    fun thumbUrl(thumb: String?, size: Int = 300): String? {
        if (thumb.isNullOrEmpty()) return null
        return "${prefs.serverUri}/photo/:/transcode?width=$size&height=$size&minSize=1&upscale=1" +
            "&url=${Uri.encode(thumb)}&X-Plex-Token=${prefs.serverToken}"
    }

    fun streamUrl(partKey: String): String =
        "${prefs.serverUri}$partKey?X-Plex-Token=${prefs.serverToken}"
}
