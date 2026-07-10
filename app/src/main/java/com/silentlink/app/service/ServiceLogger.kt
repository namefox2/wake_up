package com.silentlink.app.service

import android.content.Context
import android.os.PowerManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ServiceLogger {
    private const val LOG_FILE = "service_debug.log"
    private const val MAX_LINES = 400

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    /** 파일에 한 줄 추가 (append-only, 빠름) */
    fun log(context: Context, tag: String, message: String) {
        try {
            val line = "[${fmt.format(Date())}][$tag] $message\n"
            File(context.applicationContext.filesDir, LOG_FILE).appendText(line)
        } catch (_: Exception) {}
    }

    /** 로그 읽기 + 필요 시 자동 트리밍 (IO 스레드에서 호출) */
    fun readLogs(context: Context): String {
        return try {
            val file = File(context.applicationContext.filesDir, LOG_FILE)
            if (!file.exists()) return "(로그 없음)"
            val lines = file.readLines()
            if (lines.size > MAX_LINES) {
                val text = lines.takeLast(MAX_LINES).joinToString("\n")
                file.writeText("$text\n")
                text
            } else {
                lines.joinToString("\n")
            }
        } catch (_: Exception) {
            "(읽기 실패)"
        }
    }

    fun clear(context: Context) {
        try { File(context.applicationContext.filesDir, LOG_FILE).delete() } catch (_: Exception) {}
    }

    fun batteryOptStatus(context: Context): String {
        val pm = context.getSystemService(PowerManager::class.java)
        return if (pm.isIgnoringBatteryOptimizations(context.packageName)) "제외됨" else "최적화중"
    }
}
