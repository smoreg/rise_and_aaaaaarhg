package dev.smoreg.raa.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    /** 0 = never stop on its own. */
    val autoStopMinutes: Int = 30,
    val upcomingNotice: Boolean = true,
    val theme: ThemeMode = ThemeMode.DARK,
    /** Manufacturer switches on top of Android (autostart, lock screen, pop-ups); no API can read them. */
    val vendorSettingsDone: Boolean = false,
    val onboardingDone: Boolean = false,
)

/** What was ringing when the process died; lets a reboot resume the alarm instead of escaping it. */
data class RingRecord(val alarmId: Long, val ringAt: Long, val startedAt: Long, val quietCount: Int = 0)

class Settings(context: Context) {
    private val store = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("settings")
    }

    private object K {
        val autoStop = intPreferencesKey("auto_stop_minutes")
        val upcoming = booleanPreferencesKey("upcoming_notice")
        val theme = stringPreferencesKey("theme")
        val vendor = booleanPreferencesKey("vendor_settings_done")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val ringId = longPreferencesKey("ring_alarm_id")
        val ringAt = longPreferencesKey("ring_at")
        val ringStarted = longPreferencesKey("ring_started")
        val ringQuietCount = intPreferencesKey("ring_quiet_count")
    }

    val flow: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current() = flow.first()

    suspend fun update(f: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val s = f(p.toSettings())
            p[K.autoStop] = s.autoStopMinutes
            p[K.upcoming] = s.upcomingNotice
            p[K.theme] = s.theme.name
            p[K.vendor] = s.vendorSettingsDone
            p[K.onboarding] = s.onboardingDone
        }
    }

    suspend fun ringRecord(): RingRecord? {
        val p = store.data.first()
        val id = p[K.ringId] ?: return null
        return RingRecord(id, p[K.ringAt] ?: 0, p[K.ringStarted] ?: 0, p[K.ringQuietCount] ?: 0)
    }

    suspend fun saveRingRecord(r: RingRecord?) {
        store.edit { p ->
            if (r == null) {
                p.remove(K.ringId); p.remove(K.ringAt); p.remove(K.ringStarted); p.remove(K.ringQuietCount)
            } else {
                p[K.ringId] = r.alarmId; p[K.ringAt] = r.ringAt; p[K.ringStarted] = r.startedAt; p[K.ringQuietCount] = r.quietCount
            }
        }
    }

    suspend fun saveQuietCount(count: Int) {
        store.edit { if (it.contains(K.ringId)) it[K.ringQuietCount] = count }
    }

    private fun Preferences.toSettings() = AppSettings(
        autoStopMinutes = this[K.autoStop] ?: 30,
        upcomingNotice = this[K.upcoming] ?: true,
        theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
        vendorSettingsDone = this[K.vendor] ?: false,
        onboardingDone = this[K.onboarding] ?: false,
    )
}
