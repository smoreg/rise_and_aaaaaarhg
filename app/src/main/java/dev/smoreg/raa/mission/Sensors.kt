package dev.smoreg.raa.mission

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt

fun hasAccelerometer(context: Context) =
    context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

/**
 * Streams linear acceleration (m/s², gravity removed) and the time since the previous reading.
 * Gravity is tracked with a low-pass filter instead of TYPE_LINEAR_ACCELERATION, which needs a gyroscope.
 */
@Composable
fun ShakeListener(onSample: (accel: Float, dtMs: Long) -> Unit) {
    val context = LocalContext.current
    val sample by rememberUpdatedState(onSample)
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gravity = FloatArray(3)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                var sum = 0f
                for (i in 0..2) {
                    gravity[i] = if (last == 0L) e.values[i] else 0.9f * gravity[i] + 0.1f * e.values[i]
                    val linear = e.values[i] - gravity[i]
                    sum += linear * linear
                }
                val dt = if (last == 0L) 0 else (e.timestamp - last) / 1_000_000
                last = e.timestamp
                sample(sqrt(sum), dt.coerceIn(0, 200))
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
}
