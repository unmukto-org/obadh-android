package org.unmukto.obadh.app

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.unmukto.obadh.settings.KeyboardState

/**
 * Live keyboard state. Both facts change while the user is in system UI, so this listens to
 * the two settings that hold them. That includes the keyboard picker, which is a dialog
 * drawn over this app: choosing Obadh there writes the default-input-method setting, the
 * observer fires, and the screen updates without the user having to leave and return.
 * Resuming re-reads as well, for changes made in the full Settings app.
 */
@Composable
fun rememberKeyboardState(): State<KeyboardState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(KeyboardState.read(context)) }

    DisposableEffect(context) {
        val handler = Handler(Looper.getMainLooper())
        val refresh = Runnable { state.value = KeyboardState.read(context) }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                refresh.run()
                // The enabled-methods list can lag the setting write by a beat.
                handler.postDelayed(refresh, 300)
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, observer)
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS), false, observer)
        onDispose {
            resolver.unregisterContentObserver(observer)
            handler.removeCallbacksAndMessages(null)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.value = KeyboardState.read(context) }
    return state
}
