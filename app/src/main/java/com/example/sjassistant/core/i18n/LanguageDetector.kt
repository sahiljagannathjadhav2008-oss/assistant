package com.example.sjassistant.core.i18n

enum class ConversationDialect(
    val code: String,
    val badgeLabel: String,
    val speechLanguage: AppLanguage
) {
    MARATHI_DEVANAGARI("mr", "मराठी", AppLanguage.MARATHI),
    MARATHISH_ROMAN("mr-en", "Marathish", AppLanguage.MARATHI),
    HINDI_DEVANAGARI("hi", "हिन्दी", AppLanguage.HINDI),
    HINGLISH_ROMAN("hi-en", "Hinglish", AppLanguage.HINDI),
    ENGLISH("en", "English", AppLanguage.ENGLISH);

    companion object {
        fun fromCode(code: String?): ConversationDialect {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

data class LanguageDetectionResult(
    val primaryLanguage: AppLanguage,
    val dialect: ConversationDialect,
    val isMixedLanguage: Boolean,
    val detectedScript: String,
    val confidence: Float
)

/**
 * Zero-dependency, ultra-fast (<1ms) automatic language & code-switching detector for:
 * - Marathi (Devanagari)
 * - Marathish / Minglish (Marathi + English in Roman script, e.g. "mala battery sang")
 * - Hindi (Devanagari)
 * - Hinglish (Hindi + English in Roman script, e.g. "kal ka reminder laga do")
 * - English
 */
object LanguageDetector {

    private val marathiDevanagariMarkers = setOf(
        "मला", "माझं", "माझी", "माझे", "तुला", "तुझं", "आहे", "आहेत", "नाही", "नाहीत",
        "सांग", "सांगा", "करा", "कर", "करणं", "उघड", "उघडा", "बंद", "ठेव", "ठेवा",
        "उद्या", "आज", "सकाळी", "संध्याकाळी", "रात्री", "दुपारी", "वाजता", "मिनिटात",
        "कसं", "कसे", "कशी", "काय", "कोण", "कुठे", "केव्हा", "किती", "का",
        "हवामान", "आठवण", "नोंद", "टीप", "टास्क", "यादी", "मदत", "धन्यवाद",
        "नमस्कार", "हो", "पाहिजे", "दे", "घे", "बघ", "वाच", "चालू", "सुरू",
        "अधिक", "वजा", "गुणिले", "भागिले", "टक्के", "फाइल", "अ‍ॅप", "आणि", "पण"
    )

    private val hindiDevanagariMarkers = setOf(
        "मुझे", "मेरा", "मेरी", "मेरे", "तुम्हें", "है", "हैं", "नहीं", "नही",
        "बताओ", "बताइए", "करो", "करें", "खोलो", "खोलें", "बंद", "रखो", "लगाओ",
        "कल", "आज", "सुबह", "शाम", "रात", "दोपहर", "बजे", "मिनट",
        "कैसे", "कैसा", "क्या", "कौन", "कहाँ", "कब", "कितना", "कितने", "क्यों",
        "मौसम", "रिमाइंडर", "याद", "नोट", "टास्क", "लिस्ट", "मदद", "धन्यवाद",
        "नमस्ते", "हाँ", "चाहिए", "दो", "लो", "देखो", "पढ़ो", "चालू", "शुरू",
        "जमा", "घटा", "गुणा", "भाग", "प्रतिशत", "फाइल", "ऐप", "और", "लेकिन"
    )

    // Strong Romanized Marathi ("Marathish" / Marathi + English) markers
    private val marathishMarkers = setOf(
        "mala", "maza", "mazha", "majha", "majhi", "mazi", "maze", "majhe",
        "tula", "tuza", "tuzya", "tuzyashi", "tujhya", "tumhi", "amhi", "aapla",
        "aahe", "ahe", "aahet", "ahet", "nahi", "nahit", "nako", "hoy", "hota", "hoti",
        "sang", "sanga", "kar", "kara", "karto", "karte", "karaycha", "karayche", "karavi",
        "ughad", "ughada", "thev", "theva", "lav", "lava", "tak", "taka", "lihi", "liha",
        "udya", "sakali", "sandhyakali", "ratri", "dupari", "vajta", "atta", "ata",
        "kasa", "kase", "kashi", "kay", "kuthe", "kuthla", "kevha", "kiti", "kontya",
        "havaman", "aathvan", "athvan", "namaskar", "pahije", "bagh", "bagha",
        "vach", "vacha", "dakhav", "dakhva", "chalu", "band", "shodh", "shikav",
        "adhik", "vaja", "gunile", "bhagile", "takke", "chya", "cha", "chi", "che",
        "madhe", "madhye", "sathi", "hun", "pan", "ani", "mhanje", "mhange", "tashich",
        "tyane", "swata", "swatahun", "bhasha", "bhashet", "bolat", "olkhun", "uttare",
        "dyayla", "ase", "yala", "yachi", "yechi", "tyala", "tyachi", "asle", "aste",
        "kaam", "pani", "paise", "vel", "minit", "minitanni", "taas", "tasane", "ghari"
    )

    // Strong Romanized Hindi ("Hinglish" / Hindi + English) markers
    private val hinglishMarkers = setOf(
        "mujhe", "mera", "meri", "mere", "tujhe", "tera", "teri", "tum", "tumhe", "aap",
        "hai", "hain", "nahin", "nahi", "haan", "tha", "thi", "the", "hoga", "hogi", "honge",
        "bata", "batao", "bataiye", "karo", "karna", "kardo", "karlo", "kholo", "khol",
        "rakho", "rakh", "lagao", "laga", "daalo", "likho", "banao", "dikhao", "samjhao",
        "sikhao", "seekho", "sunao", "dekho", "padho", "chalu", "band", "chahiye",
        "kal", "aaj", "subah", "shaam", "raat", "dopahar", "baje", "abhi", "baad", "pehle",
        "mein", "me", "ka", "ki", "ke", "ko", "se", "par", "pe", "aur", "lekin", "magar",
        "kaise", "kaisa", "kaisi", "kya", "kaun", "kahan", "kab", "kitna", "kitni", "kitne",
        "kyun", "kyu", "mausam", "yaad", "namaste", "jama", "ghata", "guna", "bhaag",
        "pratishat", "wala", "wali", "wale", "matlab", "jaise", "apne", "khud", "pehchan",
        "jawab", "kitne", "bhasha", "samay", "ghante", "dawai", "paani", "paisa", "kuch"
    )

    private val englishMarkers = setOf(
        "open", "launch", "start", "create", "add", "set", "remind", "reminder",
        "note", "notes", "todo", "task", "tasks", "search", "find", "file",
        "files", "document", "what", "how", "when", "where", "who", "why", "which",
        "calculate", "convert", "weather", "battery", "torch", "flashlight",
        "wifi", "bluetooth", "alarm", "hello", "hi", "help", "summary", "size",
        "parameter", "parameters", "context", "window", "model", "ram", "memory",
        "summarize", "read", "show", "delete", "please", "thanks", "thank", "tell",
        "check", "status", "save", "language", "auto", "detect", "your", "my", "the",
        "is", "are", "for", "with", "this", "that", "from", "about", "in", "on", "to"
    )

    fun detect(input: String, fallback: AppLanguage = AppLanguage.ENGLISH): LanguageDetectionResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            val defaultDialect = when (fallback) {
                AppLanguage.MARATHI -> ConversationDialect.MARATHI_DEVANAGARI
                AppLanguage.HINDI -> ConversationDialect.HINDI_DEVANAGARI
                AppLanguage.ENGLISH -> ConversationDialect.ENGLISH
            }
            return LanguageDetectionResult(fallback, defaultDialect, false, "NONE", 0.5f)
        }

        var devanagariCount = 0
        var latinCount = 0
        var hasMarathiSpecificChars = false

        for (ch in trimmed) {
            when (ch.code) {
                in 0x0900..0x097F -> {
                    devanagariCount++
                    if (ch == 'ळ' || ch == 'ऱ' || ch == 'ॲ' || ch == 'ऑ') {
                        hasMarathiSpecificChars = true
                    }
                }
                in 'a'.code..'z'.code, in 'A'.code..'Z'.code -> latinCount++
            }
        }

        val tokens = trimmed
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        var mrDevaScore = if (hasMarathiSpecificChars) 4.0f else 0f
        var hiDevaScore = 0f
        var marathishScore = 0f
        var hinglishScore = 0f
        var enScore = 0f

        for (token in tokens) {
            if (token in marathiDevanagariMarkers) mrDevaScore += 2.4f
            if (token in hindiDevanagariMarkers) hiDevaScore += 2.4f

            if (token in marathishMarkers) marathishScore += 2.4f
            if (token in hinglishMarkers) hinglishScore += 2.2f
            if (token in englishMarkers) enScore += 0.9f

            // Marathi Devanagari morphological cues
            if (devanagariCount > 0) {
                if (token.endsWith("च") || token.endsWith("ला") || token.endsWith("ची") ||
                    token.endsWith("चे") || token.endsWith("चं") || token.endsWith("तो") ||
                    token.endsWith("ते") || token.endsWith("तात") || token.endsWith("णार") ||
                    token.endsWith("मध्ये") || token.endsWith("साठी")
                ) {
                    mrDevaScore += 0.7f
                }
                if (token.endsWith("ता") || token.endsWith("ती") || token.endsWith("ना") ||
                    token.endsWith("ने") || token.endsWith("िए") || token.endsWith("ओं") ||
                    token.endsWith("में")
                ) {
                    hiDevaScore += 0.5f
                }
            }

            // Romanized Marathish vs Hinglish morphological suffixes (only for Roman words >= 4 chars)
            if (latinCount > 0 && token.length >= 4 && token !in englishMarkers) {
                if (token.endsWith("aycha") || token.endsWith("ayche") || token.endsWith("aychi") ||
                    token.endsWith("ayla") || token.endsWith("madhe") || token.endsWith("sathi") ||
                    token.endsWith("chya") || token.endsWith("tanni") || token.endsWith("hun")
                ) {
                    marathishScore += 1.5f
                }
                if (token.endsWith("wala") || token.endsWith("wali") || token.endsWith("wale") ||
                    token.endsWith("enge") || token.endsWith("unga") || token.endsWith("iye")
                ) {
                    hinglishScore += 1.3f
                }
            }
        }

        // Script + dialect decision
        val dialect: ConversationDialect
        val primaryLang: AppLanguage

        if (devanagariCount > 0 && devanagariCount >= latinCount / 2) {
            if (mrDevaScore >= hiDevaScore && (mrDevaScore > 0f || fallback == AppLanguage.MARATHI)) {
                dialect = ConversationDialect.MARATHI_DEVANAGARI
                primaryLang = AppLanguage.MARATHI
            } else {
                dialect = ConversationDialect.HINDI_DEVANAGARI
                primaryLang = AppLanguage.HINDI
            }
        } else {
            // Primarily Latin script: distinguish Marathish vs Hinglish vs pure English
            when {
                marathishScore > 0f && marathishScore >= hinglishScore -> {
                    dialect = ConversationDialect.MARATHISH_ROMAN
                    primaryLang = AppLanguage.MARATHI
                }
                hinglishScore > 0f && hinglishScore > marathishScore -> {
                    dialect = ConversationDialect.HINGLISH_ROMAN
                    primaryLang = AppLanguage.HINDI
                }
                else -> {
                    dialect = ConversationDialect.ENGLISH
                    primaryLang = AppLanguage.ENGLISH
                }
            }
        }

        val isMixed = (devanagariCount > 0 && latinCount > 0) ||
            dialect == ConversationDialect.MARATHISH_ROMAN ||
            dialect == ConversationDialect.HINGLISH_ROMAN

        val scriptLabel = when {
            devanagariCount > 0 && latinCount > 0 -> "MIXED_DEVANAGARI_LATIN"
            dialect == ConversationDialect.MARATHISH_ROMAN -> "ROMAN_MARATHISH"
            dialect == ConversationDialect.HINGLISH_ROMAN -> "ROMAN_HINGLISH"
            devanagariCount > 0 -> "DEVANAGARI"
            latinCount > 0 -> "LATIN"
            else -> "NUMERIC"
        }

        val topScore = maxOf(mrDevaScore, hiDevaScore, marathishScore, hinglishScore, enScore, 1f)
        val sumScore = (mrDevaScore + hiDevaScore + marathishScore + hinglishScore + enScore).coerceAtLeast(1f)
        val confidence = (topScore / sumScore).coerceIn(0.60f, 0.99f)

        return LanguageDetectionResult(
            primaryLanguage = primaryLang,
            dialect = dialect,
            isMixedLanguage = isMixed,
            detectedScript = scriptLabel,
            confidence = confidence
        )
    }
}
