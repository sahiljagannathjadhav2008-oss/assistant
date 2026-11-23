package com.example.sjassistant.ai

import android.content.Context
import com.example.sjassistant.core.i18n.AppLanguage
import com.example.sjassistant.core.i18n.ConversationDialect
import com.example.sjassistant.core.i18n.LanguageDetector
import com.example.sjassistant.data.local.AssistantRepository
import com.example.sjassistant.data.local.UserPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiTurnResponse(
    val replyText: String,
    val detectedLanguage: AppLanguage,
    val detectedDialect: ConversationDialect,
    val intentTag: String,
    val engineLabel: String,
    val latencyMs: Long,
    val navigateToTab: String? = null,
    val actionPackage: String? = null
)

/**
 * Unified Offline AI Engine for SJ Assistant (Zero Background Execution).
 * Supports Photo Q&A (ML Kit Devanagari+Latin OCR & Image Labeling), File RAG Q&A,
 * and automatic detection of Marathi, Marathish, Hindi, Hinglish, and English.
 */
class LocalAiEngine(
    private val context: Context,
    private val repository: AssistantRepository
) {
    private val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    suspend fun processQuery(
        userInput: String,
        prefs: UserPreferences,
        activePhoto: PhotoAnalysisResult? = null,
        activeFileName: String? = null
    ): AiTurnResponse {
        val startMs = System.currentTimeMillis()
        val detection = LanguageDetector.detect(userInput, fallback = prefs.uiLanguage)
        val lang = detection.primaryLanguage
        val dialect = detection.dialect

        val recentHistory = repository.getRecentMessages(limit = 8)
        val compressedContext = MemoryOptimizer.compressContext(
            history = recentHistory,
            enabled = prefs.contextCompressionEnabled
        )
        val contextTokensApprox = (compressedContext.length / 4).coerceAtLeast(1)

        val engineTag = if (prefs.loadedGgufName != null) {
            "GGUF(${prefs.loadedGgufName.take(14)}) + NLU • ${contextTokensApprox}t"
        } else {
            "Offline Hybrid + MLKit • ${contextTokensApprox}t"
        }

        val intent = MultilingualNluParser.parse(
            rawInput = userInput,
            dialect = dialect,
            hasAttachedPhoto = activePhoto != null,
            hasAttachedFile = activeFileName != null
        )
        var navigateTab: String? = null
        var actionPackage: String? = null
        var intentTag = "GENERAL"

        val reply = when (intent) {
            is AssistantIntent.PhotoQuestion -> {
                intentTag = "PHOTO_QA"
                answerAboutPhoto(intent.query, activePhoto, dialect)
            }

            is AssistantIntent.Calculate -> {
                intentTag = "CALCULATOR"
                when (dialect) {
                    ConversationDialect.MARATHI_DEVANAGARI ->
                        "गणित उत्तर:\n${intent.result.expressionDisplay} = ${intent.result.formattedResult}"
                    ConversationDialect.MARATHISH_ROMAN ->
                        "Ganit answer:\n${intent.result.expressionDisplay} = ${intent.result.formattedResult}"
                    ConversationDialect.HINDI_DEVANAGARI ->
                        "गणना परिणाम:\n${intent.result.expressionDisplay} = ${intent.result.formattedResult}"
                    ConversationDialect.HINGLISH_ROMAN ->
                        "Calculation result:\n${intent.result.expressionDisplay} = ${intent.result.formattedResult}"
                    ConversationDialect.ENGLISH ->
                        "Calculation:\n${intent.result.expressionDisplay} = ${intent.result.formattedResult}"
                }
            }

            is AssistantIntent.Spark7tAndGemmaInfoQuery -> {
                intentTag = "SPARK7T_GUIDE"
                buildSpark7tAndGemmaReply(dialect)
            }

            is AssistantIntent.AiSpecsQuery -> {
                intentTag = "AI_SPECS"
                buildAiSpecsReply(dialect, prefs, contextTokensApprox)
            }

            is AssistantIntent.AutoLanguageInfoQuery -> {
                intentTag = "AUTO_LANG"
                buildAutoLanguageReply(dialect)
            }

            is AssistantIntent.CreateReminder -> {
                intentTag = "REMINDER"
                repository.addReminder(
                    title = intent.title,
                    triggerAtMillis = intent.triggerAtMillis,
                    languageCode = lang.code
                )
                val formattedTime = timeFormat.format(Date(intent.triggerAtMillis))
                when (dialect) {
                    ConversationDialect.MARATHI_DEVANAGARI ->
                        "रिमाइंडर नोंदीत जतन केला आहे:\n• विषय: ${intent.title}\n• वेळ: $formattedTime\n(अ‍ॅप बंद केल्यावर कोणताही बॅकग्राउंड अलार्म चालत नाही)"
                    ConversationDialect.MARATHISH_ROMAN ->
                        "Reminder local list madhe save kela aahe:\n• Kaam: ${intent.title}\n• Vel: $formattedTime\n(App banda kelyavar background madhe kahich chalat nahi)"
                    ConversationDialect.HINDI_DEVANAGARI ->
                        "रिमाइंडर लोकल सूची में सहेजा गया:\n• विषय: ${intent.title}\n• समय: $formattedTime\n(ऐप बंद होने पर कोई बैकग्राउंड कार्य नहीं चलता)"
                    ConversationDialect.HINGLISH_ROMAN ->
                        "Reminder local list mein save ho gaya:\n• Kaam: ${intent.title}\n• Time: $formattedTime\n(App band karne par background mein kuch nahi chalta)"
                    ConversationDialect.ENGLISH ->
                        "Saved reminder in Notes & Tasks:\n• Task: ${intent.title}\n• Time: $formattedTime\n(Foreground-only: zero background services when closed)"
                }
            }

            is AssistantIntent.CreateNote -> {
                intentTag = "NOTE"
                repository.addNote(
                    title = intent.title,
                    content = intent.content,
                    encrypt = prefs.encryptNotesByDefault,
                    languageCode = lang.code
                )
                val vaultTag = if (prefs.encryptNotesByDefault) "AES-256" else "SQLite"
                when (dialect) {
                    ConversationDialect.MARATHI_DEVANAGARI ->
                        "नोट ऑफलाइन जतन केली ($vaultTag):\n\"${intent.content}\""
                    ConversationDialect.MARATHISH_ROMAN ->
                        "Note offline save keli aahe ($vaultTag):\n\"${intent.content}\""
                    ConversationDialect.HINDI_DEVANAGARI ->
                        "नोट ऑफलाइन सेव कर लिया गया ($vaultTag):\n\"${intent.content}\""
                    ConversationDialect.HINGLISH_ROMAN ->
                        "Note offline save ho gaya hai ($vaultTag):\n\"${intent.content}\""
                    ConversationDialect.ENGLISH ->
                        "Saved offline note ($vaultTag):\n\"${intent.content}\""
                }
            }

            is AssistantIntent.CreateTodo -> {
                intentTag = "TODO"
                repository.addTodo(
                    title = intent.title,
                    priority = 1,
                    languageCode = lang.code
                )
                when (dialect) {
                    ConversationDialect.MARATHI_DEVANAGARI -> "काम (To-Do) जोडले: \"${intent.title}\""
                    ConversationDialect.MARATHISH_ROMAN -> "To-Do list madhe add kela: \"${intent.title}\""
                    ConversationDialect.HINDI_DEVANAGARI -> "कार्य (To-Do) जोड़ा गया: \"${intent.title}\""
                    ConversationDialect.HINGLISH_ROMAN -> "To-Do list mein add kar diya: \"${intent.title}\""
                    ConversationDialect.ENGLISH -> "Added to To-Do list: \"${intent.title}\""
                }
            }

            is AssistantIntent.NavigateTab -> {
                intentTag = "NAVIGATION"
                navigateTab = intent.tabKey
                when (dialect) {
                    ConversationDialect.MARATHI_DEVANAGARI -> "तुमच्या ऑफलाइन नोंदी व कामे उघडत आहे..."
                    ConversationDialect.MARATHISH_ROMAN -> "Tumchya offline notes ani tasks ughadt aahe..."
                    ConversationDialect.HINDI_DEVANAGARI -> "आपके ऑफलाइन नोट्स और कार्य खोल रहा हूँ..."
                    ConversationDialect.HINGLISH_ROMAN -> "Aapke offline notes aur tasks khol raha hoon..."
                    ConversationDialect.ENGLISH -> "Opening your offline Notes & Tasks..."
                }
            }

            is AssistantIntent.ToggleFlashlight -> {
                intentTag = "FLASHLIGHT"
                val result = DeviceCommandEngine.setFlashlight(context, intent.turnOn)
                if (result.isSuccess) {
                    when (dialect) {
                        ConversationDialect.MARATHI_DEVANAGARI -> if (intent.turnOn) "टॉर्च चालू केला आहे." else "टॉर्च बंद केला आहे."
                        ConversationDialect.MARATHISH_ROMAN -> if (intent.turnOn) "Torch chalu kela aahe." else "Torch band kela aahe."
                        ConversationDialect.HINDI_DEVANAGARI -> if (intent.turnOn) "टॉर्च चालू कर दी गई है।" else "टॉर्च बंद कर दी गई है।"
                        ConversationDialect.HINGLISH_ROMAN -> if (intent.turnOn) "Torch chalu kar di hai." else "Torch band kar di hai."
                        ConversationDialect.ENGLISH -> if (intent.turnOn) "Flashlight turned ON." else "Flashlight turned OFF."
                    }
                } else {
                    when (dialect) {
                        ConversationDialect.MARATHI_DEVANAGARI -> "या डिव्हाइसवर फ्लॅशलाइट नियंत्रित करता आला नाही."
                        ConversationDialect.MARATHISH_ROMAN -> "Ya phone var flashlight control karta aala nahi."
                        ConversationDialect.HINDI_DEVANAGARI -> "इस डिवाइस पर टॉर्च उपलब्ध नहीं है।"
                        ConversationDialect.HINGLISH_ROMAN -> "Is phone par flashlight available nahi hai."
                        ConversationDialect.ENGLISH -> "Camera flashlight is unavailable on this device."
                    }
                }
            }

            is AssistantIntent.OpenWifiSettings -> {
                intentTag = "PHONE_SETTINGS"
                DeviceCommandEngine.openWifiSettings(context)
                when (dialect) {
                    ConversationDialect.MARATHI_DEVANAGARI -> "वाय-फाय सेटिंग्ज उघडत आहे."
                    ConversationDialect.MARATHISH_ROMAN -> "Wi-Fi settings ughadt aahe."
                    ConversationDialect.HINDI_DEVANAGARI -> "वाई-फ़ाई सेटिंग्स खोल रहा हूँ।"
                    ConversationDialect.HINGLISH_ROMAN -> "Wi-Fi settings khol raha hoon."
                    ConversationDialect.ENGLISH -> "Opening Wi-Fi settings."
                }
            }

            is AssistantIntent.OpenBluetoothSettings -> {
                intentTag = "PHONE_SETTINGS"
                DeviceCommandEngine.openBluetoothSettings(context)
                when (dialect) {
                    ConversationDialect.MARATHI_DEVANAGARI -> "ब्लूटूथ सेटिंग्ज उघडत आहे."
                    ConversationDialect.MARATHISH_ROMAN -> "Bluetooth settings ughadt aahe."
                    ConversationDialect.HINDI_DEVANAGARI -> "ब्लूटूथ सेटिंग्स खोल रहा हूँ।"
                    ConversationDialect.HINGLISH_ROMAN -> "Bluetooth settings khol raha hoon."
                    ConversationDialect.ENGLISH -> "Opening Bluetooth settings."
                }
            }

            is AssistantIntent.DialNumber -> {
                intentTag = "DIALER"
                DeviceCommandEngine.openDialer(context, intent.number)
                when (dialect) {
                    ConversationDialect.MARATHI_DEVANAGARI -> "${intent.number} साठी डायलर उघडत आहे."
                    ConversationDialect.MARATHISH_ROMAN -> "${intent.number} sathi dialer ughadt aahe."
                    ConversationDialect.HINDI_DEVANAGARI -> "${intent.number} के लिए डायलर खोल रहा हूँ।"
                    ConversationDialect.HINGLISH_ROMAN -> "${intent.number} ke liye dialer khol raha hoon."
                    ConversationDialect.ENGLISH -> "Opening phone dialer for ${intent.number}."
                }
            }

            is AssistantIntent.CheckSystemStatus -> {
                intentTag = "SYSTEM_STATUS"
                val bat = DeviceCommandEngine.getBatteryStatus(context)
                val mem = MemoryOptimizer.snapshot(context)
                when (dialect) {
                    ConversationDialect.MARATHI_DEVANAGARI ->
                        "डिव्हाइस स्थिती (ऑफलाइन):\n• बॅटरी: ${bat.levelPercent}% ${if (bat.isCharging) "(चार्ज होत आहे)" else ""}\n• अ‍ॅप रॅम वापर: ${mem.totalAppRamMb} MB (लक्ष्य <500 MB)\n• उपलब्ध सिस्टम रॅम: ${mem.systemAvailableMb} MB / ${mem.systemTotalMb} MB"
                    ConversationDialect.MARATHISH_ROMAN ->
                        "Phone status (100% Offline):\n• Battery: ${bat.levelPercent}% ${if (bat.isCharging) "(Charging chalu aahe)" else ""}\n• App RAM usage: ${mem.totalAppRamMb} MB (Target <500 MB)\n• Free System RAM: ${mem.systemAvailableMb} MB / ${mem.systemTotalMb} MB"
                    ConversationDialect.HINDI_DEVANAGARI ->
                        "डिवाइस स्थिति (ऑफलाइन):\n• बैटरी: ${bat.levelPercent}% ${if (bat.isCharging) "(चार्ज हो रही है)" else ""}\n• ऐप RAM उपयोग: ${mem.totalAppRamMb} MB (लक्ष्य <500 MB)\n• उपलब्ध सिस्टम RAM: ${mem.systemAvailableMb} MB / ${mem.systemTotalMb} MB"
                    ConversationDialect.HINGLISH_ROMAN ->
                        "Phone status (100% Offline):\n• Battery: ${bat.levelPercent}% ${if (bat.isCharging) "(Charging ho rahi hai)" else ""}\n• App RAM usage: ${mem.totalAppRamMb} MB (Target <500 MB)\n• Free System RAM: ${mem.systemAvailableMb} MB / ${mem.systemTotalMb} MB"
                    ConversationDialect.ENGLISH ->
                        "Device Status (Offline):\n• Battery: ${bat.levelPercent}% ${if (bat.isCharging) "(Charging)" else ""}\n• App RAM Usage: ${mem.totalAppRamMb} MB (Target <500 MB)\n• System Free RAM: ${mem.systemAvailableMb} MB / ${mem.systemTotalMb} MB"
                }
            }

            is AssistantIntent.LaunchApp -> {
                intentTag = "APP_LAUNCHER"
                val matched = DeviceCommandEngine.findMatchingApp(context, intent.appQuery)
                if (matched != null && DeviceCommandEngine.launchApp(context, matched.packageName)) {
                    actionPackage = matched.packageName
                    when (dialect) {
                        ConversationDialect.MARATHI_DEVANAGARI -> "${matched.label} उघडत आहे..."
                        ConversationDialect.MARATHISH_ROMAN -> "${matched.label} ughadt aahe..."
                        ConversationDialect.HINDI_DEVANAGARI -> "${matched.label} खोल रहा हूँ..."
                        ConversationDialect.HINGLISH_ROMAN -> "${matched.label} khol raha hoon..."
                        ConversationDialect.ENGLISH -> "Launching ${matched.label}..."
                    }
                } else {
                    when (dialect) {
                        ConversationDialect.MARATHI_DEVANAGARI -> "'${intent.appQuery}' हे अ‍ॅप फोनमध्ये सापडले नाही."
                        ConversationDialect.MARATHISH_ROMAN -> "'${intent.appQuery}' he app phone madhe sapadla nahi."
                        ConversationDialect.HINDI_DEVANAGARI -> "'${intent.appQuery}' ऐप फोन में नहीं मिला।"
                        ConversationDialect.HINGLISH_ROMAN -> "'${intent.appQuery}' app phone mein nahi mila."
                        ConversationDialect.ENGLISH -> "Couldn't find an installed app matching '${intent.appQuery}'."
                    }
                }
            }

            is AssistantIntent.SearchFilesOrNotes -> {
                intentTag = "LOCAL_DOCS"
                answerFromDocsOrKb(userInput, lang, dialect, prioritizeDocs = true)
            }

            is AssistantIntent.KnowledgeQuery -> {
                intentTag = "KNOWLEDGE_BASE"
                answerFromDocsOrKb(userInput, lang, dialect, prioritizeDocs = false)
            }

            is AssistantIntent.GreetingOrHelp -> {
                intentTag = "GREETING"
                buildGreetingOrHelp(dialect, intent.isThankYou)
            }

            is AssistantIntent.GeneralQuery -> {
                intentTag = "LOCAL_QA"
                answerFromDocsOrKb(userInput, lang, dialect, prioritizeDocs = true)
            }
        }

        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        return AiTurnResponse(
            replyText = reply,
            detectedLanguage = lang,
            detectedDialect = dialect,
            intentTag = intentTag,
            engineLabel = engineTag,
            latencyMs = elapsed,
            navigateToTab = navigateTab,
            actionPackage = actionPackage
        )
    }

    private fun answerAboutPhoto(
        query: String,
        photo: PhotoAnalysisResult?,
        dialect: ConversationDialect
    ): String {
        if (photo == null) {
            return when (dialect) {
                ConversationDialect.MARATHISH_ROMAN ->
                    "Photo pathavnyasathi khalil chat box chya davyabajula aslela Photo icon dabun image select kara, mag tyavishayi konta hi prashna vichara!"
                ConversationDialect.HINGLISH_ROMAN ->
                    "Photo bhejne ke liye niche chat box ke paas wala Photo icon dabakar image select karein, phir uske baare mein koi bhi sawal poochein!"
                ConversationDialect.MARATHI_DEVANAGARI ->
                    "फोटो पाठवण्यासाठी खालील चॅट बॉक्समधील फोटो आयकॉन दाबून इमेज निवडा आणि त्याविषयी प्रश्न विचारा."
                ConversationDialect.HINDI_DEVANAGARI ->
                    "फ़ोटो भेजने के लिए नीचे चैट बॉक्स के पास फोटो आइकन दबाकर इमेज चुनें, फिर उसके बारे में सवाल पूछें।"
                ConversationDialect.ENGLISH ->
                    "Tap the Photo icon next to the chat input bar to attach an image, then ask me to read its text (Marathi/Hindi/English) or identify what's in it!"
            }
        }

        val labelsText = if (photo.detectedLabels.isNotEmpty()) {
            photo.detectedLabels.joinToString(", ")
        } else {
            "No distinct objects labeled"
        }

        // Filter OCR lines if user asked a specific keyword question
        val ocrLines = photo.extractedText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val queryTerms = query.lowercase()
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }

        val matchedLines = if (queryTerms.isNotEmpty() && ocrLines.size > 3) {
            ocrLines.filter { line ->
                val l = line.lowercase()
                queryTerms.any { term -> l.contains(term) }
            }.take(5)
        } else emptyList()

        val ocrBlock = when {
            photo.extractedText.isBlank() -> "(No readable text detected in this image)"
            matchedLines.isNotEmpty() -> "Matching lines:\n${matchedLines.joinToString("\n")}\n\nFull text:\n${photo.extractedText.take(700)}"
            else -> photo.extractedText.take(900)
        }

        return when (dialect) {
            ConversationDialect.MARATHISH_ROMAN ->
                "Photo Analysis (${photo.displayName} — Offline ML Kit):\n• Detected objects: $labelsText\n• Photo madhil text (OCR):\n$ocrBlock"
            ConversationDialect.HINGLISH_ROMAN ->
                "Photo Analysis (${photo.displayName} — Offline ML Kit):\n• Image mein dikha: $labelsText\n• Photo ka text (OCR):\n$ocrBlock"
            ConversationDialect.MARATHI_DEVANAGARI ->
                "फोटो विश्लेषण (${photo.displayName} — ऑफलाइन ML Kit):\n• दिसलेल्या वस्तू: $labelsText\n• फोटोमधील मजकूर (OCR):\n$ocrBlock"
            ConversationDialect.HINDI_DEVANAGARI ->
                "फ़ोटो विश्लेषण (${photo.displayName} — ऑफलाइन ML Kit):\n• पहचानी गई चीज़ें: $labelsText\n• फ़ोटो का टेक्स्ट (OCR):\n$ocrBlock"
            ConversationDialect.ENGLISH ->
                "Offline Photo Analysis (${photo.displayName}):\n• Visual Labels: $labelsText\n• Extracted Text (OCR):\n$ocrBlock"
        }
    }

    private fun buildSpark7tAndGemmaReply(dialect: ConversationDialect): String {
        return when (dialect) {
            ConversationDialect.MARATHISH_ROMAN ->
                """
                Tecno Spark 7T (4GB RAM, MediaTek Helio G35) sathi khari technical mahiti:
                1) Maximum kiti Billion Parameter AI chalel?
                   • Comfortable & Fast: 0.27B te 0.5B parameters (Gemma 3 270M, Qwen2.5 0.5B, SmolLM2 360M — 250–365 MB file, <500 MB RAM).
                   • Absolute Max Ceiling: 1.0B parameters (Gemma 3 1B Q4_K_M — ~650 MB file, ~1.25 GB RAM) fakta text-only sathi, pan Helio G35 CPU var slow (1-3 tokens/sec) chalel.
                2) Gemma 3 4B (Vision / Image) chalel ka?
                   • Nahi. Gemma 3 4B (140+ languages + Image vision) la ~3.5 te 4.5 GB RAM lagte, ani Tecno Spark 7T var Android OS 2GB vaparto, tyamule 4B model load kelyas phone crash hoil (8GB+ RAM phone lagto).
                3) Photo & File Q&A kasa chalto?
                   • Tumhi chat madhe Photo & File button vaprun offline ML Kit Vision (Marathi/Hindi/English OCR + Image Labeling) dwara photo & files vishayi prashna vicharu shakta — fakt <25 MB RAM madhe!
                """.trimIndent()
            ConversationDialect.HINGLISH_ROMAN ->
                """
                Tecno Spark 7T (4GB RAM, Helio G35) ke liye sahi technical details:
                1) Maximum kitne Billion Parameter AI chalega?
                   • Smooth & Fast: 0.27B se 0.5B parameters (Gemma 3 270M, Qwen2.5 0.5B, SmolLM2 360M — <500 MB RAM).
                   • Max Limit (Text-Only): 1.0B parameters (Gemma 3 1B Q4_K_M — ~650 MB file, ~1.25 GB RAM) chal sakta hai, par Helio G35 par slow rahega.
                2) Gemma 3 4B (Image + 140+ languages) kyun nahi chalega?
                   • Gemma 3 4B Vision ko ~3.5–4.5 GB RAM chahiye. 4GB RAM phone mein free RAM ~1.8 GB hoti hai, isliye 4B model crash ho jayega (8GB+ RAM chahiye).
                3) Photo aur File Q&A kaise karein?
                   • Chat ke Photo & File icon se aap offline ML Kit Vision (Marathi/Hindi/English OCR + Image Labeling) ke zariye kisi bhi photo ya file par sawal pooch sakte hain!
                """.trimIndent()
            else ->
                """
                Tecno Spark 7T (4GB RAM, Helio G35) AI Compatibility:
                • Comfortable & Fast: 0.27B–0.5B params (Gemma 3 270M, Qwen2.5 0.5B, SmolLM2 360M) under 500MB RAM.
                • Max Text-Only Ceiling: 1.0B params (Gemma 3 1B Q4_K_M, ~1.25GB RAM) with short 512-token context.
                • Gemma 3 4B Vision (140+ languages + images): Needs ~3.5–4.5GB RAM (8GB+ phone required), so it exceeds 4GB RAM devices.
                • Built-in Solution: Use the Photo & File buttons in Chat for fast offline ML Kit Vision (Devanagari + English OCR & Image Labeling) and file Q&A using <25MB RAM!
                """.trimIndent()
        }
    }

    private fun buildAiSpecsReply(
        dialect: ConversationDialect,
        prefs: UserPreferences,
        currentTokens: Int
    ): String {
        val mem = MemoryOptimizer.snapshot(context)
        val loadedLine = prefs.loadedGgufSummary ?: "Built-in Hybrid NLU + ML Kit Vision active"
        return when (dialect) {
            ConversationDialect.MARATHISH_ROMAN ->
                """
                SJ Assistant Specs (Tecno Spark 7T Ready):
                • Built-in Engine RAM: ~65 MB active RAM (Live: ${mem.totalAppRamMb} MB / <500 MB target).
                • Context Window & Data Context: Chat history 900 chars (~225-512 tokens, live: ~${currentTokens}t) madhe compress hote; Local files 360-char overlapping chunks madhe SQLite madhe index hotat.
                • Tecno Spark 7T Max Model: 0.27B–0.5B recommended (Gemma 3 270M / Qwen2.5 0.5B), 1.0B max ceiling (Gemma 3 1B). Gemma 3 4B Vision needs 8GB+ RAM.
                • Runtime: $loadedLine
                """.trimIndent()
            ConversationDialect.HINGLISH_ROMAN ->
                """
                SJ Assistant Specs (Tecno Spark 7T Ready):
                • Built-in Engine RAM: ~65 MB active RAM (Live: ${mem.totalAppRamMb} MB / <500 MB target).
                • Context Window & Data Context: Chat history 900 chars (~${currentTokens}t) mein compress hoti hai; Local files 360-char overlapping chunks mein SQLite mein store hoti hain.
                • Tecno Spark 7T Max Model: 0.27B–0.5B best (Gemma 3 270M / Qwen2.5 0.5B), 1.0B max limit (Gemma 3 1B). Gemma 3 4B Vision ke liye 8GB+ RAM chahiye.
                • Runtime: $loadedLine
                """.trimIndent()
            else ->
                """
                SJ Assistant Technical Specs:
                • Built-in Footprint: ~65 MB RAM (Live: ${mem.totalAppRamMb} MB / <500 MB budget) + Offline ML Kit Vision.
                • Data Context & Window: Chat compresses to 900 chars (~${currentTokens} active tokens); local files index into 360-char overlapping Room chunks.
                • Tecno Spark 7T Ceiling: 0.27B–0.5B recommended (Gemma 3 270M, Qwen2.5 0.5B), 1.0B max (Gemma 3 1B). Gemma 3 4B Vision requires 8GB+ RAM.
                • Runtime: $loadedLine
                """.trimIndent()
        }
    }

    private fun buildAutoLanguageReply(dialect: ConversationDialect): String {
        return when (dialect) {
            ConversationDialect.MARATHISH_ROMAN ->
                "Ho! Tumhala manually language choose karaychi garaj nahi. UI English madhe aahe, pan chat & voice madhe mi swatahun Marathish, Hinglish, Marathi, Hindi ani English olkhun tyach bhashet uttar deto."
            ConversationDialect.HINGLISH_ROMAN ->
                "Haan! Aapko manually language select karne ki zaroorat nahi hai. UI English mein hai, aur chat mein main khud Hinglish, Marathish, Hindi, Marathi aur English pehchan kar usi mein jawab deta hoon."
            else ->
                "Automatic language detection is always active for chat & voice (English, Marathish, Hinglish, Marathi, Hindi) while the app UI stays cleanly in English."
        }
    }

    private suspend fun answerFromDocsOrKb(
        query: String,
        lang: AppLanguage,
        dialect: ConversationDialect,
        prioritizeDocs: Boolean
    ): String {
        val chunks = repository.getAllDocumentChunks()
        val notes = repository.getAllNotesDecrypted()
        val docHits = DocumentRagEngine.searchDocumentsAndNotes(query, chunks, notes, maxHits = 2)
        val kbHits = OfflineKnowledgeBase.search(query, limit = 2)

        if (prioritizeDocs && docHits.isNotEmpty()) {
            val formatted = docHits.joinToString("\n\n") { "• [${it.sourceName}]\n${it.snippet}" }
            return when (dialect) {
                ConversationDialect.MARATHI_DEVANAGARI -> "तुमच्या स्थानिक फाइल्स व नोट्समधून सापडलेली माहिती:\n\n$formatted"
                ConversationDialect.MARATHISH_ROMAN -> "Tumchya local files ani notes madhun sapadleli mahiti:\n\n$formatted"
                ConversationDialect.HINDI_DEVANAGARI -> "आपकी स्थानीय फ़ाइलों और नोट्स से मिली जानकारी:\n\n$formatted"
                ConversationDialect.HINGLISH_ROMAN -> "Aapki local files aur notes se mili jaankari:\n\n$formatted"
                ConversationDialect.ENGLISH -> "Found in your local documents & notes:\n\n$formatted"
            }
        }

        if (kbHits.isNotEmpty()) {
            val top = kbHits.first()
            return "${top.title(lang)}\n\n${top.body(lang)}"
        }

        if (docHits.isNotEmpty()) {
            val formatted = docHits.joinToString("\n\n") { "• [${it.sourceName}]\n${it.snippet}" }
            return when (dialect) {
                ConversationDialect.MARATHI_DEVANAGARI -> "स्थानिक नोंदीतून सापडले:\n\n$formatted"
                ConversationDialect.MARATHISH_ROMAN -> "Local notes madhun sapadle:\n\n$formatted"
                ConversationDialect.HINDI_DEVANAGARI -> "स्थानीय दस्तावेज़ों से मिला:\n\n$formatted"
                ConversationDialect.HINGLISH_ROMAN -> "Local records se mila:\n\n$formatted"
                ConversationDialect.ENGLISH -> "From your local records:\n\n$formatted"
            }
        }

        return when (dialect) {
            ConversationDialect.MARATHISH_ROMAN ->
                "Mi 100% offline kaam karto (app banda kelyavar background madhe kahich chalat nahi). Tumhi photo/file attach karun tyavishayi prashna vicharu shakta, ganit karu shakta, notes/reminders save karu shakta, kinva 'battery status sang' ase bolu shakta."
            ConversationDialect.HINGLISH_ROMAN ->
                "Main 100% offline kaam karta hoon (app band hone par background mein kuch nahi chalta). Aap photo ya file attach karke sawal pooch sakte hain, notes/reminders save kar sakte hain, ya phone status check kar sakte hain."
            else ->
                "I'm running 100% offline with zero background activity. Attach a Photo or File right here to ask questions about it, or ask me for math, notes, reminders, or battery/RAM status."
        }
    }

    private fun buildGreetingOrHelp(dialect: ConversationDialect, isThankYou: Boolean): String {
        if (isThankYou) {
            return when (dialect) {
                ConversationDialect.MARATHI_DEVANAGARI -> "स्वागत आहे! आणखी काही मदत हवी असल्यास मला कधीही विचारा."
                ConversationDialect.MARATHISH_ROMAN -> "Welcome! Aankhi kahi madat havi aslyas kadhi hi vichara."
                ConversationDialect.HINDI_DEVANAGARI -> "आपका स्वागत है! कोई और मदद चाहिए तो बेझिझक पूछें।"
                ConversationDialect.HINGLISH_ROMAN -> "Welcome! Koi aur help chahiye toh kabhi bhi pooch lena."
                ConversationDialect.ENGLISH -> "You're welcome! Ask anytime — everything stays offline."
            }
        }
        return when (dialect) {
            ConversationDialect.MARATHI_DEVANAGARI ->
                "नमस्कार! मी SJ असिस्टंट आहे — १००% ऑफलाइन आणि खाजगी AI. तुम्ही फोटो किंवा फाइल पाठवून प्रश्न विचारू शकता!"
            ConversationDialect.MARATHISH_ROMAN ->
                "Namaskar! Mi SJ Assistant aahe — Tecno Spark 7T sathi fast offline AI. Photo/file attach kara, kinva Marathish, Hinglish, Marathi, Hindi, English madhe vichara!"
            ConversationDialect.HINDI_DEVANAGARI ->
                "नमस्ते! मैं SJ असिस्टेंट हूँ — 100% ऑफलाइन AI। आप फ़ोटो या फ़ाइल भेजकर सवाल पूछ सकते हैं!"
            ConversationDialect.HINGLISH_ROMAN ->
                "Namaste! Main SJ Assistant hoon — 100% offline AI. Aap photo/file bhejkar sawal pooch sakte ho ya Hinglish, Marathish, Hindi, Marathi, English mein baat kar sakte ho!"
            ConversationDialect.ENGLISH ->
                "Hello! I'm SJ Assistant — 100% offline with zero background drain. Attach a photo or file, or type/speak in English, Marathish, Hinglish, Marathi, or Hindi!"
        }
    }
}
