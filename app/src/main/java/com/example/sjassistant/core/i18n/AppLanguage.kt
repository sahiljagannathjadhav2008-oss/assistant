package com.example.sjassistant.core.i18n

import java.util.Locale

enum class AppLanguage(
    val code: String,
    val nativeLabel: String,
    val speechLocaleTag: String,
    val ttsLocale: Locale
) {
    MARATHI(
        code = "mr",
        nativeLabel = "मराठी",
        speechLocaleTag = "mr-IN",
        ttsLocale = Locale("mr", "IN")
    ),
    HINDI(
        code = "hi",
        nativeLabel = "हिन्दी",
        speechLocaleTag = "hi-IN",
        ttsLocale = Locale("hi", "IN")
    ),
    ENGLISH(
        code = "en",
        nativeLabel = "English",
        speechLocaleTag = "en-IN",
        ttsLocale = Locale("en", "IN")
    );

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return when {
                code.equals("mr-en", ignoreCase = true) -> MARATHI
                code.equals("hi-en", ignoreCase = true) -> HINDI
                else -> entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MARATHI
            }
        }
    }
}
