package com.classroomradar.app.audio

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Two-pass, device-only speech recognition:
 * - Zipformer CTC produces responsive partial text while the microphone is active.
 * - Silero VAD closes a speech segment, then Paraformer produces the final text.
 *
 * Long classroom explanations are deliberately cut into short, safe chunks. This keeps
 * the visible partial text from growing forever when the speaker does not pause.
 *
 * All paths below are Android asset paths. Neither this class nor the manifest
 * requests network access.
 */
class SherpaTwoPassEngine(
    context: Context,
) : TranscriptionEngine {
    private val assets = context.assets
    private val lock = Any()
    private val finalizer: ExecutorService = Executors.newSingleThreadExecutor()

    private var listener: TranscriptionEngine.Listener? = null
    private var onlineRecognizer: OnlineRecognizer? = null
    private var onlineStream: OnlineStream? = null
    private var vad: Vad? = null
    private var offlineRecognizer: OfflineRecognizer? = null
    private var started = false
    private var lastPartial = ""
    private var samplesSinceLastBoundary = 0

    override fun start(listener: TranscriptionEngine.Listener) {
        try {
            synchronized(lock) {
                check(!started) { "The recognizer is already running." }
                this.listener = listener
                // The streaming model is Zipformer2 **CTC** and sherpa-onnx contextual
                // biasing (hotwords) is transducer-only. Unsupported hotwords do not throw:
                // sherpa-onnx logs "Only transducer models support contextual biasing." and
                // calls exit(-1), which kills the whole process silently (exit code 255).
                // Never pass hotwords to either recognizer in this engine.
                onlineRecognizer = OnlineRecognizer(assets, onlineConfig())
                onlineStream = onlineRecognizer!!.createStream()
                vad = Vad(assets, vadConfig())
                offlineRecognizer = OfflineRecognizer(assets, offlineConfig())
                started = true
            }
        } catch (error: Throwable) {
            releaseSynchronous()
            finalizer.shutdown()
            listener.onError(error)
        }
    }

    override fun acceptPcm(samples: ShortArray, count: Int) {
        if (count <= 0) return
        val pcm = FloatArray(count) { index -> samples[index] / PCM_NORMALIZER }
        val segments = mutableListOf<FloatArray>()
        var partial: String? = null
        var fallbackFinal: String? = null

        try {
            synchronized(lock) {
                if (!started) return
                val stream = onlineStream ?: return
                val recognizer = onlineRecognizer ?: return

                stream.acceptWaveform(pcm, SAMPLE_RATE)
                while (recognizer.isReady(stream)) recognizer.decode(stream)
                val candidate = recognizer.getResult(stream).text.trim()
                if (candidate.isNotEmpty() && candidate != lastPartial) {
                    lastPartial = candidate
                    partial = candidate
                }
                if (recognizer.isEndpoint(stream)) {
                    recognizer.reset(stream)
                    lastPartial = ""
                }

                val localVad = vad ?: return
                localVad.acceptWaveform(pcm)
                // The online model can keep returning one ever-growing hypothesis when a
                // lecturer does not pause. Segment on recognised speech, not only on VAD's
                // reported state: VAD state may lag by a few windows on noisy classrooms.
                if (lastPartial.isNotBlank()) {
                    samplesSinceLastBoundary += pcm.size
                } else {
                    samplesSinceLastBoundary = 0
                }
                if (samplesSinceLastBoundary >= MAX_FINAL_SEGMENT_SAMPLES) {
                    val segmentCountBeforeFlush = segments.size
                    localVad.flush()
                    drainVad(localVad, segments)
                    samplesSinceLastBoundary = 0
                    recognizer.reset(stream)

                    // In the rare case where VAD cannot emit a segment after a forced
                    // boundary, retain the visible online text as a checkpoint. This is a
                    // deliberate no-loss fallback; when VAD did emit audio, Paraformer
                    // remains the only source of the persisted final text.
                    if (segments.size == segmentCountBeforeFlush && lastPartial.isNotBlank()) {
                        fallbackFinal = lastPartial
                    }
                    lastPartial = ""
                }
                drainVad(localVad, segments)
                if (segments.isNotEmpty()) samplesSinceLastBoundary = 0
            }
        } catch (error: Throwable) {
            reportError(error)
            return
        }

        partial?.let { text -> listener?.onPartialText(text) }
        segments.forEach(::queueFinalPass)
        // Keep every final callback on the same single-threaded executor. This preserves final
        // transcript order and keeps QuestionDetector's mutable prompt/debounce state confined
        // to one thread, even when VAD had no audio segment at a forced boundary.
        fallbackFinal?.let(::queueFallbackFinal)
    }

    override fun stop(onComplete: () -> Unit) {
        val trailingSegments = mutableListOf<FloatArray>()
        var recognizer: OfflineRecognizer? = null
        var fallbackFinal: String? = null
        var wasStarted = false
        synchronized(lock) {
            if (!started) return@synchronized
            wasStarted = true
            started = false
            samplesSinceLastBoundary = 0
            vad?.let {
                runCatching { it.flush() }
                drainVad(it, trailingSegments)
            }
            if (trailingSegments.isEmpty() && lastPartial.isNotBlank()) {
                // VAD can occasionally fail to produce a final segment during a quick
                // stop. Keep the last user-visible text instead of dropping it.
                fallbackFinal = lastPartial
            }
            lastPartial = ""
            onlineStream?.release()
            onlineStream = null
            onlineRecognizer?.release()
            onlineRecognizer = null
            vad?.release()
            vad = null
            recognizer = offlineRecognizer
        }
        if (!wasStarted) {
            onComplete()
            return
        }

        trailingSegments.forEach(::queueFinalPass)
        fallbackFinal?.let(::queueFallbackFinal)
        // This task is queued after all remaining final-pass jobs, so the native
        // recognizer is never released while it is being decoded on another thread.
        finalizer.execute {
            runCatching { recognizer?.release() }
            onComplete()
        }
        finalizer.shutdown()
        synchronized(lock) { offlineRecognizer = null }
    }

    private fun queueFinalPass(segment: FloatArray) {
        if (segment.isEmpty()) return
        val recognizer = synchronized(lock) { offlineRecognizer } ?: return
        finalizer.execute {
            try {
                // Paraformer is not a transducer: createStream(hotwords) would make
                // sherpa-onnx exit(-1) the moment the first speech segment finishes.
                // Always use the plain stream here.
                val stream: OfflineStream = recognizer.createStream()
                try {
                    stream.acceptWaveform(segment, SAMPLE_RATE)
                    recognizer.decode(stream)
                    val text = recognizer.getResult(stream).text.trim()
                    if (text.isNotEmpty()) listener?.onFinalText(text)
                } finally {
                    stream.release()
                }
            } catch (error: Throwable) {
                reportError(error)
            }
        }
    }

    private fun queueFallbackFinal(text: String) {
        if (text.isBlank()) return
        finalizer.execute { listener?.onFinalText(text) }
    }

    private fun drainVad(vad: Vad, destination: MutableList<FloatArray>) {
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            if (segment.samples.isNotEmpty()) destination += segment.samples.copyOf()
        }
    }

    private fun reportError(error: Throwable) {
        listener?.onError(error)
    }

    private fun releaseSynchronous() = synchronized(lock) {
        runCatching { onlineStream?.release() }
        runCatching { onlineRecognizer?.release() }
        runCatching { vad?.release() }
        runCatching { offlineRecognizer?.release() }
        onlineStream = null
        onlineRecognizer = null
        vad = null
        offlineRecognizer = null
        started = false
    }

    private fun onlineConfig() = OnlineRecognizerConfig(
        modelConfig = OnlineModelConfig(
            zipformer2Ctc = OnlineZipformer2CtcModelConfig(
                model = "models/first_pass/model.int8.onnx",
            ),
            tokens = "models/first_pass/tokens.txt",
            numThreads = 4,
            provider = "cpu",
        ),
        endpointConfig = EndpointConfig(
            rule1 = EndpointRule(false, 1.5F, 0.0F),
            rule2 = EndpointRule(true, 0.8F, 0.0F),
            rule3 = EndpointRule(false, 0.0F, MAX_LIVE_SEGMENT_SECONDS),
        ),
        enableEndpoint = true,
    )

    private fun offlineConfig() = OfflineRecognizerConfig(
        modelConfig = OfflineModelConfig(
            paraformer = OfflineParaformerModelConfig(
                model = "models/second_pass/model.int8.onnx",
            ),
            tokens = "models/second_pass/tokens.txt",
            numThreads = 4,
            provider = "cpu",
        ),
    )

    private fun vadConfig() = VadModelConfig(
        sileroVadModelConfig = SileroVadModelConfig(
            model = "models/vad/silero_vad.onnx",
            threshold = 0.5F,
            minSilenceDuration = 0.35F,
            minSpeechDuration = 0.25F,
            windowSize = 512,
            maxSpeechDuration = MAX_FINAL_SEGMENT_SECONDS,
        ),
        sampleRate = SAMPLE_RATE,
        numThreads = 1,
        provider = "cpu",
    )

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val PCM_NORMALIZER = 32_768.0F
        const val MAX_FINAL_SEGMENT_SECONDS = 4.0F
        const val MAX_LIVE_SEGMENT_SECONDS = 4.5F
        const val MAX_FINAL_SEGMENT_SAMPLES = (SAMPLE_RATE * MAX_FINAL_SEGMENT_SECONDS).toInt()
    }
}
