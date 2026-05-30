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
        return audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
    }

    fun getCurrentVolumePercent(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        return if (max > 0) (current * 100) / max else 0
    }

    fun setMute(muted: Boolean) {
        if (!canWriteSettings()) return
        audioManager.ringerMode = if (muted) {
            AudioManager.RINGER_MODE_SILENT
        } else {
            AudioManager.RINGER_MODE_NORMAL
        }
    }

    fun setVolumeLevel(level: VolumeLevel) {
        if (!canWriteSettings()) return
        when (level) {
            VolumeLevel.MUTE -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
            else -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                val targetVolume = (max * level.value) / 100
                audioManager.setStreamVolume(
                    AudioManager.STREAM_RING,
                    targetVolume,
                    0
                )
            }
        }
    }
}
