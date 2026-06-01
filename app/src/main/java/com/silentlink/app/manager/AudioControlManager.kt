package com.silentlink.app.manager

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.silentlink.app.model.VolumeLevel

class AudioControlManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun canWriteSettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun isMuted(): Boolean {
        return audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT ||
               audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
    }

    fun getCurrentVolumeLevel(): VolumeLevel {
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT  -> VolumeLevel.MUTE
            AudioManager.RINGER_MODE_VIBRATE -> VolumeLevel.VIBRATE
            else                             -> VolumeLevel.SOUND
        }
    }

    fun setMute(muted: Boolean): Boolean {
        if (!canWriteSettings()) return false
        return try {
            if (muted) {
                try {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                } catch (_: SecurityException) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            true
        } catch (_: Exception) { false }
    }

    fun setVolumeLevel(level: VolumeLevel): Boolean {
        if (!canWriteSettings()) return false
        return try {
            when (level) {
                VolumeLevel.MUTE    -> setMute(true)
                VolumeLevel.VIBRATE -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    true
                }
                VolumeLevel.SOUND   -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                    val target = (max * level.value) / 100
                    if (target > 0) audioManager.setStreamVolume(AudioManager.STREAM_RING, target, 0)
                    true
                }
            }
        } catch (_: Exception) { false }
    }
}
