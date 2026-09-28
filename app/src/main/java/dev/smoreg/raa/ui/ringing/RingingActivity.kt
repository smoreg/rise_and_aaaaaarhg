package dev.smoreg.raa.ui.ringing

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.smoreg.raa.RaaApp
import dev.smoreg.raa.alarm.Notifications
import dev.smoreg.raa.alarm.Phase
import dev.smoreg.raa.alarm.RingSession
import dev.smoreg.raa.alarm.Ringer
import dev.smoreg.raa.ui.theme.Night
import dev.smoreg.raa.ui.theme.RaaTheme
import kotlinx.coroutines.launch

class RingingActivity : AppCompatActivity() {
    private var loadingEarly by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onBackPressedDispatcher.addCallback(this) {
            // Back does nothing while it rings; only the early-dismiss screen may be left.
            if (Ringer.session.value?.phase == Phase.EARLY) {
                Ringer.cancelEarly()
                finish()
            }
        }
        handleEarly(intent)

        setContent {
            val session by Ringer.session.collectAsStateWithLifecycle()
            LaunchedEffect(session, loadingEarly) {
                if (session == null && !loadingEarly) finish()
            }
            RaaTheme(Night, dark = true) {
                session?.let { RingingScreen(it) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A stopped instance may already be on its way out; hand the intent to a fresh one.
        if (isFinishing) {
            startActivity(Intent(intent))
            return
        }
        handleEarly(intent)
    }

    private fun handleEarly(intent: Intent?) {
        intent ?: return
        if (!intent.getBooleanExtra(EXTRA_EARLY, false) || Ringer.session.value != null) return
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val at = intent.getLongExtra(EXTRA_AT, 0)
        loadingEarly = true
        lifecycleScope.launch {
            val alarm = RaaApp.container.db.alarms().get(id)
            if (alarm != null && Ringer.session.value == null) {
                Notifications.cancelUpcoming(this@RingingActivity, id)
                Ringer.begin(RingSession(alarm, Phase.EARLY, ringAt = at))
            }
            loadingEarly = false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val ringing = Ringer.session.value?.phase?.let { it != Phase.EARLY } == true
        val volumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        return if (ringing && volumeKey) true else super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        Ringer.setScreenVisible(true)
    }

    override fun onPause() {
        Ringer.setScreenVisible(false)
        super.onPause()
    }

    /** An early-dismiss screen left in the background would otherwise hold the session for hours. */
    override fun onStop() {
        if (Ringer.session.value?.phase == Phase.EARLY) {
            Ringer.cancelEarly()
            finish()
        }
        super.onStop()
    }

    companion object {
        private const val EXTRA_EARLY = "early"
        private const val EXTRA_ID = "alarm_id"
        private const val EXTRA_AT = "at"

        fun early(context: Context, id: Long, ringAt: Long): Intent =
            Intent(context, RingingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_EARLY, true)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_AT, ringAt)
    }
}
