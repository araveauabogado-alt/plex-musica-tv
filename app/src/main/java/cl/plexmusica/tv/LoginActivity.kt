package cl.plexmusica.tv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Vincula la TV con tu cuenta usando un código en plex.tv/link y luego elige el servidor. */
class LoginActivity : AppCompatActivity() {
    private lateinit var code: TextView
    private lateinit var status: TextView
    private lateinit var linkBox: View
    private lateinit var servers: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        code = findViewById(R.id.code)
        status = findViewById(R.id.status)
        linkBox = findViewById(R.id.linkBox)
        servers = findViewById(R.id.servers)
        servers.layoutManager = LinearLayoutManager(this)
        lifecycleScope.launch { linkLoop() }
    }

    private suspend fun linkLoop() {
        if (Plex.prefs.userToken != null) {
            loadServers()
            return
        }
        while (true) {
            val pin = try {
                Plex.createPin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = getString(R.string.error_auto_retry, e.message ?: "")
                delay(5000)
                continue
            }
            code.text = pin.code
            status.text = getString(R.string.waiting_link)
            val deadline = System.currentTimeMillis() + 10 * 60_000L
            while (System.currentTimeMillis() < deadline) {
                delay(2000)
                val token = try {
                    Plex.checkPin(pin.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (token != null) {
                    Plex.prefs.userToken = token
                    loadServers()
                    return
                }
            }
            // El código expiró: se pide uno nuevo.
        }
    }

    private suspend fun loadServers() {
        linkBox.visibility = View.GONE
        while (true) {
            status.text = getString(R.string.loading_servers)
            val list = try {
                Plex.servers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = getString(R.string.error_auto_retry, e.message ?: "")
                delay(5000)
                continue
            }
            when {
                list.isEmpty() -> status.text = getString(R.string.no_servers)
                list.size == 1 -> choose(list[0])
                else -> {
                    status.text = getString(R.string.pick_server)
                    servers.visibility = View.VISIBLE
                    servers.adapter = TextAdapter(
                        list.map { it.name + if (it.owned) "" else getString(R.string.shared_suffix) }
                    ) { choose(list[it]) }
                    servers.post { servers.getChildAt(0)?.requestFocus() }
                }
            }
            return
        }
    }

    private fun choose(server: PlexServer) {
        val p = Plex.prefs
        p.serverId = server.id
        p.serverName = server.name
        p.serverToken = server.token
        p.serverUri = null
        p.sectionId = null
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
