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
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "깨워줘 알람"
        val alarmJson = intent.getStringExtra(EXTRA_ALARM_JSON)
        val alarm = alarmJson?.let { runCatching { Gson().fromJson(it, RemoteAlarm::class.java) }.getOrNull() }

        AlarmRingService.start(
            context, alarmId, label,
            alarmSound = alarm?.alarmSound ?: true,
            alarmVibrate = alarm?.alarmVibrate ?: true
        )

        if (alarm != null && alarm.isEnabled) {
            AlarmScheduler(context).schedule(alarm)
        }
    }
}

class AlarmDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmRingService.dismiss(context)
    }
}
