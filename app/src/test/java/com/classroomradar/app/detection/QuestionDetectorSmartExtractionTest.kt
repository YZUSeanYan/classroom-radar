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

class QuestionDetectorSmartExtractionTest {
    @Test
    fun `combines prompt and a question split across several ASR chunks`() {
        val detector = QuestionDetector()

        assertNull(detector.extractQuestion("请一位同学回答一下麦克斯韦方程组是", 1_000))
        val result = detector.extractQuestion("什么", 2_000)

        assertNotNull(result)
        assertEquals("回答一下", result?.matchedTrigger)
        assertEquals("麦克斯韦方程组是什么", result?.question)
    }

    @Test
    fun `trims lecture lead-in and keeps only embedded question before trigger`() {
        val result = QuestionDetector().extractQuestion(
            "下面做个练习，电场强度的单位是什么？谁来回答一下",
            20_000,
        )

        assertNotNull(result)
        assertEquals("电场强度的单位是什么？", result?.question)
    }

    @Test
    fun `keeps an unpunctuated question before trigger`() {
        val result = QuestionDetector().extractQuestion(
            "有谁知道麦克斯韦方程组是什么谁来回答一下",
            20_000,
        )

        assertNotNull(result)
        assertEquals("有谁知道麦克斯韦方程组是什么", result?.question)
    }

    @Test
    fun `backtrace refines question rather than copying lecture lead-in`() {
        val detector = QuestionDetector()
        detector.onFinalTranscript("接下来我们想一想，为什么电场线不能相交？", 1_000)

        val result = detector.extractQuestion("请一位同学回答一下", 20_000)

        assertNotNull(result)
        assertEquals("为什么电场线不能相交？", result?.question)
    }

    @Test
    fun `different questions are not suppressed by debounce`() {
        val detector = QuestionDetector()
        assertNotNull(detector.extractQuestion("什么是电场？", 1_000))

        assertNotNull(detector.extractQuestion("什么是磁场？", 2_000))
    }

    @Test
    fun `bare trigger waits for actual question`() {
        val detector = QuestionDetector()
        assertNull(detector.extractQuestion("请一位同学回答一下", 1_000))

        val result = detector.extractQuestion("为什么电场线不能相交？", 2_000)
        assertNotNull(result)
        assertEquals("为什么电场线不能相交？", result?.question)
    }

    @Test
    fun `does not treat an instruction alone as the question`() {
        val detector = QuestionDetector()

        assertNull(detector.extractQuestion("请一位同学回答一下", 1_000))
        assertNull(detector.extractQuestion("这位同学说一下", 2_000))
    }

    @Test
    fun `backtrace keeps the actual question as source context`() {
        val detector = QuestionDetector()
        detector.onFinalTranscript("为什么电场线不能相交？", 1_000)

        val result = detector.extractQuestion("请一位同学回答一下", 20_000)

        assertNotNull(result)
        assertEquals("为什么电场线不能相交？", result?.sourceLine)
    }

    @Test
    fun `rhetorical opener fragment is surfaced for review`() {
        val detector = QuestionDetector()

        // Sensitivity policy (2026-08-14, user feedback): an opener-led chunk alerts even
        // with a declarative tail — for a review tool a rhetorical question is still worth
        // keeping, while pure lecture ticks without any interrogative word stay silent.
        assertNotNull(detector.extractQuestion("为什么会这样我们后面再讲", 1_000))
        assertNull(QuestionDetector().extractQuestion("这个公式对吧大家都明白了", 1_000))
    }
}
