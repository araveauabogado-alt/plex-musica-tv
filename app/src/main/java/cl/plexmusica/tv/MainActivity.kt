package cl.plexmusica.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private enum class Tab { ARTISTS, ALBUMS, PLAYLISTS, SEARCH }

    private val prefs get() = Plex.prefs
    private lateinit var status: TextView
    private lateinit var grid: RecyclerView
    private lateinit var searchRow: View
    private lateinit var searchBox: EditText
    private lateinit var libButton: Button
    private lateinit var tabButtons: Map<Tab, Button>

    private var sections: List<Pair<String, String>> = emptyList()
    private var connected = false
    private var job: Job? = null
    private var current = Tab.ARTISTS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.userToken == null || prefs.serverId == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        grid = findViewById(R.id.grid)
        searchRow = findViewById(R.id.searchRow)
        searchBox = findViewById(R.id.searchBox)
        libButton = findViewById(R.id.btnLibrary)
        grid.layoutManager = GridLayoutManager(this, Ui.spanCount(this))

        tabButtons = mapOf(
            Tab.ARTISTS to findViewById<Button>(R.id.btnArtists),
            Tab.ALBUMS to findViewById<Button>(R.id.btnAlbums),
            Tab.PLAYLISTS to findViewById<Button>(R.id.btnPlaylists),
            Tab.SEARCH to findViewById<Button>(R.id.btnSearch),
        )
        tabButtons.forEach { (tab, button) -> button.setOnClickListener { select(tab) } }

        findViewById<Button>(R.id.btnNowPlaying).setOnClickListener {
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            prefs.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        libButton.setOnClickListener { nextLibrary() }
        findViewById<Button>(R.id.btnSearchGo).setOnClickListener { runSearch() }
        searchBox.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                runSearch(); true
            } else false
        }

        tabButtons.getValue(Tab.ARTISTS).requestFocus()
        select(Tab.ARTISTS)
    }

    private fun setStatus(text: String) {
        status.text = text
        status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun launchJob(block: suspend () -> Unit) {
        job?.cancel()
        job = lifecycleScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStatus(getString(R.string.error_retry, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /** Busca la mejor dirección del servidor (local primero) y las bibliotecas de música. */
    private suspend fun ensureConnected() {
        if (connected) return
        setStatus(getString(R.string.connecting, prefs.serverName ?: ""))
        val server = Plex.servers().firstOrNull { it.id == prefs.serverId }
            ?: throw IOException(getString(R.string.server_not_found))
        prefs.serverToken = server.token
        prefs.serverName = server.name
        prefs.serverUri = Plex.findConnection(server)
            ?: throw IOException(getString(R.string.server_unreachable))
        sections = Plex.musicSections()
        if (sections.isEmpty()) throw IOException(getString(R.string.no_music))
        if (sections.none { it.first == prefs.sectionId }) prefs.sectionId = sections.first().first
        connected = true
        updateLibButton()
    }

    private fun select(tab: Tab) {
        current = tab
        tabButtons.forEach { (t, b) -> b.isSelected = t == tab }
        if (tab == Tab.SEARCH) {
            searchRow.visibility = View.VISIBLE
            grid.adapter = null
            setStatus("")
            searchBox.requestFocus()
            if (!connected) launchJob { ensureConnected(); setStatus("") }
            return
        }
        searchRow.visibility = View.GONE
        launchJob {
            ensureConnected()
            setStatus(getString(R.string.loading))
            val sec = prefs.sectionId
            val path = when (tab) {
                Tab.ARTISTS -> "/library/sections/$sec/all?type=8"
                Tab.ALBUMS -> "/library/sections/$sec/all?type=9"
                else -> "/playlists?playlistType=audio"
            }
            show(Plex.items(path))
        }
    }

    private fun runSearch() {
        val q = searchBox.text.toString().trim()
        if (q.isEmpty()) return
        launchJob {
            ensureConnected()
            setStatus(getString(R.string.searching))
            show(Plex.search(q, prefs.sectionId))
            grid.post { grid.getChildAt(0)?.requestFocus() }
        }
    }

    private fun show(items: List<PlexItem>) {
        grid.adapter = CardAdapter(items) { Ui.open(this, it, items) }
        setStatus(if (items.isEmpty()) getString(R.string.empty) else "")
    }

    private fun updateLibButton() {
        libButton.visibility = if (sections.size > 1) View.VISIBLE else View.GONE
        val name = sections.firstOrNull { it.first == prefs.sectionId }?.second ?: ""
        libButton.text = getString(R.string.library_x, name)
    }

    private fun nextLibrary() {
        if (sections.size < 2) return
        val i = sections.indexOfFirst { it.first == prefs.sectionId }
        prefs.sectionId = sections[(i + 1) % sections.size].first
        updateLibButton()
        select(if (current == Tab.SEARCH) Tab.ARTISTS else current)
    }
}
