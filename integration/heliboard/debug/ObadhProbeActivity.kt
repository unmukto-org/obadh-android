// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Debug-only, offline C ABI benchmark and editor for testing the actual upstream IME. */
class ObadhProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 120, 24, 24) }
        val status = TextView(this).apply { text = "Measuring Obadh composition…" }
        val editor = EditText(this).apply { hint = "Try Bangla and English"; setSingleLine(false) }
        column.addView(status)
        column.addView(editor)
        setContentView(column)
        Thread {
            ObadhCombiner.warmUp()
            val combiner = ObadhCombiner()
            val timings = LongArray(14000)
            var index = 0
            repeat(2000) {
                combiner.reset()
                for (letter in "bangla") {
                    val event = Event.createSoftwareKeypressEvent(letter.code, 0, 0, 0, false)
                    val start = System.nanoTime()
                    combiner.processEvent(arrayListOf(), event)
                    combiner.combiningStateFeedback
                    timings[index++] = System.nanoTime() - start
                }
                check(combiner.combiningStateFeedback.toString() == "বাংলা")
                val start = System.nanoTime()
                combiner.processEvent(arrayListOf(), Event.createSoftwareKeypressEvent(' '.code, 0, 0, 0, false))
                timings[index++] = System.nanoTime() - start
            }
            val samples = timings.drop(700).sorted() // discard initial JIT warm-up
            fun percentile(p: Double) = samples[((samples.size - 1) * p).toInt()] / 1000.0
            val result = "C ABI composition: n=${samples.size}, p50=${percentile(.5)} µs, p95=${percentile(.95)} µs, p99=${percentile(.99)} µs, max=${samples.last() / 1000.0} µs. বাংলা verified."
            Log.i("ObadhProbe", result)
            runOnUiThread { status.text = result }
        }.apply { name = "ObadhBenchmark"; start() }
    }
}
