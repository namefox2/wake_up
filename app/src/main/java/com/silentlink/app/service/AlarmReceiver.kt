package com.silentlink.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.silentlink.app.MainActivity
import com.silentlink.app.manager.AlarmScheduler
import com.silentlink.app.model.RemoteAlarm

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_LABEL = "alarm_label"
        const val EXTRA_ALARM_JSON = "alarm_json"
        const val CHANNEL_ID = "silentlink_alarm"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "SilentLink 알람"
        val alarmJson = intent.getStringExtra(EXTRA_ALARM_JSON)

        showNotification(context, alarmId, label)

        // 반복 알람이면 다음 발생 시간으로 재스케줄
        alarmJson?.let {
            val alarm = runCatching { Gson().fromJson(it, RemoteAlarm::class.java) }.getOrNull()
            if (alarm != null && alarm.isEnabled && alarm.days.isNotEmpty()) {
                AlarmScheduler(context).schedule(alarm)
            }
        }
    }

    private fun showNotification(context: Context, alarmId: String, label: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val channel = NotificationChannel(
            CHANNEL_ID, "SilentLink 알람",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setSound(alarmUri, android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build())
        }
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context, alarmId.hashCode() + 1,
            Intent(context, AlarmDismissReceiver::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("⏰  $label")
            .setContentText("SilentLink 알람")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "해제", dismissIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        nm.notify(alarmId.hashCode(), notification)
    }
}

// 알람 해제 버튼용 Receiver
class AlarmDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID) ?: return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(alarmId.hashCode())
    }
}
