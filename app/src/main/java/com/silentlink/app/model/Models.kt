package com.silentlink.app.model

data class DeviceStatus(
    val isMuted: Boolean = false,
    val volumeLevel: VolumeLevel = VolumeLevel.MEDIUM,
    val isAccessAllowed: Boolean = true,
    val isOnline: Boolean = false,
    val lastUpdated: Long = 0L
)

enum class VolumeLevel(val label: String, val icon: String, val value: Int) {
    MUTE("무음", "🔇", 0),
    LOW("작게", "🔉", 25),
    MEDIUM("보통", "🔊", 60),
    MAX("최대", "📢", 100)
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

sealed class Command {
    data class SetMute(val muted: Boolean) : Command()
    data class SetVolume(val level: VolumeLevel) : Command()
    object Disconnect : Command()
}
