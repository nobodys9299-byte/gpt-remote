package com.gptremote.mitv.haptic

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants

enum class HapticStrength(val label: String) {
    OFF("Off"),
    LIGHT("Subtle"),
    MEDIUM("Crisp"),
    STRONG("Strong")
}

/**
 * Hardware vibrator + optional UI sound click controller.
 */
class HapticManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var strength: HapticStrength
        get() {
            val saved = prefs.getString(KEY_STRENGTH, HapticStrength.MEDIUM.name)
            return runCatching { HapticStrength.valueOf(saved ?: "") }.getOrDefault(HapticStrength.MEDIUM)
        }
        set(value) {
            prefs.edit().putString(KEY_STRENGTH, value.name).apply()
        }

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND, value).apply()
        }

    fun tick() {
        perform(lightMs = 14L, mediumMs = 26L, strongMs = 42L, playSound = false)
    }

    fun click() {
        perform(lightMs = 24L, mediumMs = 42L, strongMs = 68L, playSound = true)
    }

    fun heavyClick() {
        perform(lightMs = 34L, mediumMs = 58L, strongMs = 90L, playSound = true)
    }

    private fun perform(lightMs: Long, mediumMs: Long, strongMs: Long, playSound: Boolean) {
        if (playSound && soundEnabled) {
            runCatching {
                audioManager?.playSoundEffect(SoundEffectConstants.CLICK, 0.9f)
            }
        }

        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val current = strength
        if (current == HapticStrength.OFF) return

        val (durationMs, amp) = when (current) {
            HapticStrength.OFF -> return
            HapticStrength.LIGHT -> lightMs to 145
            HapticStrength.MEDIUM -> mediumMs to 215
            HapticStrength.STRONG -> strongMs to 255
        }

        val amplitude = if (vib.hasAmplitudeControl()) amp else VibrationEffect.DEFAULT_AMPLITUDE
        vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
    }

    companion object {
        private const val PREFS_NAME = "haptic_prefs"
        private const val KEY_STRENGTH = "haptic_strength"
        private const val KEY_SOUND = "sound_enabled"
    }
}
