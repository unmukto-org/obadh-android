// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.unmukto.obadh.app.ObadhTheme

/** Share the settings app's Material dialog appearance and accessibility with the native picker. */
internal fun ComponentActivity.mediaDialog(title: String,message: String,positive: String="Close",
    cancel: String?=null,onCancel: ()->Unit={},onPositive: ()->Unit={}) {
    val root=findViewById<ViewGroup>(android.R.id.content)
    val view=ComposeView(this)
    fun dismiss() { view.disposeComposition();root.removeView(view) }
    root.addView(view,ViewGroup.LayoutParams(1,1))
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent { ObadhTheme {
        AlertDialog(onDismissRequest={ dismiss();onCancel() },title={ Text(title) },text={ Text(message) },
            confirmButton={ TextButton(onClick={ dismiss();onPositive() }) { Text(positive) } },
            dismissButton=if(cancel==null)null else {{ TextButton(onClick={ dismiss();onCancel() }) { Text(cancel) } }})
    }}
}
