package com.silentlink.app

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val FILE = "crash.log"
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun setup(context: Context) {
        val appContext = context.applicationContext
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sb = StringBuilder()
                sb.appendLine("=== 크래시 리포트 ===")
                sb.appendLine("시각: ${fmt.format(Date())}")
                sb.appendLine("스레드: ${thread.name}")
                sb.appendLine("Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                sb.appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL}")
                sb.appendLine("앱 버전: ${appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName}")
                sb.appendLine()
                sb.appendLine(throwable.stackTraceToString())
                sb.appendLine()
                File(appContext.filesDir, FILE).writeText(sb.toString())
            } catch (_: Exception) {}
            default?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String {
        return try {
            val file = File(context.applicationContext.filesDir, FILE)
            if (file.exists()) file.readText() else "(저장된 오류 로그 없음)"
        } catch (_: Exception) { "(읽기 실패)" }
    }

    fun clear(context: Context) {
        try { File(context.applicationContext.filesDir, FILE).delete() } catch (_: Exception) {}
    }

    fun exists(context: Context): Boolean =
        File(context.applicationContext.filesDir, FILE).exists()
}
