package cl.plexmusica.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Lista de canciones de un álbum o una playlist. */
class TracksActivity : AppCompatActivity() {
    companion object {
        fun intent(ctx: Context, title: String, subtitle: String, thumb: String?, path: String): Intent =
            Intent(ctx, TracksActivity::class.java)
                .putExtra("title", title)
                .putExtra("subtitle", subtitle)
                .putExtra("thumb", thumb)
                .putExtra("path", path)
    }

    private var tracks: List<PlexItem> = emptyList()
    private lateinit var status: TextView
    private lateinit var list: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracks)
        findViewById<TextView>(R.id.header).text = intent.getStringExtra("title")
        findViewById<TextView>(R.id.subheader).text = intent.getStringExtra("subtitle")
        findViewById<ImageView>(R.id.cover).load(Plex.thumbUrl(intent.getStringExtra("thumb"), 500)) {
            crossfade(true)
            placeholder(R.drawable.ic_music)
            error(R.drawable.ic_music)
        }
        status = findViewById(R.id.status)
        list = findViewById(R.id.list)
        list.layoutManager = LinearLayoutManager(this)

        val btnPlay = findViewById<Button>(R.id.btnPlayAll)
        btnPlay.setOnClickListener { play(0, false) }
        findViewById<Button>(R.id.btnShuffle).setOnClickListener { play(0, true) }
        btnPlay.requestFocus()

        val path = intent.getStringExtra("path") ?: return
        lifecycleScope.launch {
            status.text = getString(R.string.loading)
            try {
                tracks = Plex.items(path).filter { it.type == "track" }
                val variedArtists = tracks.map { it.artist }.distinct().size > 1
                list.adapter = TrackAdapter(tracks, variedArtists) { play(it, false) }
                val total = tracks.sumOf { it.duration }
                status.text = getString(R.string.tracks_summary, tracks.size, Ui.time(total))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = getString(R.string.error_simple, e.message ?: "")
            }
        }
    }

    private fun play(index: Int, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        PlayerConnection.play(this, tracks, index, shuffle)
        startActivity(Intent(this, NowPlayingActivity::class.java))
    }
}
