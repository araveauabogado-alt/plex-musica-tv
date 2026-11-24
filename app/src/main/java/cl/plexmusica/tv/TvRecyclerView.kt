package cl.plexmusica.tv

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView pensada para el control remoto.
 *
 * Al mantener apretada una flecha, la lista normal a veces todavía no ha dibujado
 * el siguiente elemento y el foco "salta" a otra fila. Aquí el foco siempre va al
 * elemento vecino dentro de la misma lista (desplazándola si hace falta) y se queda
 * quieto en los bordes en vez de escaparse. Además limita la velocidad de repetición
 * para que el desplazamiento acompañe al foco.
 */
class TvRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : RecyclerView(context, attrs) {

    private var lastRepeat = 0L

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && isDpad(event.keyCode)) {
            val now = event.eventTime
            if (now - lastRepeat < REPEAT_MS) return true // se descarta: demasiado rápido
            lastRepeat = now
        }
        return super.dispatchKeyEvent(event)
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val lm = layoutManager as? LinearLayoutManager ?: return super.focusSearch(focused, direction)
        val item = findContainingItemView(focused) ?: return super.focusSearch(focused, direction)
        val pos = getChildAdapterPosition(item)
        val count = adapter?.itemCount ?: 0
        if (pos == NO_POSITION || count == 0) return super.focusSearch(focused, direction)

        val span = (lm as? GridLayoutManager)?.spanCount ?: 1
        val horizontal = lm.orientation == HORIZONTAL

        val target: Int = if (horizontal) {
            when (direction) {
                View.FOCUS_RIGHT -> if (pos + 1 < count) pos + 1 else return focused
                View.FOCUS_LEFT -> if (pos > 0) pos - 1 else return focused
                else -> return super.focusSearch(focused, direction) // arriba/abajo: cambiar de fila
            }
        } else if (span == 1 && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            return super.focusSearch(focused, direction) // lista simple: a los lados están los botones
        } else {
            val col = pos % span
            when (direction) {
                View.FOCUS_RIGHT -> if (span > 1 && col < span - 1 && pos + 1 < count) pos + 1 else return focused
                View.FOCUS_LEFT -> if (span > 1 && col > 0) pos - 1 else return focused
                View.FOCUS_DOWN -> when {
                    pos + span < count -> pos + span
                    pos / span < (count - 1) / span -> count - 1 // última fila incompleta
                    else -> return focused
                }
                View.FOCUS_UP -> if (pos - span >= 0) pos - span else return super.focusSearch(focused, direction)
                else -> return super.focusSearch(focused, direction)
            }
        }

        findViewHolderForAdapterPosition(target)?.itemView?.let { return it }
        // Aún no está dibujado: se desplaza la lista y se enfoca cuando aparezca.
        scrollToPosition(target)
        post { findViewHolderForAdapterPosition(target)?.itemView?.requestFocus() }
        return focused
    }

    private fun isDpad(code: Int) = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
        code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN

    companion object {
        private const val REPEAT_MS = 85L
    }
}
