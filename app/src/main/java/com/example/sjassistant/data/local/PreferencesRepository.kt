package com.example.sjassistant.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.sjassistant.core.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sjDataStore: DataStore<Preferences> by preferencesDataStore(name = "sj_prefs")

data class UserPreferences(
    val uiLanguage: AppLanguage = AppLanguage.MARATHI,
    val darkMode: Boolean = true,
    val autoSpeakReplies: Boolean = false,
    val contextCompressionEnabled: Boolean = true,
    val encryptNotesByDefault: Boolean = true,
    val loadedGgufName: String? = null,
    val loadedGgufSummary: String? = null
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val CONTEXT_COMPRESSION = booleanPreferencesKey("context_compression")
        val ENCRYPT_NOTES = booleanPreferencesKey("encrypt_notes")
        val GGUF_NAME = stringPreferencesKey("gguf_name")
        val GGUF_SUMMARY = stringPreferencesKey("gguf_summary")
    }

    val preferences: Flow<UserPreferences> = context.sjDataStore.data.map { prefs ->
        UserPreferences(
            uiLanguage = AppLanguage.fromCode(prefs[Keys.UI_LANGUAGE]),
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            autoSpeakReplies = prefs[Keys.AUTO_SPEAK] ?: false,
            contextCompressionEnabled = prefs[Keys.CONTEXT_COMPRESSION] ?: true,
            encryptNotesByDefault = prefs[Keys.ENCRYPT_NOTES] ?: true,
            loadedGgufName = prefs[Keys.GGUF_NAME],
            loadedGgufSummary = prefs[Keys.GGUF_SUMMARY]
        )
    }

    suspend fun setUiLanguage(language: AppLanguage) {
        context.sjDataStore.edit { it[Keys.UI_LANGUAGE] = language.code }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.sjDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.sjDataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setContextCompression(enabled: Boolean) {
        context.sjDataStore.edit { it[Keys.CONTEXT_COMPRESSION] = enabled }
    }

    suspend fun setEncryptNotesByDefault(enabled: Boolean) {
        context.sjDataStore.edit { it[Keys.ENCRYPT_NOTES] = enabled }
    }

    suspend fun setLoadedGguf(name: String?, summary: String?) {
        context.sjDataStore.edit { prefs ->
            if (name == null) {
                prefs.remove(Keys.GGUF_NAME)
                prefs.remove(Keys.GGUF_SUMMARY)
            } else {
                prefs[Keys.GGUF_NAME] = name
                prefs[Keys.GGUF_SUMMARY] = summary.orEmpty()
            }
        }
    }
}
