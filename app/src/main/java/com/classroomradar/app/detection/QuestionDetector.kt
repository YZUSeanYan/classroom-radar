package com.classroomradar.app.detection

/**
 * Deterministic, fully local classroom-question detector.
 *
 * It deliberately separates “was someone prompted?” from “what is the actual
 * question?” so copied text does not include a long lecture lead-in or roll-call words.
 *
 * The pipeline is two-stage, matching the product site's promise: a matched cue fires an
 * immediate [DetectionEvent.PromptMatched] without waiting for the full question; once a
 * complete question is extracted, [DetectionEvent.QuestionExtracted] follows.
 */
class QuestionDetector(
    private val triggerWords: Set<String> = DEFAULT_TRIGGER_WORDS,
    private val debounceMillis: Long = 15_000L,
) {
    private val recentLines = ArrayDeque<String>()
    private var lastAlertAt = Long.MIN_VALUE
    private var lastAlertQuestion = ""
    private var pendingPrompt: PendingPrompt? = null
    private var lastPromptAt = Long.MIN_VALUE
    private var lastPromptTrigger = ""

    fun onFinalTranscript(text: String, timestampMillis: Long): List<DetectionEvent> {
        val line = text.trim()
        if (line.isEmpty()) return emptyList()

        recentLines.addLast(line)
        while (recentLines.size > MAX_CONTEXT_LINES) recentLines.removeFirst()

        val matchedTrigger = triggerWords
            .asSequence()
            .filter { line.contains(it, ignoreCase = true) }
            .maxWithOrNull(
                compareBy<String> { line.lastIndexOf(it, ignoreCase = true) }
                    .thenBy(String::length),
            )
        val events = mutableListOf<DetectionEvent>()
        if (matchedTrigger != null) {
            val normalizedTrigger = matchedTrigger.lowercase()
            // The warning is the fast stage, so its debounce only suppresses the exact
            // same cue repeated back-to-back; a different cue always warns immediately.
            if (
                lastPromptAt == Long.MIN_VALUE ||
                timestampMillis - lastPromptAt >= PROMPT_DEBOUNCE_MILLIS ||
                normalizedTrigger != lastPromptTrigger
            ) {
                lastPromptAt = timestampMillis
                lastPromptTrigger = normalizedTrigger
                events += DetectionEvent.PromptMatched(timestampMillis, matchedTrigger)
            }
        }
        val waitingPrompt = pendingPrompt?.takeIf { timestampMillis - it.timestampMillis <= PENDING_TRIGGER_MILLIS }
        pendingPrompt = null

        val inlineQuestion = matchedTrigger?.let { trigger ->
            extractQuestionAfterTrigger(line, trigger)
                ?: extractQuestionBeforeTrigger(line, trigger)
                ?: extractQuestionFromTriggerOnwards(line, trigger)
        }
        val extraction = when {
            inlineQuestion != null -> QuestionExtraction(inlineQuestion, line)
            matchedTrigger != null -> findQuestionBacktrace(skipCurrentLine = true)
            waitingPrompt != null -> {
                val prompt = waitingPrompt.append(line)
                extractQuestionFromPrompt(prompt)?.let { QuestionExtraction(it, prompt.text) }
            }
            else -> extractQuestionSpan(line)?.let { QuestionExtraction(it, line) }
        }
        val question = extraction?.question

        if (question == null || !shouldAlert(question, matchedTrigger != null || waitingPrompt != null)) {
            pendingPrompt = when {
                matchedTrigger != null -> PendingPrompt(matchedTrigger, timestampMillis, line)
                waitingPrompt != null -> waitingPrompt.append(line)
                else -> null
            }
            return events
        }

        val normalizedQuestion = normalize(question)
        if (
            lastAlertAt != Long.MIN_VALUE &&
            timestampMillis - lastAlertAt < debounceMillis &&
            normalizedQuestion == lastAlertQuestion
        ) return events

        lastAlertAt = timestampMillis
        lastAlertQuestion = normalizedQuestion
        events += DetectionEvent.QuestionExtracted(
            DetectionResult(
                timestampMillis = timestampMillis,
                matchedTrigger = matchedTrigger ?: waitingPrompt?.word,
                question = question,
                // Highlight the sentence that was actually selected. Previously a trigger on a
                // later line could make the timeline highlight the cue instead of the question.
                sourceLine = extraction.sourceLine,
            ),
        )
        return events
    }

    /**
     * A teacher often says the prompt and the question across two ASR chunks. Keep a
     * short, bounded prompt buffer so "请同学回答一下" + "麦克斯韦方程组是" +
     * "什么" becomes one useful question instead of three unrelated fragments.
     */
    private fun extractQuestionFromPrompt(prompt: PendingPrompt): String? =
        (extractQuestionAfterTrigger(prompt.text, prompt.word) ?: extractQuestionSpan(prompt.text))
            ?.replace(Regex("(?<=[\\p{IsHan}])\\s+(?=[\\p{IsHan}])"), "")

    private fun findQuestionBacktrace(skipCurrentLine: Boolean): QuestionExtraction? {
        val candidates = recentLines.toList().let { lines ->
            if (skipCurrentLine) lines.dropLast(1) else lines
        }
        // The design brief calls for the *nearest complete question* before a teacher cue.
        // Work backwards, but discard fragments that only contain an interrogative word.
        return candidates.asReversed().firstNotNullOfOrNull { source ->
            extractQuestionSpan(source)
                ?.takeIf { shouldAlert(it, hasClassroomPrompt = true) }
                ?.let { QuestionExtraction(it, source) }
        }
    }

    private fun extractQuestionAfterTrigger(text: String, trigger: String): String? {
        val remainder = text.substringAfter(trigger, missingDelimiterValue = "")
        return extractQuestionSpan(trimInstructionPrefix(remainder))
    }

    private fun extractQuestionBeforeTrigger(text: String, trigger: String): String? {
        var prefix = text.substringBeforeLast(trigger, missingDelimiterValue = "").trim()
        while (true) {
            val residue = TRIGGER_PREFIX_RESIDUES.firstOrNull { prefix.endsWith(it) } ?: break
            prefix = prefix.removeSuffix(residue).trim()
        }
        return extractQuestionSpan(prefix)
    }

    /**
     * English cue words double as question openers ("ok, what is the capital of France"
     * matches trigger "what is"). When neither side of the trigger yields a question,
     * re-extract starting at the trigger itself.
     */
    private fun extractQuestionFromTriggerOnwards(text: String, trigger: String): String? {
        val triggerIndex = text.lastIndexOf(trigger, ignoreCase = true)
        if (triggerIndex < 0) return null
        return extractQuestionSpan(trimLectureLeadIn(text.substring(triggerIndex)))
    }

    private fun extractQuestionSpan(text: String): String? {
        val compact = trimQuestionCandidate(trimLectureLeadIn(text))
        if (compact.isEmpty()) return null

        val throughQuestion = compact.takeThroughQuestionPunctuation()
        val markerStart = questionStartIndex(throughQuestion)
        val beforeQuestionEnding = throughQuestion.dropLastQuestionPunctuation()
        val sentenceStart = beforeQuestionEnding.lastIndexOfAny(SENTENCE_BOUNDARIES) + 1
        val start = markerStart ?: sentenceStart
        val candidate = trimQuestionCandidate(throughQuestion.substring(start))
        return candidate.takeIf { it.length >= MINIMUM_QUESTION_LENGTH && looksLikeQuestion(it) }
    }

    /**
     * A transcript may contain a rhetorical sentence such as “为什么会这样，我们后面再讲”。
     * Do not send a classroom alert merely because it contains an interrogative word. A
     * prompt/callout can lower the threshold, but an isolated line must still carry a real
     * question structure before it is allowed to alert on its own.
     *
     * On-device ASR finalizes lines without “？”, so real spoken questions usually arrive
     * unpunctuated; both the Chinese and the English path therefore have a punctuation-free
     * fallback with its own guards.
     */
    private fun shouldAlert(question: String, hasClassroomPrompt: Boolean): Boolean {
        val compact = trimQuestionCandidate(question)
        if (isInstructionOnly(compact) || substantiveCharacterCount(compact) < MIN_SUBSTANTIVE_CHARACTERS) return false
        val hasPunctuation = compact.any { it == '？' || it == '?' }
        val hasOpening = QUESTION_STARTS.any { compact.startsWith(it, ignoreCase = true) } || hasEnglishOpening(compact)
        val hasEnding = QUESTION_ENDINGS.any { compact.endsWith(it) }
        val hasConversationalPattern = CONVERSATIONAL_QUESTION_PATTERNS.any { compact.contains(it, ignoreCase = true) }

        if (!looksLikeQuestion(compact)) return false
        if (hasClassroomPrompt) {
            // A teacher has explicitly called for an answer; any remaining interrogative
            // signal is enough, so "提一个问题" + "这个问题怎么解决" still combines.
            if (hasPunctuation || hasOpening || hasEnding || hasConversationalPattern) return true
            return INTERROGATIVE_WORDS.any { compact.contains(it) } || hasEnglishOpening(compact)
        }
        if (hasPunctuation) return hasOpening || hasEnding || hasConversationalPattern
        // English: a chunk that opens on a wh-/auxiliary word and runs for a few words is
        // overwhelmingly a direct question ("what is the difference between tcp and udp").
        if (hasEnglishOpening(compact) && englishWordCount(compact) >= MIN_ENGLISH_QUESTION_WORDS) return true
        if (ENGLISH_CONVERSATIONAL_PATTERNS.any { compact.contains(it, ignoreCase = true) }) return true
        // Chinese: a chunk opening on a question marker is a direct question even when it
        // ends on the answer term ("什么是 g m"). Rhetorical continuations ("为什么会这样
        // 我们后面再讲") are surfaced too — for a review tool they are useful, and the user
        // has repeatedly asked for higher recall.
        if (hasOpening && substantiveCharacterCount(compact) >= MIN_PUNCTUATIONLESS_CHARACTERS) return true
        // Otherwise only pass when the chunk closes on the interrogative clue
        // ("有人知道吗", "他问的什么问题"), which keeps lecture ticks like "这个公式对吧" out.
        val hasInterrogativeWord = INTERROGATIVE_WORDS.any { compact.contains(it) }
        val closesOnQuestion = PUNCTUATIONLESS_CLOSERS.any { compact.endsWith(it) } || hasConversationalPattern
        return hasInterrogativeWord &&
            closesOnQuestion &&
            substantiveCharacterCount(compact) >= MIN_PUNCTUATIONLESS_CHARACTERS
    }

    private fun looksLikeQuestion(text: String): Boolean {
        val compact = trimQuestionCandidate(text)
        if (compact.any { it == '？' || it == '?' }) return true
        return QUESTION_STARTS.any { compact.startsWith(it, ignoreCase = true) } ||
            hasEnglishOpening(compact) ||
            QUESTION_ENDINGS.any { compact.endsWith(it) } ||
            CONVERSATIONAL_QUESTION_PATTERNS.any { compact.contains(it, ignoreCase = true) } ||
            INTERROGATIVE_WORDS.any { compact.contains(it) }
    }

    private fun questionStartIndex(text: String): Int? = QUESTION_STARTS
        .map { marker -> text.indexOf(marker, ignoreCase = true) }
        .filter { it >= 0 }
        .minOrNull()

    /** English openers are matched on word boundaries so "however"/"whole" stay silent. */
    private fun hasEnglishOpening(text: String): Boolean {
        val compact = trimQuestionCandidate(text)
        return ENGLISH_QUESTION_OPENERS.any { compact.startsWithEnglishWord(it) }
    }

    private fun String.startsWithEnglishWord(word: String): Boolean {
        if (!startsWith(word, ignoreCase = true)) return false
        val next = getOrNull(word.length) ?: return true
        return !next.isLetterOrDigit()
    }

    private fun englishWordCount(text: String): Int =
        text.split(Regex("\\s+")).count { token -> token.any { ch -> ch in 'a'..'z' || ch in 'A'..'Z' } }

    private fun trimInstructionPrefix(text: String): String {
        var remaining = trimWrapper(text)
        while (true) {
            val instruction = INSTRUCTION_PREFIXES.firstOrNull { remaining.startsWith(it, ignoreCase = true) } ?: break
            remaining = trimWrapper(remaining.substring(instruction.length))
        }
        return remaining
    }

    private fun trimLectureLeadIn(text: String): String {
        var remaining = text.trim()
        while (true) {
            val leadIn = LECTURE_LEAD_INS.firstOrNull { remaining.startsWith(it, ignoreCase = true) } ?: break
            remaining = trimWrapper(remaining.substring(leadIn.length))
        }
        return remaining
    }

    private fun String.takeThroughQuestionPunctuation(): String {
        val ending = indexOfFirst { it == '？' || it == '?' }
        return if (ending >= 0) substring(0, ending + 1) else this
    }

    private fun String.dropLastQuestionPunctuation(): String =
        if (lastOrNull() == '？' || lastOrNull() == '?') dropLast(1) else this

    private fun trimWrapper(text: String): String = text.trim()
        .trimStart(*WRAPPER_PUNCTUATION)
        .trimEnd(*NON_QUESTION_TRAILING_PUNCTUATION)

    private fun trimQuestionCandidate(text: String): String = text.trim()
        .trimStart(*WRAPPER_PUNCTUATION)
        .trimEnd(*NON_QUESTION_TRAILING_PUNCTUATION)

    private fun normalize(text: String): String = text
        .lowercase()
        .filterNot { it.isWhitespace() || it in WRAPPER_PUNCTUATION }

    private fun substantiveCharacterCount(text: String): Int = text.count { it.isLetterOrDigit() }

    private fun isInstructionOnly(text: String): Boolean {
        val compact = text
            .lowercase()
            .filter { it.isLetterOrDigit() || it in '\u4e00'..'\u9fff' }
        return INSTRUCTION_ONLY_PHRASES.any { phrase ->
            compact == phrase || compact.removePrefix("请") == phrase
        }
    }

    private data class PendingPrompt(
        val word: String,
        val timestampMillis: Long,
        val text: String,
    ) {
        fun append(nextLine: String): PendingPrompt = copy(
            text = "$text $nextLine".replace(Regex("\\s+"), " ").trim().keepPromptAndTail(),
        )
    }

    private data class QuestionExtraction(
        val question: String,
        val sourceLine: String,
    )

    data class DetectionResult(
        val timestampMillis: Long,
        val matchedTrigger: String?,
        val question: String,
        val sourceLine: String,
    )

    /** Detection pipeline output. A prompt warning never waits for the full question. */
    sealed interface DetectionEvent {
        val timestampMillis: Long

        /** Stage 1 (yellow bar): a cue word was just heard; a question may follow. */
        data class PromptMatched(
            override val timestampMillis: Long,
            val trigger: String,
        ) : DetectionEvent

        /** Stage 2 (red bar): the complete question was extracted. */
        data class QuestionExtracted(
            val result: DetectionResult,
        ) : DetectionEvent {
            override val timestampMillis: Long get() = result.timestampMillis
        }
    }

    companion object {
        private const val MAX_CONTEXT_LINES = 30
        // Classroom cadence: the teacher cues, pauses, then asks. 8s was too tight and
        // dropped real questions that arrived 10-15s after the cue (seen on device).
        private const val PENDING_TRIGGER_MILLIS = 20_000L
        private const val PROMPT_DEBOUNCE_MILLIS = 10_000L
        private const val MAX_PENDING_TEXT_LENGTH = 260
        private const val MINIMUM_QUESTION_LENGTH = 3
        private const val MIN_SUBSTANTIVE_CHARACTERS = 4
        private const val MIN_PUNCTUATIONLESS_CHARACTERS = 5
        private const val MIN_ENGLISH_QUESTION_WORDS = 3
        private val WRAPPER_PUNCTUATION = charArrayOf('，', ',', '。', '.', '：', ':', '；', ';', '？', '?', '！', '!')
        private val NON_QUESTION_TRAILING_PUNCTUATION = charArrayOf('，', ',', '。', '.', '：', ':', '；', ';', '！', '!')
        private val SENTENCE_BOUNDARIES = charArrayOf('，', ',', '。', '.', '：', ':', '；', ';', '？', '?', '！', '!')
        val DEFAULT_TRIGGER_WORDS = setOf(
            "回答一下", "请一位同学", "点一位同学", "哪位同学", "找一位同学", "找一位",
            "有没有同学", "谁来回答", "谁来说说", "谁来试试", "谁愿意", "谁愿意试试",
            "点一个", "点个名", "提问一个", "随机点", "抽一位", "我叫一个", "举手回答",
            "提一个问题", "问一个问题", "提个问题", "问个问题", "提个问", "提问一下", "谁能告诉",
            "问大家", "一个问题", "问你们",
            "what is", "who can", "what about", "how about", "any volunteers",
            "any question", "any questions", "anyone know", "who knows", "any idea",
            "can anyone", "do you know", "who wants", "let me ask", "answer this",
            "raise your hand", "think about it", "tell me", "what do you think",
        )

        private val QUESTION_ENDINGS = setOf("吗", "呢", "什么", "为什么", "怎么", "如何", "哪些", "哪个", "哪一个", "是否", "能不能", "会不会", "多少", "啥", "对不对", "是不是", "好不好")
        /** Chinese-only: English markers are matched on word boundaries via [hasEnglishOpening]. */
        private val QUESTION_STARTS = setOf(
            "什么是", "什么叫", "为什么", "怎么", "如何", "哪些", "哪个", "哪一个", "是否", "能不能", "会不会", "请问",
            "有谁知道", "谁知道", "谁能", "大家说说", "谁来说说", "解释一下", "谈谈", "比较一下",
            "有没有人知道", "有人知道", "有没有人",
        )
        /** Wh-words plus auxiliaries; any of them at the very start of a chunk marks a question. */
        private val ENGLISH_QUESTION_OPENERS = listOf(
            "what", "why", "how", "which", "who", "whom", "whose", "when", "where",
            "is", "are", "was", "were", "do", "does", "did", "can", "could",
            "would", "should", "shall", "may", "have", "has",
        )
        private val CONVERSATIONAL_QUESTION_PATTERNS = setOf(
            "你认为", "你觉得", "怎么看", "怎样看", "听懂了吗", "明白了吗", "对不对", "是不是", "好不好",
            "有没有人知道", "有人知道",
        )
        private val ENGLISH_CONVERSATIONAL_PATTERNS = setOf(
            "do you think", "any idea", "any ideas", "make sense", "is that right", "is that clear",
        )
        /** Words that prove a punctuation-free Chinese chunk really is interrogative. */
        private val INTERROGATIVE_WORDS = setOf(
            "吗", "呢", "什么", "为什么", "怎么", "怎样", "如何", "哪个", "哪些", "哪里", "哪儿",
            "谁", "是否", "能不能", "会不会", "多少", "啥", "对不对", "是不是", "好不好",
        )
        /** A punctuation-free line only alerts when it closes on one of these ("他问的什么问题"). */
        private val PUNCTUATIONLESS_CLOSERS = QUESTION_ENDINGS + "问题"
        private val INSTRUCTION_PREFIXES = setOf(
            "请一位同学", "同学", "你来", "你", "来", "请", "回答一下", "回答", "说一个", "说说", "讲一个", "讲讲", "一个", "吗",
            "就是", "然后", "吧", "嗯", "呃", "啊",
            "tell me ", "tell us ", "please ", "can you ", "could you ", "who can ", "who wants to ",
        )
        private val TRIGGER_PREFIX_RESIDUES = setOf("谁来", "请", "同学")
        private val LECTURE_LEAD_INS = setOf(
            "那么", "接下来", "我们想一想", "大家想一想", "我们来看看", "下面", "第一个问题是", "问题是", "我想问一下",
            "so ", "now ", "ok ", "okay ", "well ", "um ", "uh ", "and ", "please ",
        )
        private val INSTRUCTION_ONLY_PHRASES = setOf(
            "回答一下", "回答", "说说", "讲讲", "谁来回答", "有没有同学",
            "请一位同学", "这位同学", "同学", "你来", "来", "请",
        )

        private fun String.keepPromptAndTail(): String {
            if (length <= MAX_PENDING_TEXT_LENGTH) return this
            // Preserve the opening classroom cue and the most recent ASR tail. Keeping
            // only the prefix used to lose a question that arrived after a long prompt.
            val promptHead = take(72)
            val questionTail = takeLast(MAX_PENDING_TEXT_LENGTH - promptHead.length - 1)
            return "$promptHead $questionTail"
        }
    }
}
