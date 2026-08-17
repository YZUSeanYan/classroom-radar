package com.classroomradar.app.data

import android.content.Context
import java.io.File

class ClassroomRepository private constructor(
    private val dao: ClassroomDao,
    private val filesDir: File,
) {

    fun createSession(startedAtMillis: Long): Long =
        dao.insertSession(
            SessionEntity(startedAtMillis = startedAtMillis, title = defaultTitle(startedAtMillis)),
        )

    fun renameSession(sessionId: Long, title: String) {
        val cleaned = title.trim()
        if (cleaned.isNotEmpty()) dao.renameSession(sessionId, cleaned)
    }

    fun appendTranscript(sessionId: Long, timestampMillis: Long, text: String) {
        dao.insertTranscript(
            TranscriptLineEntity(
                sessionId = sessionId,
                timestampMillis = timestampMillis,
                text = text,
            ),
        )
    }

    fun appendQuestion(
        sessionId: Long,
        timestampMillis: Long,
        matchedTrigger: String?,
        question: String,
        sourceLine: String,
    ) {
        dao.insertQuestion(
            QuestionEventEntity(
                sessionId = sessionId,
                timestampMillis = timestampMillis,
                matchedTrigger = matchedTrigger,
                question = question,
                sourceLine = sourceLine,
            ),
        )
    }

    fun finishSession(sessionId: Long, endedAtMillis: Long) = dao.finishSession(sessionId, endedAtMillis)

    fun setAudioPath(sessionId: Long, relativePath: String?) = dao.updateAudioPath(sessionId, relativePath)

    fun observeSessionSummaries() = dao.observeSessionSummaries()
    fun observeSession(sessionId: Long) = dao.observeSession(sessionId)
    fun observeTranscript(sessionId: Long) = dao.observeTranscript(sessionId)
    fun observeQuestions(sessionId: Long) = dao.observeQuestions(sessionId)

    fun loadExport(sessionId: Long): ExportRecord? {
        val session = dao.getSession(sessionId) ?: return null
        return ExportRecord(session, dao.getTranscript(sessionId), dao.getQuestions(sessionId))
    }

    fun deleteSession(sessionId: Long) {
        dao.removeSession(sessionId)?.let(::deletePrivateAudio)
    }

    fun clearAllSessions() {
        dao.removeAllSessions().forEach(::deletePrivateAudio)
    }

    private fun deletePrivateAudio(relativePath: String) {
        File(filesDir, relativePath).takeIf { it.isFile }?.delete()
    }

    private fun defaultTitle(startedAtMillis: Long): String =
        "课堂记录 " + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
            .format(java.util.Date(startedAtMillis))

    data class ExportRecord(
        val session: SessionEntity,
        val transcript: List<TranscriptLineEntity>,
        val questions: List<QuestionEventEntity>,
    )

    companion object {
        @Volatile private var instance: ClassroomRepository? = null

        fun get(context: Context): ClassroomRepository = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { appContext ->
                ClassroomRepository(
                    dao = ClassroomDatabase.get(appContext).classroomDao(),
                    filesDir = appContext.filesDir,
                ).also { instance = it }
            }
        }
    }
}
