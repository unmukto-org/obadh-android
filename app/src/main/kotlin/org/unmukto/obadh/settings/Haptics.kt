package org.unmukto.obadh.settings

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Key haptics with a strength: 0 off, 1 light, 2 medium, 3 strong. Plays a short one-shot on
 * the vibrator at that amplitude (the phone's own tap where it can't set one). [level] is
 * mirrored from preferences by the keyboard when a field starts.
 */
object Haptics {
    const val OFF = 0
    const val DEFAULT = 2

    @Volatile var level = DEFAULT

    // Milliseconds and amplitude (1-255) per level; a long press adds a little.
    private val millis = intArrayOf(0, 9, 13, 20)
    private val amplitude = intArrayOf(0, 55, 130, 255)

    /** A key tap, or with [long] the heavier tick of a long press or flick. */
    fun play(view: View, long: Boolean = false, strength: Int = level) {
        if (strength <= OFF) return
        val s = strength.coerceIn(1, 3)
        val vibrator = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator == null || !vibrator.hasVibrator()) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            return
        }
        val ms = (millis[s] + if (long) 6 else 0).toLong()
        val amp = if (vibrator.hasAmplitudeControl()) amplitude[s] else VibrationEffect.DEFAULT_AMPLITUDE
        try { vibrator.vibrate(VibrationEffect.createOneShot(ms, amp)) }
        catch (_: Exception) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    }
}
