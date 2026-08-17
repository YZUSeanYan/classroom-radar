package com.classroomradar.app.audio

/** Boundary for the bundled, fully offline ASR implementation. */
interface TranscriptionEngine : AutoCloseable {
    interface Listener {
        fun onPartialText(text: String)
        fun onFinalText(text: String)
        fun onError(error: Throwable)
    }

    fun start(listener: Listener)
    fun acceptPcm(samples: ShortArray, count: Int)
    /** Invokes [onComplete] after all queued final recognition work has drained. */
    fun stop(onComplete: () -> Unit = {})
    override fun close() = stop()
}
