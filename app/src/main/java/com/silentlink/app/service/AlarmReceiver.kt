package com.silentlink.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.silentlink.app.manager.AlarmScheduler
import com.silentlink.app.model.RemoteAlarm

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_LABEL = "alarm_label"
        const val EXTRA_ALARM_JSON = "alarm_json"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "SilentLink 알람"
        val alarmJson = intent.getStringExtra(EXTRA_ALARM_JSON)

        AlarmRingService.start(context, alarmId, label)

        // 활성 알람은 다음 발생 시간으로 재스케줄
        alarmJson?.let {
            val alarm = runCatching { Gson().fromJson(it, RemoteAlarm::class.java) }.getOrNull()
            if (alarm != null && alarm.isEnabled) {
                AlarmScheduler(context).schedule(alarm)
            }
        }
    }
}

class AlarmDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmRingService.dismiss(context)
    }
}
