package com.silentlink.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.silentlink.app.MainActivity
import com.silentlink.app.manager.AudioControlManager
import com.silentlink.app.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SilentLinkService : Service() {

    private val repository = FirebaseRepository()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var audioManager: AudioControlManager
    private var commandListenerJob: Job? = null

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
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startListeningCommands()
    }

    private fun startListeningCommands() {
        commandListenerJob = scope.launch {
            val myUid = repository.getCurrentUserId() ?: return@launch
            repository.observeMyCommands(myUid).collect { commands ->
                commands["setMute"]?.let {
                    audioManager.setMute(it as Boolean)
                }
                commands["setVolume"]?.let {
                    val levelName = it as? String ?: return@let
                    val level = runCatching {
                        com.silentlink.app.model.VolumeLevel.valueOf(levelName)
                    }.getOrNull() ?: return@let
                    audioManager.setVolumeLevel(level)
                }
            }
        }
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
        commandListenerJob?.cancel()
        super.onDestroy()
    }
}
