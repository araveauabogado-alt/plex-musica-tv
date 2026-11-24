package cl.plexmusica.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private enum class Tab { HOME, ARTISTS, ALBUMS, PLAYLISTS, SEARCH }

    private val prefs get() = Plex.prefs
    private lateinit var status: TextView
    private lateinit var grid: RecyclerView
    private lateinit var searchRow: View
    private lateinit var searchBox: EditText
    private lateinit var libButton: Button
    private lateinit var sortButton: Button
    private lateinit var letterScroll: View
    private lateinit var letterBar: LinearLayout
    private lateinit var homeScroll: View
    private lateinit var homeRows: LinearLayout
    private lateinit var tabButtons: Map<Tab, Button>

    private var sections: List<Pair<String, String>> = emptyList()
    private var connected = false
    private var job: Job? = null
    private var current = Tab.HOME

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
        sortButton = findViewById(R.id.btnSort)
        letterScroll = findViewById(R.id.letterScroll)
        letterBar = findViewById(R.id.letterBar)
        homeScroll = findViewById(R.id.homeScroll)
        homeRows = findViewById(R.id.homeRows)
        grid.layoutManager = GridLayoutManager(this, Ui.spanCount(this))

        tabButtons = mapOf(
            Tab.HOME to findViewById<Button>(R.id.btnHome),
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
        sortButton.setOnClickListener { toggleSort() }
        updateSortButton()
        findViewById<Button>(R.id.btnSearchGo).setOnClickListener { runSearch() }
        searchBox.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                runSearch(); true
            } else false
        }

        tabButtons.getValue(Tab.HOME).requestFocus()
        select(Tab.HOME)

        // Si ya hay música sonando al abrir la app, se muestra directo "Reproduciendo".
        if (savedInstanceState == null) {
            PlayerConnection.get(this) { c ->
                if (!isFinishing && c.mediaItemCount > 0 && c.playWhenReady) {
                    startActivity(Intent(this, NowPlayingActivity::class.java))
                }
            }
        }
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

    private val recentMode get() = prefs.sortMode == "recent"

    private fun select(tab: Tab) {
        current = tab
        tabButtons.forEach { (t, b) -> b.isSelected = t == tab }
        homeScroll.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        grid.visibility = if (tab == Tab.HOME) View.GONE else View.VISIBLE
        if (tab == Tab.HOME) {
            searchRow.visibility = View.GONE
            buildLetters(emptyList(), false)
            launchJob {
                ensureConnected()
                loadHome()
            }
            return
        }
        if (tab == Tab.SEARCH) {
            searchRow.visibility = View.VISIBLE
            grid.adapter = null
            buildLetters(emptyList(), false)
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
            val sort = if (recentMode) "addedAt:desc" else "titleSort"
            val path = when (tab) {
                Tab.ARTISTS -> "/library/sections/$sec/all?type=8&sort=$sort"
                Tab.ALBUMS -> "/library/sections/$sec/all?type=9&sort=$sort"
                else -> "/playlists?playlistType=audio"
            }
            val items = Plex.items(path)
            val sorted = if (recentMode) items.sortedByDescending { it.addedAt } else Ui.sortAlpha(items)
            show(sorted, letters = !recentMode)
        }
    }

    // ---------- Inicio ----------

    private suspend fun loadHome() {
        setStatus(getString(R.string.loading))
        val sec = prefs.sectionId ?: return
        val shelves = coroutineScope {
            val releases = async { safe { Plex.recentReleases(sec) } }
            val played = async { safe { Plex.recentlyPlayed(sec) } }
            val added = async { safe { Plex.recentlyAdded(sec) } }
            val today = async { safe { Plex.anniversaries(sec) } }
            listOf(
                Shelf(getString(R.string.home_releases), releases.await()),
                Shelf(getString(R.string.home_played), played.await()),
                Shelf(getString(R.string.home_added), added.await()),
                Shelf(getString(R.string.home_today), today.await()),
            )
        }
        showHome(shelves.filter { it.items.isNotEmpty() })
    }

    private suspend fun safe(block: suspend () -> List<PlexItem>): List<PlexItem> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    private fun showHome(shelves: List<Shelf>) {
        homeRows.removeAllViews()
        val cardPx = (124 * resources.displayMetrics.density).toInt()
        for (shelf in shelves) {
            val row = layoutInflater.inflate(R.layout.item_shelf, homeRows, false)
            row.findViewById<TextView>(R.id.shelfTitle).text = shelf.title
            val list = row.findViewById<RecyclerView>(R.id.shelfList)
            list.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            list.adapter = CardAdapter(shelf.items, { Ui.queueMenu(this, it) }, cardPx) {
                Ui.open(this, it, shelf.items)
            }
            homeRows.addView(row)
        }
        setStatus(if (shelves.isEmpty()) getString(R.string.empty) else "")
    }

    private fun runSearch() {
        val q = searchBox.text.toString().trim()
        if (q.isEmpty()) return
        launchJob {
            ensureConnected()
            setStatus(getString(R.string.searching))
            show(Plex.search(q, prefs.sectionId), letters = false)
            grid.post { grid.getChildAt(0)?.requestFocus() }
        }
    }

    private fun show(items: List<PlexItem>, letters: Boolean) {
        grid.adapter = CardAdapter(items, { Ui.queueMenu(this, it) }) { Ui.open(this, it, items) }
        buildLetters(items, letters)
        setStatus(if (items.isEmpty()) getString(R.string.empty) else "")
    }

    /** Fila de letras para saltar directo a una parte de la lista. */
    private fun buildLetters(items: List<PlexItem>, enabled: Boolean) {
        letterBar.removeAllViews()
        if (!enabled || items.size < 20) {
            letterScroll.visibility = View.GONE
            return
        }
        val first = LinkedHashMap<String, Int>()
        items.forEachIndexed { i, item ->
            val letter = Ui.letterOf(item)
            if (letter !in first) first[letter] = i
        }
        for ((letter, pos) in first) {
            val tv = layoutInflater.inflate(R.layout.item_letter, letterBar, false) as TextView
            tv.text = letter
            tv.setOnClickListener { jumpTo(pos) }
            letterBar.addView(tv)
        }
        letterScroll.visibility = View.VISIBLE
    }

    private fun jumpTo(pos: Int) {
        val lm = grid.layoutManager as GridLayoutManager
        lm.scrollToPositionWithOffset(pos, 0)
        grid.post {
            grid.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
        }
    }

    private fun toggleSort() {
        prefs.sortMode = if (recentMode) "az" else "recent"
        updateSortButton()
        select(if (current == Tab.SEARCH) Tab.ARTISTS else current)
    }

    private fun updateSortButton() {
        sortButton.text = getString(if (recentMode) R.string.sort_recent else R.string.sort_az)
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
