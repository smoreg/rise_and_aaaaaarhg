package dev.smoreg.raa.mission

import dev.smoreg.raa.data.ShakeLevel

/**
 * Progress for the shake task. Only shaking harder than [threshold] (m/s² beyond gravity) counts,
 * and the excess counts squared: shaking twice as hard fills it four times faster. When you stop,
 * it leaks away, so one flick and back to sleep gets you nowhere.
 */
class ShakeDetector(
    private val target: Float,
    private val threshold: Float = 6f,
    private val leakPerSec: Float = 4f,
) {
    private var energy = 0f

    val progress get() = (energy / target).coerceIn(0f, 1f)
    val done get() = energy >= target

    fun counts(accel: Float) = accel >= threshold

    /** [accel] is linear acceleration in m/s², [dtMs] the time since the previous sample. */
    fun feed(accel: Float, dtMs: Long) {
        val dt = dtMs / 1000f
        energy = if (counts(accel)) {
            val excess = accel - threshold
            energy + excess * excess / GAIN_DIVISOR * dt
        } else {
            (energy - leakPerSec * dt).coerceAtLeast(0f)
        }
        energy = energy.coerceAtMost(target)
    }

    companion object {
        /** A firm shake (~18 m/s²) earns about 12 per second. */
        private const val GAIN_DIVISOR = 12f

        fun target(level: ShakeLevel) = when (level) {
            ShakeLevel.LIGHT -> 30f
            ShakeLevel.AAAGH -> 70f
            ShakeLevel.EARTHQUAKE -> 150f
        }
    }
}

/**
 * Progress for furious tapping. Every tap adds [gain], and progress leaks [leakPerSec] every
 * second. With the defaults the leak eats five taps a second: tapping at that rate goes
 * nowhere, eight a second finishes in about fifteen seconds.
 */
class TapDetector(
    private val gain: Float = 1f / 40,
    private val leakPerSec: Float = 5f / 40,
) {
    var progress = 0f
        private set

    val done get() = progress >= 1f

    fun tap() {
        progress = (progress + gain).coerceAtMost(1f)
    }

    fun tick(dtMs: Long) {
        if (!done) progress = (progress - leakPerSec * dtMs / 1000f).coerceAtLeast(0f)
    }
}
