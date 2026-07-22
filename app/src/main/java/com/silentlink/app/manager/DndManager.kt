package com.silentlink.app.manager

import android.app.NotificationManager
import android.content.Context
import com.silentlink.app.model.DndConfig
import java.util.Calendar

class DndManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    fun hasNotificationPolicyAccess(): Boolean {
        return notificationManager?.isNotificationPolicyAccessGranted == true
    }

    fun isInDndTime(config: DndConfig): Boolean {
        if (!config.isEnabled) return false
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_WEEK).toLocalDayNum()
        if (currentDay !in config.days) return false

        val startMin = config.startHour * 60 + config.startMinute
        val endMin   = config.endHour   * 60 + config.endMinute
        val nowMin   = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return if (startMin <= endMin) nowMin in startMin..endMin
        else nowMin >= startMin || nowMin <= endMin
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
