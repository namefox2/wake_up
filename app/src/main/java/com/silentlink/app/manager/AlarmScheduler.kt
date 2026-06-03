package com.silentlink.app.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.gson.Gson
import com.silentlink.app.model.RemoteAlarm
import com.silentlink.app.service.AlarmReceiver
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: RemoteAlarm) {
        if (!alarm.isEnabled) return
        val triggerAt = nextTriggerTime(alarm) ?: return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label.ifEmpty { "SilentLink 알람" })
            putExtra(AlarmReceiver.EXTRA_ALARM_JSON, Gson().toJson(alarm))
        }
        val pending = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // 정확한 알람 권한 미허용 시 ±10분 허용 윈도우로 대체
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000L, pending)
            } else {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pending), pending)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(alarmId: String) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    fun nextTriggerTime(alarm: RemoteAlarm): Long? {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // 이미 지난 시간이면 내일부터 탐색
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        // 최대 14일 내에서 다음 유효한 날 탐색
        repeat(14) {
            val dayNum = cal.get(Calendar.DAY_OF_WEEK).toLocalDayNum()
            val isTargetDay = alarm.days.isEmpty() || dayNum in alarm.days
            val isHoliday = alarm.excludeHolidays && KoreanHolidays.isHoliday(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            if (isTargetDay && !isHoliday) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    private fun Int.toLocalDayNum(): Int = when (this) {
        Calendar.MONDAY    -> 1
        Calendar.TUESDAY   -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY  -> 4
        Calendar.FRIDAY    -> 5
        Calendar.SATURDAY  -> 6
        Calendar.SUNDAY    -> 7
        else               -> 0
    }
}
