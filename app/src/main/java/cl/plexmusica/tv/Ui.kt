package cl.plexmusica.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlin.math.max

object Ui {
    val focusScale = View.OnFocusChangeListener { v, hasFocus ->
        val s = if (hasFocus) 1.08f else 1f
        v.animate().scaleX(s).scaleY(s).setDuration(120).start()
    }

    fun spanCount(context: Context, itemDp: Int = 180): Int {
        val dm = context.resources.displayMetrics
        return max(2, ((dm.widthPixels / dm.density - 96) / itemDp).toInt())
    }

    fun time(ms: Long): String {
        if (ms <= 0) return "0:00"
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
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
        holder.img.load(Plex.thumbUrl(item.thumb)) {
            crossfade(true)
            placeholder(R.drawable.ic_music)
            error(R.drawable.ic_music)
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}

class TrackAdapter(
    private val items: List<PlexItem>,
    private val showArtist: Boolean,
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
