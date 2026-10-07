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

    // Phones that can set the amplitude vary it (1-255) over a short pulse. Many cannot: their
    // motor is on or off, so the pulse's length is the only strength, and it has to be longer
    // than a tick to be felt at all. A long press adds a little.
    private val millisWithAmplitude = intArrayOf(0, 12, 18, 26)
    private val amplitude = intArrayOf(0, 70, 150, 255)
    private val millisOnOff = intArrayOf(0, 16, 30, 48)

    /** A key tap, or with [long] the heavier tick of a long press or flick. */
    fun play(view: View, long: Boolean = false, strength: Int = level) {
        if (strength <= OFF) return
        val s = strength.coerceIn(1, 3)
        val vibrator = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator == null || !vibrator.hasVibrator()) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            return
        }
        val variable = vibrator.hasAmplitudeControl()
        val ms = ((if (variable) millisWithAmplitude[s] else millisOnOff[s]) + if (long) 10 else 0).toLong()
        val amp = if (variable) amplitude[s] else VibrationEffect.DEFAULT_AMPLITUDE
        try { vibrator.vibrate(VibrationEffect.createOneShot(ms, amp)) }
        catch (_: Exception) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    }
}
