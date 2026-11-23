package com.example

import com.example.sjassistant.ai.AssistantIntent
import com.example.sjassistant.ai.CalculatorEngine
import com.example.sjassistant.ai.GgufHeaderParser
import com.example.sjassistant.ai.MultilingualNluParser
import com.example.sjassistant.ai.OfflineKnowledgeBase
import com.example.sjassistant.core.i18n.AppLanguage
import com.example.sjassistant.core.i18n.ConversationDialect
import com.example.sjassistant.core.i18n.LanguageDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SjAssistantEngineTest {

    @Test
    fun languageDetector_autoDetectsMarathiMarathishHindiHinglishAndEnglish() {
        val mrDeva = LanguageDetector.detect("मला हवामान सांग")
        assertEquals(AppLanguage.MARATHI, mrDeva.primaryLanguage)
        assertEquals(ConversationDialect.MARATHI_DEVANAGARI, mrDeva.dialect)

        val marathish = LanguageDetector.detect("yala hinglish shikav ani yechi size parameter context window kiti aahe sang")
        assertEquals(AppLanguage.MARATHI, marathish.primaryLanguage)
        assertEquals(ConversationDialect.MARATHISH_ROMAN, marathish.dialect)
        assertTrue(marathish.isMixedLanguage)

        val hinglish = LanguageDetector.detect("Kal ka reminder laga do")
        assertEquals(AppLanguage.HINDI, hinglish.primaryLanguage)
        assertEquals(ConversationDialect.HINGLISH_ROMAN, hinglish.dialect)
        assertTrue(hinglish.isMixedLanguage)

        val hiDeva = LanguageDetector.detect("मुझे बैटरी की स्थिति बताओ")
        assertEquals(AppLanguage.HINDI, hiDeva.primaryLanguage)
        assertEquals(ConversationDialect.HINDI_DEVANAGARI, hiDeva.dialect)

        val en = LanguageDetector.detect("Open notes")
        assertEquals(AppLanguage.ENGLISH, en.primaryLanguage)
        assertEquals(ConversationDialect.ENGLISH, en.dialect)
    }

    @Test
    fun calculatorEngine_handlesDevanagariMarathishHinglishAndPercentages() {
        val res1 = CalculatorEngine.tryEvaluate("२५ अधिक १५")
        assertNotNull(res1)
        assertEquals(40.0, res1!!.numericValue, 0.001)

        val res2 = CalculatorEngine.tryEvaluate("250 का 15%")
        assertNotNull(res2)
        assertEquals(37.5, res2!!.numericValue, 0.001)

        val res3 = CalculatorEngine.tryEvaluate("250 adhik 15")
        assertNotNull(res3)
        assertEquals(265.0, res3!!.numericValue, 0.001)

        val res4 = CalculatorEngine.tryEvaluate("(250 + 15%) * 2")
        assertNotNull(res4)
        assertEquals(575.0, res4!!.numericValue, 0.001)
    }

    @Test
    fun nluParser_routesMarathishHinglishAndSpecsIntents() {
        val remHinglish = MultilingualNluParser.parse("Kal ka reminder laga do", ConversationDialect.HINGLISH_ROMAN)
        assertTrue(remHinglish is AssistantIntent.CreateReminder)

        val remMarathish = MultilingualNluParser.parse("10 minitanni pani pinyacha reminder lav", ConversationDialect.MARATHISH_ROMAN)
        assertTrue(remMarathish is AssistantIntent.CreateReminder)

        val specs = MultilingualNluParser.parse("yechi size parameter context window kiti aahe", ConversationDialect.MARATHISH_ROMAN)
        assertTrue(specs is AssistantIntent.AiSpecsQuery)

        val spark = MultilingualNluParser.parse("tecno spark 7t madhe kiti billion gemma 3 chalel", ConversationDialect.MARATHISH_ROMAN)
        assertTrue(spark is AssistantIntent.Spark7tAndGemmaInfoQuery)

        val photoQa = MultilingualNluParser.parse(
            "ya photo madhe kay lihila aahe?",
            ConversationDialect.MARATHISH_ROMAN,
            hasAttachedPhoto = true
        )
        assertTrue(photoQa is AssistantIntent.PhotoQuestion)

        val nav = MultilingualNluParser.parse("Open notes", ConversationDialect.ENGLISH)
        assertTrue(nav is AssistantIntent.NavigateTab)

        val note = MultilingualNluParser.parse("नोट सेव्ह कर: उद्या बाजार", ConversationDialect.MARATHI_DEVANAGARI)
        assertTrue(note is AssistantIntent.CreateNote)

        val torch = MultilingualNluParser.parse("torch chalu kar", ConversationDialect.MARATHISH_ROMAN)
        assertTrue(torch is AssistantIntent.ToggleFlashlight)
        assertTrue((torch as AssistantIntent.ToggleFlashlight).turnOn)
    }

    @Test
    fun ggufHeaderParser_validatesBinaryHeader() {
        val validBuf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        validBuf.putInt(0x46554747) // GGUF
        validBuf.putInt(3)          // version 3
        validBuf.putLong(196L)      // tensorCount
        validBuf.putLong(28L)       // kvCount

        val parsed = GgufHeaderParser.parseHeader(
            ByteArrayInputStream(validBuf.array()),
            "qwen2.5-0.5b-q4_k_m.gguf",
            365L * 1024L * 1024L
        )
        assertTrue(parsed.isValidGguf)
        assertEquals(3, parsed.ggufVersion)
        assertEquals(196L, parsed.tensorCount)

        val badBuf = ByteArray(24) { 0x11 }
        val rejected = GgufHeaderParser.parseHeader(
            ByteArrayInputStream(badBuf),
            "invalid.bin",
            1024L
        )
        assertFalse(rejected.isValidGguf)
    }

    @Test
    fun offlineKnowledgeBase_retrievesMultilingualTopics() {
        val mrHits = OfflineKnowledgeBase.search("मला हवामान सांग")
        assertTrue(mrHits.isNotEmpty())
        assertEquals("kb_weather_offline", mrHits.first().id)

        val hiHits = OfflineKnowledgeBase.search("बुखार प्राथमिक उपचार")
        assertTrue(hiHits.isNotEmpty())
        assertEquals("kb_first_aid_heat", hiHits.first().id)
    }
}
