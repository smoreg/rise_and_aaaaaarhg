package dev.smoreg.raa.wake

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.smoreg.raa.BuildConfig
import dev.smoreg.raa.alarm.elapsed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val USER_AGENT = "RiseAndAaaaaaagh/${BuildConfig.VERSION_NAME}"

private const val CONNECT_TIMEOUT_MS = 15_000L
/** Buffering this long counts as a dead stream: an alarm must not stutter in silence. */
private const val STALL_MS = 5_000L
private const val RETRY_DELAY_MS = 3_000L
private const val WATCH_MS = 500L

/**
 * One internet radio stream, started muted ahead of the alarm so that by ring time it is known to
 * play. Anything short of steady playback is a failure, and the alarm plays its melody instead.
 * Main thread only.
 */
class RadioTuner(private val context: Context) {
    enum class State { IDLE, CONNECTING, PLAYING, FAILED }

    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private var player: ExoPlayer? = null
    private var url = ""
    var alarmId = -1L
        private set
    /** A ringing alarm plays it: no more retries, and a warm-up for another alarm must not replace it. */
    var claimed = false
        private set
    private var retriesLeft = 0
    private var connectingSince = 0L
    /** [elapsed] since playback has gone on without a stall; 0 while it has not. */
    private var steadySince = 0L
    private var stalledSince = 0L

    val alive get() = state.value == State.CONNECTING || state.value == State.PLAYING

    fun warmUp(alarmId: Long, url: String) {
        if (claimed) return
        if (this.alarmId == alarmId && this.url == url && alive) return
        release()
        this.alarmId = alarmId
        this.url = url
        retriesLeft = 1
        connect()
    }

    /** Playing without a stall for at least [ms]; 0 means playing right now. */
    fun steadyFor(ms: Long) = player != null && steadySince > 0 && stalledSince == 0L && elapsed() - steadySince >= ms

    fun claim() {
        claimed = true
    }

    fun setVolume(v: Float) {
        player?.volume = v
    }

    fun release() {
        handler.removeCallbacks(reconnect)
        drop()
        claimed = false
        alarmId = -1
        url = ""
        mutableState.value = State.IDLE
    }

    // Cross-protocol redirects and a custom source factory are marked unstable, not deprecated.
    @OptIn(UnstableApi::class)
    private fun connect() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            // Station links often redirect from http to https.
            .setAllowCrossProtocolRedirects(true)
        val p = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_ALARM).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                false,
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        p.volume = 0f
        p.addListener(listener)
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.play()
        player = p
        connectingSince = elapsed()
        steadySince = 0
        stalledSince = 0
        mutableState.value = State.CONNECTING
        handler.postDelayed(watch, WATCH_MS)
    }

    private fun drop() {
        handler.removeCallbacks(watch)
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
        steadySince = 0
        stalledSince = 0
    }

    private fun fail() {
        drop()
        if (!claimed && retriesLeft > 0) {
            retriesLeft--
            handler.postDelayed(reconnect, RETRY_DELAY_MS)
        } else {
            mutableState.value = State.FAILED
        }
    }

    private val reconnect = Runnable { connect() }

    private val watch = object : Runnable {
        override fun run() {
            val now = elapsed()
            when {
                state.value == State.CONNECTING && now - connectingSince > CONNECT_TIMEOUT_MS -> fail()
                stalledSince > 0 && now - stalledSince > STALL_MS -> fail()
                else -> handler.postDelayed(this, WATCH_MS)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    stalledSince = 0
                    if (steadySince == 0L) steadySince = elapsed()
                    mutableState.value = State.PLAYING
                }
                // A rebuffer restarts the count: the stream has to prove itself again.
                Player.STATE_BUFFERING -> if (state.value == State.PLAYING) {
                    steadySince = 0
                    stalledSince = elapsed()
                }
                Player.STATE_ENDED -> fail()
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) = fail()
    }

    companion object {
        /** How long the stream must play without a stall before the alarm trusts it. */
        const val STEADY_MS = 20_000L
    }
}
