package com.gptremote.mitv.haptic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class HapticStrength(val label: String) {
    OFF("Off"),
    LIGHT("Subtle"),
    MEDIUM("Crisp"),
    STRONG("Strong")
}

/**
 * Hardware vibrator + synthesized low-latency UI click sound controller.
 * Uses [SoundPool] over USAGE_MEDIA so button click sounds work reliably even when
 * Android's system "Touch sounds" setting is muted/off.
 */
class HapticManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val soundPool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }

    @Volatile
    private var clickSoundId: Int = 0

    @Volatile
    private var clickSoundLoaded: Boolean = false

    init {
        initClickSound()
    }

    private fun initClickSound() {
        thread(name = "gpt-click-sfx", isDaemon = true) {
            runCatching {
                val wavFile = File(appContext.cacheDir, "gpt_ui_click_v2.wav")
                if (!wavFile.exists() || wavFile.length() < 64L) {
                    writeClickWav(wavFile)
                }
                val pool = soundPool
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0 && sampleId == clickSoundId) {
                        clickSoundLoaded = true
                    }
                }
                clickSoundId = pool.load(wavFile.absolutePath, 1)
            }
        }
    }

    private fun writeClickWav(target: File) {
        val sampleRate = 44100
        val durationSec = 0.018
        val numSamples = (sampleRate * durationSec).toInt()
        val dataBytes = numSamples * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1.toShort()) // PCM format
            putShort(1.toShort()) // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }
        val pcm = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            val t = i / sampleRate.toDouble()
            // Crisp mechanical pop: short initial attack + dual harmonic decay
            val env = exp(-t * 320.0)
            val wave = (
                0.65 * sin(2.0 * PI * 1450.0 * t) +
                    0.35 * sin(2.0 * PI * 3100.0 * t)
                ) * env
            val sample = (wave * 26000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm.putShort(sample.toShort())
        }
        FileOutputStream(target).use { out ->
            out.write(header.array())
            out.write(pcm.array())
        }
    }

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
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND, value).apply()
        }

    fun tick() {
        perform(lightMs = 14L, mediumMs = 26L, strongMs = 42L, playSound = false, gain = 0f)
    }

    fun click() {
        perform(lightMs = 24L, mediumMs = 42L, strongMs = 68L, playSound = true, gain = 0.88f)
    }

    fun heavyClick() {
        perform(lightMs = 34L, mediumMs = 58L, strongMs = 90L, playSound = true, gain = 1.0f)
    }

    private fun perform(
        lightMs: Long,
        mediumMs: Long,
        strongMs: Long,
        playSound: Boolean,
        gain: Float
    ) {
        if (playSound && soundEnabled) {
            var played = false
            if (clickSoundLoaded && clickSoundId != 0) {
                val streamId = runCatching {
                    soundPool.play(clickSoundId, gain, gain, 1, 0, if (gain >= 0.99f) 0.94f else 1.06f)
                }.getOrDefault(0)
                if (streamId > 0) {
                    played = true
                }
            }
            if (!played) {
                runCatching {
                    audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, gain)
                }
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
