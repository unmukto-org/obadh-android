package org.unmukto.obadh.keyboard

/** Stable IME component identity; upstream owns Android input/editing behavior. */
class ObadhInputMethodService : helium314.keyboard.latin.LatinIME() {
    override fun onCreate() {
        org.unmukto.obadh.settings.NativePreferences.apply(this,
            org.unmukto.obadh.settings.NativePreferences.snapshot(this))
        helium314.keyboard.latin.utils.DeviceProtectedUtils.getSharedPreferences(this).edit()
            .putBoolean(helium314.keyboard.latin.settings.Settings.PREF_GESTURE_INPUT,
                getSharedPreferences("swipe_download", MODE_PRIVATE).getBoolean("enabled", false)).apply()
        NativeObadhFeatures.initialize(this)
        super.onCreate()
        NativeObadhFeatures.attach(this)
        NativeDataMigration.run(this)
    }
    override fun onTextInput(rawText: String?) {
        rawText?.let(NativeObadhFeatures::recordEmoji)
        super.onTextInput(rawText)
    }

    private fun capturesVolume(keyCode: Int) = isInputViewShown && NativeObadhFeatures.volumeCursor &&
        (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP)

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (!capturesVolume(keyCode)) return super.onKeyDown(keyCode, event)
        // Native key handling commits/resets composing state and respects editor cursor updates.
        val direction = if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.ARROW_LEFT
        else helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.ARROW_RIGHT
        onEvent(helium314.keyboard.event.Event.createSoftwareKeypressEvent(direction, 0, -1, -1, false))
        return true
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean =
        if (capturesVolume(keyCode)) true else super.onKeyUp(keyCode, event)
}
