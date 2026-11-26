package com.gptremote.mitv.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticStrength(val label: String) {
    OFF("Off"),
    LIGHT("Subtle"),
    MEDIUM("Crisp"),
    STRONG("Strong")
}

/**
 * Hardware vibrator controller for tactile remote feedback across all buttons and touchpad actions.
 */
class HapticManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var strength: HapticStrength
        get() {
            val saved = prefs.getString(KEY_STRENGTH, HapticStrength.MEDIUM.name)
            return runCatching { HapticStrength.valueOf(saved ?: "") }.getOrDefault(HapticStrength.MEDIUM)
        }
        set(value) {
            prefs.edit().putString(KEY_STRENGTH, value.name).apply()
        }

    fun tick() {
        perform(durationMs = 12L, amplitudeScale = 0.45f, predefinedId = VibrationEffect.EFFECT_TICK)
    }

    fun click() {
        perform(durationMs = 20L, amplitudeScale = 0.72f, predefinedId = VibrationEffect.EFFECT_CLICK)
    }

    fun heavyClick() {
        perform(durationMs = 34L, amplitudeScale = 1.0f, predefinedId = VibrationEffect.EFFECT_HEAVY_CLICK)
    }

    private fun perform(durationMs: Long, amplitudeScale: Float, predefinedId: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val current = strength
        if (current == HapticStrength.OFF) return

        val multiplier = when (current) {
            HapticStrength.OFF -> return
            HapticStrength.LIGHT -> 0.55f
            HapticStrength.MEDIUM -> 0.85f
            HapticStrength.STRONG -> 1.0f
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && current == HapticStrength.MEDIUM) {
            vib.vibrate(VibrationEffect.createPredefined(predefinedId))
            return
        }

        val scaledMs = (durationMs * multiplier).toLong().coerceAtLeast(8L)
        val amplitude = if (vib.hasAmplitudeControl()) {
            (255f * amplitudeScale * multiplier).toInt().coerceIn(25, 255)
        } else {
            VibrationEffect.DEFAULT_AMPLITUDE
        }
        vib.vibrate(VibrationEffect.createOneShot(scaledMs, amplitude))
    }

    companion object {
        private const val PREFS_NAME = "haptic_prefs"
        private const val KEY_STRENGTH = "haptic_strength"
    }
}
