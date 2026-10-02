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

/** What makes the sound: a looping file, or a radio stream already proven to play. */
private interface Voice {
    val broken: Boolean
    fun setVolume(v: Float)
    fun play()
    fun pause()
    fun release()
}

private class FileVoice(private val mp: MediaPlayer) : Voice {
    /** Set by the player's error callback; the next tick drops it so the service starts a fresh one. */
    @Volatile private var failed = false

    init {
        mp.setOnErrorListener { _, _, _ -> failed = true; true }
    }

    override val broken get() = failed
    override fun setVolume(v: Float) = mp.setVolume(v, v)
    override fun play() = mp.start()
    override fun pause() {
        if (mp.isPlaying) mp.pause()
    }
    override fun release() = mp.release()
}

/** A live stream cannot pause: quiet time only mutes it, so it is still live afterwards. */
private class RadioVoice(private val tuner: RadioTuner) : Voice {
    private var level = 0f
    private var muted = false

    override val broken get() = !tuner.alive
    override fun setVolume(v: Float) {
        level = v
        if (!muted) tuner.setVolume(v)
    }
    override fun play() {
        muted = false
        tuner.setVolume(level)
    }
    override fun pause() {
        muted = true
        tuner.setVolume(0f)
    }
    override fun release() = tuner.release()
}

/**
 * Loops the alarm sound on the alarm stream. A file that fails to load falls back to a bundled sound;
 * a radio stream that is not steady, or stops being steady, falls back to the alarm's own sound.
 */
class SoundPlayer(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var voice: Voice? = null
    /** Where the ramp counts from; kept when the radio gives way to the melody mid-ramp. */
    private var startedAt = 0L
    private var pausedAt = 0L
    private var rampMs = 0L
    private var originalVolume = -1
    private var targetVolume = 0

    private var started = false
    private var paused = false
    private var failures = 0
    /** The stream was not ready or broke: the rest of this ring is the melody. */
    private var radioOff = false
    private var radioWait: RadioTuner? = null
    private var radioWaitUntil = 0L

    val active get() = started && voice?.broken != true

    /** The volume to put back afterwards, remembered across a process kill by the ring record. */
    val userVolume get() = originalVolume

    fun rememberUserVolume(volume: Int) {
        if (volume >= 0 && originalVolume < 0) originalVolume = volume
    }

    /**
     * [radio] is used only if it has played steadily for [RadioTuner.STEADY_MS]. With [radioWaitMs]
     * (a test ring, started cold) it may still be connecting: silence until it plays, or the melody.
     */
    suspend fun start(sound: String, volumePercent: Int, rampSeconds: Int, radio: RadioTuner? = null, radioWaitMs: Long = 0) {
        if (started) return
        started = true
        if (sound == Sound.SILENT && radio == null) return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        targetVolume = ceil(max * volumePercent / 100f).toInt().coerceIn(1, max)
        if (originalVolume < 0) originalVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        enforceVolume()
        rampMs = rampSeconds * 1000L
        if (radio != null && !radioOff) {
            when {
                radio.steadyFor(if (radioWaitMs > 0) 0 else RadioTuner.STEADY_MS) -> {
                    radio.claim()
                    begin(RadioVoice(radio))
                    return
                }
                radioWaitMs > 0 && radio.alive -> {
                    radioWait = radio
                    radioWaitUntil = elapsed() + radioWaitMs
                    return
                }
                else -> {
                    radio.release()
                    radioOff = true
                }
            }
        }
        // A player that failed once (decoder crash, broken file) is not retried: straight to the bundled sound.
        val uri = if (failures == 0) uriOf(sound) else builtinUri(FALLBACK)
        // prepare() reads the file synchronously; on the main thread a slow file is an ANR mid-alarm.
        val mp = withContext(Dispatchers.IO) { create(uri) ?: create(builtinUri(FALLBACK)) }
        mp?.let { begin(FileVoice(it)) }
    }

    private fun begin(v: Voice) {
        val now = elapsed()
        if (startedAt == 0L) startedAt = now
        // Swapped while quiet: the quiet stretch does not count towards the ramp.
        if (paused) {
            paused = false
            startedAt += now - pausedAt
        }
        voice = v
        tick()
        v.play()
    }

    /** Called on every service tick: keeps the ramp going and the volume where the alarm wants it. */
    fun tick() {
        radioWait?.let { r ->
            when {
                r.steadyFor(0) -> {
                    radioWait = null
                    r.claim()
                    begin(RadioVoice(r))
                }
                elapsed() > radioWaitUntil || !r.alive -> {
                    radioWait = null
                    r.release()
                    radioOff = true
                    started = false
                }
            }
            return
        }
        val v = voice ?: return
        if (v.broken) {
            if (v is RadioVoice) radioOff = true else failures++
            v.release()
            voice = null
            started = false
            return
        }
        val t = if (rampMs <= 0) 1f else ((elapsed() - startedAt).toFloat() / rampMs).coerceIn(0f, 1f)
        // Even steps in decibels sound like an even rise; linear amplitude jumps early and then stalls.
        val level = 10f.pow(RAMP_FLOOR_DB * (1f - t) / 20f)
        v.setVolume(level)
        enforceVolume()
    }

    fun pause() {
        val v = voice ?: return
        if (paused) return
        v.pause()
        paused = true
        pausedAt = elapsed()
    }

    /** The ramp is frozen while quiet and carries on from where it stopped. */
    fun resume() {
        val v = voice ?: return
        if (!paused) return
        paused = false
        startedAt += elapsed() - pausedAt
        tick()
        v.play()
    }

    fun release() {
        radioWait?.release()
        radioWait = null
        voice?.release()
        voice = null
        started = false
        paused = false
        startedAt = 0
        radioOff = false
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
