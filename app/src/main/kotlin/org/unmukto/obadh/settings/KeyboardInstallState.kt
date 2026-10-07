package org.unmukto.obadh.settings

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** Snapshot of the two facts setup cares about: Obadh is turned on, and it is the current keyboard. */
data class KeyboardState(val enabled: Boolean, val selected: Boolean) {
    val ready: Boolean get() = enabled && selected

    companion object {
        fun read(context: Context) =
            KeyboardState(KeyboardInstallState.isEnabled(context), KeyboardInstallState.isSelected(context))
    }
}

/** Whether the IME is enabled in system settings and currently selected. */
object KeyboardInstallState {
    fun isEnabled(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == context.packageName }
    }

    fun isSelected(context: Context): Boolean {
        val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return current?.startsWith(context.packageName + "/") == true
    }
}
