package dev.smoreg.raa.wake

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import androidx.core.net.toUri
import dev.smoreg.raa.R
import dev.smoreg.raa.data.Sound
import dev.smoreg.raa.alarm.elapsed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.pow

/** Where the volume ramp starts: -40 dB is a whisper, but clearly audible in a quiet bedroom. */
private const val RAMP_FLOOR_DB = -40f
private const val FALLBACK = "klaxon"

/** Loops the alarm sound on the alarm stream. A file that fails to load falls back to a bundled sound. */
class SoundPlayer(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var startedAt = 0L
    private var pausedAt = 0L
    private var rampMs = 0L
    private var originalVolume = -1
    private var targetVolume = 0

    private var started = false
    private var paused = false
    /** Set by the player's error callback; the next tick drops it so the service starts a fresh one. */
    private var failed = false
    private var failures = 0

    val active get() = started && !failed

    /** The volume to put back afterwards, remembered across a process kill by the ring record. */
    val userVolume get() = originalVolume

    fun rememberUserVolume(volume: Int) {
        if (volume >= 0 && originalVolume < 0) originalVolume = volume
    }

    suspend fun start(sound: String, volumePercent: Int, rampSeconds: Int) {
        if (started) return
        started = true
        if (sound == Sound.SILENT) return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        targetVolume = ceil(max * volumePercent / 100f).toInt().coerceIn(1, max)
        if (originalVolume < 0) originalVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        enforceVolume()
        // A player that failed once (decoder crash, broken file) is not retried: straight to the bundled sound.
        val uri = if (failures == 0) uriOf(sound) else builtinUri(FALLBACK)
        // prepare() reads the file synchronously; on the main thread a slow file is an ANR mid-alarm.
        player = withContext(Dispatchers.IO) { create(uri) ?: create(builtinUri(FALLBACK)) }
        startedAt = elapsed()
        rampMs = rampSeconds * 1000L
        tick()
        player?.start()
    }

    /** Called on every service tick: keeps the ramp going and the volume where the alarm wants it. */
    fun tick() {
        if (failed) {
            failed = false
            failures++
            player?.release()
            player = null
            started = false
            return
        }
        val p = player ?: return
        val t = if (rampMs <= 0) 1f else ((elapsed() - startedAt).toFloat() / rampMs).coerceIn(0f, 1f)
        // Even steps in decibels sound like an even rise; linear amplitude jumps early and then stalls.
        val v = 10f.pow(RAMP_FLOOR_DB * (1f - t) / 20f)
        p.setVolume(v, v)
        enforceVolume()
    }

    fun pause() {
        val p = player ?: return
        if (paused) return
        if (p.isPlaying) p.pause()
        paused = true
        pausedAt = elapsed()
    }

    /** The ramp is frozen while quiet and carries on from where it stopped. */
    fun resume() {
        val p = player ?: return
        if (!paused) return
        paused = false
        startedAt += elapsed() - pausedAt
        tick()
        p.start()
    }

    fun release() {
        player?.release()
        player = null
        started = false
        paused = false
        if (originalVolume >= 0) runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0) }
        originalVolume = -1
    }

    /** Exactly the alarm's level: lower than the phone's alarm volume is as much a setting as higher. */
    private fun enforceVolume() {
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) != targetVolume) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0) }
        }
    }

    private fun create(uri: Uri?): MediaPlayer? {
        uri ?: return null
        val mp = MediaPlayer()
        return runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mp.setDataSource(context, uri)
            mp.isLooping = true
            mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setOnErrorListener { _, _, _ -> failed = true; true }
            mp.prepare()
            mp
        }.getOrElse {
            mp.release()
            null
        }
    }

    private fun uriOf(sound: String): Uri? = when {
        sound.startsWith("builtin:") -> builtinUri(sound.removePrefix("builtin:"))
        sound == Sound.SYSTEM -> RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
        else -> sound.toUri()
    }

    private fun builtinUri(name: String): Uri {
        val res = when (name) {
            "rise_and_shine" -> R.raw.rise_and_shine
            "beeps" -> R.raw.beeps
            "dawn" -> R.raw.dawn
            else -> R.raw.klaxon
        }
        return Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName).appendPath(res.toString()).build()
    }
}
