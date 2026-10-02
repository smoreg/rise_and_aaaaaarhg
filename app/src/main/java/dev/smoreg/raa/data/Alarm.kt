package dev.smoreg.raa.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek

/** QR and SHAKE go silent while you work on the task and scream again when you give up. */
enum class DismissMode { BUTTON, QR, SHAKE }

/** How much shaking it takes. Harder shaking fills it faster, so it is effort, not seconds. */
enum class ShakeLevel { LIGHT, AAAGH, EARTHQUAKE }

enum class LightMode { NONE, SUNRISE, STROBE }

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    /** Bit 0 = Monday … bit 6 = Sunday. 0 rings once and then switches itself off. */
    val days: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val dismissMode: DismissMode = DismissMode.BUTTON,
    val sound: String = Sound.DEFAULT,
    /** Percent of the alarm stream maximum. */
    val volume: Int = 80,
    val rampSeconds: Int = 60,
    val vibrate: Boolean = true,
    val light: LightMode = LightMode.NONE,
    val sunriseMinutes: Int = 10,
    /** 0 disables snooze. */
    val snoozeMinutes: Int = 5,
    val snoozeMax: Int = 3,
    val shakeLevel: ShakeLevel = ShakeLevel.AAAGH,
    val quietMinutes: Int = 3,
    /** Occurrences at or before this instant are already handled (rung, dismissed or skipped). */
    val handledUntil: Long = 0,
    val snoozeUntil: Long = 0,
    val snoozeCount: Int = 0,
    /** Plays [radioUrl] instead of [sound]; [sound] stays the fallback when the stream is not steady. */
    val radio: Boolean = false,
    val radioUrl: String = "",
    val radioName: String = "",
) {
    val repeating get() = days != 0
    val canSnooze get() = snoozeMinutes > 0 && snoozeCount < snoozeMax
    val usesRadio get() = radio && radioUrl.isNotBlank()
}

/** Bit of [day] in [Alarm.days]. */
fun dayBit(day: DayOfWeek) = 1 shl (day.value - 1)

object Sound {
    const val SILENT = "silent"
    const val SYSTEM = "system"
    const val DEFAULT = "builtin:rise_and_shine"
    val BUILTIN = listOf("rise_and_shine", "klaxon", "beeps", "dawn")
    fun builtin(name: String) = "builtin:$name"
}
