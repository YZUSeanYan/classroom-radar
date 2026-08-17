package com.classroomradar.app.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.classroomradar.app.MainActivity
import com.classroomradar.app.R
import com.classroomradar.app.audio.RecordingService

/** A local widget: one tap starts listening after microphone permission is granted. */
class WaterClassWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, manager, appWidgetIds, isRecording = false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_START_LISTENING) {
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                RecordingService.isRealtimeNotificationEnabled(context)
            ) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START),
                )
            } else {
                // Do not launch a microphone foreground service from the widget when its
                // required visible/live notification is unavailable. The main screen explains
                // the precise permission or channel setting and can request microphone access.
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_START_LISTENING = "com.classroomradar.app.widget.START_LISTENING"

        fun refresh(context: Context, isRecording: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, WaterClassWidgetProvider::class.java))
            updateAll(context, manager, ids, isRecording)
        }

        private fun updateAll(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            isRecording: Boolean,
        ) {
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_water_class).apply {
                setTextViewText(R.id.widget_status, if (isRecording) "正在设备端聆听" else "一键开始离线听课")
                setOnClickPendingIntent(
                    R.id.widget_start,
                    PendingIntent.getBroadcast(
                        context,
                        30,
                        Intent(context, WaterClassWidgetProvider::class.java).setAction(ACTION_START_LISTENING),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setOnClickPendingIntent(
                    R.id.widget_open,
                    PendingIntent.getActivity(
                        context,
                        31,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
