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
import com.silentlink.app.model.VolumeLevel
import com.silentlink.app.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SilentLinkService : Service() {

    private val repository = FirebaseRepository()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var audioManager: AudioControlManager
    private lateinit var alarmScheduler: AlarmScheduler
    private val scheduledAlarmIds = mutableSetOf<String>()
    private var ringerModeReceiver: BroadcastReceiver? = null
    @Volatile private var lastSyncMs = 0L
    private var restoreJob: Job? = null
    @Volatile private var restoreLevel: VolumeLevel? = null

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

    // START_STICKY: OS가 서비스를 종료해도 자동으로 재시작
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // Firebase Auth 초기화가 늦어질 수 있으므로 최대 10초 재시도
    private suspend fun awaitUserId(): String? {
        repeat(5) {
            repository.getCurrentUserId()?.let { return it }
            delay(2_000)
        }
        return null
    }

    private fun syncActualMuteState() {
        val now = System.currentTimeMillis()
        if (now - lastSyncMs < 2_000) return
        lastSyncMs = now
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
        scope.launch {
            val myUid = awaitUserId() ?: return@launch
            var backoffMs = 5_000L
            while (isActive) {
                val failed = runCatching {
                    repository.observeMyCommands(myUid).collect { commands ->
                        commands["setVolume"]?.let {
                            val levelName = it as? String ?: return@let
                            val level = runCatching {
                                VolumeLevel.valueOf(levelName)
                            }.getOrNull() ?: return@let

                            if (level == VolumeLevel.MUTE && !audioManager.canSetMute()) {
                                showDndPermissionNotification()
                            }

                            val original = restoreLevel ?: audioManager.getCurrentVolumeLevel()
                            val ok = audioManager.setVolumeLevel(level)
                            if (ok) {
                                restoreLevel = original
                                restoreJob?.cancel()
                                restoreJob = scope.launch {
                                    delay(10 * 60 * 1000L)
                                    val target = restoreLevel ?: return@launch
                                    restoreLevel = null
                                    audioManager.setVolumeLevel(target)
                                    runCatching { repository.updateVolumeStatus(myUid, audioManager.getCurrentVolumeLevel()) }
                                }
                                showVolumeChangedNotification()
                                runCatching { repository.updateVolumeStatus(myUid, audioManager.getCurrentVolumeLevel()) }
                                runCatching { repository.deleteCommand(myUid, "setVolume") }
                            }
                        }
                    }
                }.isFailure
                if (isActive) {
                    delay(if (failed) backoffMs else 2_000L)
                    backoffMs = if (failed) minOf(backoffMs * 2, 60_000L) else 5_000L
                }
            }
        }
    }

    private fun startListeningAlarms() {
        scope.launch {
            val myUid = awaitUserId() ?: return@launch
            var backoffMs = 5_000L
            while (isActive) {
                val failed = runCatching {
                    repository.observeAlarms(myUid).collect { alarms ->
                        val newIds = alarms.map { it.id }.toSet()
                        scheduledAlarmIds.forEach { id ->
                            if (id !in newIds) alarmScheduler.cancel(id)
                        }
                        scheduledAlarmIds.clear()
                        alarms.filter { it.isEnabled }.forEach { alarm ->
                            alarmScheduler.schedule(alarm)
                            scheduledAlarmIds.add(alarm.id)
                        }
                    }
                }.isFailure
                if (isActive) {
                    delay(if (failed) backoffMs else 2_000L)
                    backoffMs = if (failed) minOf(backoffMs * 2, 60_000L) else 5_000L
                }
            }
        }
    }

    private fun showVolumeChangedNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val channelId = "silentlink_alerts"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "SilentLink 알림", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle("볼륨 설정 변경됨")
            .setContentText("10분 후 원래 상태로 돌아갑니다")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(9002, notification)
    }

    private fun showDndPermissionNotification() {
        val intent = Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS")
        val pi = PendingIntent.getActivity(
            this, 9001, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nm = getSystemService(NotificationManager::class.java)
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
        restoreJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
