package dev.smoreg.raa.ui

import android.content.Context
import android.text.format.DateFormat
import dev.smoreg.raa.R
import dev.smoreg.raa.data.dayBit
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

fun formatTime(context: Context, hour: Int, minute: Int): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern))
}

/** Time with the am/pm suffix where the phone uses one: for running text, not for the big clock. */
fun formatClock(context: Context, hour: Int, minute: Int): String =
    listOfNotNull(formatTime(context, hour, minute), amPm(context, hour)).joinToString(" ")

/** "am"/"pm" suffix when the phone uses a 12-hour clock, otherwise null. */
fun amPm(context: Context, hour: Int): String? =
    if (DateFormat.is24HourFormat(context)) null
    else LocalTime.of(hour, 0).format(DateTimeFormatter.ofPattern("a", locale(context)))

fun locale(context: Context): Locale = context.resources.configuration.locales[0]

/** Days in the order the user's locale starts the week with. */
fun weekDays(context: Context): List<DayOfWeek> {
    val first = WeekFields.of(locale(context)).firstDayOfWeek
    return (0L until 7L).map { first.plus(it) }
}

fun dayShort(context: Context, day: DayOfWeek): String =
    day.getDisplayName(TextStyle.SHORT_STANDALONE, locale(context))

fun describeDays(context: Context, days: Int): String = when (days) {
    0 -> context.getString(R.string.days_once)
    0b1111111 -> context.getString(R.string.days_every)
    0b0011111 -> context.getString(R.string.days_weekdays)
    0b1100000 -> context.getString(R.string.days_weekend)
    else -> weekDays(context).filter { days and dayBit(it) != 0 }.joinToString(" ") { dayShort(context, it) }
}

/** "in 7 h 12 min" with proper plurals. */
fun describeIn(context: Context, millis: Long): String {
    val totalMin = ((millis + 59_999) / 60_000).coerceAtLeast(1)
    val d = (totalMin / (60 * 24)).toInt()
    val h = ((totalMin / 60) % 24).toInt()
    val m = (totalMin % 60).toInt()
    val r = context.resources
    val parts = buildList {
        if (d > 0) add(r.getQuantityString(R.plurals.days, d, d))
        if (h > 0) add(r.getQuantityString(R.plurals.hours, h, h))
        if (m > 0 && d == 0) add(r.getQuantityString(R.plurals.minutes, m, m))
    }
    return context.getString(R.string.rings_in, parts.joinToString(" "))
}
