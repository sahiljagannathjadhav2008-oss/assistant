package com.example.sjassistant.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sjassistant.ai.GgufHeaderParser
import com.example.sjassistant.ai.LocalAiEngine
import com.example.sjassistant.ai.MemoryOptimizer
import com.example.sjassistant.ai.MemorySnapshot
import com.example.sjassistant.ai.ModelCatalog
import com.example.sjassistant.ai.ModelSpec
import com.example.sjassistant.ai.OfflineVisionAnalyzer
import com.example.sjassistant.ai.PhotoAnalysisResult
import com.example.sjassistant.core.i18n.AppLanguage
import com.example.sjassistant.core.i18n.LanguageDetector
import com.example.sjassistant.data.local.AssistantRepository
import com.example.sjassistant.data.local.ChatMessageEntity
import com.example.sjassistant.data.local.DocumentEntity
import com.example.sjassistant.data.local.NoteModel
import com.example.sjassistant.data.local.PreferencesRepository
import com.example.sjassistant.data.local.ReminderEntity
import com.example.sjassistant.data.local.SjDatabase
import com.example.sjassistant.data.local.TodoEntity
import com.example.sjassistant.data.local.UserPreferences
import com.example.sjassistant.voice.OfflineVoiceManager
import com.example.sjassistant.voice.VoiceUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val database = SjDatabase.getInstance(context)
    private val repository = AssistantRepository(database)
    private val prefsRepo = PreferencesRepository(context)
    private val aiEngine = LocalAiEngine(context, repository)
    val voiceManager = OfflineVoiceManager(context)

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserPreferences()
    )

    val messages: StateFlow<List<ChatMessageEntity>> = repository.messages.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val notes: StateFlow<List<NoteModel>> = repository.notes.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val todos: StateFlow<List<TodoEntity>> = repository.todos.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val reminders: StateFlow<List<ReminderEntity>> = repository.reminders.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val documents: StateFlow<List<DocumentEntity>> = repository.documents.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val voiceState: StateFlow<VoiceUiState> = voiceManager.state

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _isAnalyzingAttachment = MutableStateFlow(false)
    val isAnalyzingAttachment: StateFlow<Boolean> = _isAnalyzingAttachment.asStateFlow()

    private val _activePhoto = MutableStateFlow<PhotoAnalysisResult?>(null)
    val activePhoto: StateFlow<PhotoAnalysisResult?> = _activePhoto.asStateFlow()

    private val _activeFileName = MutableStateFlow<String?>(null)
    val activeFileName: StateFlow<String?> = _activeFileName.asStateFlow()

    private val _memorySnapshot = MutableStateFlow(MemoryOptimizer.snapshot(context))
    val memorySnapshot: StateFlow<MemorySnapshot> = _memorySnapshot.asStateFlow()

    private val _navigationEvent = MutableStateFlow<String?>(null)
    val navigationEvent: StateFlow<String?> = _navigationEvent.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val recommendedModels: List<ModelSpec> = ModelCatalog.recommendedModels

    init {
        refreshSystemStats()
    }

    fun sendUserMessage(rawText: String) {
        val text = rawText.trim()
        val photo = _activePhoto.value
        val file = _activeFileName.value
        if ((text.isEmpty() && photo == null && file == null) || _isThinking.value) return

        val effectivePrompt = when {
            text.isNotEmpty() -> text
            photo != null -> "Analyze and read text in attached photo: ${photo.displayName}"
            else -> "Summarize attached file: $file"
        }

        viewModelScope.launch {
            _isThinking.value = true
            val currentPrefs = preferences.value
            val detected = LanguageDetector.detect(effectivePrompt, currentPrefs.uiLanguage)

            val userBubbleText = buildString {
                if (photo != null) append("[Photo: ${photo.displayName}]\n")
                if (file != null) append("[File: $file]\n")
                append(effectivePrompt)
            }

            withContext(Dispatchers.IO) {
                repository.addMessage(
                    ChatMessageEntity(
                        role = "USER",
                        content = userBubbleText,
                        languageCode = detected.dialect.code,
                        intentTag = "USER_PROMPT"
                    )
                )
            }

            val response = withContext(Dispatchers.Default) {
                aiEngine.processQuery(
                    userInput = effectivePrompt,
                    prefs = currentPrefs,
                    activePhoto = photo,
                    activeFileName = file
                )
            }

            val insertedId = withContext(Dispatchers.IO) {
                repository.addMessage(
                    ChatMessageEntity(
                        role = "ASSISTANT",
                        content = response.replyText,
                        languageCode = response.detectedDialect.code,
                        intentTag = response.intentTag,
                        engineLabel = response.engineLabel,
                        latencyMs = response.latencyMs,
                        actionPackage = response.actionPackage
                    )
                )
            }

            if (currentPrefs.autoSpeakReplies) {
                voiceManager.speak(response.replyText, response.detectedLanguage, insertedId)
            }
            if (response.navigateToTab != null) {
                _navigationEvent.value = response.navigateToTab
            }

            refreshSystemStats()
            _isThinking.value = false
        }
    }

    fun attachPhoto(uri: Uri) {
        viewModelScope.launch {
            _isAnalyzingAttachment.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching { OfflineVisionAnalyzer.analyzePhoto(context, uri) }.getOrNull()
            }
            if (result != null) {
                _activePhoto.value = result
                _statusBanner.value = "Photo ready (${result.summaryLine})"
            } else {
                _statusBanner.value = "Could not analyze selected photo"
            }
            refreshSystemStats()
            _isAnalyzingAttachment.value = false
        }
    }

    fun clearAttachedPhoto() {
        _activePhoto.value = null
    }

    fun attachFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzingAttachment.value = true
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            var fileName = "document.txt"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) fileName = cursor.getString(idx) ?: fileName
                }
            }
            val mimeType = context.contentResolver.getType(uri) ?: "text/plain"
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readText()
            }.orEmpty()

            if (text.isNotBlank()) {
                val doc = repository.indexDocument(fileName, mimeType, text.take(120_000))
                _activeFileName.value = doc.fileName
                _statusBanner.value = "File indexed: ${doc.fileName} (${doc.chunkCount} chunks)"
            } else {
                _statusBanner.value = "Could not read text from $fileName (supported: .txt, .md, .csv, .json)"
            }
            refreshSystemStats()
            _isAnalyzingAttachment.value = false
        }
    }

    fun clearAttachedFile() {
        _activeFileName.value = null
    }

    fun onAppBackgrounded() {
        // Strictly stop all mic/TTS & release volatile memory when user closes/leaves the app
        voiceManager.stopListening()
        voiceManager.stopSpeaking()
    }

    fun consumeNavigationEvent() {
        _navigationEvent.value = null
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun clearChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearChat()
        }
    }

    fun startVoiceCapture() {
        val lang = preferences.value.uiLanguage
        voiceManager.startListening(lang) { recognizedText ->
            sendUserMessage(recognizedText)
        }
    }

    fun toggleSpeakMessage(message: ChatMessageEntity) {
        val vs = voiceState.value
        if (vs.isSpeaking && vs.speakingMessageId == message.id) {
            voiceManager.stopSpeaking()
        } else {
            val lang = AppLanguage.fromCode(message.languageCode)
            voiceManager.speak(message.content, lang, message.id)
        }
    }

    fun createNote(title: String, content: String, encrypt: Boolean) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.addNote(
                title = title.ifBlank { content.take(30) },
                content = content,
                encrypt = encrypt,
                languageCode = "en"
            )
            refreshSystemStats()
        }
    }

    fun togglePinNote(note: NoteModel) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.togglePinNote(note)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNote(id)
        }
    }

    fun createTodo(title: String, priority: Int) {
        if (title.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTodo(title.trim(), priority, "en")
        }
    }

    fun toggleTodo(id: Long, completed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleTodo(id, completed)
        }
    }

    fun deleteTodo(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTodo(id)
        }
    }

    fun createReminder(title: String, minutesFromNow: Int) {
        if (title.isBlank()) return
        val mins = minutesFromNow.coerceIn(1, 10080)
        val triggerAt = System.currentTimeMillis() + mins * 60_000L
        viewModelScope.launch(Dispatchers.IO) {
            repository.addReminder(title.trim(), triggerAt, "en")
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteReminder(id)
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDocument(id)
        }
    }

    fun inspectAndLoadGguf(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = GgufHeaderParser.inspectUri(context, uri)
            if (result.isValidGguf) {
                prefsRepo.setLoadedGguf(result.fileName, result.statusSummary)
                _statusBanner.value = "Loaded ${result.fileName}: ${result.statusSummary}"
            } else {
                _statusBanner.value = result.statusSummary
            }
            refreshSystemStats()
        }
    }

    fun unloadGgufModel() {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setLoadedGguf(null, null)
            System.gc()
            refreshSystemStats()
        }
    }

    fun refreshSystemStats() {
        _memorySnapshot.value = MemoryOptimizer.snapshot(context)
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDarkMode(enabled) }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoSpeak(enabled) }
    }

    fun setContextCompression(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setContextCompression(enabled) }
    }

    fun setEncryptNotesByDefault(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setEncryptNotesByDefault(enabled) }
    }

    override fun onCleared() {
        voiceManager.release()
        super.onCleared()
    }
}
