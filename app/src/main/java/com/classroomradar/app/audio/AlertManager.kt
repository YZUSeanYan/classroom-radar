package com.classroomradar.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.classroomradar.app.data.AppPreferences
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Local haptic and sound alert. Notification delivery remains in RecordingService. */
class AlertManager(private val context: Context, private val preferences: AppPreferences) {
    fun alert() {
        if (preferences.vibrationEnabled) vibrate()
        if (preferences.soundEnabled) playTone()
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        } ?: return
        val pattern = when (preferences.vibrationPattern) {
            AppPreferences.VIBRATION_URGENT -> longArrayOf(0, 90, 50, 90, 50, 240)
            AppPreferences.VIBRATION_GENTLE -> longArrayOf(0, 260)
            else -> longArrayOf(0, 130, 70, 230)
        }
        // minSdk is 29, so the modern VibrationEffect path is always available.
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun playTone() {
        // Sound only makes sense on headphones: a classroom alert must never blast from the
        // speaker. The tone is a soft synthesized chime instead of the harsh system beep.
        if (!headphoneConnected()) return
        Thread { playChime() }.start()
    }

    private fun playChime() {
        val sampleRate = 22_050
        val durationMs = 340
        val totalSamples = sampleRate * durationMs / 1000
        val pcm = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            // B5 → E6 upward two-note chime with exponential decay.
            val note = if (t < 0.15) 987.77 else 1318.51
            val envelope = exp(-5.0 * t / (durationMs / 1_000.0))
            pcm[i] = (sin(2.0 * PI * note * t) * envelope * CHIME_AMPLITUDE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(totalSamples * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm, 0, totalSamples)
            track.play()
            Thread.sleep(durationMs + 80L)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun headphoneConnected(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        if (audio.isBluetoothA2dpOn || audio.isBluetoothScoOn) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
            }
        }
        @Suppress("DEPRECATION")
        return audio.isWiredHeadsetOn
    }

    private companion object {
        const val CHIME_AMPLITUDE = 10_000
    }
}
