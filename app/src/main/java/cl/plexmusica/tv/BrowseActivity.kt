package cl.plexmusica.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Cuadrícula genérica: por ejemplo, los álbumes de un artista. */
class BrowseActivity : TvActivity() {
    /** Atrás mantenido: el foco sube a los botones de arriba (Reproducir). */
    override fun onBackLongPress(): Boolean {
        val play = findViewById<View>(R.id.btnPlayAll)
        return play.visibility == View.VISIBLE && play.requestFocus()
    }

    companion object {
        fun intent(ctx: Context, title: String, path: String, playAllPath: String?): Intent =
            Intent(ctx, BrowseActivity::class.java)
                .putExtra("title", title)
                .putExtra("path", path)
                .putExtra("playAll", playAllPath)
    }

    private lateinit var status: TextView
    private lateinit var grid: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)
        Ui.backToNowPlaying(this, isRoot = false)
        findViewById<TextView>(R.id.header).text = intent.getStringExtra("title")
        status = findViewById(R.id.status)
        grid = findViewById(R.id.grid)
        grid.layoutManager = GridLayoutManager(this, Ui.spanCount(this))

        val playAll = intent.getStringExtra("playAll")
        val btnPlay = findViewById<Button>(R.id.btnPlayAll)
        val btnShuffle = findViewById<View>(R.id.btnShuffle)
        if (playAll == null) {
            btnPlay.visibility = View.GONE
            btnShuffle.visibility = View.GONE
        } else {
            btnPlay.setOnClickListener { playAll(playAll, false) }
            btnShuffle.setOnClickListener { playAll(playAll, true) }
        }

        val path = intent.getStringExtra("path") ?: return
        lifecycleScope.launch {
            status.text = getString(R.string.loading)
            try {
                val items = Plex.items(path)
                grid.adapter = CardAdapter(items, { Ui.queueMenu(this@BrowseActivity, it) }) { Ui.open(this@BrowseActivity, it, items) }
                status.text = if (items.isEmpty()) getString(R.string.empty) else ""
                grid.post { grid.getChildAt(0)?.requestFocus() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = getString(R.string.error_simple, e.message ?: "")
            }
        }
    }

    private fun playAll(path: String, shuffle: Boolean) {
        lifecycleScope.launch {
            try {
                val tracks = Plex.items(path)
                PlayerConnection.play(this@BrowseActivity, tracks, 0, shuffle)
                startActivity(Intent(this@BrowseActivity, NowPlayingActivity::class.java))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = getString(R.string.error_simple, e.message ?: "")
            }
        }
    }
}
