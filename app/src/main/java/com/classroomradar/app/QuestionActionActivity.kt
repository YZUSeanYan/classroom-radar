package com.classroomradar.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Handles notification actions without requiring the main screen to be open. */
class QuestionActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        val question = intent.getStringExtra(EXTRA_QUESTION).orEmpty()
        when (intent.action) {
            ACTION_COPY -> {
                copyQuestion(question)
                Toast.makeText(this, "问题已复制", Toast.LENGTH_SHORT).show()
            }
            ACTION_OPEN_DOUBAO -> openSolver(DOUBAO_PACKAGE, "豆包", question)
            ACTION_OPEN_DEEPSEEK -> openSolver(DEEPSEEK_PACKAGE, "DeepSeek", question)
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun openSolver(packageName: String, displayName: String, question: String) {
        copyQuestion(question)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName)
                .takeIf { it.resolveActivity(packageManager) != null }
        if (launchIntent == null) {
            Toast.makeText(this, "未安装$displayName，问题已复制", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(this, "无法打开$displayName，问题已复制", Toast.LENGTH_LONG).show() }
    }

    private fun copyQuestion(question: String) {
        if (question.isBlank()) return
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("课堂问题", question))
    }

    companion object {
        const val EXTRA_QUESTION = "question"
        const val ACTION_COPY = "com.classroomradar.app.action.COPY_QUESTION"
        const val ACTION_OPEN_DOUBAO = "com.classroomradar.app.action.OPEN_DOUBAO"
        const val ACTION_OPEN_DEEPSEEK = "com.classroomradar.app.action.OPEN_DEEPSEEK"
        const val DOUBAO_PACKAGE = "com.larus.nova"
        const val DEEPSEEK_PACKAGE = "com.deepseek.chat"
    }
}
