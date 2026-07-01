package com.silentlink.app.manager

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.silentlink.app.model.VolumeLevel

class AudioControlManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun canWriteSettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun canSetMute(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
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

    fun setVolumeLevel(level: VolumeLevel): Boolean {
        return try {
            when (level) {
                VolumeLevel.MUTE -> {
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    } catch (_: SecurityException) {
                        // DND 권한 없음 — 진동으로 폴백
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    }
                    true
                }
                VolumeLevel.VIBRATE -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    true
                }
                VolumeLevel.SOUND -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    if (canWriteSettings()) {
                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                        val target = (max * level.value) / 100
                        if (target > 0) audioManager.setStreamVolume(AudioManager.STREAM_RING, target, 0)
                    }
                    true
                }
            }
        } catch (_: Exception) { false }
    }
}
