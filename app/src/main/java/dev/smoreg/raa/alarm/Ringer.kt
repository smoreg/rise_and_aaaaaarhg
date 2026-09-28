package dev.smoreg.raa.alarm

import android.os.SystemClock
import dev.smoreg.raa.RaaApp
import dev.smoreg.raa.data.Alarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { SUNRISE, RING, EARLY }

enum class Outcome { DISMISSED, SNOOZED, GAVE_UP }

/** 2 Hz, below the 3–30 Hz band that triggers photosensitive seizures. Shared by flashlight and screen. */
const val STROBE_HALF_PERIOD_MS = 250L

/** Durations inside a ring use the monotonic clock: changing the phone's time must not end or mute it. */
fun elapsed() = SystemClock.elapsedRealtime()

data class RingSession(
    val alarm: Alarm,
    val phase: Phase,
    /** Wall-clock time of the occurrence this session belongs to. */
    val ringAt: Long,
    val sunriseStart: Long = ringAt,
    val startedAt: Long = System.currentTimeMillis(),
    /** [elapsed] when the sound started; 0 while it is still only light. */
    val ringingSince: Long = if (phase == Phase.RING) elapsed() else 0,
    /** [elapsed] until which the quiet walk to the QR code lasts. */
    val mutedUntil: Long = 0,
    /** Quiet walks taken so far; each one is shorter than the last. */
    val quietCount: Int = 0,
    /** The task is being worked on right now (shaking, tapping), so stay silent. */
    val engaged: Boolean = false,
    val lostCode: Boolean = false,
    val cameraActive: Boolean = false,
    /** The ringing screen is in front; the notification then must not pop up over it. */
    val screenVisible: Boolean = false,
    val test: Boolean = false,
) {
    fun mutedLeftMs() = (mutedUntil - elapsed()).coerceAtLeast(0)
    fun muted() = mutedLeftMs() > 0 || engaged

    /** 3, 2, 1, 1, 1… minutes when the alarm is set to 3. */
    val nextQuietMinutes get() = (alarm.quietMinutes - quietCount).coerceAtLeast(1)
    val canSnooze get() = phase == Phase.RING && alarm.canSnooze

    fun sunriseProgress(now: Long): Float = when {
        phase != Phase.SUNRISE -> 1f
        ringAt <= sunriseStart -> 1f
        else -> ((now - sunriseStart).toFloat() / (ringAt - sunriseStart)).coerceIn(0f, 1f)
    }
}

/** The one alarm currently ringing (or being dismissed early). Service and screen both watch it. */
object Ringer {
    private const val BUSY_RETRY_MS = 5 * 60_000L

    private val state = MutableStateFlow<RingSession?>(null)
    val session: StateFlow<RingSession?> = state.asStateFlow()

    /** False when something is already ringing: a test or an early dismiss must never replace a real ring. */
    fun begin(s: RingSession): Boolean = state.compareAndSet(null, s)

    private fun update(f: (RingSession) -> RingSession) = state.update { it?.let(f) }

    /** Sunrise or an abandoned early-dismiss screen both turn into a real ring when its time comes. */
    fun startRinging() {
        val before = state.value ?: return
        update { if (it.phase != Phase.RING) it.copy(phase = Phase.RING, ringingSince = elapsed()) else it }
        if (before.phase != Phase.RING && !before.test) RaaApp.container.scope.launch { RaaApp.container.settings.markRang() }
    }

    fun goQuiet() {
        update { if (it.muted()) it else it.copy(mutedUntil = elapsed() + it.nextQuietMinutes * 60_000L, quietCount = it.quietCount + 1) }
        // Remembered across a restart, so killing the app does not reset the windows to full length.
        val s = state.value ?: return
        if (!s.test) {
            val untilWall = System.currentTimeMillis() + s.mutedLeftMs()
            RaaApp.container.scope.launch { RaaApp.container.settings.saveQuiet(s.quietCount, untilWall) }
        }
    }

    fun setEngaged(on: Boolean) = update { if (it.engaged == on) it else it.copy(engaged = on) }

    fun loseCode() = update { it.copy(lostCode = true) }

    fun setCamera(active: Boolean) = update { it.copy(cameraActive = active) }

    fun setScreenVisible(visible: Boolean) = update { if (it.screenVisible == visible) it else it.copy(screenVisible = visible) }

    /** Leaving the early-dismiss screen without finishing the task keeps the alarm as it was, notice included. */
    fun cancelEarly() {
        val s = state.getAndUpdate { if (it?.phase == Phase.EARLY) null else it } ?: return
        if (s.phase == Phase.EARLY) Notifications.upcoming(RaaApp.container.context, s.alarm, s.ringAt)
    }

    fun dropIfNotReal() = state.update { if (it != null && (it.test || it.phase == Phase.EARLY)) null else it }

    fun finish(outcome: Outcome) {
        val s = state.getAndUpdate { null } ?: return
        RaaApp.container.scope.launch { settle(s, outcome) }
    }

    /** Another alarm fired while one is ringing: try it again once this one is dealt with. */
    suspend fun deferBusy(alarmId: Long) {
        val c = RaaApp.container
        val alarm = c.db.alarms().get(alarmId) ?: return
        val deferred = alarm.copy(snoozeUntil = System.currentTimeMillis() + BUSY_RETRY_MS)
        c.db.alarms().upsert(deferred)
        c.scheduler.schedule(deferred)
    }

    private suspend fun settle(s: RingSession, outcome: Outcome) {
        val c = RaaApp.container
        c.scheduler.disarmWatchdog(s.alarm.id)
        if (s.test) return
        c.settings.saveRingRecord(null)
        val fresh = c.db.alarms().get(s.alarm.id) ?: return
        // If the clock went back past the ring time meanwhile, the occurrence is only handled up to
        // "now": marking a future instant would silently skip the days in between.
        val handled = maxOf(fresh.handledUntil, minOf(s.ringAt, System.currentTimeMillis() + 60_000L))
        val updated = if (outcome == Outcome.SNOOZED) {
            fresh.copy(
                snoozeUntil = System.currentTimeMillis() + fresh.snoozeMinutes * 60_000L,
                snoozeCount = fresh.snoozeCount + 1,
                handledUntil = handled,
            )
        } else {
            fresh.copy(
                snoozeUntil = 0,
                snoozeCount = 0,
                handledUntil = handled,
                enabled = fresh.enabled && fresh.repeating,
            )
        }
        c.db.alarms().upsert(updated)
        c.scheduler.schedule(updated)
        if (outcome == Outcome.GAVE_UP) Notifications.gaveUp(c.context, fresh)
    }
}
