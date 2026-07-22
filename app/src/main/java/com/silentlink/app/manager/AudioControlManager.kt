package com.silentlink.app.manager

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.silentlink.app.model.VolumeLevel

class AudioControlManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    fun canSetMute(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    fun isMuted(): Boolean {
        val mode = audioManager?.ringerMode ?: return false
        return mode == AudioManager.RINGER_MODE_SILENT || mode == AudioManager.RINGER_MODE_VIBRATE
    }

    fun getCurrentVolumeLevel(): VolumeLevel {
        return when (audioManager?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT  -> VolumeLevel.MUTE
            AudioManager.RINGER_MODE_VIBRATE -> VolumeLevel.VIBRATE
            else                             -> VolumeLevel.SOUND
        }
    }

    fun setVolumeLevel(level: VolumeLevel): Boolean {
        val am = audioManager ?: return false
        return try {
            when (level) {
                VolumeLevel.MUTE -> {
                    try {
                        am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    } catch (_: SecurityException) {
                        am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    }
                    true
                }
                VolumeLevel.VIBRATE -> {
                    exitDndIfActive()
                    am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    true
                }
                VolumeLevel.SOUND -> {
                    exitDndIfActive()
                    am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    if (canWriteSettings()) {
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_RING)
                        val target = (max * level.value) / 100
                        if (target > 0) am.setStreamVolume(AudioManager.STREAM_RING, target, 0)
                    }
                    true
                }
            }
        } catch (_: Exception) { false }
    }

    fun canSetMuteOrVolume() = canSetMute()

    private fun exitDndIfActive() {
        val am = audioManager ?: return
        if (am.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm?.isNotificationPolicyAccessGranted == true) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }
    }
}
