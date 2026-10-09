// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.obadh

import android.app.AlertDialog
import android.content.res.Configuration
import helium314.keyboard.latin.utils.prefs
import android.os.IBinder
import android.view.WindowManager
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getPlatformDialogThemeContext

/** Languages first, Android's full IME picker available explicitly rather than mixed into the list. */
fun createObadhLanguagePicker(ime: LatinIME, imm: RichInputMethodManager, token: IBinder): AlertDialog {
    val subtypes = SubtypeSettings.getEnabledSubtypes().sortedBy { if (it.languageTag.startsWith("bn")) 0 else 1 }
    val selected = subtypes.indexOf(imm.currentSubtype.rawSubtype).coerceAtLeast(0)
    val labels = subtypes.map { if (it.languageTag.startsWith("bn")) "বাংলা · Obadh" else "English · QWERTY" }
    val appearance = ime.prefs().getString("obadh.theme_mode", "system")
    val themedContext = if (appearance == "system") ime else ime.createConfigurationContext(Configuration(ime.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (appearance == "dark") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
    })
    val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(themedContext))
        .setTitle("Change language")
        .setSingleChoiceItems(labels.toTypedArray(), selected) { d, index ->
            d.dismiss()
            ime.switchToSubtype(subtypes[index])
        }
        .setNeutralButton("Other keyboards") { _, _ -> imm.inputMethodManager.showInputMethodPicker() }
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    dialog.window?.apply {
        attributes = attributes.apply { this.token = token; type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG }
        addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }
    return dialog
}
