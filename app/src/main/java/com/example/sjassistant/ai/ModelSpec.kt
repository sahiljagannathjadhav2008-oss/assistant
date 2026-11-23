package com.example.sjassistant.ai

data class ModelSpec(
    val id: String,
    val displayName: String,
    val parametersLabel: String,
    val quantization: String,
    val fileSizeMb: Int,
    val estimatedRamMb: Int,
    val nativeContextWindow: Int,
    val mobileContextWindow: Int,
    val supportsVision: Boolean,
    val minDeviceRamGb: Int,
    val fitsSpark7t: Boolean,
    val spark7tVerdict: String,
    val notes: String
)

object ModelCatalog {
    const val BUILTIN_NLU_RAM_MB = 65
    const val BUILTIN_CONTEXT_CHAR_LIMIT = 900
    const val BUILTIN_DOC_CHUNK_CHARS = 360
    const val BUILTIN_DOC_MAX_CHARS = 120_000

    val recommendedModels = listOf(
        ModelSpec(
            id = "gemma-3-270m-q4_k_m",
            displayName = "Gemma 3 270M Instruct",
            parametersLabel = "0.27B (270M params)",
            quantization = "GGUF Q4_K_M",
            fileSizeMb = 240,
            estimatedRamMb = 580,
            nativeContextWindow = 32768,
            mobileContextWindow = 1024,
            supportsVision = false,
            minDeviceRamGb = 3,
            fitsSpark7t = true,
            spark7tVerdict = "Recommended for Tecno Spark 7T",
            notes = "Text-only Gemma 3 family (140+ language training). Runs comfortably on 4GB RAM Helio G35."
        ),
        ModelSpec(
            id = "qwen2.5-0.5b-q4_k_m",
            displayName = "Qwen2.5 0.5B Instruct",
            parametersLabel = "0.5B (494M params)",
            quantization = "GGUF Q4_K_M",
            fileSizeMb = 365,
            estimatedRamMb = 440,
            nativeContextWindow = 32768,
            mobileContextWindow = 1024,
            supportsVision = false,
            minDeviceRamGb = 2,
            fitsSpark7t = true,
            spark7tVerdict = "Best <500MB RAM Balance",
            notes = "Strong Marathi/Hindi/English tokenization under 500MB RAM on 2GB–4GB phones."
        ),
        ModelSpec(
            id = "smollm2-360m-q4_k_m",
            displayName = "SmolLM2 360M Instruct",
            parametersLabel = "0.36B (360M params)",
            quantization = "GGUF Q4_K_M",
            fileSizeMb = 250,
            estimatedRamMb = 310,
            nativeContextWindow = 8192,
            mobileContextWindow = 512,
            supportsVision = false,
            minDeviceRamGb = 2,
            fitsSpark7t = true,
            spark7tVerdict = "Fastest on Helio G35",
            notes = "Fastest token speed on entry-level quad/octa Cortex-A53 CPUs."
        ),
        ModelSpec(
            id = "gemma-3-1b-it-q4_k_m",
            displayName = "Gemma 3 1B Instruct",
            parametersLabel = "1.0B params",
            quantization = "GGUF Q4_K_M",
            fileSizeMb = 650,
            estimatedRamMb = 1250,
            nativeContextWindow = 32768,
            mobileContextWindow = 512,
            supportsVision = false,
            minDeviceRamGb = 4,
            fitsSpark7t = true,
            spark7tVerdict = "Max Ceiling on Spark 7T (Text-Only)",
            notes = "140+ languages; practical 1B max limit on 4GB RAM (keep other apps closed & 512 context)."
        ),
        ModelSpec(
            id = "gemma-3-4b-it-q4_k_m",
            displayName = "Gemma 3 4B Multimodal",
            parametersLabel = "4.0B params (Text + Vision)",
            quantization = "GGUF Q4_K_M",
            fileSizeMb = 2500,
            estimatedRamMb = 3800,
            nativeContextWindow = 131072,
            mobileContextWindow = 1024,
            supportsVision = true,
            minDeviceRamGb = 8,
            fitsSpark7t = false,
            spark7tVerdict = "Too Heavy for Spark 7T (Needs 8GB+ RAM)",
            notes = "Supports 140+ languages & native image vision, but ~3.8GB RAM requirement crashes 4GB phones. Use built-in ML Kit Vision for photos on Spark 7T."
        )
    )
}
