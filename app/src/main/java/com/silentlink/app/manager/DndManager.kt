package com.silentlink.app.manager

import android.app.NotificationManager
import android.content.Context
import com.silentlink.app.model.DndSchedule
import java.util.Calendar

class DndManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun hasNotificationPolicyAccess(): Boolean {
        return notificationManager.isNotificationPolicyAccessGranted
    }

    fun isInDndSchedule(schedules: List<DndSchedule>): Boolean {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentDay = when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 0
        }

        return schedules.filter { it.isEnabled }.any { schedule ->
            if (currentDay !in schedule.days) return@any false

            val startMinutes = schedule.startHour * 60 + schedule.startMinute
            val endMinutes = schedule.endHour * 60 + schedule.endMinute
            val currentMinutes = currentHour * 60 + currentMinute

            if (startMinutes <= endMinutes) {
                currentMinutes in startMinutes..endMinutes
            } else {
                // 자정 걸치는 경우 (예: 22:00 ~ 07:00)
                currentMinutes >= startMinutes || currentMinutes <= endMinutes
            }
        }
    }
}
