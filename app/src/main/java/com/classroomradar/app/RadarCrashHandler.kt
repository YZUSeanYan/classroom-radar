package com.classroomradar.app

import android.content.Context
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort local crash capture. The app has no network permission, so the report is written
 * to the private files directory and offered for manual copy on the next launch. The system
 * default handler still runs afterwards, keeping normal crash behaviour (and logcat) intact.
 * Native aborts (SIGABRT) bypass this handler entirely and still require adb logcat.
 */
object RadarCrashHandler {
    private const val CRASH_FILE_NAME = "crash_last.log"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val writer = StringWriter()
                writer.appendLine("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())}")
                writer.appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                writer.appendLine("版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                writer.appendLine("线程：${thread.name}")
                writer.appendLine()
                error.printStackTrace(PrintWriter(writer))
                java.io.File(appContext.filesDir, CRASH_FILE_NAME).writeText(writer.toString())
            }
            previous?.uncaughtException(thread, error) ?: throw error
        }
    }

    fun consumeReport(context: Context): String? {
        val file = java.io.File(context.applicationContext.filesDir, CRASH_FILE_NAME)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun dismissReport(context: Context) {
        runCatching { java.io.File(context.applicationContext.filesDir, CRASH_FILE_NAME).delete() }
    }
}
