package com.silentlink.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.silentlink.app.DndPrefs
import com.silentlink.app.MainActivity
import com.silentlink.app.manager.DndManager
import kotlinx.coroutines.flow.MutableStateFlow

class AlarmRingService : Service() {

    private var ringtone: Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable { stopSelf() }

    companion object {
        const val CHANNEL_ID = "silentlink_alarm_ring"
        const val NOTIFICATION_ID = 2001
        const val ACTION_DISMISS = "com.silentlink.app.ALARM_DISMISS"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_LABEL = "alarm_label"
        const val EXTRA_ALARM_SOUND = "alarm_sound"
        const val EXTRA_ALARM_VIBRATE = "alarm_vibrate"
        private const val AUTO_DISMISS_MS = 60 * 1000L // 1분 후 자동 해제

        val isRinging = MutableStateFlow(false)
        var ringingLabel = ""
            private set

        fun start(context: Context, alarmId: String, label: String, alarmSound: Boolean = true, alarmVibrate: Boolean = true) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ALARM_LABEL, label)
                putExtra(EXTRA_ALARM_SOUND, alarmSound)
                putExtra(EXTRA_ALARM_VIBRATE, alarmVibrate)
            }
            context.startForegroundService(intent)
        }

        fun dismiss(context: Context) {
            context.stopService(Intent(context, AlarmRingService::class.java))
        }
    }

    private var shouldSound = true
    private var shouldVibrate = true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS || intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 방해금지 시간이면 알람을 울리지 않음
        val dndConfig = DndPrefs.load(this)
        if (DndManager(this).isInDndTime(dndConfig)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: ""
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "SilentLink 알람"
        shouldSound = intent.getBooleanExtra(EXTRA_ALARM_SOUND, true)
        shouldVibrate = intent.getBooleanExtra(EXTRA_ALARM_VIBRATE, true)

        ringingLabel = label
        isRinging.value = true

        createNotificationChannel()
        val notification = buildNotification(alarmId, label)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (shouldSound) startRinging()
        if (shouldVibrate) startVibrating()

        // 10분 후 자동 해제
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        autoDismissHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)

        return START_NOT_STICKY
    }

    private fun startRinging() {
        // Already playing — don't restart
        if (ringtone?.isPlaying == true || mediaPlayer?.isPlaying == true) return

        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // API 28+: Ringtone supports isLooping natively
            ringtone = RingtoneManager.getRingtone(this, uri)?.also { r ->
                r.audioAttributes = alarmAttrs
                r.isLooping = true
                r.play()
            }
        } else {
            // API 26-27: MediaPlayer with looping
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(alarmAttrs)
                    setDataSource(this@AlarmRingService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                mediaPlayer?.release()
                mediaPlayer = null
            }
        }
    }

    private fun startVibrating() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 700, 300, 700, 300)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun buildNotification(alarmId: String, label: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val dismissIntent = PendingIntent.getService(
            this, alarmId.hashCode(),
            Intent(this, AlarmRingService::class.java).apply {
                action = ACTION_DISMISS
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏰ $label")
            .setContentText("탭하여 앱 열기")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openIntent)
            .setFullScreenIntent(openIntent, true)
            .addAction(android.R.drawable.ic_delete, "해제", dismissIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SilentLink 알람 울림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "알람이 울리는 동안 표시됩니다"
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        isRinging.value = false
        ringingLabel = ""
        ringtone?.stop()
        ringtone = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
