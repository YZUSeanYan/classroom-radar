package com.classroomradar.app.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.classroomradar.app.MainActivity
import com.classroomradar.app.QuestionActionActivity
import com.classroomradar.app.R
import com.classroomradar.app.RadarCrashHandler
import com.classroomradar.app.data.AppPreferences
import com.classroomradar.app.data.ClassroomRepository
import com.classroomradar.app.detection.QuestionDetector
import com.classroomradar.app.widget.WaterClassWidgetProvider
import java.io.File
import java.util.concurrent.Executors

class RecordingService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val persistenceWorker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val repository by lazy { ClassroomRepository.get(applicationContext) }
    private val preferences by lazy { AppPreferences.get(applicationContext) }
    private val alertManager by lazy { AlertManager(applicationContext, preferences) }
    private var detector = QuestionDetector()
    private var recorder: AudioRecord? = null
    private var engine: TranscriptionEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val audioLock = Any()
    private val transcriptLock = Any()
    private var audioWriter: WavFileWriter? = null
    @Volatile private var activeSessionId: Long? = null
    private var startedAt = 0L
    @Volatile private var recording = false
    @Volatile private var finishingRecording = false
    @Volatile private var latestPartialText = ""
    @Volatile private var latestFinalText = ""
    @Volatile private var latestQuestionText = ""
    @Volatile private var foregroundUpdatePending = false
    private val recentFinalTexts = ArrayDeque<String>()

    override fun onCreate() {
        super.onCreate()
        // The service can be started by the widget before any activity exists.
        RadarCrashHandler.install(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording() {
        // Multiple UI/widget start intents are harmless. In particular, never turn an active
        // recording into a service stop just because a stale start PendingIntent was tapped.
        if (recording || finishingRecording) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        startedAt = System.currentTimeMillis()
        recording = true
        latestPartialText = ""
        latestFinalText = ""
        latestQuestionText = ""
        synchronized(transcriptLock) { recentFinalTexts.clear() }
        updateActiveSnapshot {
            ActiveRecordingSnapshot(
                sessionId = null,
                startedAtMillis = startedAt,
                liveTranscript = "",
            )
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, foregroundNotification(0))
        acquireWakeLock()
        WaterClassWidgetProvider.refresh(this, isRecording = true)
        RecordingBus.emit(RecorderEvent.Status(isRecording = true, elapsedSeconds = 0))
        scheduleElapsedUpdate()

        worker.execute {
            val sessionId = runCatching { repository.createSession(startedAt) }
                .getOrElse {
                    handleEngineFailure(it)
                    return@execute
                }
            if (!recording) {
                repository.finishSession(sessionId, System.currentTimeMillis())
                return@execute
            }
            activeSessionId = sessionId
            updateActiveSnapshot { snapshot -> snapshot?.copy(sessionId = sessionId) }
            detector = QuestionDetector(preferences.activeTriggerWords())
            configureOptionalAudioCapture(sessionId)
            RecordingBus.emit(RecorderEvent.SessionStarted(sessionId, startedAt))

            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBuffer <= 0) {
                handleEngineFailure(IllegalStateException("The microphone does not support the requested audio format."))
                return@execute
            }
            val localRecorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    maxOf(minBuffer * 2, SAMPLE_RATE / 5 * 2),
                )
            } catch (error: Throwable) {
                handleEngineFailure(error)
                return@execute
            }
            if (localRecorder.state != AudioRecord.STATE_INITIALIZED) {
                localRecorder.runCatching { release() }
                handleEngineFailure(IllegalStateException("The microphone could not be initialized."))
                return@execute
            }
            if (!recording) {
                localRecorder.release()
                return@execute
            }
            recorder = localRecorder
            try {
                if (!recording) return@execute
                val localEngine = SherpaTwoPassEngine(applicationContext)
                engine = localEngine
                localEngine.start(object : TranscriptionEngine.Listener {
                    override fun onPartialText(text: String) = handlePartialTranscript(text)
                    override fun onFinalText(text: String) = handleFinalTranscript(text)
                    override fun onError(error: Throwable) = handleEngineFailure(error)
                })
                if (!recording) return@execute
                localRecorder.startRecording()
                check(localRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "The microphone did not enter the recording state."
                }
                val buffer = ShortArray(SAMPLE_RATE / 5)
                var consecutiveEmptyReads = 0
                while (recording) {
                    val read = localRecorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    when {
                        read > 0 -> {
                            consecutiveEmptyReads = 0
                            synchronized(audioLock) { audioWriter?.runCatching { write(buffer, read) } }
                            localEngine.acceptPcm(buffer, read)
                        }
                        read == 0 -> {
                            consecutiveEmptyReads += 1
                            check(consecutiveEmptyReads < MAX_CONSECUTIVE_EMPTY_READS) {
                                "The microphone repeatedly returned no audio data."
                            }
                        }
                        else -> error("The microphone read failed with code $read.")
                    }
                }
            } catch (error: Throwable) {
                // A user-initiated stop can race a blocking read/release. That path has already
                // begun the graceful final-pass drain, so only report a failure while recording.
                if (recording) handleEngineFailure(error)
            }
        }
    }

    private fun handleFinalTranscript(text: String) {
        if (!recording && !finishingRecording) return
        val timestampMillis = System.currentTimeMillis()
        val sessionId = activeSessionId ?: return
        latestFinalText = text
        latestPartialText = ""
        rememberFinalTranscript(text)
        updateActiveSnapshot { snapshot -> snapshot?.copy(liveTranscript = "") }
        requestForegroundNotificationUpdate()
        RecordingBus.emit(RecorderEvent.Transcript(text, timestampMillis))
        persistenceWorker.execute {
            runCatching { repository.appendTranscript(sessionId, timestampMillis, text) }
        }
        detector.onFinalTranscript(text, timestampMillis).forEach { event ->
            when (event) {
                is QuestionDetector.DetectionEvent.PromptMatched ->
                    RecordingBus.emit(RecorderEvent.Prompt(event.timestampMillis, event.trigger))
                is QuestionDetector.DetectionEvent.QuestionExtracted -> {
                    val result = event.result
                    RecordingBus.emit(RecorderEvent.Question(result))
                    persistenceWorker.execute {
                        runCatching {
                            repository.appendQuestion(
                                sessionId = sessionId,
                                timestampMillis = result.timestampMillis,
                                matchedTrigger = result.matchedTrigger,
                                question = result.question,
                                sourceLine = result.sourceLine,
                            )
                        }
                    }
                    latestQuestionText = result.question
                    requestForegroundNotificationUpdate()
                    alertManager.alert()
                    notifyQuestion(result.question)
                }
            }
        }
    }

    private fun handlePartialTranscript(text: String) {
        if (recording) {
            latestPartialText = text
            updateActiveSnapshot { snapshot -> snapshot?.copy(liveTranscript = text) }
            requestForegroundNotificationUpdate()
            RecordingBus.emit(RecorderEvent.PartialTranscript(text))
        }
    }

    private fun handleEngineFailure(error: Throwable) {
        RecordingBus.emit(RecorderEvent.Failure(error.message ?: "离线识别引擎不可用"))
        if (recording) stopRecording()
    }

    private fun scheduleElapsedUpdate() {
        mainHandler.post(object : Runnable {
            override fun run() {
                if (!recording) return
                val elapsedSeconds = elapsedSeconds()
                RecordingBus.emit(RecorderEvent.Status(true, elapsedSeconds))
                updateForegroundNotification()
                mainHandler.postDelayed(this, 1_000)
            }
        })
    }

    private fun stopRecording() {
        if (!recording) return
        // Flags flip first on the calling thread so the UI and the read loop react at once.
        // The blocking native teardown (AudioRecord.stop/release, VAD flush, final-pass drain)
        // is queued on the same serial worker that owns the read loop: the loop observes
        // recording=false and exits first, then the teardown runs, so ordering and the
        // final-pass persistence chain are identical to before — only the main-thread freeze
        // is gone.
        recording = false
        finishingRecording = true
        mainHandler.removeCallbacksAndMessages(null)
        foregroundUpdatePending = false
        updateActiveSnapshot { null }
        releaseWakeLock()
        WaterClassWidgetProvider.refresh(this, isRecording = false)
        RecordingBus.emit(RecorderEvent.Status(isRecording = false, elapsedSeconds = 0))
        worker.execute {
            recorder?.runCatching {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
                release()
            }
            recorder = null
            synchronized(audioLock) {
                audioWriter?.runCatching { close() }
                audioWriter = null
            }
            val localEngine = engine
            engine = null
            if (localEngine != null) localEngine.stop(::finishRecordingAfterFinalPass)
            else finishRecordingAfterFinalPass()
        }
    }

    /** Stops the service only once Sherpa's queued Paraformer results are persisted. */
    private fun finishRecordingAfterFinalPass() {
        val sessionId = activeSessionId
        persistenceWorker.execute {
            sessionId?.let { repository.finishSession(it, System.currentTimeMillis()) }
            activeSessionId = null
            finishingRecording = false
            mainHandler.post {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun foregroundNotification(elapsedSeconds: Long): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val expandedText = buildString {
            append(notificationExpandedTranscript())
            if (latestQuestionText.isNotBlank()) {
                append("\n\n最近提问：")
                append(compactNotificationText(latestQuestionText, 72))
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText("${formatDuration(elapsedSeconds)} · ${notificationCollapsedTranscript()}")
            .setSubText("● 正在录音 · ${notificationTranscriptState()} · 设备端识别")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(getString(R.string.notification_recording_title))
                    .bigText(expandedText)
                    .setSummaryText("● 正在录音 · ${formatDuration(elapsedSeconds)} · 设备端识别"),
            )
            .setRequestPromotedOngoing(true)
            .addAction(0, getString(R.string.stop), stop)
            .build()
    }

    private fun notifyQuestion(question: String) {
        val copy = questionActionPendingIntent(QuestionActionActivity.ACTION_COPY, question, 20)
        val openDoubao = questionActionPendingIntent(QuestionActionActivity.ACTION_OPEN_DOUBAO, question, 21)
        val openDeepSeek = questionActionPendingIntent(QuestionActionActivity.ACTION_OPEN_DEEPSEEK, question, 22)
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle(getString(R.string.notification_question_title))
            .setContentText(question)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "可能被提问\n问题回溯：$question",
                ),
            )
            .setContentIntent(openDoubao)
            .addAction(0, "复制", copy)
            .addAction(0, "豆包", openDoubao)
            .addAction(0, "DeepSeek", openDeepSeek)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(QUESTION_NOTIFICATION_ID, notification)
    }

    private fun questionActionPendingIntent(action: String, question: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, QuestionActionActivity::class.java).apply {
                this.action = action
                putExtra(QuestionActionActivity.EXTRA_QUESTION, question)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW),
        )
        // The legacy alert channel was created with the system default notification sound,
        // and channel audio settings are frozen after creation — so existing installs keep
        // ringing no matter what the in-app toggles say. Ship the fix under a fresh id.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ALERT)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH).apply {
                // Vibration and the headphone-only chime are handled by AlertManager behind
                // the user's settings; the notification itself must always stay silent.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    override fun onDestroy() {
        stopRecording()
        // Graceful, not shutdownNow: stopRecording queues the native teardown on this serial
        // worker (right behind the read loop, which exits within one buffer period), and an
        // immediate shutdown would drop it — losing the final-pass drain and finishSession.
        worker.shutdown()
        persistenceWorker.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.classroomradar.app.action.START"
        const val ACTION_STOP = "com.classroomradar.app.action.STOP"
        const val CHANNEL_RECORDING = "recording"
        private const val CHANNEL_ALERT = "question_alert_v2"
        private const val LEGACY_CHANNEL_ALERT = "question_alert"
        private const val NOTIFICATION_ID = 10
        private const val QUESTION_NOTIFICATION_ID = 11
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val NOTIFICATION_UPDATE_THROTTLE_MILLIS = 350L
        private const val MAX_NOTIFICATION_TEXT_LENGTH = 72
        private const val MAX_WAKE_LOCK_MILLIS = 6 * 60 * 60 * 1_000L
        private const val MAX_CONSECUTIVE_EMPTY_READS = 8
        @Volatile private var activeRecordingSnapshot: ActiveRecordingSnapshot? = null

        data class ActiveRecordingSnapshot(
            val sessionId: Long?,
            val startedAtMillis: Long,
            val liveTranscript: String,
        )

        private fun formatDuration(seconds: Long): String =
            "%02d:%02d".format(seconds / 60, seconds % 60)

        fun isRealtimeNotificationEnabled(context: android.content.Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return false
            // A new install has no channel until startRecording() creates it immediately before
            // startForeground(). Treat that absence as available instead of blocking the first
            // recording attempt and sending the user to a settings page with no channel to show.
            if (manager.getNotificationChannel(CHANNEL_RECORDING)?.importance == NotificationManager.IMPORTANCE_NONE) return false
            return Build.VERSION.SDK_INT < 36 || manager.canPostPromotedNotifications()
        }

        /**
         * Returns true when ordinary notifications are already usable and the remaining blocker,
         * if any, is Android 16's promoted/live-update setting.
         */
        fun shouldOpenPromotedNotificationSettings(context: android.content.Context): Boolean {
            if (Build.VERSION.SDK_INT < 36) return false
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return false
            return manager.getNotificationChannel(CHANNEL_RECORDING)?.importance != NotificationManager.IMPORTANCE_NONE
        }

        fun currentRecordingSnapshot(): ActiveRecordingSnapshot? = activeRecordingSnapshot

        private fun updateActiveSnapshot(transform: (ActiveRecordingSnapshot?) -> ActiveRecordingSnapshot?) {
            activeRecordingSnapshot = transform(activeRecordingSnapshot)
        }
    }

    private fun configureOptionalAudioCapture(sessionId: Long) {
        if (!preferences.saveAudioEnabled) return
        val relativePath = "recordings/session_$sessionId.wav"
        val target = File(filesDir, relativePath)
        runCatching {
            target.parentFile?.mkdirs()
            synchronized(audioLock) { audioWriter = WavFileWriter(target) }
            repository.setAudioPath(sessionId, relativePath)
        }.onFailure {
            target.delete()
            synchronized(audioLock) { audioWriter = null }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:class-session").apply {
            setReferenceCounted(false)
            // The foreground service refreshes this lock for each recording. The timeout
            // is only a failsafe for a lifecycle fault and should not end normal classes.
            acquire(MAX_WAKE_LOCK_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.runCatching { if (isHeld) release() }
        wakeLock = null
    }

    private fun requestForegroundNotificationUpdate() {
        if (!recording || foregroundUpdatePending) return
        foregroundUpdatePending = true
        mainHandler.postDelayed(
            {
                foregroundUpdatePending = false
                if (recording) updateForegroundNotification()
            },
            NOTIFICATION_UPDATE_THROTTLE_MILLIS,
        )
    }

    private fun updateForegroundNotification() {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            foregroundNotification(elapsedSeconds()),
        )
    }

    private fun elapsedSeconds(): Long = (System.currentTimeMillis() - startedAt).coerceAtLeast(0) / 1_000

    private fun notificationTranscriptState(): String = when {
        latestFinalText.isNotBlank() && latestPartialText.isNotBlank() -> "上段定稿 · 实时识别"
        latestPartialText.isNotBlank() -> "实时识别"
        latestFinalText.isNotBlank() -> "最近定稿"
        else -> "实时识别"
    }

    private fun notificationCollapsedTranscript(): String = when {
        latestFinalText.isNotBlank() && latestPartialText.isNotBlank() ->
            "${compactNotificationText(latestFinalText, 30)} · ${compactNotificationText(latestPartialText, 38)}"
        latestPartialText.isNotBlank() -> compactNotificationText(latestPartialText, MAX_NOTIFICATION_TEXT_LENGTH)
        latestFinalText.isNotBlank() -> compactNotificationText(latestFinalText, MAX_NOTIFICATION_TEXT_LENGTH)
        else -> "正在等待语音…"
    }

    private fun notificationExpandedTranscript(): String {
        val recentFinals = synchronized(transcriptLock) { recentFinalTexts.toList() }
        return buildString {
            recentFinals.forEach { line ->
                if (isNotEmpty()) append('\n')
                append("• ")
                append(compactNotificationText(line, 96))
            }
            if (latestPartialText.isNotBlank()) {
                if (isNotEmpty()) append("\\n\\n")
                append("正在识别：")
                append(compactNotificationText(latestPartialText, 120))
            }
            if (isEmpty()) append("正在等待语音…")
        }
    }

    private fun rememberFinalTranscript(text: String) {
        synchronized(transcriptLock) {
            if (recentFinalTexts.lastOrNull() == text) return
            recentFinalTexts.addLast(text)
            while (recentFinalTexts.size > 4) recentFinalTexts.removeFirst()
        }
    }

    private fun compactNotificationText(text: String, maxLength: Int): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= maxLength) normalized else "…${normalized.takeLast(maxLength - 1)}"
    }

}
