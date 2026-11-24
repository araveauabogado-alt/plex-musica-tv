package cl.plexmusica.tv

import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity

/**
 * Base de las pantallas de biblioteca: distingue Atrás corto de Atrás mantenido.
 * - Atrás corto: lo de siempre (ver [Ui.backToNowPlaying]).
 * - Atrás mantenido (~0,5 s): [onBackLongPress], que en cada pantalla sube el foco a la fila superior.
 */
open class TvActivity : AppCompatActivity() {
    private var longHandled = false

    /** Devuelve true si la pantalla usó el Atrás mantenido. */
    open fun onBackLongPress(): Boolean = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.repeatCount == 0) {
                longHandled = false
                event.startTracking()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && onBackLongPress()) {
            longHandled = true
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (longHandled) {
                longHandled = false
                return true
            }
            if (event.isTracking && !event.isCanceled) {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}
