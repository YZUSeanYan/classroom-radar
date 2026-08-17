package com.classroomradar.app.detection

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

/**
 * Replay of the real-device transcript from 2026-08-14 (second user screenshot): sherpa
 * finalizes lines without any punctuation and uses cue phrasings the old trigger list did
 * not know, so neither the yellow prompt nor the question card appeared.
 */
class QuestionDetectorRealClassReplayTest {
    @Test
    fun `captures cue variant and unpunctuated questions from real classroom audio`() {
        val detector = QuestionDetector()

        val cueEvents = detector.onFinalTranscript("我们来提一个问题", 1_000)
        assertTrue(
            "“来提一个问题” must fire the stage-1 warning",
            cueEvents.any { it is QuestionDetector.DetectionEvent.PromptMatched },
        )

        val question = detector.extractQuestion("就是有没有人知道什么是 com", 6_000)
        assertNotNull("cue + unpunctuated question 5s later must combine", question)
        assertTrue(question!!.question.contains("什么是"))

        val followUp = detector.extractQuestion("有人知道吗", 10_000)
        assertNotNull("“有人知道吗” without punctuation must still alert", followUp)

        val echo = detector.extractQuestion("他问的什么问题", 15_000)
        assertNotNull("a chunk closing on “问题” with an interrogative word must alert", echo)
    }

    @Test
    fun `captures unpunctuated chinese question without any cue`() {
        val detector = QuestionDetector()

        val result = detector.extractQuestion("有没有人知道什么是丰字", 2_000)

        assertNotNull(result)
        assertTrue(result!!.question.contains("有没有人知道"))
    }

    @Test
    fun `captures english question after an english cue`() {
        val detector = QuestionDetector()

        val cueEvents = detector.onFinalTranscript("ok class any question", 1_000)
        assertTrue(cueEvents.any { it is QuestionDetector.DetectionEvent.PromptMatched })

        val result = detector.extractQuestion("what is the difference between tcp and udp", 6_000)
        assertNotNull("english cue + unpunctuated wh-question must combine", result)
        assertTrue(result!!.question.lowercase().startsWith("what"))
    }

    @Test
    fun `captures standalone english questions without punctuation`() {
        assertNotNull(
            QuestionDetector().extractQuestion("what is the difference between tcp and udp", 2_000),
        )
        assertNotNull(
            QuestionDetector().extractQuestion("why does the sky look blue", 2_000),
        )
        assertNotNull(
            QuestionDetector().extractQuestion("can anyone tell me why the sky is blue", 2_000),
        )
    }

    @Test
    fun `captures question ending on the answer term after a casual cue`() {
        val detector = QuestionDetector()

        val cueEvents = detector.onFinalTranscript("嗯我先问大家一个问题吧", 1_000)
        assertTrue(
            "“问大家一个问题” must fire the stage-1 warning",
            cueEvents.any { it is QuestionDetector.DetectionEvent.PromptMatched },
        )
        // "大家知不知道" alone is not yet a question; the question lands on the next chunk.
        assertNull(detector.extractQuestion("嗯就是大家知不知道", 4_000))

        val result = detector.extractQuestion("什么是 g m", 8_000)
        assertNotNull("the question ending on the answer term must still combine", result)
        assertTrue(result!!.question.startsWith("什么是"))
    }

    @Test
    fun `captures opener-led chinese question without cue or punctuation`() {
        assertNotNull(QuestionDetector().extractQuestion("什么是 g m", 2_000))
    }

    @Test
    fun `does not alert on english declaratives or pure lecture ticks`() {
        // Declarative sentences that merely contain a wh-word mid-sentence stay silent.
        assertNull(
            QuestionDetector().extractQuestion("today we will learn how photosynthesis works in detail", 2_000),
        )
        // Word-boundary matching: "however"/"whole" must not count as question openers.
        assertNull(
            QuestionDetector().extractQuestion("however this method fails sometimes", 2_000),
        )
        assertNull(
            QuestionDetector().extractQuestion("the whole class please open your books", 2_000),
        )
        // Lecture ticks without an interrogative word stay silent.
        assertNull(
            QuestionDetector().extractQuestion("我们来看这个问题很重要", 2_000),
        )
        assertNull(
            QuestionDetector().extractQuestion("这个公式对吧大家都明白了", 2_000),
        )
    }
}
