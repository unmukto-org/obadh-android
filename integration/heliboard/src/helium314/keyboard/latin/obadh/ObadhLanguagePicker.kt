// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.obadh

import android.app.AlertDialog
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
    val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(ime))
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
