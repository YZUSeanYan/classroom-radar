package com.classroomradar.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Reads only the stage-2 result, ignoring stage-1 prompt warnings. */
private fun QuestionDetector.extractQuestion(
    text: String,
    timestampMillis: Long,
): QuestionDetector.DetectionResult? =
    onFinalTranscript(text, timestampMillis)
        .filterIsInstance<QuestionDetector.DetectionEvent.QuestionExtracted>()
        .firstOrNull()
        ?.result

/**
 * Replay of the real-device transcript from 2026-08-14 (user screenshot): the ASR chopped
 * one utterance into short fragments and the question was never captured because the
 * pending-cue window (then 8s) expired between the fragments.
 */
class QuestionDetectorFragmentedReplayTest {
    @Test
    fun `captures question when cue and question are fragmented with classroom-paced pauses`() {
        val detector = QuestionDetector()

        assertNull(detector.extractQuestion("嗯那么我们首先来提个问", 1_000))
        assertNull(detector.extractQuestion("有没有同学知道什么是", 6_000))
        val result = detector.extractQuestion("什么是 g l m", 17_000)

        assertNotNull("cue + question 11s apart must still combine", result)
        assertEquals("有没有同学", result?.matchedTrigger)
    }

    @Test
    fun `cue plus question tail 20s apart still alerts`() {
        val detector = QuestionDetector()

        assertNull(detector.extractQuestion("有没有同学知道什么是", 6_000))
        val result = detector.extractQuestion("有人知道吗", 25_000)

        assertNotNull("question tail 19s after the cue must still alert", result)
    }
}
