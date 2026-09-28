package dev.smoreg.raa.wake

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import kotlin.math.ceil

/** Back flashlight with brightness steps where the phone supports them (API 33+). */
class Torch(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val id: String? = runCatching {
        cameras.cameraIdList.firstOrNull {
            val c = cameras.getCameraCharacteristics(it)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()
    private val maxLevel: Int = if (id != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
    } else {
        1
    }
    private var level = -1

    /** 0..1. Without brightness steps the torch only comes on for the last tenth of the ramp. */
    fun set(fraction: Float) {
        val cam = id ?: return
        val next = when {
            fraction <= 0f -> 0
            maxLevel > 1 -> ceil(fraction * maxLevel).toInt().coerceIn(1, maxLevel)
            fraction >= 0.9f -> 1
            else -> 0
        }
        if (next == level) return
        level = next
        runCatching {
            when {
                next == 0 -> cameras.setTorchMode(cam, false)
                maxLevel > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    cameras.turnOnTorchWithStrengthLevel(cam, next)
                else -> cameras.setTorchMode(cam, true)
            }
        }
    }

    fun off() = set(0f)
}
