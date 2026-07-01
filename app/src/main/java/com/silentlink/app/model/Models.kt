package com.silentlink.app.model

data class PartnerState(
    val uid: String = "",
    val status: DeviceStatus = DeviceStatus(),
    val alarmsForThem: List<RemoteAlarm> = emptyList()
)

data class ControllerState(
    val uid: String = "",
    val inviteCode: String = ""
)

data class DeviceStatus(
    val isMuted: Boolean = false,
    val volumeLevel: VolumeLevel = VolumeLevel.SOUND,
    val isAccessAllowed: Boolean = true,
    val isOnline: Boolean = false,
    val lastUpdated: Long = 0L,
    val activity: UserActivity = UserActivity.NONE
)

enum class UserActivity(val label: String, val emoji: String, val description: String) {
    NONE("없음", "", ""),
    MOVIE("영화감상중", "🎬", "영화나 드라마를 보고 있어요"),
    EXAM("시험중", "📝", "시험 또는 공부 중이에요"),
    MEETING("회의중", "💼", "회의 또는 업무 중이에요"),
    DRIVING("운전중", "🚗", "운전 중이라 답장이 어려워요"),
    CLASS("수업중", "📚", "수업이나 강의 중이에요"),
    SLEEPING("취침중", "🌙", "자고 있어요"),
    WORKOUT("운동중", "🏃", "운동 중이에요"),
    CONCERT("공연관람중", "🎵", "공연이나 콘서트 중이에요")
}

enum class VolumeLevel(val label: String, val icon: String, val value: Int) {
    MUTE("무음", "🔇", 0),
    VIBRATE("진동", "📳", 0),
    SOUND("소리", "🔊", 60)
}

data class DndSchedule(
    val id: String = "",
    val name: String = "",
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val days: Set<Int> = emptySet(), // 1=월, 2=화, 3=수, 4=목, 5=금, 6=토, 7=일
    val isEnabled: Boolean = true
)

data class ConnectionInfo(
    val myCode: String = "",
    val partnerCode: String = "",
    val isConnected: Boolean = false,
    val connectedAt: Long = 0L
)

enum class AppTheme(val displayName: String) {
    DARK("다크"),
    LIGHT("라이트"),
    PURPLE("퍼플"),
    FOREST("포레스트"),
    SUNSET("선셋"),
    PINK("핑크")
}

data class RemoteAlarm(
    val id: String = "",
    val label: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5), // 1=월..7=일, 빈 Set=매일
    val isEnabled: Boolean = true,
    val createdAt: Long = 0L,
    val alarmSound: Boolean = true,
    val alarmVibrate: Boolean = true
) {
    fun displayTime(): String {
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val amPm = if (hour < 12) "오전" else "오후"
        return "$amPm %d:%02d".format(h12, minute)
    }
}

data class DndConfig(
    val isEnabled: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7) // 기본: 매일
)

sealed class Command {
    data class SetMute(val muted: Boolean) : Command()
    data class SetVolume(val level: VolumeLevel) : Command()
    object Disconnect : Command()
}
