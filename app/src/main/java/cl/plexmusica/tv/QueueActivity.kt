package cl.plexmusica.tv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load

/** Cola de reproducción: la canción actual y las que vienen, en el orden real (también en aleatorio). */
class QueueActivity : AppCompatActivity() {
    private var controller: MediaController? = null
    private lateinit var list: RecyclerView
    private lateinit var status: TextView
    private var order: List<Int> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            ) refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)
        list = findViewById(R.id.list)
        status = findViewById(R.id.status)
        list.layoutManager = LinearLayoutManager(this)
        findViewById<Button>(R.id.btnClear).setOnClickListener { clearUpcoming() }
    }

    override fun onStart() {
        super.onStart()
        PlayerConnection.get(this) { c ->
            controller = c
            c.addListener(listener)
            refresh()
            list.post { list.getChildAt(0)?.requestFocus() }
        }
    }

    override fun onStop() {
        controller?.removeListener(listener)
        controller = null
        super.onStop()
    }

    /** Índices de la cola desde la canción actual, siguiendo el orden de reproducción. */
    private fun playbackOrder(c: MediaController): List<Int> {
        val tl = c.currentTimeline
        if (tl.isEmpty) return emptyList()
        val out = mutableListOf<Int>()
        var i = c.currentMediaItemIndex
        while (i != C.INDEX_UNSET && out.size < tl.windowCount) {
            out += i
            i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return out
    }

    private fun refresh() {
        val c = controller ?: return
        val focused = list.focusedChild?.let { list.getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION
        order = playbackOrder(c)
        if (order.isEmpty()) {
            list.adapter = null
            status.text = getString(R.string.queue_empty)
            return
        }
        val items = order.map { c.getMediaItemAt(it) }
        list.adapter = QueueAdapter(items, { pos -> playAt(pos) }) { pos -> itemMenu(pos) }
        val upcoming = order.size - 1
        status.text = resources.getQuantityString(R.plurals.queue_upcoming, upcoming, upcoming) +
            "   ·   " + getString(R.string.queue_hint)
        if (focused != RecyclerView.NO_POSITION) {
            val target = focused.coerceAtMost(order.size - 1)
            list.post { list.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus() }
        }
    }

    private fun playAt(pos: Int) {
        val c = controller ?: return
        val idx = order.getOrNull(pos) ?: return
        if (pos == 0) return
        c.seekToDefaultPosition(idx)
        c.play()
    }

    private fun itemMenu(pos: Int) {
        if (pos == 0) return // la canción que suena no se quita desde aquí
        val c = controller ?: return
        val idx = order.getOrNull(pos) ?: return
        val title = c.getMediaItemAt(idx).mediaMetadata.title ?: ""
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(arrayOf(getString(R.string.queue_play_now), getString(R.string.queue_remove))) { _, which ->
                val ctrl = controller ?: return@setItems
                if (which == 0) {
                    ctrl.seekToDefaultPosition(idx)
                    ctrl.play()
                } else {
                    ctrl.removeMediaItem(idx)
                }
            }
            .show()
    }

    private fun clearUpcoming() {
        val c = controller ?: return
        if (order.size <= 1) return
        // Se quitan de mayor a menor índice para no desplazar los que faltan.
        order.drop(1).sortedDescending().forEach { c.removeMediaItem(it) }
    }
}

class QueueAdapter(
    private val items: List<MediaItem>,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
) : RecyclerView.Adapter<QueueAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.img)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
        val dur: TextView = v.findViewById(R.id.dur)
        val badge: TextView = v.findViewById(R.id.badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_queue, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val md = items[position].mediaMetadata
        val ctx = holder.itemView.context
        holder.title.text = md.title ?: ""
        holder.sub.text = listOfNotNull(md.artist, md.albumTitle).joinToString(" · ")
        holder.dur.text = Ui.time(md.extras?.getLong("duration") ?: 0L)
        holder.img.load(md.artworkUri) {
            placeholder(R.drawable.ic_music)
            error(R.drawable.ic_music)
        }
        val current = position == 0
        holder.badge.visibility = if (current) View.VISIBLE else View.GONE
        holder.title.setTextColor(ctx.getColor(if (current) R.color.accent else R.color.text))
        holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        holder.itemView.setOnLongClickListener {
            onLongClick(holder.bindingAdapterPosition)
            true
        }
    }

    override fun getItemCount() = items.size
}
