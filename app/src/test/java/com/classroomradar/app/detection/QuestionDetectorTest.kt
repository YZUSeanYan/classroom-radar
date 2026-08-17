package com.classroomradar.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

private fun QuestionDetector.promptWarnings(
    text: String,
    timestampMillis: Long,
): List<QuestionDetector.DetectionEvent.PromptMatched> =
    onFinalTranscript(text, timestampMillis)
        .filterIsInstance<QuestionDetector.DetectionEvent.PromptMatched>()

class QuestionDetectorTest {
    @Test
    fun `trigger returns the preceding question as backtrace`() {
        val detector = QuestionDetector()
        detector.onFinalTranscript("什么是麦克斯韦方程组？", 1_000)

        val result = detector.extractQuestion("请一位同学回答一下。", 20_000)

        assertNotNull(result)
        assertEquals("回答一下", result?.matchedTrigger)
        assertEquals("什么是麦克斯韦方程组？", result?.question)
    }

    @Test
    fun `same question is debounced for fifteen seconds`() {
        val detector = QuestionDetector()
        detector.onFinalTranscript("谁来回答", 20_000)

        assertNull(detector.extractQuestion("谁来回答", 25_000))
    }

    @Test
    fun `custom trigger is carried to the following question`() {
        val detector = QuestionDetector(setOf("这位同学"))

        assertNull(detector.extractQuestion("这位同学，请说一下你的想法。", 20_000))
        val result = detector.extractQuestion("什么是麦克斯韦方程组？", 21_000)

        assertNotNull(result)
        assertEquals("这位同学", result?.matchedTrigger)
        assertEquals("什么是麦克斯韦方程组？", result?.question)
    }

    @Test
    fun `trigger extracts the question from the same transcript line`() {
        val detector = QuestionDetector()

        val result = detector.extractQuestion("请一位同学回答一下，什么是麦克斯韦方程组？", 20_000)

        assertNotNull(result)
        assertEquals("什么是麦克斯韦方程组？", result?.question)
    }

    @Test
    fun `trigger waits for a question in the following transcript line`() {
        val detector = QuestionDetector()

        assertNull(detector.extractQuestion("请一位同学回答一下。", 20_000))
        val result = detector.extractQuestion("什么是麦克斯韦方程组？", 22_000)

        assertNotNull(result)
        assertEquals("回答一下", result?.matchedTrigger)
        assertEquals("什么是麦克斯韦方程组？", result?.question)
    }

    @Test
    fun `question starter is extracted even when recognition omits punctuation`() {
        val detector = QuestionDetector()

        val result = detector.extractQuestion("请一位同学回答一下什么是麦克斯韦方程组", 20_000)

        assertNotNull(result)
        assertEquals("什么是麦克斯韦方程组", result?.question)
    }

    @Test
    fun `cue fires a prompt warning immediately without waiting for a question`() {
        val detector = QuestionDetector()

        val events = detector.onFinalTranscript("请一位同学回答一下。", 1_000)

        assertEquals(1, events.filterIsInstance<QuestionDetector.DetectionEvent.PromptMatched>().size)
        assertTrue(events.filterIsInstance<QuestionDetector.DetectionEvent.QuestionExtracted>().isEmpty())
        val prompt = events.first() as QuestionDetector.DetectionEvent.PromptMatched
        assertEquals("回答一下", prompt.trigger)
        assertEquals(1_000L, prompt.timestampMillis)
    }

    @Test
    fun `prompt warning is followed by the question event on the next line`() {
        val detector = QuestionDetector()
        detector.onFinalTranscript("请一位同学回答一下。", 1_000)

        val events = detector.onFinalTranscript("什么是麦克斯韦方程组？", 3_000)

        assertEquals(1, events.filterIsInstance<QuestionDetector.DetectionEvent.QuestionExtracted>().size)
    }

    @Test
    fun `inline question emits both prompt warning and question`() {
        val detector = QuestionDetector()

        val events = detector.onFinalTranscript("请一位同学回答一下，什么是麦克斯韦方程组？", 20_000)

        assertEquals(1, detector.promptWarningCount(events))
        assertEquals(1, events.filterIsInstance<QuestionDetector.DetectionEvent.QuestionExtracted>().size)
    }

    @Test
    fun `same cue repeated within ten seconds warns only once`() {
        val detector = QuestionDetector()

        assertEquals(1, detector.promptWarnings("请一位同学回答一下", 1_000).size)
        assertEquals(0, detector.promptWarnings("请一位同学回答一下", 5_000).size)
        assertEquals(1, detector.promptWarnings("请一位同学回答一下", 20_000).size)
    }

    @Test
    fun `a different cue warns again immediately`() {
        val detector = QuestionDetector()

        assertEquals(1, detector.promptWarnings("请一位同学回答一下", 1_000).size)
        assertEquals(1, detector.promptWarnings("有没有同学知道", 2_000).size)
    }

    @Test
    fun `expanded built-in trigger list exceeds twenty entries`() {
        assertTrue(QuestionDetector.DEFAULT_TRIGGER_WORDS.size >= 20)
    }

    private fun QuestionDetector.promptWarningCount(
        events: List<QuestionDetector.DetectionEvent>,
    ): Int = events.filterIsInstance<QuestionDetector.DetectionEvent.PromptMatched>().size
}
