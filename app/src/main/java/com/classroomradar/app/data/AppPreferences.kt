package com.classroomradar.app.data

import android.content.Context
import androidx.core.content.edit
import com.classroomradar.app.detection.QuestionDetector

/** Small, local-only settings store. No account, analytics or network SDK is involved. */
class AppPreferences private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var vibrationEnabled: Boolean
        get() = preferences.getBoolean(KEY_VIBRATION, true)
        set(value) = preferences.edit { putBoolean(KEY_VIBRATION, value) }

    var soundEnabled: Boolean
        // 默认关闭：提示音只在连接耳机后才有意义，安静课堂上外放会很突兀。
        get() = preferences.getBoolean(KEY_SOUND, false)
        set(value) = preferences.edit { putBoolean(KEY_SOUND, value) }

    var saveAudioEnabled: Boolean
        get() = preferences.getBoolean(KEY_SAVE_AUDIO, false)
        set(value) = preferences.edit { putBoolean(KEY_SAVE_AUDIO, value) }

    var usageNoticeAccepted: Boolean
        get() = preferences.getBoolean(KEY_USAGE_NOTICE, false)
        set(value) = preferences.edit { putBoolean(KEY_USAGE_NOTICE, value) }

    var vibrationPattern: String
        get() = preferences.getString(KEY_VIBRATION_PATTERN, VIBRATION_STANDARD) ?: VIBRATION_STANDARD
        set(value) = preferences.edit { putString(KEY_VIBRATION_PATTERN, value) }

    fun customTriggerWords(): List<String> =
        preferences.getStringSet(KEY_CUSTOM_TRIGGERS, emptySet()).orEmpty().sorted()

    fun addCustomTrigger(word: String) {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return
        preferences.edit {
            putStringSet(KEY_CUSTOM_TRIGGERS, preferences.getStringSet(KEY_CUSTOM_TRIGGERS, emptySet()).orEmpty() + cleaned)
        }
    }

    fun removeCustomTrigger(word: String) {
        preferences.edit {
            putStringSet(KEY_CUSTOM_TRIGGERS, preferences.getStringSet(KEY_CUSTOM_TRIGGERS, emptySet()).orEmpty() - word)
        }
    }

    fun isBuiltInTriggerEnabled(word: String): Boolean =
        word !in preferences.getStringSet(KEY_DISABLED_BUILT_INS, emptySet()).orEmpty()

    fun setBuiltInTriggerEnabled(word: String, enabled: Boolean) {
        val disabled = preferences.getStringSet(KEY_DISABLED_BUILT_INS, emptySet()).orEmpty().toMutableSet()
        if (enabled) disabled.remove(word) else disabled.add(word)
        preferences.edit { putStringSet(KEY_DISABLED_BUILT_INS, disabled) }
    }

    fun activeTriggerWords(): Set<String> = buildSet {
        addAll(QuestionDetector.DEFAULT_TRIGGER_WORDS.filter(::isBuiltInTriggerEnabled))
        addAll(customTriggerWords())
    }

    companion object {
        private const val FILE_NAME = "water_class_radar_settings"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_SAVE_AUDIO = "save_audio_enabled"
        private const val KEY_USAGE_NOTICE = "usage_notice_accepted"
        private const val KEY_VIBRATION_PATTERN = "vibration_pattern"
        private const val KEY_CUSTOM_TRIGGERS = "custom_trigger_words"
        private const val KEY_DISABLED_BUILT_INS = "disabled_built_in_triggers"

        const val VIBRATION_STANDARD = "standard"
        const val VIBRATION_URGENT = "urgent"
        const val VIBRATION_GENTLE = "gentle"

        @Volatile private var instance: AppPreferences? = null

        fun get(context: Context): AppPreferences = instance ?: synchronized(this) {
            instance ?: AppPreferences(context).also { instance = it }
        }
    }
}
