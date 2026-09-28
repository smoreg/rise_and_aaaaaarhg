package dev.smoreg.raa.alarm

import dev.smoreg.raa.data.Alarm
import dev.smoreg.raa.data.LightMode
import dev.smoreg.raa.data.dayBit
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZonedDateTime

object NextTrigger {
    /** Next moment the alarm makes sound, strictly after both [now] and what is already handled. */
    fun ring(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null
        val nowMs = now.toInstant().toEpochMilli()
        if (alarm.snoozeUntil > nowMs) return Instant.ofEpochMilli(alarm.snoozeUntil).atZone(now.zone)

        val handled = Instant.ofEpochMilli(alarm.handledUntil).atZone(now.zone)
        val floor = if (handled.isAfter(now)) handled else now
        var date = floor.toLocalDate()
        repeat(8) {
            // atZone moves a time that falls into a DST gap forward, which is what an alarm should do.
            val candidate = date.atTime(alarm.hour, alarm.minute).atZone(now.zone)
            if (candidate.isAfter(floor) && matches(alarm.days, date.dayOfWeek)) return candidate
            date = date.plusDays(1)
        }
        return null
    }

    /** When the sunrise light starts for [ring], or null if there is none to schedule. */
    fun sunrise(alarm: Alarm, ring: ZonedDateTime, now: ZonedDateTime): ZonedDateTime? {
        if (alarm.light != LightMode.SUNRISE || alarm.sunriseMinutes <= 0) return null
        if (alarm.snoozeUntil > now.toInstant().toEpochMilli()) return null
        return ring.minusMinutes(alarm.sunriseMinutes.toLong()).takeIf { it.isAfter(now) }
    }

    fun matches(days: Int, day: DayOfWeek) = days == 0 || days and dayBit(day) != 0
}
