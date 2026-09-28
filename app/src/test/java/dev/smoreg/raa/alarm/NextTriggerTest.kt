package dev.smoreg.raa.alarm

import dev.smoreg.raa.data.Alarm
import dev.smoreg.raa.data.LightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTriggerTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(s: String): ZonedDateTime = LocalDateTime.parse(s).atZone(zone)
    private fun ms(s: String) = at(s).toInstant().toEpochMilli()

    // 2026-09-28 is a Monday.
    private val mon = 1
    private val wed = 1 shl 2
    private val sun = 1 shl 6

    @Test fun oneShotLaterToday() {
        val a = Alarm(hour = 7, minute = 30)
        assertEquals(at("2026-09-28T07:30"), NextTrigger.ring(a, at("2026-09-28T06:00")))
    }

    @Test fun oneShotTomorrowWhenTimePassed() {
        val a = Alarm(hour = 7, minute = 30)
        assertEquals(at("2026-09-29T07:30"), NextTrigger.ring(a, at("2026-09-28T07:30")))
    }

    @Test fun repeatingPicksNextSelectedDay() {
        val a = Alarm(hour = 8, minute = 0, days = wed or sun)
        assertEquals(at("2026-09-30T08:00"), NextTrigger.ring(a, at("2026-09-28T09:00")))
        assertEquals(at("2026-10-04T08:00"), NextTrigger.ring(a, at("2026-09-30T08:00")))
    }

    @Test fun handledOccurrenceIsSkipped() {
        // Dismissed during the sunrise at 06:55: today's 07:00 must not ring again.
        val a = Alarm(hour = 7, minute = 0, days = mon or wed, handledUntil = ms("2026-09-28T07:00"))
        assertEquals(at("2026-09-30T07:00"), NextTrigger.ring(a, at("2026-09-28T06:55")))
    }

    @Test fun snoozeWins() {
        val a = Alarm(hour = 7, minute = 0, snoozeUntil = ms("2026-09-28T07:05"))
        assertEquals(at("2026-09-28T07:05"), NextTrigger.ring(a, at("2026-09-28T07:00:10")))
    }

    @Test fun disabledNeverRings() {
        assertNull(NextTrigger.ring(Alarm(enabled = false), at("2026-09-28T06:00")))
    }

    @Test fun springForwardGapMovesLater() {
        // 2027-03-28 02:00 → 03:00 in Madrid.
        val a = Alarm(hour = 2, minute = 30)
        assertEquals(LocalDateTime.parse("2027-03-28T03:30"), NextTrigger.ring(a, at("2027-03-28T00:00"))!!.toLocalDateTime())
    }

    @Test fun sunriseStartsLeadMinutesBefore() {
        val a = Alarm(hour = 7, minute = 0, light = LightMode.SUNRISE, sunriseMinutes = 15)
        val ring = at("2026-09-28T07:00")
        assertEquals(at("2026-09-28T06:45"), NextTrigger.sunrise(a, ring, at("2026-09-28T05:00")))
        assertNull(NextTrigger.sunrise(a, ring, at("2026-09-28T06:50")))
    }
}
