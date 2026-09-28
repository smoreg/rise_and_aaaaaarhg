package dev.smoreg.raa.mission

import dev.smoreg.raa.data.ShakeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorsTest {
    private fun shake(d: ShakeDetector, accel: Float, ms: Long) = repeat((ms / 20).toInt()) { d.feed(accel, 20) }

    @Test fun firmShakeFinishesAaaghInAboutSixSeconds() {
        val d = ShakeDetector(ShakeDetector.target(ShakeLevel.AAAGH))
        shake(d, accel = 18f, ms = 5_000)
        assertFalse(d.done)
        shake(d, accel = 18f, ms = 1_000)
        assertTrue(d.done)
    }

    @Test fun harderShakingIsFaster() {
        val soft = ShakeDetector(100f).also { shake(it, accel = 12f, ms = 2_000) }
        val hard = ShakeDetector(100f).also { shake(it, accel = 24f, ms = 2_000) }
        assertTrue(hard.progress > soft.progress * 4)
    }

    @Test fun holdingStillOrWalkingDoesNotCount() {
        val d = ShakeDetector(30f)
        shake(d, accel = 4f, ms = 60_000)
        assertEquals(0f, d.progress)
    }

    @Test fun progressLeaksWhenYouStop() {
        val d = ShakeDetector(1000f)
        shake(d, accel = 18f, ms = 2_000)
        val before = d.progress
        shake(d, accel = 0f, ms = 2_000)
        assertTrue(d.progress < before)
    }

    private fun tapAt(d: TapDetector, perSecond: Int, seconds: Int) {
        val gap = 1000L / perSecond
        repeat(perSecond * seconds) {
            d.tap()
            d.tick(gap)
        }
    }

    @Test fun lazyTappingNeverFinishes() {
        val d = TapDetector()
        tapAt(d, perSecond = 4, seconds = 120)
        assertFalse(d.done)
    }

    @Test fun furiousTappingFinishes() {
        val d = TapDetector()
        tapAt(d, perSecond = 8, seconds = 15)
        assertTrue(d.done)
    }
}
