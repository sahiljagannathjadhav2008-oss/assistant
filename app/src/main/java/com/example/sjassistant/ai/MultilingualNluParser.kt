package com.example.sjassistant.ai

import com.example.sjassistant.core.i18n.ConversationDialect
import java.util.Calendar

sealed class AssistantIntent {
    data class Calculate(val result: CalculationResult) : AssistantIntent()
    data class CreateReminder(val title: String, val triggerAtMillis: Long) : AssistantIntent()
    data class CreateNote(val title: String, val content: String) : AssistantIntent()
    data class CreateTodo(val title: String) : AssistantIntent()
    data class LaunchApp(val appQuery: String) : AssistantIntent()
    data class NavigateTab(val tabKey: String) : AssistantIntent()
    data class ToggleFlashlight(val turnOn: Boolean) : AssistantIntent()
    data object OpenWifiSettings : AssistantIntent()
    data object OpenBluetoothSettings : AssistantIntent()
    data class DialNumber(val number: String) : AssistantIntent()
    data object CheckSystemStatus : AssistantIntent()
    data object AiSpecsQuery : AssistantIntent()
    data object Spark7tAndGemmaInfoQuery : AssistantIntent()
    data object AutoLanguageInfoQuery : AssistantIntent()
    data class PhotoQuestion(val query: String) : AssistantIntent()
    data class SearchFilesOrNotes(val query: String) : AssistantIntent()
    data class KnowledgeQuery(val query: String) : AssistantIntent()
    data class GreetingOrHelp(val isThankYou: Boolean = false) : AssistantIntent()
    data class GeneralQuery(val rawText: String) : AssistantIntent()
}

/**
 * Fast (<5ms) rule + pattern + temporal entity parser for:
 * Marathi, Marathish (Marathi+English), Hindi, Hinglish (Hindi+English), and English.
 */
object MultilingualNluParser {

    fun parse(
        rawInput: String,
        dialect: ConversationDialect,
        hasAttachedPhoto: Boolean = false,
        hasAttachedFile: Boolean = false
    ): AssistantIntent {
        val text = rawInput.trim()
        val lower = CalculatorEngine.convertDevanagariDigits(text).lowercase()

        // 0. If user attached a photo right now and asks about it (or taps send)
        if (hasAttachedPhoto && (text.isBlank() || !isExplicitUtilityCommand(lower))) {
            return AssistantIntent.PhotoQuestion(text.ifBlank { "Describe and read this photo" })
        }

        // If user attached a file right now and asks about it
        if (hasAttachedFile && (text.isBlank() || !isExplicitUtilityCommand(lower))) {
            return AssistantIntent.SearchFilesOrNotes(text.ifBlank { "Summarize this file" })
        }

        // Explicit photo question
        if (containsAny(lower, listOf("photo", "image", "picture", "screenshot", "फोटो", "तस्वीर", "इमेज"))) {
            return AssistantIntent.PhotoQuestion(text)
        }

        // 1. Direct Calculator intent
        CalculatorEngine.tryEvaluate(text)?.let { calc ->
            return AssistantIntent.Calculate(calc)
        }

        // 2. Tecno Spark 7T / Gemma 3 / Billion parameter / Model download question
        if (containsAny(
                lower,
                listOf(
                    "spark 7t", "tecno", "gemma", "billion", "4b", "140",
                    "download model", "model download", "kitna gb", "kiti billion"
                )
            )
        ) {
            return AssistantIntent.Spark7tAndGemmaInfoQuery
        }

        // 3. AI Model Size / Parameters / Context Window / Data Context self-inspection
        if (containsAny(
                lower,
                listOf(
                    "parameter", "parameters", "paryameter", "context window", "context length",
                    "data context", "ciontext", "token", "tokens", "model size", "yachi size",
                    "yechi size", "size kiti", "kitna bada", "kitne parameter", "kiti ram"
                )
            )
        ) {
            return AssistantIntent.AiSpecsQuery
        }

        // 4. Auto language detection / Hinglish / Marathish capability question
        if (containsAny(
                lower,
                listOf(
                    "hinglish", "marathish", "minglish", "auto detect", "bhasha", "bhashet",
                    "language detect", "kontya bhashet", "swatahun", "apne aap", "khud pehchan"
                )
            )
        ) {
            return AssistantIntent.AutoLanguageInfoQuery
        }

        // 5. Flashlight / Torch control
        if (containsAny(lower, listOf("torch", "flashlight", "टॉर्च", "फ्लॅश", "फ्लैश", "बत्ती", "batti"))) {
            val turnOff = containsAny(lower, listOf("off", "band", "बंद", "close", "disable"))
            return AssistantIntent.ToggleFlashlight(turnOn = !turnOff)
        }

        // 6. Wi-Fi / Bluetooth settings
        if (containsAny(lower, listOf("wifi", "wi-fi", "वाय-फाय", "वायफाय", "वाई-फाई", "वाईफाई"))) {
            return AssistantIntent.OpenWifiSettings
        }
        if (containsAny(lower, listOf("bluetooth", "ब्लूटूथ"))) {
            return AssistantIntent.OpenBluetoothSettings
        }

        // 7. Battery / RAM / System status
        if (containsAny(
                lower,
                listOf(
                    "battery", "बॅटरी", "बैटरी", "ram", "रॅम", "memory", "मेमरी", "मेमोरी",
                    "status", "स्थिती", "स्थिति", "charge", "charging"
                )
            )
        ) {
            return AssistantIntent.CheckSystemStatus
        }

        // 8. Dialer command
        val phoneMatch = Regex("""(?:call|dial|फोन कर|कॉल कर|कॉल लगाओ|call kar|phone kar)\s*([+0-9\-\s]{5,15})""").find(lower)
        if (phoneMatch != null) {
            return AssistantIntent.DialNumber(phoneMatch.groupValues[1].replace(" ", ""))
        }

        // 9. Navigate to Notes / Workspace
        if (containsAny(
                lower,
                listOf(
                    "open notes", "show notes", "my notes", "notes dakhav", "notes ughad",
                    "notes kholo", "notes dikhao", "नोट्स उघड", "नोट्स दाखव", "नोट्स खोलो", "नोट्स दिखाओ"
                )
            )
        ) {
            return AssistantIntent.NavigateTab("workspace")
        }
        if (containsAny(
                lower,
                listOf(
                    "open todo", "show todo", "open tasks", "tasks dakhav", "todo dikhao",
                    "टास्क दाखव", "टास्क दिखाओ"
                )
            )
        ) {
            return AssistantIntent.NavigateTab("workspace")
        }

        // 10. Reminder creation
        if (containsAny(
                lower,
                listOf(
                    "remind", "reminder", "रिमाइंडर", "आठवण", "याद दिला",
                    "aathvan", "athvan", "yaad dila", "alarm lav", "alarm lagao"
                )
            )
        ) {
            val triggerMillis = parseReminderTime(lower)
            val cleanedTitle = cleanReminderTitle(text)
            return AssistantIntent.CreateReminder(
                title = cleanedTitle.ifBlank { defaultReminderTitle(dialect) },
                triggerAtMillis = triggerMillis
            )
        }

        // 11. Note creation
        if (containsAny(
                lower,
                listOf(
                    "save note", "create note", "add note", "make note",
                    "note save kar", "note thev", "note lihi", "note kar",
                    "note banao", "note likho", "note save karo", "note rakh",
                    "नोट सेव्ह", "नोट लिही", "नोट ठेवा", "नोट सेव", "नोट लिखो", "टीप"
                )
            )
        ) {
            val content = cleanNoteContent(text)
            val title = content.split(Regex("[.।\n]")).firstOrNull()?.take(38)?.trim()
                ?.ifBlank { "Quick Note" } ?: "Quick Note"
            return AssistantIntent.CreateNote(title = title, content = content.ifBlank { text })
        }

        // 12. Todo creation
        if (containsAny(
                lower,
                listOf(
                    "add todo", "add task", "create task", "todo",
                    "task tak", "task add kar", "kaam add kar", "kaam tak",
                    "task daalo", "task jodo", "kaam jodo",
                    "टास्क", "काम जोड", "कार्य जोड़", "to-do"
                )
            )
        ) {
            val taskTitle = cleanTodoTitle(text)
            return AssistantIntent.CreateTodo(title = taskTitle.ifBlank { text })
        }

        // 13. Open / Launch installed app
        val launchPrefixes = listOf("open ", "launch ", "start ", "kholo ", "khol ", "ughad ", "उघड ", "खोलो ")
        for (prefix in launchPrefixes) {
            if (lower.startsWith(prefix)) {
                val appName = text.substring(prefix.length).trim()
                if (appName.isNotEmpty()) return AssistantIntent.LaunchApp(appName)
            }
        }
        val launchSuffixes = listOf(
            " उघड", " उघडा", " खोलो", " खोलें",
            " kholo", " khol", " ughad", " ughada", " chalu kar", " chalu karo", " open"
        )
        for (suffix in launchSuffixes) {
            if (lower.endsWith(suffix)) {
                val appName = text.substring(0, text.length - suffix.length).trim()
                if (appName.isNotEmpty()) return AssistantIntent.LaunchApp(appName)
            }
        }

        // 14. Local file / document search
        if (containsAny(lower, listOf("file", "document", "doc", "pdf", "csv", "फाइल", "फ़ाइल", "दस्तऐवज", "दस्तावेज़", "search my"))) {
            return AssistantIntent.SearchFilesOrNotes(text)
        }

        // 15. Greetings & Thanks
        if (containsAny(lower, listOf("thank", "thanks", "धन्यवाद", "आभारी", "शुक्रिया", "dhanyawad", "shukriya"))) {
            return AssistantIntent.GreetingOrHelp(isThankYou = true)
        }
        if (lower in setOf(
                "hi", "hello", "hey", "namaste", "namaskar", "नमस्कार", "नमस्ते",
                "हॅलो", "हेलो", "help", "madad", "madat", "मदत", "मदद"
            )
        ) {
            return AssistantIntent.GreetingOrHelp(isThankYou = false)
        }

        // 16. Knowledge Base / General QA
        val kbHits = OfflineKnowledgeBase.search(text, limit = 1)
        if (kbHits.isNotEmpty()) {
            return AssistantIntent.KnowledgeQuery(text)
        }

        return AssistantIntent.GeneralQuery(text)
    }

    private fun isExplicitUtilityCommand(lower: String): Boolean {
        return containsAny(
            lower,
            listOf("torch", "flashlight", "wifi", "bluetooth", "battery", "open ", "call ")
        )
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun parseReminderTime(lower: String): Long {
        val cal = Calendar.getInstance()

        val minMatch = Regex("""(\d+)\s*(?:min|minute|minutes|minit|minitanni|मिनिट|मिनिटांनी|मिनिटात|मिनट)""").find(lower)
        if (minMatch != null) {
            val mins = minMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 1440) ?: 10
            cal.add(Calendar.MINUTE, mins)
            return cal.timeInMillis
        }

        val hrMatch = Regex("""(\d+)\s*(?:hr|hour|hours|taas|tasane|ghante|ghanta|तास|तासाने|घंटे|घंटा)""").find(lower)
        if (hrMatch != null) {
            val hrs = hrMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 72) ?: 1
            cal.add(Calendar.HOUR_OF_DAY, hrs)
            return cal.timeInMillis
        }

        val isTomorrow = containsAny(lower, listOf("tomorrow", "उद्या", "कल", "kal", "udya"))
        if (isTomorrow) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val timeMatch = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|वाजता|बजे|baje|vajta)?""").find(lower)
        if (timeMatch != null && (timeMatch.groupValues[3].isNotEmpty() || timeMatch.groupValues[2].isNotEmpty())) {
            var hour = timeMatch.groupValues[1].toIntOrNull() ?: 9
            val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val amPm = timeMatch.groupValues[3]
            val isEvening = amPm == "pm" || containsAny(
                lower,
                listOf("evening", "night", "संध्याकाळी", "रात्री", "शाम", "रात", "shaam", "sandhyakali", "ratri")
            )
            if (isEvening && hour in 1..11) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
            cal.set(Calendar.SECOND, 0)
            if (!isTomorrow && cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        if (isTomorrow) {
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
        } else {
            cal.add(Calendar.MINUTE, 15)
        }
        return cal.timeInMillis
    }

    private fun cleanReminderTitle(raw: String): String {
        return raw.replace(
            Regex("""(?i)(set\s+a?\s*reminder\s*(?:for|to|in)?|remind\s+me\s*(?:to|in|at)?|reminder\s+laga\s*do|reminder\s+lagao|reminder\s+lav|रिमाइंडर\s*लाव|रिमाइंडर\s*लगाओ|आठवण\s*कर|उद्या|सकाळी|कल|सुबह|udya|sakali|kal|subah|वाजता|बजे|vajta|baje|cha|ka|\d+\s*(?:min|minutes|minit|minitanni|मिनिटांनी|मिनट|hr|hours|taas|तासाने))"""),
            " "
        ).replace(Regex("""^[\s:,\-]+|[\s:,\-]+$"""), "").replace(Regex("""\s+"""), " ")
    }

    private fun cleanNoteContent(raw: String): String {
        return raw.replace(
            Regex("""(?i)^(save\s+note|create\s+note|add\s+note|make\s+note|note\s+save\s+karo|note\s+save\s+kar|note\s+thev|note\s+lihi|note\s+banao|note\s+likho|नोट\s*सेव्ह\s*कर|नोट\s*लिही|नोट\s*ठेवा|नोट\s*सेव\s*करो|नोट\s*लिखो|टीप)[:\s\-]*"""),
            ""
        ).trim()
    }

    private fun cleanTodoTitle(raw: String): String {
        return raw.replace(
            Regex("""(?i)^(add\s+to-?do|add\s+task|create\s+task|to-?do|task\s+tak|task\s+add\s+kar|kaam\s+add\s+kar|task\s+daalo|task\s+jodo|टास्क\s*टाक|टास्क\s*जोड|कार्य\s*जोड़ो)[:\s\-]*"""),
            ""
        ).replace(Regex("""(?i)\s*(task\s+tak|task\s+add\s+kar|task\s+daalo|टास्क\s*टाक|टास्क\s*जोड|कार्य\s*जोड़ो)$"""), "").trim()
    }

    private fun defaultReminderTitle(dialect: ConversationDialect): String = when (dialect) {
        ConversationDialect.MARATHI_DEVANAGARI -> "SJ असिस्टंट रिमाइंडर"
        ConversationDialect.MARATHISH_ROMAN -> "SJ Assistant Reminder"
        ConversationDialect.HINDI_DEVANAGARI -> "SJ असिस्टेंट रिमाइंडर"
        ConversationDialect.HINGLISH_ROMAN -> "SJ Assistant Reminder"
        ConversationDialect.ENGLISH -> "SJ Assistant Reminder"
    }
}
