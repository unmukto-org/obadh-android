package org.unmukto.obadh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import org.unmukto.obadh.settings.KeyboardPreferences

/** Explicit, private destination from the IME. The existing settings back stack is preserved. */
class ThemeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableObadhEdgeToEdge()
        setContent { ObadhTheme { AppearanceScreen(remember { KeyboardPreferences(this) }, ::finish) } }
    }
}
