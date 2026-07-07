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
import com.silentlink.app.DndPrefs
import com.silentlink.app.MainActivity
import com.silentlink.app.manager.AlarmScheduler
import com.silentlink.app.manager.AudioControlManager
import com.silentlink.app.manager.DndManager
import com.silentlink.app.model.VolumeLevel
import com.silentlink.app.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SilentLinkService : Service() {

    private val repository = FirebaseRepository()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        ServiceLogger.log(this, "SERVICE", "코루틴 예외: ${throwable.javaClass.simpleName}: ${throwable.message}")
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    private lateinit var audioManager: AudioControlManager
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var dndManager: DndManager
    private val scheduledAlarmIds = mutableSetOf<String>()
    private var ringerModeReceiver: BroadcastReceiver? = null
    @Volatile private var lastSyncMs = 0L
    private var restoreJob: Job? = null
    @Volatile private var restoreLevel: VolumeLevel? = null
    @Volatile private var myAccessAllowed = true
    @Volatile private var lastKnownAlarms: List<com.silentlink.app.model.RemoteAlarm> = emptyList()
    private val alarmMutex = Mutex()

    private val servicePrefs by lazy {
        getSharedPreferences("silentlink_restore", Context.MODE_PRIVATE)
    }

    companion object {
        const val CHANNEL_ID = "silentlink_service"
        const val NOTIFICATION_ID = 1001
        private const val KEY_RESTORE_LEVEL = "restore_level"
        private const val KEY_RESTORE_AT_MS = "restore_at_ms"

        fun start(context: Context) {
            val intent = Intent(context, SilentLinkService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                ServiceLogger.log(context, "SERVICE", "startForegroundService 실패: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SilentLinkService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Catch any uncaught exception in this process and write it to the debug log
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            ServiceLogger.log(this, "CRASH", "${e.javaClass.simpleName} in ${t.name}: ${e.message}")
            prevHandler?.uncaughtException(t, e)
        }
        ServiceLogger.log(this, "SERVICE", "onCreate [A] 배터리최적화=${ServiceLogger.batteryOptStatus(this)}")
        audioManager = AudioControlManager(this)
        alarmScheduler = AlarmScheduler(this)
        dndManager = DndManager(this)
        ServiceLogger.log(this, "SERVICE", "onCreate [B] 매니저 초기화완료")
        createNotificationChannel()
        ServiceLogger.log(this, "SERVICE", "onCreate [C] 채널 생성완료")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ServiceLogger.log(this, "SERVICE", "onCreate [D] startForeground 성공")
        } catch (e: Exception) {
            ServiceLogger.log(this, "SERVICE", "onCreate [D] startForeground 실패: ${e.javaClass.simpleName}: ${e.message}")
            stopSelf()
            return
        }
        startListeningCommands()
        startListeningAlarms()
        startListeningMyAccess()
        startListeningControllers()
        syncActualMuteState()
        registerRingerModeReceiver()
        checkPendingRestore()
        ServiceWatchdogReceiver.schedule(this)
        ServiceLogger.log(this, "SERVICE", "onCreate [E] 완료")
    }

    // START_STICKY: OS가 서비스를 종료해도 자동으로 재시작
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = if (intent == null) "START_STICKY재시작" else "명시적시작"
        ServiceLogger.log(this, "SERVICE", "onStartCommand reason=$reason flags=$flags startId=$startId")
        return START_STICKY
    }

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
            val myUid = repository.getCurrentUserId() ?: awaitUserId() ?: return@launch
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
            // 인증 완료 직후 현재 볼륨 상태를 Firebase에 확실하게 동기화
            runCatching { repository.updateVolumeStatus(myUid, audioManager.getCurrentVolumeLevel()) }
            var backoffMs = 5_000L
            while (isActive) {
                val failed = runCatching {
                    repository.observeMyCommands(myUid).collect { commands ->
                        commands["setVolume"]?.let {
                            val levelName = it as? String ?: return@let
                            val level = runCatching {
                                VolumeLevel.valueOf(levelName)
                            }.getOrNull() ?: return@let

                            // 방해금지 시간 중 수신된 명령은 무시하고 삭제
                            val dndConfig = DndPrefs.load(this@SilentLinkService)
                            if (dndManager.isInDndTime(dndConfig)) {
                                runCatching { repository.deleteCommand(myUid, "setVolume") }
                                return@let
                            }

                            val original = restoreLevel ?: audioManager.getCurrentVolumeLevel()
                            val ok = audioManager.setVolumeLevel(level)
                            val actualLevel = audioManager.getCurrentVolumeLevel()
                            if (ok) {
                                // 무음 요청인데 실제로는 진동이 된 경우 = DND 권한 없음
                                if (level == VolumeLevel.MUTE && actualLevel != VolumeLevel.MUTE) {
                                    showDndPermissionNotification()
                                }
                                scheduleRestore(original)
                                showVolumeChangedNotification()
                                runCatching { repository.updateVolumeStatus(myUid, actualLevel) }
                            }
                            runCatching { repository.deleteCommand(myUid, "setVolume") }
                        }
                    }
                }.isFailure
                if (isActive) {
                    if (failed) ServiceLogger.log(this@SilentLinkService, "FIREBASE", "명령 수신 실패, ${backoffMs}ms 후 재시도")
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
                        lastKnownAlarms = alarms
                        resyncAlarms()
                    }
                }.isFailure
                if (isActive) {
                    delay(if (failed) backoffMs else 2_000L)
                    backoffMs = if (failed) minOf(backoffMs * 2, 60_000L) else 5_000L
                }
            }
        }
    }

    private fun startListeningMyAccess() {
        scope.launch {
            val myUid = awaitUserId() ?: return@launch
            repository.observeAccessAllowed(myUid).collect { allowed ->
                if (myAccessAllowed != allowed) {
                    myAccessAllowed = allowed
                    resyncAlarms()
                }
            }
        }
    }

    private suspend fun resyncAlarms() = alarmMutex.withLock {
        val alarms = lastKnownAlarms
        val newIds = alarms.map { it.id }.toSet()
        scheduledAlarmIds.forEach { id -> if (id !in newIds) alarmScheduler.cancel(id) }
        scheduledAlarmIds.clear()
        if (myAccessAllowed) {
            alarms.filter { it.isEnabled }.forEach { alarm ->
                alarmScheduler.schedule(alarm)
                scheduledAlarmIds.add(alarm.id)
            }
        }
    }

    private fun scheduleRestore(original: VolumeLevel) {
        val restoreAtMs = System.currentTimeMillis() + 10 * 60 * 1000L
        restoreLevel = original
        servicePrefs.edit()
            .putString(KEY_RESTORE_LEVEL, original.name)
            .putLong(KEY_RESTORE_AT_MS, restoreAtMs)
            .apply()
        restoreJob?.cancel()
        restoreJob = scope.launch {
            val delayMs = restoreAtMs - System.currentTimeMillis()
            if (delayMs > 0) delay(delayMs)
            doRestore()
        }
    }

    private fun checkPendingRestore() {
        val levelName = servicePrefs.getString(KEY_RESTORE_LEVEL, null) ?: return
        val restoreAtMs = servicePrefs.getLong(KEY_RESTORE_AT_MS, 0L)
        val level = runCatching { VolumeLevel.valueOf(levelName) }.getOrNull() ?: run {
            servicePrefs.edit().remove(KEY_RESTORE_LEVEL).remove(KEY_RESTORE_AT_MS).apply()
            return
        }
        restoreLevel = level
        restoreJob?.cancel()
        // 볼륨 복원은 Firebase 초기화와 무관하게 바로 진행
        restoreJob = scope.launch {
            val delayMs = restoreAtMs - System.currentTimeMillis()
            if (delayMs > 0) delay(delayMs)
            doRestore()
        }
    }

    private suspend fun doRestore() {
        val target = restoreLevel ?: return
        restoreLevel = null
        servicePrefs.edit().remove(KEY_RESTORE_LEVEL).remove(KEY_RESTORE_AT_MS).apply()
        audioManager.setVolumeLevel(target)
        // Firebase 상태 업데이트는 비동기로 — 볼륨 복원 자체를 막지 않음
        scope.launch {
            val myUid = repository.getCurrentUserId() ?: awaitUserId() ?: return@launch
            runCatching { repository.updateVolumeStatus(myUid, audioManager.getCurrentVolumeLevel()) }
        }
    }

    private fun startListeningControllers() {
        scope.launch {
            val myUid = awaitUserId() ?: return@launch
            var initialized = false
            var prevControllers = emptySet<String>()
            repository.observeControllers(myUid).collect { uids ->
                val current = uids.toSet()
                if (initialized) {
                    val newOnes = current - prevControllers
                    if (newOnes.isNotEmpty() && (!audioManager.canSetMute() || !audioManager.canWriteSettings())) {
                        showPermissionGuideNotification()
                    }
                }
                initialized = true
                prevControllers = current
            }
        }
    }

    private fun showPermissionGuideNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val channelId = "silentlink_alerts"
        val openIntent = PendingIntent.getActivity(
            this, 9003,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "settings")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("새 기기가 나를 등록했습니다")
            .setContentText("볼륨 제어를 받으려면 권한 설정이 필요합니다. 탭하여 설정하세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(9003, notification)
    }

    private fun showVolumeChangedNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val channelId = "silentlink_alerts"
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
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "SilentLink 연결 유지", NotificationManager.IMPORTANCE_LOW).apply {
                description = "상대방과의 연결을 유지합니다"
            }
        )
        // Alerts channel: created once here with HIGH importance.
        // Android ignores subsequent createNotificationChannel calls with lower importance,
        // so this must be the first (and only) registration.
        nm.createNotificationChannel(
            NotificationChannel("silentlink_alerts", "깨워줘 알림", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("깨워줘")
            .setContentText("연결 중...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ServiceLogger.log(this, "SERVICE", "onDestroy 배터리최적화=${ServiceLogger.batteryOptStatus(this)}")
        ringerModeReceiver?.let { runCatching { unregisterReceiver(it) } }
        ringerModeReceiver = null
        restoreJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
