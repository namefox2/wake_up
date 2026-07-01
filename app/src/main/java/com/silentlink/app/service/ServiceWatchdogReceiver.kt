package com.silentlink.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

class ServiceWatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        SilentLinkService.start(context)
        schedule(context)
    }

    companion object {
        private const val REQUEST_CODE = 8877
        private const val INTERVAL_MS = 5 * 60 * 1000L // 5분마다

        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = pendingIntent(context)
            val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        }

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, REQUEST_CODE,
                Intent(context, ServiceWatchdogReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
    }
}
