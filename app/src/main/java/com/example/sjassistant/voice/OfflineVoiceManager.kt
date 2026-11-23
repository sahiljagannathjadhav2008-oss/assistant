package com.example.sjassistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.sjassistant.core.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class VoiceUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val speakingMessageId: Long? = null,
    val isTtsReady: Boolean = false,
    val isOfflineSpeechAvailable: Boolean = true,
    val partialTranscript: String = "",
    val voiceError: String? = null
)

/**
 * 100% Offline-first Voice Manager using Android's SpeechRecognizer (EXTRA_PREFER_OFFLINE = true)
 * and TextToSpeech with dynamic Marathi (mr-IN), Hindi (hi-IN), and English (en-IN) locale routing.
 */
class OfflineVoiceManager(private val context: Context) {

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    init {
        initTts()
        _state.value = _state.value.copy(
            isOfflineSpeechAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        )
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setSpeechRate(0.98f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(isSpeaking = true)
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.value = _state.value.copy(isSpeaking = false, speakingMessageId = null)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(isSpeaking = false, speakingMessageId = null)
                    }
                })
                _state.value = _state.value.copy(isTtsReady = true)
            }
        }
    }

    fun startListening(
        language: AppLanguage,
        onFinalResult: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(
                voiceError = "Offline speech recognizer unavailable on this device"
            )
            return
        }

        stopSpeaking()
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = _state.value.copy(
                        isListening = true,
                        partialTranscript = "",
                        voiceError = null
                    )
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _state.value = _state.value.copy(isListening = false)
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try again."
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        else -> "Offline voice input unavailable ($error)."
                    }
                    _state.value = _state.value.copy(
                        isListening = false,
                        partialTranscript = "",
                        voiceError = msg
                    )
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull().orEmpty().trim()
                    _state.value = _state.value.copy(
                        isListening = false,
                        partialTranscript = ""
                    )
                    if (best.isNotEmpty()) {
                        onFinalResult(best)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    _state.value = _state.value.copy(partialTranscript = partial)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechLocaleTag)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("mr-IN", "hi-IN", "en-IN"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _state.value = _state.value.copy(isListening = false)
    }

    fun speak(text: String, language: AppLanguage, messageId: Long? = null) {
        val engine = tts ?: return
        if (!_state.value.isTtsReady) return

        val targetLocale = selectSupportedLocale(engine, language)
        engine.language = targetLocale

        val cleanText = text.replace(Regex("[•*_`#]"), " ").trim()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, messageId?.toString() ?: "sj_tts")
        }
        _state.value = _state.value.copy(isSpeaking = true, speakingMessageId = messageId)
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, messageId?.toString() ?: "sj_tts")
    }

    fun stopSpeaking() {
        tts?.stop()
        _state.value = _state.value.copy(isSpeaking = false, speakingMessageId = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(voiceError = null)
    }

    private fun selectSupportedLocale(engine: TextToSpeech, language: AppLanguage): Locale {
        val preferred = language.ttsLocale
        val status = engine.isLanguageAvailable(preferred)
        if (status >= TextToSpeech.LANG_AVAILABLE) return preferred
        // Fallback between Devanagari locales or English-India
        val hiStatus = engine.isLanguageAvailable(Locale("hi", "IN"))
        if (language == AppLanguage.MARATHI && hiStatus >= TextToSpeech.LANG_AVAILABLE) {
            return Locale("hi", "IN")
        }
        return Locale("en", "IN")
    }

    fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
