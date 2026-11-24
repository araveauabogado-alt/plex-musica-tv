package cl.plexmusica.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

object Ui {
    val focusScale = View.OnFocusChangeListener { v, hasFocus ->
        val s = if (hasFocus) 1.1f else 1f
        v.animate().scaleX(s).scaleY(s).setDuration(120).start()
    }

    fun spanCount(context: Context, itemDp: Int = 120): Int {
        val dm = context.resources.displayMetrics
        return max(2, ((dm.widthPixels / dm.density - 96) / itemDp).toInt())
    }

    private val collator: Collator = Collator.getInstance(Locale("es")).apply { strength = Collator.PRIMARY }

    /** Letra para la barra de atajos: A–Z, o "#" para números y símbolos. */
    fun letterOf(item: PlexItem): String {
        val s = Normalizer.normalize(item.titleSort.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        val c = s.firstOrNull()?.uppercaseChar() ?: return "#"
        return if (c in 'A'..'Z') c.toString() else "#"
    }

    /** Orden alfabético con "#" primero y las letras agrupadas igual que en la barra. */
    fun sortAlpha(items: List<PlexItem>): List<PlexItem> =
        items.sortedWith(Comparator { a, b ->
            val la = letterOf(a)
            val lb = letterOf(b)
            if (la != lb) {
                when {
                    la == "#" -> -1
                    lb == "#" -> 1
                    else -> la.compareTo(lb)
                }
            } else collator.compare(a.titleSort, b.titleSort)
        })

    fun time(ms: Long): String {
        if (ms <= 0) return "0:00"
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }

    /** Canciones que representa un elemento (para agregarlo a la cola). */
    suspend fun tracksOf(item: PlexItem): List<PlexItem> = when (item.type) {
        "track" -> listOf(item)
        "album" -> Plex.items("/library/metadata/${item.ratingKey}/children")
        "artist" -> Plex.items("/library/metadata/${item.ratingKey}/allLeaves")
        "playlist" -> Plex.items(item.key)
        else -> emptyList()
    }.filter { it.type == "track" }

    /** Menú al mantener presionado OK: reproducir a continuación o agregar al final de la cola. */
    fun queueMenu(activity: AppCompatActivity, item: PlexItem) {
        val options = arrayOf(
            activity.getString(R.string.queue_next),
            activity.getString(R.string.queue_end),
        )
        AlertDialog.Builder(activity)
            .setTitle(item.title)
            .setItems(options) { _, which ->
                activity.lifecycleScope.launch {
                    try {
                        val tracks = tracksOf(item)
                        PlayerConnection.enqueue(activity, tracks, next = which == 0) { n ->
                            Toast.makeText(
                                activity,
                                activity.resources.getQuantityString(R.plurals.added_to_queue, n, n),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(activity, activity.getString(R.string.error_simple, e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    fun open(activity: Activity, item: PlexItem, siblings: List<PlexItem>) {
        when (item.type) {
            "artist" -> activity.startActivity(
                BrowseActivity.intent(
                    activity, item.title,
                    "/library/metadata/${item.ratingKey}/children",
                    "/library/metadata/${item.ratingKey}/allLeaves",
                )
            )
            "album" -> activity.startActivity(
                TracksActivity.intent(
                    activity, item.title, item.subtitle, item.thumb,
                    "/library/metadata/${item.ratingKey}/children",
                )
            )
            "playlist" -> activity.startActivity(
                TracksActivity.intent(activity, item.title, item.subtitle, item.thumb, item.key)
            )
            "track" -> {
                val tracks = siblings.filter { it.type == "track" }
                PlayerConnection.play(activity, tracks, tracks.indexOf(item))
                activity.startActivity(Intent(activity, NowPlayingActivity::class.java))
            }
        }
    }
}

class CardAdapter(
    private val items: List<PlexItem>,
    private val onLongClick: ((PlexItem) -> Unit)? = null,
    private val onClick: (PlexItem) -> Unit,
) : RecyclerView.Adapter<CardAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.img)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        v.onFocusChangeListener = Ui.focusScale
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.sub.text = item.subtitle
        holder.img.load(Plex.thumbUrl(item.thumb, 220)) {
            crossfade(true)
            placeholder(R.drawable.ic_music)
            error(R.drawable.ic_music)
        }
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(item)
            onLongClick != null
        }
    }

    override fun getItemCount() = items.size
}

class TrackAdapter(
    private val items: List<PlexItem>,
    private val showArtist: Boolean,
    private val onLongClick: ((Int) -> Unit)? = null,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<TrackAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val num: TextView = v.findViewById(R.id.num)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
        val dur: TextView = v.findViewById(R.id.dur)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.num.text = (position + 1).toString()
        holder.title.text = item.title
        holder.sub.text = item.artist
        holder.sub.visibility = if (showArtist && item.artist.isNotEmpty()) View.VISIBLE else View.GONE
        holder.dur.text = Ui.time(item.duration)
        holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(holder.bindingAdapterPosition)
            onLongClick != null
        }
    }

    override fun getItemCount() = items.size
}

class TextAdapter(
    private val labels: List<String>,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<TextAdapter.VH>() {

    class VH(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_text, parent, false) as TextView)

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.text.text = labels[position]
        holder.text.setOnClickListener { onClick(holder.bindingAdapterPosition) }
    }

    override fun getItemCount() = labels.size
}
