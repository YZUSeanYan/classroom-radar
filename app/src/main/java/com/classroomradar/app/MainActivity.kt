package com.classroomradar.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.classroomradar.app.audio.RecorderEvent
import com.classroomradar.app.audio.RecordingBus
import com.classroomradar.app.audio.RecordingService
import com.classroomradar.app.data.AppPreferences
import com.classroomradar.app.data.ClassroomRepository
import com.classroomradar.app.data.QuestionEventEntity
import com.classroomradar.app.data.SessionEntity
import com.classroomradar.app.data.SessionSummary
import com.classroomradar.app.data.TranscriptLineEntity
import com.classroomradar.app.detection.QuestionDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.kyant.backdrop.backdrops.layerBackdrop

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RadarCrashHandler.install(this)
        // The UI intentionally stays white in either system theme; force dark system-bar
        // glyphs as well so a device-wide dark mode cannot leave light icons on white.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(RadarBg.toArgb(), RadarBg.toArgb()),
            navigationBarStyle = SystemBarStyle.light(RadarBg.toArgb(), RadarBg.toArgb()),
        )
        setContent {
            WaterClassRadarTheme {
                LiquidGlassRoot {
                val context = LocalContext.current
                val presenter = remember { ClassroomPresenter(context.applicationContext) }
                DisposableEffect(presenter) {
                    onDispose { presenter.close() }
                }
                val dashboard by rememberState(presenter.dashboard)
                val history by rememberState(presenter.history)
                val detail by rememberState(presenter.detail)
                val settings by rememberState(presenter.settings)
                var page by remember { mutableStateOf(AppPage.Home) }
                var showUsageNotice by remember { mutableStateOf(!settings.usageNoticeAccepted) }
                var showNotificationSettings by remember { mutableStateOf(false) }
                var crashReport by remember { mutableStateOf(RadarCrashHandler.consumeReport(context)) }
                var pendingStart by remember { mutableStateOf(false) }
                var pendingExport by remember { mutableStateOf<ExportRequest?>(null) }
                var historySelecting by remember { mutableStateOf(false) }
                var historySelectedIds by remember { mutableStateOf(setOf<Long>()) }
                var showHistoryDeleteConfirmation by remember { mutableStateOf(false) }
                val permissionRequester = rememberLauncher { permissions ->
                    if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
                        if (RecordingService.isRealtimeNotificationEnabled(context)) {
                            presenter.start()
                        } else {
                            showNotificationSettings = true
                        }
                    }
                }
                val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/plain"),
                ) { uri: Uri? ->
                    pendingExport?.let { request -> if (uri != null) presenter.writeExport(uri, request.content) }
                    pendingExport = null
                }

                LaunchedEffect(pendingExport?.fileName) {
                    pendingExport?.let { exportLauncher.launch(it.fileName) }
                }

                // Leaving the history page always drops the multi-selection session.
                LaunchedEffect(page) {
                    if (page != AppPage.History) {
                        historySelecting = false
                        historySelectedIds = emptySet()
                        showHistoryDeleteConfirmation = false
                    }
                }

                fun beginListening() {
                    if (!settings.usageNoticeAccepted) {
                        pendingStart = true
                        showUsageNotice = true
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permissionRequester.launch(requiredPermissions(context))
                    } else if (!RecordingService.isRealtimeNotificationEnabled(context)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionRequester.launch(requiredPermissions(context))
                        } else {
                            showNotificationSettings = true
                        }
                    } else {
                        presenter.start()
                    }
                }

                crashReport?.let { report ->
                    CrashReportDialog(
                        report = report,
                        onDismiss = {
                            RadarCrashHandler.dismissReport(context)
                            crashReport = null
                        },
                    )
                }

                if (showUsageNotice) {
                    UsageNotice(
                        onConfirm = {
                            presenter.acceptUsageNotice()
                            showUsageNotice = false
                            if (pendingStart) {
                                pendingStart = false
                                beginListening()
                            }
                        },
                        onDismiss = {
                            pendingStart = false
                            showUsageNotice = false
                        },
                    )
                }

                if (showNotificationSettings) {
                    RealtimeNotificationRequired(
                        onOpenSettings = {
                            openRealtimeNotificationSettings(context)
                            showNotificationSettings = false
                        },
                        onDismiss = { showNotificationSettings = false },
                    )
                }

                if (showHistoryDeleteConfirmation) {
                    GlassConfirmDialog(
                        title = "删除 ${historySelectedIds.size} 条课堂记录？",
                        message = "所选课堂的转写、提问和私有录音都会一并删除，无法恢复；正在记录的课堂会自动跳过。",
                        confirmLabel = "删除",
                        dismissLabel = "取消",
                        onConfirm = {
                            showHistoryDeleteConfirmation = false
                            presenter.deleteSessions(historySelectedIds)
                            historySelecting = false
                            historySelectedIds = emptySet()
                        },
                        onDismiss = { showHistoryDeleteConfirmation = false },
                        tone = GlassTone.Danger,
                    )
                }

                if (page != AppPage.Home && page != AppPage.History && page != AppPage.Settings) {
                    BackHandler { page = AppPage.Home }
                }

                AppScaffold(
                    page = page,
                    onPageChange = { page = it },
                    onBack = { page = AppPage.Home },
                    bottomOverlay = when {
                        page == AppPage.Home -> {
                            {
                                HomeActionDock(
                                    state = dashboard,
                                    onRecordingToggle = { if (dashboard.isRecording) presenter.stop() else beginListening() },
                                )
                            }
                        }
                        page == AppPage.History && historySelecting -> {
                            {
                                GlassPanel(
                                    tone = GlassTone.Neutral,
                                    effect = GlassEffect.Elevated,
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        text = "删除所选（${historySelectedIds.size}）",
                                        color = if (historySelectedIds.isEmpty()) LiquidTextSecondary.copy(alpha = 0.45f) else RadarAlert,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable(enabled = historySelectedIds.isNotEmpty()) {
                                                showHistoryDeleteConfirmation = true
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        else -> null
                    },
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = { pageTransition() },
                        label = "pageTransition",
                    ) { targetPage ->
                    when (targetPage) {
                        AppPage.Home -> HomeScreen(
                            state = dashboard,
                            onOpenTimeline = {
                                dashboard.activeSessionId?.let {
                                    presenter.loadSession(it)
                                    page = AppPage.Timeline
                                }
                            },
                            modifier = Modifier.padding(innerPadding),
                            twoPane = LocalConfiguration.current.screenWidthDp >= 840,
                        )

                        AppPage.History -> HistoryScreen(
                            sessions = history,
                            selecting = historySelecting,
                            selectedIds = historySelectedIds,
                            onSelectingChange = { selecting ->
                                historySelecting = selecting
                                if (!selecting) historySelectedIds = emptySet()
                            },
                            onSelectionChange = { historySelectedIds = it },
                            onOpenSession = { sessionId ->
                                presenter.loadSession(sessionId)
                                page = AppPage.SessionDetail
                            },
                            modifier = Modifier.padding(innerPadding),
                        )

                        AppPage.Settings -> SettingsScreen(
                            state = settings,
                            onVibrationChanged = presenter::setVibrationEnabled,
                            onVibrationPatternChanged = presenter::setVibrationPattern,
                            onSoundChanged = presenter::setSoundEnabled,
                            onSaveAudioChanged = presenter::setSaveAudioEnabled,
                            onOpenTriggers = { page = AppPage.TriggerWords },
                            onOpenBatterySettings = { openBatteryOptimizationSettings(context) },
                            onOpenNotificationSettings = { openRealtimeNotificationSettings(context) },
                            modifier = Modifier.padding(innerPadding),
                        )

                        AppPage.TriggerWords -> TriggerWordsScreen(
                            state = settings,
                            onBuiltInChanged = presenter::setBuiltInTriggerEnabled,
                            onAddCustom = presenter::addCustomTrigger,
                            onRemoveCustom = presenter::removeCustomTrigger,
                            modifier = Modifier.padding(innerPadding),
                        )

                        AppPage.Timeline -> TimelineScreen(
                            detail = detail,
                            modifier = Modifier.padding(innerPadding),
                        )

                        AppPage.SessionDetail -> SessionDetailScreen(
                            detail = detail,
                            isActiveRecording = dashboard.isRecording && dashboard.activeSessionId == detail.session?.id,
                            onOpenTimeline = { page = AppPage.Timeline },
                            onExportMarkdown = { detail.session?.id?.let { presenter.prepareExport(it, true) { pendingExport = it } } },
                            onExportText = { detail.session?.id?.let { presenter.prepareExport(it, false) { pendingExport = it } } },
                            onRename = { title -> detail.session?.id?.let { presenter.renameSession(it, title) } },
                            onDelete = {
                                detail.session?.id?.let {
                                    if (presenter.deleteSession(it)) page = AppPage.History
                                }
                            },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                    }
                }
                }
            }
        }
    }
}

private enum class AppPage(val title: String) {
    Home("水课雷达"),
    History("历史记录"),
    Settings("设置"),
    TriggerWords("触发词管理"),
    Timeline("提问时间轴"),
    SessionDetail("课堂详情"),
}

/** Stage-1 trigger hit: shown as the yellow "可能被提问" banner while extraction continues. */
private data class PromptWarning(val timestampMillis: Long, val trigger: String)

private data class DashboardState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Long = 0,
    val activeSessionId: Long? = null,
    val transcript: List<String> = emptyList(),
    val liveTranscript: String = "",
    val failureMessage: String? = null,
    val questions: List<QuestionDetector.DetectionResult> = emptyList(),
    val latestPrompt: PromptWarning? = null,
    val activeTriggerWords: Set<String> = emptySet(),
)

private data class SessionDetailState(
    val session: SessionEntity? = null,
    val transcript: List<TranscriptLineEntity> = emptyList(),
    val questions: List<QuestionEventEntity> = emptyList(),
)

private data class SettingsState(
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val saveAudioEnabled: Boolean = false,
    val usageNoticeAccepted: Boolean = false,
    val disabledBuiltInTriggers: Set<String> = emptySet(),
    val customTriggers: List<String> = emptyList(),
    val vibrationPattern: String = AppPreferences.VIBRATION_STANDARD,
)

private data class ExportRequest(val fileName: String, val content: String)

private class ClassroomPresenter(private val appContext: Context) {
    private val repository = ClassroomRepository.get(appContext)
    private val preferences = AppPreferences.get(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var detailJob: Job? = null
    private var activeSessionJob: Job? = null

    private val _dashboard = MutableStateFlow(DashboardState(activeTriggerWords = preferences.activeTriggerWords()))
    val dashboard: StateFlow<DashboardState> = _dashboard.asStateFlow()
    private val _history = MutableStateFlow<List<SessionSummary>>(emptyList())
    val history: StateFlow<List<SessionSummary>> = _history.asStateFlow()
    private val _detail = MutableStateFlow(SessionDetailState())
    val detail: StateFlow<SessionDetailState> = _detail.asStateFlow()
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    init {
        restoreActiveRecording()
        scope.launch {
            RecordingBus.events.collect { event ->
                when (event) {
                    is RecorderEvent.SessionStarted -> observeActiveSession(event.sessionId, event.startedAtMillis)
                    is RecorderEvent.Status -> _dashboard.value = _dashboard.value.copy(
                        isRecording = event.isRecording,
                        elapsedSeconds = event.elapsedSeconds,
                        liveTranscript = if (event.isRecording) _dashboard.value.liveTranscript else "",
                    )
                    is RecorderEvent.PartialTranscript -> _dashboard.value = _dashboard.value.copy(liveTranscript = event.text, failureMessage = null)
                    is RecorderEvent.Transcript -> _dashboard.value = _dashboard.value.copy(
                        transcript = (_dashboard.value.transcript + event.text).takeLast(120),
                        liveTranscript = "",
                        failureMessage = null,
                    )
                    is RecorderEvent.Question -> _dashboard.value = _dashboard.value.copy(
                        questions = listOf(event.result) + _dashboard.value.questions,
                        latestPrompt = null,
                    )
                    is RecorderEvent.Prompt -> _dashboard.value = _dashboard.value.copy(
                        latestPrompt = PromptWarning(event.timestampMillis, event.trigger),
                    )
                    is RecorderEvent.Failure -> _dashboard.value = _dashboard.value.copy(failureMessage = event.message, liveTranscript = "")
                }
            }
        }
        scope.launch { repository.observeSessionSummaries().collect { _history.value = it } }
    }

    private fun restoreActiveRecording() {
        RecordingService.currentRecordingSnapshot()?.let { snapshot ->
            snapshot.sessionId?.let { observeActiveSession(it, snapshot.startedAtMillis, snapshot.liveTranscript) }
        }
    }

    private fun observeActiveSession(sessionId: Long, startedAtMillis: Long, liveTranscript: String = "") {
        activeSessionJob?.cancel()
        activeSessionJob = scope.launch {
            combine(
                repository.observeTranscript(sessionId),
                repository.observeQuestions(sessionId),
            ) { transcript, questions -> transcript to questions }
                .collect { (transcript, questions) ->
                    val snapshot = RecordingService.currentRecordingSnapshot()
                    val stillRecordingThisSession = snapshot?.sessionId == sessionId
                    _dashboard.value = DashboardState(
                        isRecording = stillRecordingThisSession,
                        elapsedSeconds = if (stillRecordingThisSession) {
                            ((System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0) / 1_000)
                        } else 0,
                        activeSessionId = sessionId,
                        transcript = transcript.map { it.text }.takeLast(120),
                        liveTranscript = snapshot?.liveTranscript ?: liveTranscript,
                        questions = questions.map { event ->
                            QuestionDetector.DetectionResult(
                                timestampMillis = event.timestampMillis,
                                matchedTrigger = event.matchedTrigger,
                                question = event.question,
                                sourceLine = event.sourceLine,
                            )
                        },
                        latestPrompt = _dashboard.value.latestPrompt,
                        activeTriggerWords = _dashboard.value.activeTriggerWords,
                    )
                }
        }
    }

    fun close() {
        activeSessionJob?.cancel()
        scope.cancel()
    }

    fun start() = ContextCompat.startForegroundService(
        appContext,
        Intent(appContext, RecordingService::class.java).setAction(RecordingService.ACTION_START),
    )

    fun stop() = appContext.startService(
        Intent(appContext, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
    )

    fun loadSession(sessionId: Long) {
        detailJob?.cancel()
        detailJob = scope.launch {
            combine(
                repository.observeSession(sessionId),
                repository.observeTranscript(sessionId),
                repository.observeQuestions(sessionId),
            ) { session, transcript, questions -> SessionDetailState(session, transcript, questions) }
                .collect { _detail.value = it }
        }
    }

    fun deleteSession(sessionId: Long): Boolean {
        // The detail page normally disables this action while recording, but keep the service's
        // live snapshot as the authority in case a stale UI state or an already-open confirmation
        // dialog reaches this method. Deleting the parent row would otherwise make later service
        // inserts fail their foreign-key constraint and lose the rest of the class transcript.
        if (RecordingService.currentRecordingSnapshot()?.sessionId == sessionId) {
            _dashboard.value = _dashboard.value.copy(
                failureMessage = "正在记录的课堂不能删除，请先停止录音。",
            )
            return false
        }
        scope.launch(Dispatchers.IO) {
            if (RecordingService.currentRecordingSnapshot()?.sessionId == sessionId) return@launch
            repository.deleteSession(sessionId)
            withContext(Dispatchers.Main.immediate) {
                if (_dashboard.value.activeSessionId == sessionId && !_dashboard.value.isRecording) {
                    _dashboard.value = _dashboard.value.copy(
                        activeSessionId = null,
                        transcript = emptyList(),
                        questions = emptyList(),
                    )
                }
            }
        }
        return true
    }

    fun deleteSessions(sessionIds: Set<Long>) {
        // Same live-snapshot authority as deleteSession: the session currently being recorded
        // is skipped so later service inserts keep their foreign-key parent.
        val recordingId = RecordingService.currentRecordingSnapshot()?.sessionId
        val deletable = sessionIds.filterTo(mutableSetOf()) { it != recordingId }
        if (deletable.size < sessionIds.size) {
            _dashboard.value = _dashboard.value.copy(
                failureMessage = "正在记录的课堂已跳过，请先停止录音再删除。",
            )
        }
        if (deletable.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val racingRecordingId = RecordingService.currentRecordingSnapshot()?.sessionId
            deletable.filter { it != racingRecordingId }.forEach { repository.deleteSession(it) }
            withContext(Dispatchers.Main.immediate) {
                val current = _dashboard.value
                val clearedActive = current.activeSessionId?.let { it in deletable } == true
                if (clearedActive && !current.isRecording) {
                    _dashboard.value = current.copy(
                        activeSessionId = null,
                        transcript = emptyList(),
                        questions = emptyList(),
                    )
                }
            }
        }
    }

    fun prepareExport(sessionId: Long, markdown: Boolean, onReady: (ExportRequest) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val record = repository.loadExport(sessionId) ?: return@launch
            val extension = if (markdown) "md" else "txt"
            val content = if (markdown) record.toMarkdown() else record.toPlainText()
            val title = record.session.title.ifBlank { "课堂记录" }
            val fileName = "水课雷达_${title}_${fileStamp(record.session.startedAtMillis)}.$extension"
            withContext(Dispatchers.Main.immediate) { onReady(ExportRequest(fileName, content)) }
        }
    }

    fun writeExport(uri: Uri, content: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(content) }
                } ?: error("无法打开所选文件")
            }.onFailure { error ->
                withContext(Dispatchers.Main.immediate) {
                    _dashboard.value = _dashboard.value.copy(failureMessage = "导出失败：${error.message}")
                }
            }
        }
    }

    fun acceptUsageNotice() {
        preferences.usageNoticeAccepted = true
        refreshSettings()
    }

    fun setVibrationEnabled(enabled: Boolean) { preferences.vibrationEnabled = enabled; refreshSettings() }
    fun setSoundEnabled(enabled: Boolean) { preferences.soundEnabled = enabled; refreshSettings() }
    fun setSaveAudioEnabled(enabled: Boolean) { preferences.saveAudioEnabled = enabled; refreshSettings() }
    fun setVibrationPattern(pattern: String) { preferences.vibrationPattern = pattern; refreshSettings() }
    fun setBuiltInTriggerEnabled(word: String, enabled: Boolean) { preferences.setBuiltInTriggerEnabled(word, enabled); refreshSettings() }
    fun addCustomTrigger(word: String) { preferences.addCustomTrigger(word); refreshSettings() }
    fun removeCustomTrigger(word: String) { preferences.removeCustomTrigger(word); refreshSettings() }

    fun renameSession(sessionId: Long, title: String) {
        scope.launch(Dispatchers.IO) { repository.renameSession(sessionId, title) }
    }

    private fun refreshSettings() {
        _settings.value = readSettings()
        _dashboard.value = _dashboard.value.copy(activeTriggerWords = preferences.activeTriggerWords())
    }

    private fun readSettings() = SettingsState(
        vibrationEnabled = preferences.vibrationEnabled,
        soundEnabled = preferences.soundEnabled,
        saveAudioEnabled = preferences.saveAudioEnabled,
        usageNoticeAccepted = preferences.usageNoticeAccepted,
        disabledBuiltInTriggers = QuestionDetector.DEFAULT_TRIGGER_WORDS.filterNot(preferences::isBuiltInTriggerEnabled).toSet(),
        customTriggers = preferences.customTriggerWords(),
        vibrationPattern = preferences.vibrationPattern,
    )
}

/**
 * Layered layout: page content is recorded into the liquid-glass backdrop layer, while the
 * header, navigation bar and the home action dock float above it as siblings. The chrome
 * samples (and visibly refracts) whatever scrolls beneath; everything inside the recorded
 * layer must stay on the static renderer, or the backdrop loops and the process dies.
 */
@Composable
private fun AppScaffold(
    page: AppPage,
    onPageChange: (AppPage) -> Unit,
    onBack: () -> Unit,
    bottomOverlay: (@Composable () -> Unit)? = null,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    val rootPage = page == AppPage.Home || page == AppPage.History || page == AppPage.Settings
    val backdrop = LocalLiquidBackdrop.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 金标联盟大屏适配：<600dp 用底部玻璃导航条（可拖动胶囊）；≥600dp 换侧边玻璃导航轨，
    // 内容在超宽屏限宽居中不拉伸。折叠/旋转由 manifest configChanges 保证不重建，这里
    // 直接随 LocalConfiguration 重组即完成形态切换。
    val widthDp = LocalConfiguration.current.screenWidthDp
    val useRail = widthDp >= 600
    val railInset = if (useRail) 112.dp else 0.dp
    val contentCap = if (useRail) 840.dp else Int.MAX_VALUE.dp
    val chromeCap = if (useRail) 952.dp else Int.MAX_VALUE.dp
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            RadarAmbientBackground()
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.widthIn(max = contentCap).fillMaxWidth().fillMaxHeight()) {
                    // Content starts below the floating header; the bottom stays unpadded so
                    // lists can scroll under the glass navigation/dock and be refracted by them.
                    content(
                        androidx.compose.foundation.layout.PaddingValues(
                            start = railInset,
                            top = topInset + 88.dp,
                            bottom = 0.dp,
                        ),
                    )
                }
            }
        }
        GlassPanel(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = chromeCap).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            tone = GlassTone.Neutral,
            effect = GlassEffect.Elevated,
            shape = RoundedCornerShape(24.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!rootPage) {
                    GlassInlineAction(
                        label = "返回",
                        onClick = onBack,
                        content = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, modifier = Modifier.size(18.dp), tint = LiquidCyan) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LiquidTextPrimary)
                    Text(if (page == AppPage.Home) "离线课堂助手" else "水课雷达", style = MaterialTheme.typography.labelMedium, color = LiquidTextSecondary)
                }
                if (page == AppPage.Home) {
                    // App icon (same radar mark as the launcher icon) instead of the mic glyph.
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(RadarBrand),
                    )
                }
            }
        }
        if (rootPage) {
            if (useRail) {
                GlassNavRail(
                    page = page,
                    onPageChange = onPageChange,
                    modifier = Modifier.align(Alignment.CenterStart).statusBarsPadding().navigationBarsPadding().padding(start = 16.dp),
                )
            } else {
                GlassNavBar(
                    page = page,
                    onPageChange = onPageChange,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        if (bottomOverlay != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = railInset, bottom = if (useRail) 36.dp else 96.dp),
            ) { bottomOverlay() }
        }
    }
}

/**
 * Static warm ambient light fields behind the page content. They never sample the backdrop
 * (safe inside the recorded layer), keep the plain background from feeling sterile, and give
 * the floating glass chrome something subtle to refract.
 */
@Composable
private fun RadarAmbientBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFFFFF8F3), RadarBg, RadarBg)),
        )
        drawCircle(
            Brush.radialGradient(
                colors = listOf(RadarBrand.copy(alpha = 0.09f), Color.Transparent),
                center = Offset(size.width * 0.86f, size.height * 0.1f),
                radius = size.width * 0.75f,
            ),
        )
        drawCircle(
            Brush.radialGradient(
                colors = listOf(RadarWarn.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(size.width * 0.08f, size.height * 0.52f),
                radius = size.width * 0.6f,
            ),
        )
        drawCircle(
            Brush.radialGradient(
                colors = listOf(RadarBrand.copy(alpha = 0.05f), Color.Transparent),
                center = Offset(size.width * 0.72f, size.height * 0.94f),
                radius = size.width * 0.65f,
            ),
        )
    }
}

/** Emphasized-decelerate page motion in the spirit of the 金标联盟 一镜到底 guidance:
 * chrome (header/nav) stays put for continuity while content glides in with a long soft
 * settle and the outgoing page exits quickly without fighting for attention. */
private val RootPageOrder = listOf(AppPage.Home, AppPage.History, AppPage.Settings)
private val PageDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private fun AnimatedContentTransitionScope<AppPage>.pageTransition(): ContentTransform {
    val fromRoot = RootPageOrder.indexOf(initialState)
    val toRoot = RootPageOrder.indexOf(targetState)
    // Between root tabs, slide with the tab order; entering a detail page pushes from the
    // right, returning slides back from the left.
    val forward = if (fromRoot >= 0 && toRoot >= 0) toRoot > fromRoot else toRoot < 0
    val enter = fadeIn(tween(400, easing = PageDecelerate)) +
        slideInHorizontally(tween(400, easing = PageDecelerate)) { fullWidth -> if (forward) fullWidth / 5 else -fullWidth / 5 } +
        scaleIn(tween(400, easing = PageDecelerate), initialScale = 0.96f)
    val exit = fadeOut(tween(160)) +
        slideOutHorizontally(tween(200)) { fullWidth -> if (forward) -fullWidth / 12 else fullWidth / 12 }
    return enter.togetherWith(exit)
}

/** Bottom navigation with a draggable liquid-glass selection pill, floating above the content layer. */
@Composable
private fun GlassNavBar(page: AppPage, onPageChange: (AppPage) -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(
        modifier = modifier,
        tone = GlassTone.Neutral,
        effect = GlassEffect.Elevated,
        shape = RoundedCornerShape(28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val pages = listOf(AppPage.Home, AppPage.History, AppPage.Settings)
            val slotWidth = maxWidth / 3
            val selectedIndex = pages.indexOf(page).coerceAtLeast(0)
            val density = LocalDensity.current
            val slotWidthPx = with(density) { slotWidth.toPx() }
            // During a drag the pill tracks the finger directly; otherwise it springs to the
            // selected slot. Null drag offset = not dragging.
            var dragX by remember { mutableStateOf<Float?>(null) }
            val slotX by animateFloatAsState(
                targetValue = slotWidthPx * selectedIndex,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "navPill",
            )
            val pillX = dragX ?: slotX
            val dragging = dragX != null
            // Droplet squash while held.
            val pillScaleX by animateFloatAsState(
                targetValue = if (dragging) 1.1f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                label = "navPillSquash",
            )
            // The drag detector sits on the whole bar: nav-item clicks still get plain taps
            // (a tap never crosses the drag touch slop), while any horizontal press-drag —
            // starting on the pill or anywhere else on the bar — pulls the pill along and
            // snaps it to the nearest tab on release.
            Box(
                modifier = Modifier.fillMaxWidth().pointerInput(slotWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragX = slotX },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragX = ((dragX ?: slotX) + dragAmount).coerceIn(0f, slotWidthPx * 2)
                        },
                        onDragEnd = {
                            val nearest = ((dragX ?: slotX) / slotWidthPx).roundToInt().coerceIn(0, 2)
                            dragX = null
                            onPageChange(pages[nearest])
                        },
                        onDragCancel = { dragX = null },
                    )
                },
            ) {
                // The pill is a brand-tinted glass droplet riding on the bar: it refracts
                // whatever scrolls beneath and snaps to the nearest tab after a drag.
                GlassPanel(
                    modifier = Modifier
                        .offset { IntOffset(pillX.roundToInt(), 0) }
                        .padding(3.dp)
                        .size(width = slotWidth - 6.dp, height = 56.dp)
                        .graphicsLayer {
                            scaleX = pillScaleX
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                    tone = GlassTone.Accent,
                    effect = GlassEffect.Soft,
                    shape = RoundedCornerShape(21.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {}
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LiquidNavItem(page == AppPage.Home, "听课", Icons.Outlined.Mic) { onPageChange(AppPage.Home) }
                    LiquidNavItem(page == AppPage.History, "记录", Icons.Outlined.History) { onPageChange(AppPage.History) }
                    LiquidNavItem(page == AppPage.Settings, "设置", Icons.Outlined.Settings) { onPageChange(AppPage.Settings) }
                }
            }
        }
    }
}

/** Vertical glass navigation rail for medium/expanded widths (金标大屏形态). */
@Composable
private fun GlassNavRail(page: AppPage, onPageChange: (AppPage) -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(
        modifier = modifier,
        tone = GlassTone.Neutral,
        effect = GlassEffect.Elevated,
        shape = RoundedCornerShape(28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RailNavItem(page == AppPage.Home, "听课", Icons.Outlined.Mic) { onPageChange(AppPage.Home) }
            RailNavItem(page == AppPage.History, "记录", Icons.Outlined.History) { onPageChange(AppPage.History) }
            RailNavItem(page == AppPage.Settings, "设置", Icons.Outlined.Settings) { onPageChange(AppPage.Settings) }
        }
    }
}

@Composable
private fun RailNavItem(selected: Boolean, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val tint = if (selected) RadarBrandDeep else LiquidTextSecondary
    val inner: @Composable BoxScope.() -> Unit = {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp), tint = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
    if (selected) {
        // Selected slot gets the same brand-tinted glass droplet as the bottom bar's pill.
        GlassPanel(
            modifier = Modifier.width(76.dp).height(64.dp).clickable(onClick = onClick),
            tone = GlassTone.Accent,
            effect = GlassEffect.Soft,
            shape = RoundedCornerShape(20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            content = inner,
        )
    } else {
        Box(
            modifier = Modifier.width(76.dp).height(64.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
            content = inner,
        )
    }
}

@Composable
private fun RowScope.LiquidNavItem(selected: Boolean, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(21.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = if (selected) RadarBrandDeep else LiquidTextSecondary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) RadarBrandDeep else LiquidTextSecondary)
    }
}

@Composable
private fun HomeScreen(
    state: DashboardState,
    onOpenTimeline: () -> Unit,
    modifier: Modifier = Modifier,
    twoPane: Boolean = false,
) {
    // The list itself owns the bottom padding, so transcript cards slide under the glass
    // dock and navigation bar and are refracted by them as they pass.
    val bottomPad = if (LocalConfiguration.current.screenWidthDp >= 600) 148.dp else 208.dp
    if (twoPane) {
        // ≥840dp（平板/折叠展开/横屏）：转写左栏、提问右栏，一屏同时看两块，内容不拉伸。
        Row(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard(state.isRecording, state.elapsedSeconds)
                TranscriptCard(
                    transcript = state.transcript,
                    liveTranscript = state.liveTranscript,
                    failureMessage = state.failureMessage,
                    triggerWords = state.activeTriggerWords,
                )
                Spacer(Modifier.height(bottomPad))
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPad),
            ) {
                item { QuestionSectionHeader(state, onOpenTimeline) }
                questionItems(state)
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPad),
    ) {
        item { StatusCard(state.isRecording, state.elapsedSeconds) }
        item {
            TranscriptCard(
                transcript = state.transcript,
                liveTranscript = state.liveTranscript,
                failureMessage = state.failureMessage,
                triggerWords = state.activeTriggerWords,
            )
        }
        item { QuestionSectionHeader(state, onOpenTimeline) }
        questionItems(state)
    }
}

@Composable
private fun QuestionSectionHeader(state: DashboardState, onOpenTimeline: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("老师提问", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        GlassInlineAction(
            label = "时间轴",
            onClick = onOpenTimeline,
            enabled = state.activeSessionId != null,
            content = {
                Icon(
                    Icons.Outlined.Timeline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (state.activeSessionId != null) LiquidTextPrimary else LiquidTextSecondary.copy(alpha = 0.45f),
                )
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.questionItems(state: DashboardState) {
    state.latestPrompt?.let { prompt ->
        item(key = "prompt_${prompt.timestampMillis}") {
            Box(modifier = Modifier.animateItem()) { PromptWarningCard(prompt) }
        }
    }
    if (state.questions.isEmpty()) {
        item { EmptyQuestionCard() }
    } else {
        items(state.questions, key = { it.timestampMillis }) {
            Box(modifier = Modifier.animateItem()) { QuestionPreviewCard(it) }
        }
    }
}

/** Floating two-button dock: start/stop recording and copy the transcript. */
@Composable
private fun HomeActionDock(
    state: DashboardState,
    onRecordingToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    GlassPanel(
        modifier = modifier,
        tone = GlassTone.Neutral,
        effect = GlassEffect.Elevated,
        shape = RoundedCornerShape(36.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockRecordButton(recording = state.isRecording, onClick = onRecordingToggle)
            DockCopyButton(
                onClick = {
                    val text = buildString {
                        state.transcript.takeLast(20).forEach { line ->
                            if (isNotEmpty()) append('\n')
                            append(line)
                        }
                        if (state.liveTranscript.isNotEmpty()) {
                            if (isNotEmpty()) append('\n')
                            append(state.liveTranscript)
                        }
                        if (isEmpty()) {
                            state.questions.lastOrNull()?.let { append(it.question) }
                        }
                    }
                    if (text.isBlank()) {
                        Toast.makeText(context, "暂无可复制内容", Toast.LENGTH_SHORT).show()
                    } else {
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

/**
 * The dock's primary action: a solid gradient mic button (no text label — the glyph carries
 * the meaning). While recording it flips to a red stop square and breathes a sonar-style
 * halo ring, echoing the radar theme.
 */
@Composable
private fun DockRecordButton(recording: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "dockRecordPress",
    )
    Box(contentAlignment = Alignment.Center) {
        if (recording) {
            val halo = rememberInfiniteTransition(label = "recordHalo")
            val haloProgress by halo.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
                label = "recordHaloProgress",
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        val scale = 1f + haloProgress * 0.5f
                        scaleX = scale
                        scaleY = scale
                        alpha = (1f - haloProgress) * 0.55f
                    }
                    .border(2.dp, RadarAlert, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        if (recording) listOf(Color(0xFFFF8A75), RadarAlert)
                        else listOf(RadarBrand, RadarBrandDeep),
                    ),
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = if (recording) "停止记录" else "开始记录",
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (recording) {
                Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Color.White))
            } else {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(28.dp), tint = Color.White)
            }
        }
    }
}

/** Secondary dock action: small glass circle, icon only. */
@Composable
private fun DockCopyButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "dockCopyPress",
    )
    GlassPanel(
        modifier = Modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .size(50.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "复制转写",
                role = Role.Button,
                onClick = onClick,
            ),
        tone = GlassTone.Neutral,
        effect = GlassEffect.Soft,
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Icon(
            Icons.Outlined.ContentCopy,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).size(20.dp),
            tint = RadarBrandDeep,
        )
    }
}

@Composable
private fun StatusCard(recording: Boolean, seconds: Long) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        tone = if (recording) GlassTone.AlertSoft else GlassTone.Neutral,
        effect = GlassEffect.Static,
        shape = RoundedCornerShape(18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val foreground = if (recording) RadarAlert else LiquidTextPrimary
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                val pulse = rememberInfiniteTransition(label = "recordDot")
                val dotAlpha by pulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
                    label = "recordDotAlpha",
                )
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background((if (recording) RadarAlert else LiquidTextPrimary).copy(alpha = if (recording) dotAlpha else 1f)))
                Text(if (recording) "正在录音" else "准备开始", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = foreground)
            }
            if (recording) {
                Text(formatDuration(seconds), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = foreground)
            }
        }
    }
}

@Composable
private fun PrivacyBadge() {
    GlassPanel(
        tone = GlassTone.Neutral,
        shape = RoundedCornerShape(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(16.dp), tint = LiquidCyan)
            Text("设备端识别 · 无网络权限", style = MaterialTheme.typography.labelMedium, color = LiquidTextSecondary)
        }
    }
}

@Composable
private fun TranscriptCard(
    transcript: List<String>,
    liveTranscript: String,
    failureMessage: String?,
    triggerWords: Set<String>,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), tone = GlassTone.Neutral, effect = GlassEffect.Static, shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, null, modifier = Modifier.size(20.dp), tint = RadarBrand)
                Spacer(Modifier.width(8.dp))
                Text("实时课堂记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LiquidTextPrimary)
            }
            // Fixed-height scrollable region: new text sticks to the bottom, the user can
            // scroll up to read earlier lines without the card growing taller.
            val scrollState = rememberScrollState()
            LaunchedEffect(transcript.size, liveTranscript.length) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            Column(
                modifier = Modifier.fillMaxWidth().height(148.dp).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (transcript.isEmpty() && liveTranscript.isEmpty()) {
                    Text("转写内容会实时显示在这里", color = LiquidTextSecondary)
                } else {
                    transcript.takeLast(30).forEach { line ->
                        Text(
                            highlightTriggers(line, triggerWords),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = LiquidTextPrimary,
                        )
                    }
                    if (liveTranscript.isNotEmpty()) {
                        GlassPanel(
                            modifier = Modifier.animateContentSize(),
                            tone = GlassTone.Accent,
                            effect = GlassEffect.Static,
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        ) {
                            Text(
                                highlightTriggers("正在识别  $liveTranscript", triggerWords),
                                color = LiquidTextPrimary,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (failureMessage != null) Text(failureMessage, color = LiquidTextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Website-style <mark>: trigger words get a soft yellow chip with deep-brand text. */
private fun highlightTriggers(text: String, triggerWords: Set<String>): AnnotatedString {
    if (triggerWords.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    val matches = mutableListOf<IntRange>()
    triggerWords.forEach { word ->
        if (word.isBlank()) return@forEach
        var index = text.indexOf(word)
        while (index >= 0) {
            matches.add(index until index + word.length)
            index = text.indexOf(word, index + word.length)
        }
    }
    if (matches.isEmpty()) return AnnotatedString(text)
    // Merge overlapping ranges so longer built-in phrases win over their own substrings.
    val merged = matches.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, range ->
        val last = acc.lastOrNull()
        if (last != null && range.first <= last.last) {
            acc[acc.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            acc.add(range)
        }
        acc
    }
    return buildAnnotatedString {
        append(text)
        merged.forEach { range ->
            addStyle(
                SpanStyle(background = RadarWarnSoft, color = RadarBrandDeep, fontWeight = FontWeight.Bold),
                range.first,
                range.last + 1,
            )
        }
    }
}

@Composable
private fun EmptyQuestionCard() {
    GlassPanel(modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp), tone = GlassTone.Neutral, effect = GlassEffect.Static, shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Outlined.Timeline, null, tint = LiquidTextSecondary)
            Text("正在等待课堂提问", color = LiquidTextSecondary)
        }
    }
}

@Composable
private fun PromptWarningCard(prompt: PromptWarning) {
    // Stage-1 banner: the trigger word just fired, the full question is still being extracted.
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        tone = GlassTone.Warning,
        effect = GlassEffect.Static,
        shape = RoundedCornerShape(20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, modifier = Modifier.size(20.dp), tint = RadarWarnText)
            Spacer(Modifier.width(10.dp))
            Text(
                "⚠️ ${formatClock(prompt.timestampMillis)} 可能被提问：检测到“${prompt.trigger}”",
                fontWeight = FontWeight.SemiBold,
                color = RadarWarnText,
            )
        }
    }
}

@Composable
private fun QuestionPreviewCard(question: QuestionDetector.DetectionResult) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), tone = GlassTone.Neutral, effect = GlassEffect.Static, shape = RoundedCornerShape(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WarningAmber, null, tint = RadarAlert)
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("❓ ${formatClock(question.timestampMillis)} · 课堂提问", fontWeight = FontWeight.Bold, color = RadarAlert)
                    question.matchedTrigger?.let { Text("检测到“$it”", color = LiquidTextSecondary) }
                    Text("问题回溯：${question.question}", maxLines = 3, overflow = TextOverflow.Ellipsis, color = LiquidTextPrimary)
                }
            }
            QuestionAiActions(question.question)
        }
    }
}

/** AI 接力：复制问题或经透明中转直接跳豆包 / DeepSeek（未安装时 Toast 兜底）。 */
@Composable
private fun QuestionAiActions(question: String) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AiActionChip("复制") {
            launchQuestionAction(context, QuestionActionActivity.ACTION_COPY, question)
        }
        AiActionChip("问豆包") {
            launchQuestionAction(context, QuestionActionActivity.ACTION_OPEN_DOUBAO, question)
        }
        AiActionChip("问 DeepSeek") {
            launchQuestionAction(context, QuestionActionActivity.ACTION_OPEN_DEEPSEEK, question)
        }
    }
}

@Composable
private fun AiActionChip(label: String, onClick: () -> Unit) {
    GlassPanel(
        modifier = Modifier.clickable(onClick = onClick),
        tone = GlassTone.Accent,
        effect = GlassEffect.Static,
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = RadarBrandDeep)
    }
}

private fun launchQuestionAction(context: Context, action: String, question: String) {
    runCatching {
        context.startActivity(
            Intent(context, QuestionActionActivity::class.java)
                .setAction(action)
                .putExtra(QuestionActionActivity.EXTRA_QUESTION, question)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryScreen(
    sessions: List<SessionSummary>,
    selecting: Boolean,
    selectedIds: Set<Long>,
    onSelectingChange: (Boolean) -> Unit,
    onSelectionChange: (Set<Long>) -> Unit,
    onOpenSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassPanel(tone = GlassTone.Neutral, effect = GlassEffect.Static, shape = RoundedCornerShape(24.dp)) {
                Text("还没有已保存的课堂记录", color = LiquidTextSecondary)
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp),
    ) {
        item {
            if (selecting) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "已选 ${selectedIds.size} / ${sessions.size} 项",
                        color = LiquidTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    val allSelected = selectedIds.size == sessions.size
                    Text(
                        text = if (allSelected) "取消全选" else "全选",
                        color = RadarBrandDeep,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                onSelectionChange(if (allSelected) emptySet() else sessions.mapTo(mutableSetOf()) { it.id })
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "退出",
                        color = LiquidTextSecondary,
                        modifier = Modifier
                            .clickable { onSelectingChange(false) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            } else {
                Text("每节课均只保存在本机，长按可批量删除。", color = LiquidTextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            }
        }
        items(sessions, key = { it.id }) { session ->
            val selected = session.id in selectedIds
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (selecting) {
                                onSelectionChange(if (selected) selectedIds - session.id else selectedIds + session.id)
                            } else {
                                onOpenSession(session.id)
                            }
                        },
                        onLongClick = {
                            if (!selecting) {
                                onSelectingChange(true)
                                onSelectionChange(setOf(session.id))
                            }
                        },
                    ),
                tone = if (selected) GlassTone.Accent else GlassTone.Neutral,
                effect = GlassEffect.Static,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selecting) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .then(
                                    if (selected) Modifier.background(RadarBrand)
                                    else Modifier.border(2.dp, LiquidTextSecondary.copy(alpha = 0.5f), CircleShape),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(RadarBrandSoft).padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${session.questionCount}问", fontWeight = FontWeight.Bold, color = RadarBrandDeep)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(session.title, fontWeight = FontWeight.Bold, color = LiquidTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatDateTime(session.startedAtMillis)} · ${formatSessionDuration(session)}", style = MaterialTheme.typography.bodySmall, color = LiquidTextSecondary)
                        if (session.audioRelativePath != null) {
                            Text("已保存私有录音", style = MaterialTheme.typography.labelMedium, color = RadarBrandDeep)
                        }
                    }
                    if (!selecting) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, modifier = Modifier.size(16.dp).rotate(180f), tint = LiquidTextSecondary)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun TimelineScreen(detail: SessionDetailState, modifier: Modifier = Modifier) {
    val session = detail.session
    if (session == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在加载记录…", color = LiquidTextSecondary) }
        return
    }
    if (detail.questions.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("这节课没有捕捉到提问", color = LiquidTextSecondary)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(session.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LiquidTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${formatDateTime(session.startedAtMillis)} · 共 ${detail.questions.size} 次提问", style = MaterialTheme.typography.bodySmall, color = LiquidTextSecondary)
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(RadarBrandSoft).padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("已捕捉 ${detail.questions.size}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = RadarBrandDeep)
                }
            }
        }
        items(detail.questions.size) { index ->
            val event = detail.questions[index]
            val isLatest = index == 0
            var expanded by remember(event.id) { mutableStateOf(false) }
            GlassPanel(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                tone = if (isLatest) GlassTone.Warning else GlassTone.Neutral,
                effect = GlassEffect.Static,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("❓ ${formatClock(event.timestampMillis)} · 问题回溯", fontWeight = FontWeight.Bold, color = RadarAlert)
                        if (isLatest) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(RadarBrand).padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("最新", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                    event.matchedTrigger?.let { Text("预警触发词：“$it”", color = LiquidTextSecondary) }
                    Text(event.question, style = MaterialTheme.typography.titleMedium, color = LiquidTextPrimary)
                    QuestionAiActions(event.question)
                    Text(if (expanded) "收起上下文" else "点击回看上下文 ±3 句", color = RadarBrandDeep)
                    if (expanded) {
                        HorizontalDivider()
                        contextLines(detail.transcript, event).forEach { line ->
                            val highlight = line.text == event.sourceLine
                            Text(
                                text = line.text,
                                color = if (highlight) LiquidTextPrimary else LiquidTextSecondary,
                                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun SessionDetailScreen(
    detail: SessionDetailState,
    isActiveRecording: Boolean,
    onOpenTimeline: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportText: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirmation by remember(detail.session?.id) { mutableStateOf(false) }
    var editingTitle by remember(detail.session?.id) { mutableStateOf<String?>(null) }
    if (showDeleteConfirmation) {
        GlassConfirmDialog(
            title = "删除这节课？",
            message = "转写、提问和可选的私有录音都会删除，无法恢复。",
            confirmLabel = "删除",
            dismissLabel = "取消",
            onConfirm = { showDeleteConfirmation = false; onDelete() },
            onDismiss = { showDeleteConfirmation = false },
            tone = GlassTone.Danger,
        )
    }
    val session = detail.session
    if (session == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在加载记录…", color = LiquidTextSecondary) }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
    ) {
        item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), tone = GlassTone.Accent, effect = GlassEffect.Static) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val editing = editingTitle
                    if (editing != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassTextInput(
                                value = editing,
                                onValueChange = { editingTitle = it },
                                label = "课程名称",
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            GlassRoundIconAction(
                                icon = Icons.Outlined.Add,
                                contentDescription = "保存名称",
                                onClick = { onRename(editing); editingTitle = null },
                                enabled = editing.isNotBlank(),
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                session.title,
                                modifier = Modifier.weight(1f).clickable { editingTitle = session.title },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = LiquidTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            GlassInlineAction(
                                label = "重命名",
                                onClick = { editingTitle = session.title },
                                content = {
                                    Icon(Icons.Outlined.Description, null, modifier = Modifier.size(16.dp), tint = RadarBrandDeep)
                                },
                            )
                        }
                    }
                    Text("${formatDateTime(session.startedAtMillis)} · ${formatSessionDuration(session)} · ${detail.questions.size} 个提问", color = LiquidTextSecondary)
                    if (session.audioRelativePath != null) Text("原始录音已保存在应用私有目录", color = LiquidTextPrimary)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GlassTextAction("提问时间轴", onOpenTimeline, Modifier.weight(1f), Icons.Outlined.Timeline)
                GlassTextAction("导出 MD", onExportMarkdown, Modifier.weight(1f), Icons.Outlined.FileDownload)
            }
        }
        item {
            GlassTextAction("导出 TXT", onExportText, Modifier.fillMaxWidth(), Icons.Outlined.Description)
        }
        item { Text("转写全文", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LiquidTextPrimary) }
        if (detail.transcript.isEmpty()) {
            item { Text("本节课还没有定稿转写。", color = LiquidTextSecondary) }
        } else {
            val questionSourceLines = detail.questions.map { it.sourceLine }.toSet()
            items(detail.transcript, key = { it.id }) { line ->
                val highlighted = line.text in questionSourceLines
                Text(
                    line.text,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(
                        if (highlighted) RadarWarnSoft else Color.Transparent,
                    ).padding(if (highlighted) 9.dp else 4.dp),
                    color = if (highlighted) LiquidTextPrimary else LiquidTextSecondary,
                )
            }
        }
        item {
            GlassTextAction(
                label = if (isActiveRecording) "录音中无法删除" else "删除本节记录",
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.DeleteOutline,
                tone = GlassTone.Danger,
                enabled = !isActiveRecording,
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun TriggerWordsScreen(
    state: SettingsState,
    onBuiltInChanged: (String, Boolean) -> Unit,
    onAddCustom: (String) -> Unit,
    onRemoveCustom: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customWord by remember { mutableStateOf("") }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
    ) {
        item {
            Text("触发词命中立即黄色预警（可能被提问）；提取出完整问题后震动提醒并保存到记录。疑问句检测始终保持开启。", color = LiquidTextSecondary)
        }
        item { Text("内置词表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LiquidTextPrimary) }
        items(QuestionDetector.DEFAULT_TRIGGER_WORDS.toList(), key = { it }) { word ->
            val enabled = word !in state.disabledBuiltInTriggers
            SettingRow(
                title = word,
                supporting = "内置触发词",
                checked = enabled,
                onCheckedChange = { onBuiltInChanged(word, it) },
            )
        }
        item { Text("自定义词", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LiquidTextPrimary) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassTextInput(
                    value = customWord,
                    onValueChange = { customWord = it },
                    label = "例如：这位同学",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                GlassRoundIconAction(
                    icon = Icons.Outlined.Add,
                    contentDescription = "添加",
                    onClick = { onAddCustom(customWord); customWord = "" },
                    enabled = customWord.isNotBlank(),
                )
            }
        }
        if (state.customTriggers.isEmpty()) {
            item { Text("还没有自定义触发词", color = LiquidTextSecondary) }
        } else {
            items(state.customTriggers, key = { it }) { word ->
                GlassPanel(modifier = Modifier.fillMaxWidth(), tone = GlassTone.Neutral, effect = GlassEffect.Static, shape = RoundedCornerShape(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(word, modifier = Modifier.weight(1f), color = LiquidTextPrimary)
                        GlassRoundIconAction(
                            icon = Icons.Outlined.Close,
                            contentDescription = "删除",
                            onClick = { onRemoveCustom(word) },
                            tone = GlassTone.Danger,
                            tint = Color.White,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

private fun vibrationPatternLabel(pattern: String): String = when (pattern) {
    AppPreferences.VIBRATION_URGENT -> "急促"
    AppPreferences.VIBRATION_GENTLE -> "温和"
    else -> "标准"
}

@Composable
private fun SettingsScreen(
    state: SettingsState,
    onVibrationChanged: (Boolean) -> Unit,
    onVibrationPatternChanged: (String) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onSaveAudioChanged: (Boolean) -> Unit,
    onOpenTriggers: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp),
    ) {
        item { Text("提醒", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LiquidTextPrimary) }
        item {
            SettingRow("震动提醒", "捕捉到提问时使用本机震动", state.vibrationEnabled, onVibrationChanged, Icons.Outlined.Vibration)
        }
        item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), tone = GlassTone.Neutral, effect = GlassEffect.Static, shape = RoundedCornerShape(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("震动节奏", fontWeight = FontWeight.SemiBold, color = LiquidTextPrimary)
                    Text("标准兼顾安静课堂；急促更适合嘈杂环境；温和只轻点一下。", style = MaterialTheme.typography.bodySmall, color = LiquidTextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            AppPreferences.VIBRATION_STANDARD,
                            AppPreferences.VIBRATION_URGENT,
                            AppPreferences.VIBRATION_GENTLE,
                        ).forEach { pattern ->
                            val selected = state.vibrationPattern == pattern
                            GlassPanel(
                                modifier = Modifier.clickable { onVibrationPatternChanged(pattern) },
                                tone = if (selected) GlassTone.Success else GlassTone.Neutral,
                                effect = GlassEffect.Static,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    vibrationPatternLabel(pattern),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else LiquidTextPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingRow("提示音", "仅连接耳机时播放轻提示音，避免课堂外放；默认关闭", state.soundEnabled, onSoundChanged, Icons.AutoMirrored.Outlined.VolumeUp)
        }
        item { GlassDivider() }
        item { Text("隐私与存储", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LiquidTextPrimary) }
        item {
            SettingRow("保存原始录音", "默认关闭；开启后仅保存到应用私有目录", state.saveAudioEnabled, onSaveAudioChanged, Icons.Outlined.Lock)
        }
        item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), tone = GlassTone.Accent, effect = GlassEffect.Static, shape = RoundedCornerShape(24.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.PrivacyTip, null, tint = LiquidCyan)
                    Spacer(Modifier.width(10.dp))
                    Text("水课雷达不申请网络权限，不收集、不上传任何录音、转写或提问记录。卸载应用会清除其私有数据。", color = LiquidTextSecondary)
                }
            }
        }
        item { GlassDivider() }
        item { Text("使用与保活", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LiquidTextPrimary) }
        item {
            GlassTextAction("管理提问触发词", onOpenTriggers, Modifier.fillMaxWidth(), Icons.Outlined.Lightbulb)
        }
        item {
            GlassTextAction("打开系统电池优化设置", onOpenBatterySettings, Modifier.fillMaxWidth(), Icons.Outlined.BatteryChargingFull)
        }
        item {
            GlassTextAction("打开实时通知设置", onOpenNotificationSettings, Modifier.fillMaxWidth(), Icons.Outlined.NotificationsActive)
        }
        item {
            Text("录音时请保持前台服务通知可见；部分国产系统还需要在系统设置中允许自启动或取消电池限制。", color = LiquidTextSecondary)
        }
        item { GlassDivider() }
        item {
            val context = LocalContext.current
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("关于", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LiquidTextPrimary)
                Text("水课雷达 v0.4.1 · 全程设备端识别", color = LiquidTextSecondary)
                Text("包含 sherpa-onnx 与 Silero VAD 等开源组件，详情见应用随附的第三方声明。", color = LiquidTextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { openPrivacyPolicy(context) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.PrivacyTip, null, modifier = Modifier.size(18.dp), tint = RadarBrandDeep)
                    Spacer(Modifier.width(8.dp))
                    Text("隐私政策", fontWeight = FontWeight.SemiBold, color = RadarBrandDeep)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, modifier = Modifier.size(16.dp).rotate(180f), tint = LiquidTextSecondary)
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun SettingRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), tone = GlassTone.Neutral, effect = GlassEffect.Static, shape = RoundedCornerShape(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(it, null, tint = LiquidCyan)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = LiquidTextPrimary)
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = LiquidTextSecondary)
            }
            GlassToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
                label = title,
            )
        }
    }
}

@Composable
private fun GlassDivider() = Box(
    modifier = Modifier.fillMaxWidth().height(1.dp).background(LiquidTextSecondary.copy(alpha = 0.24f)),
)

@Composable
private fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    GlassConfirmDialog(
        title = "上次异常退出",
        message = "应用上次运行时遇到了错误并已记录到本机。如愿意排查，可将日志复制后发给开发者。",
        confirmLabel = "复制日志",
        dismissLabel = "知道了",
        onConfirm = {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("水课雷达崩溃日志", report))
            android.widget.Toast.makeText(context, "日志已复制", android.widget.Toast.LENGTH_SHORT).show()
            onDismiss()
        },
        onDismiss = onDismiss,
        tone = GlassTone.Danger,
    )
}

@Composable
private fun UsageNotice(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    GlassConfirmDialog(
        title = "使用提示",
        message = "本应用仅供个人学习记录使用，请勿传播课堂录音与转写内容。所有处理均在设备本地完成，应用不申请网络权限。",
        confirmLabel = "同意并继续",
        dismissLabel = "暂不使用",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun RealtimeNotificationRequired(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    GlassConfirmDialog(
        title = "需要开启实时通知",
        message = "录音期间需要显示前台实时通知，才能在通知栏持续显示计时、转写和停止按钮。请在系统设置中允许水课雷达通知，并开启“课堂录音状态”通知渠道后再开始录音。安卓 16 还可在下一页开启系统“实时更新”。",
        confirmLabel = "去开启",
        dismissLabel = "暂不录音",
        onConfirm = onOpenSettings,
        onDismiss = onDismiss,
    )
}

private fun requiredPermissions(context: Context): Array<String> = buildList {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
private fun rememberLauncher(onResult: (Map<String, Boolean>) -> Unit) =
    androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        onResult,
    )

/** 跳转官网隐私政策页；无网络权限的是本 App，浏览器负责联网。 */
private fun openPrivacyPolicy(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://shuike.seanyan.store/#/privacy"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, "请用浏览器打开 shuike.seanyan.store/#/privacy", Toast.LENGTH_LONG).show()
    }
}

private fun openBatteryOptimizationSettings(context: Context) {    runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openRealtimeNotificationSettings(context: Context) {
    // The promoted-notification page only fixes Android 16 Live Updates. If normal app
    // notifications or the recording channel are blocked, send the user to the regular app
    // notification page instead so the actual blocker is visible.
    val action = if (
        Build.VERSION.SDK_INT >= 36 &&
        RecordingService.shouldOpenPromotedNotificationSettings(context)
    ) {
        promotedNotificationSettingsAction()
    } else {
        Settings.ACTION_APP_NOTIFICATION_SETTINGS
    }
    val intent = Intent(action)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
                .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
}

@RequiresApi(36)
private fun promotedNotificationSettingsAction(): String =
    Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS

private fun contextLines(lines: List<TranscriptLineEntity>, event: QuestionEventEntity): List<TranscriptLineEntity> {
    if (lines.isEmpty()) return emptyList()
    val center = lines.indexOfLast { it.timestampMillis <= event.timestampMillis }.let { if (it >= 0) it else 0 }
    return lines.subList(max(0, center - 3), min(lines.size, center + 4))
}

private fun ClassroomRepository.ExportRecord.toMarkdown(): String = buildString {
    appendLine("# 水课雷达课堂记录")
    appendLine()
    appendLine("- 课程名称：${session.title}")
    appendLine("- 开始时间：${formatDateTime(session.startedAtMillis)}")
    appendLine("- 课程时长：${formatSessionDuration(session)}")
    appendLine("- 提问数量：${questions.size}")
    appendLine()
    appendLine("## 提问时间轴")
    if (questions.isEmpty()) appendLine("- 未捕捉到提问")
    questions.forEach { event ->
        appendLine("- ${formatClock(event.timestampMillis)} · ${event.matchedTrigger?.let { "触发词：$it；" } ?: ""}${event.question}")
    }
    appendLine()
    appendLine("## 转写全文")
    transcript.forEach { appendLine(it.text) }
}

private fun ClassroomRepository.ExportRecord.toPlainText(): String = buildString {
    appendLine("水课雷达课堂记录")
    appendLine("课程名称：${session.title}")
    appendLine("开始时间：${formatDateTime(session.startedAtMillis)}")
    appendLine("课程时长：${formatSessionDuration(session)}")
    appendLine("提问数量：${questions.size}")
    appendLine()
    appendLine("提问时间轴")
    questions.forEach { event -> appendLine("${formatClock(event.timestampMillis)} ${event.question}") }
    appendLine()
    appendLine("转写全文")
    transcript.forEach { appendLine(it.text) }
}

private fun formatDuration(seconds: Long): String = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
private fun formatDateTime(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.CHINA).format(Date(millis))
private fun formatClock(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(millis))
private fun fileStamp(millis: Long): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date(millis))
private fun formatSessionDuration(session: SessionEntity): String = formatDuration(((session.endedAtMillis ?: System.currentTimeMillis()) - session.startedAtMillis).coerceAtLeast(0) / 1_000)
private fun formatSessionDuration(session: SessionSummary): String = formatDuration(((session.endedAtMillis ?: System.currentTimeMillis()) - session.startedAtMillis).coerceAtLeast(0) / 1_000)

private val WaterClassRadarColors = lightColorScheme(
    primary = RadarBrand,
    onPrimary = Color.White,
    secondary = RadarBrandDeep,
    onSecondary = Color.White,
    background = RadarBg,
    onBackground = LiquidTextPrimary,
    surface = Color.White,
    onSurface = LiquidTextPrimary,
    surfaceVariant = RadarSection,
    onSurfaceVariant = LiquidTextSecondary,
    outline = Color(0xFFE6E5E3),
    error = RadarAlert,
)

/** 品牌橙低透明度的按压反馈，替换系统默认的灰色水波纹。 */
private val RadarRippleConfiguration = RippleConfiguration(color = RadarBrand)

@Composable
private fun WaterClassRadarTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = WaterClassRadarColors,
) {
    CompositionLocalProvider(LocalRippleConfiguration provides RadarRippleConfiguration) {
        content()
    }
}

@Composable
private fun <T> rememberState(flow: StateFlow<T>): androidx.compose.runtime.State<T> {
    val state = remember { mutableStateOf(flow.value) }
    LaunchedEffect(flow) { flow.collect { state.value = it } }
    return state
}

@Preview(showBackground = true)
@Composable
private fun WaterClassRadarPreview() = WaterClassRadarTheme { HomeScreen(DashboardState(), {}) }
