package org.unmukto.obadh.app

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

/** Let native navigation controls sit on the app surface, without the three-button scrim. */
internal fun ComponentActivity.enableObadhEdgeToEdge() {
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
}
