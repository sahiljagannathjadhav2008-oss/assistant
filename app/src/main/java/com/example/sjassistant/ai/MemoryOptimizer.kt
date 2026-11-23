package com.example.sjassistant.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import com.example.sjassistant.data.local.ChatMessageEntity

data class MemorySnapshot(
    val appHeapUsedMb: Int,
    val nativeUsedMb: Int,
    val totalAppRamMb: Int,
    val systemAvailableMb: Int,
    val systemTotalMb: Int,
    val isLowRamDevice: Boolean,
    val within500MbTarget: Boolean
)

/**
 * Monitors live app & device RAM and compresses chat history to fit a strict
 * <= 512-token context budget for 2GB-4GB RAM Android phones.
 */
object MemoryOptimizer {
    const val TARGET_MAX_APP_RAM_MB = 500
    const val MAX_COMPRESSED_CHARS = 900

    fun snapshot(context: Context): MemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val runtime = Runtime.getRuntime()
        val javaHeapMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()
        val nativeHeapMb = (Debug.getNativeHeapAllocatedSize() / (1024 * 1024)).toInt()
        val totalAppMb = (javaHeapMb + nativeHeapMb).coerceAtLeast(1)

        val availMb = (memInfo.availMem / (1024 * 1024)).toInt()
        val totalSysMb = (memInfo.totalMem / (1024 * 1024)).toInt()

        return MemorySnapshot(
            appHeapUsedMb = javaHeapMb,
            nativeUsedMb = nativeHeapMb,
            totalAppRamMb = totalAppMb,
            systemAvailableMb = availMb,
            systemTotalMb = totalSysMb,
            isLowRamDevice = am.isLowRamDevice || totalSysMb <= 3200,
            within500MbTarget = totalAppMb < TARGET_MAX_APP_RAM_MB
        )
    }

    /**
     * Compresses conversation history by preserving the latest 3 turns verbatim
     * and summarizing earlier turns into compact key phrases to save KV-cache RAM.
     */
    fun compressContext(history: List<ChatMessageEntity>, enabled: Boolean): String {
        if (history.isEmpty()) return ""
        if (!enabled) {
            return history.takeLast(6).joinToString("\n") { "${it.role}: ${it.content}" }
        }
        val recent = history.takeLast(3)
        val older = history.dropLast(3)

        val olderSummary = if (older.isNotEmpty()) {
            val topics = older
                .filter { it.role == "USER" }
                .takeLast(4)
                .joinToString("; ") { it.content.take(36).replace("\n", " ") }
            "[Earlier topics: $topics]\n"
        } else ""

        val recentBlock = recent.joinToString("\n") { msg ->
            "${msg.role}: ${msg.content.take(180)}"
        }
        return (olderSummary + recentBlock).take(MAX_COMPRESSED_CHARS)
    }
}
