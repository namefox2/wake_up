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
                    exitDndIfActive()  // DND 활성 상태면 먼저 해제 시도
                    try {
                        am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    } catch (_: SecurityException) {
                        // 무음 실패 시 진동으로 폴백 (진동도 DND로 막힐 수 있으므로 별도 try-catch)
                        try { am.ringerMode = AudioManager.RINGER_MODE_VIBRATE } catch (_: SecurityException) { }
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

    private fun exitDndIfActive() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.isNotificationPolicyAccessGranted &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }
}
