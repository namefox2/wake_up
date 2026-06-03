package com.silentlink.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.silentlink.app.MainActivity
import com.silentlink.app.manager.AlarmScheduler
import com.silentlink.app.manager.AudioControlManager
import com.silentlink.app.model.RemoteAlarm
import com.silentlink.app.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SilentLinkService : Service() {

    private val repository = FirebaseRepository()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var audioManager: AudioControlManager
    private lateinit var alarmScheduler: AlarmScheduler
    private var commandListenerJob: Job? = null
    private var alarmListenerJob: Job? = null
    private val scheduledAlarmIds = mutableSetOf<String>()
    private var ringerModeReceiver: BroadcastReceiver? = null

    companion object {
        const val CHANNEL_ID = "silentlink_service"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, SilentLinkService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SilentLinkService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = AudioControlManager(this)
        alarmScheduler = AlarmScheduler(this)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        startListeningCommands()
        startListeningAlarms()
        syncActualMuteState()
        registerRingerModeReceiver()
    }

    private fun syncActualMuteState() {
        scope.launch {
            val myUid = repository.getCurrentUserId() ?: return@launch
            runCatching { repository.updateVolumeStatus(myUid, audioManager.getCurrentVolumeLevel()) }
        }
    }

    private fun registerRingerModeReceiver() {
        ringerModeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                    syncActualMuteState()
                }
            }
        }
        registerReceiver(ringerModeReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
    }

    private fun startListeningCommands() {
        commandListenerJob = scope.launch {
            val myUid = repository.getCurrentUserId() ?: return@launch
            repository.observeMyCommands(myUid).collect { commands ->
                commands["setVolume"]?.let {
                    val levelName = it as? String ?: return@let
                    val level = runCatching {
                        com.silentlink.app.model.VolumeLevel.valueOf(levelName)
                    }.getOrNull() ?: return@let

                    if (level == com.silentlink.app.model.VolumeLevel.MUTE && !audioManager.canSetMute()) {
                        showDndPermissionNotification()
                    }

                    val ok = audioManager.setVolumeLevel(level)
                    if (ok) {
                        runCatching { repository.updateVolumeStatus(myUid, audioManager.getCurrentVolumeLevel()) }
                        // 처리 후 명령 삭제 — 서비스 재시작 시 재적용 방지
                        runCatching { repository.deleteCommand(myUid, "setVolume") }
                    }
                }
            }
        }
    }

    private fun startListeningAlarms() {
        alarmListenerJob = scope.launch {
            val myUid = repository.getCurrentUserId() ?: return@launch
            repository.observeAlarms(myUid).collect { alarms ->
                // 삭제된 알람 취소
                val newIds = alarms.map { it.id }.toSet()
                scheduledAlarmIds.forEach { id ->
                    if (id !in newIds) alarmScheduler.cancel(id)
                }
                scheduledAlarmIds.clear()
                // 활성 알람 스케줄
                alarms.filter { it.isEnabled }.forEach { alarm ->
                    alarmScheduler.schedule(alarm)
                    scheduledAlarmIds.add(alarm.id)
                }
            }
        }
    }

    private fun showDndPermissionNotification() {
        val intent = Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS")
        val pi = PendingIntent.getActivity(
            this, 9001, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nm = getSystemService(NotificationManager::class.java)

        // 알림 채널이 없으면 생성
        val channelId = "silentlink_alerts"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "SilentLink 알림", NotificationManager.IMPORTANCE_HIGH)
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("🔕 무음 설정 불가")
            .setContentText("방해금지 접근 허용이 필요합니다. 탭하여 설정하세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(9001, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SilentLink 연결 유지",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "상대방과의 연결을 유지합니다"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SilentLink")
            .setContentText("연결 중...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ringerModeReceiver?.let { runCatching { unregisterReceiver(it) } }
        ringerModeReceiver = null
        commandListenerJob?.cancel()
        alarmListenerJob?.cancel()
        super.onDestroy()
    }
}
