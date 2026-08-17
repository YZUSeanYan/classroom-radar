package com.classroomradar.app.audio

import com.classroomradar.app.detection.QuestionDetector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface RecorderEvent {
    data class SessionStarted(val sessionId: Long, val startedAtMillis: Long) : RecorderEvent
    data class Status(val isRecording: Boolean, val elapsedSeconds: Long) : RecorderEvent
    data class PartialTranscript(val text: String) : RecorderEvent
    data class Transcript(val text: String, val timestampMillis: Long) : RecorderEvent

    /** Stage-1 cue warning: UI-only, never persisted, never vibrates or notifies. */
    data class Prompt(val timestampMillis: Long, val trigger: String) : RecorderEvent
    data class Question(val result: QuestionDetector.DetectionResult) : RecorderEvent
    data class Failure(val message: String) : RecorderEvent
}

/** Process-local event stream between the foreground service and Compose UI. */
object RecordingBus {
    private val _events = MutableSharedFlow<RecorderEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun emit(event: RecorderEvent) {
        _events.tryEmit(event)
    }
}
