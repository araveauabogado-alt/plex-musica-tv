package cl.plexmusica.tv

import android.app.Application
import android.content.Context
import java.util.UUID

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Plex.prefs = Prefs(this)
        LocalWaveform.init(this)
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("plex", Context.MODE_PRIVATE)

    val clientId: String
        get() {
            val existing = sp.getString("clientId", null)
            if (existing != null) return existing
            val id = UUID.randomUUID().toString()
            sp.edit().putString("clientId", id).apply()
            return id
        }

    var userToken: String?
        get() = sp.getString("userToken", null)
        set(value) { sp.edit().putString("userToken", value).apply() }

    var serverId: String?
        get() = sp.getString("serverId", null)
        set(value) { sp.edit().putString("serverId", value).apply() }

    var serverName: String?
        get() = sp.getString("serverName", null)
        set(value) { sp.edit().putString("serverName", value).apply() }

    var serverToken: String?
        get() = sp.getString("serverToken", null)
        set(value) { sp.edit().putString("serverToken", value).apply() }

    var serverUri: String?
        get() = sp.getString("serverUri", null)
        set(value) { sp.edit().putString("serverUri", value).apply() }

    var sectionId: String?
        get() = sp.getString("sectionId", null)
        set(value) { sp.edit().putString("sectionId", value).apply() }

    /** Orden de las listas: "az" o "recent". */
    var sortMode: String
        get() = sp.getString("sortMode", "az") ?: "az"
        set(value) { sp.edit().putString("sortMode", value).apply() }

    fun logout() {
        val id = clientId
        sp.edit().clear().putString("clientId", id).apply()
    }
}
