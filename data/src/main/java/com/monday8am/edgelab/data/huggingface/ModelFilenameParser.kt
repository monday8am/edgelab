package com.monday8am.edgelab.data.huggingface

/**
 * Parses model filenames to extract metadata.
 *
 * Observed filename patterns:
 * - Qwen3-0.6B.litertlm
 * - Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm
 * - gemma3-1b-it-int4.litertlm
 * - Gemma3-1B-IT_q4_ekv1280_sm8650.litertlm (device-specific - skip)
 */
internal object ModelFilenameParser {

    /** Metadata extracted from a model filename. */
    data class ParsedMetadata(
        val modelFamily: String,
        val parameterCount: Float?,
        val quantization: String,
        val contextLength: Int?,
        val displayName: String,
    )

    // Regex patterns for metadata extraction
    private val PARAM_REGEX_B = Regex("""(\d+\.?\d*)B""", RegexOption.IGNORE_CASE)
    private val PARAM_REGEX_M = Regex("""(\d+\.?\d*)M""", RegexOption.IGNORE_CASE)
    private val QUANT_REGEX = Regex("""[._-](q\d+|int\d+|f32|f16|fp16)""", RegexOption.IGNORE_CASE)
    private val CONTEXT_REGEX = Regex("""ekv(\d+)""", RegexOption.IGNORE_CASE)

    // Device suffixes to filter out (hardware-specific builds)
    private val DEVICE_SUFFIXES = listOf("mt6989", "mt6991", "mt6993", "sm8550", "sm8650", "sm8750")

    // Valid model file extension
    private const val VALID_EXTENSION = ".litertlm"

    /**
     * Parses a filename and returns extracted metadata.
     *
     * @param filename The model file name (e.g., "Gemma3-1B-IT_q4_ekv4096.litertlm")
     * @param modelId The full Hugging Face model ID (e.g., "litert-community/Gemma3-1B-IT")
     * @return Parsed metadata, or null if the file should be skipped
     */
    fun parse(filename: String, modelId: String): ParsedMetadata? {
        // Skip non-LiteRT model files
        if (!filename.endsWith(VALID_EXTENSION, ignoreCase = true)) {
            return null
        }

        // Skip device-specific variants
        if (DEVICE_SUFFIXES.any { filename.contains(it, ignoreCase = true) }) {
            return null
        }

        val baseName = filename.substringBeforeLast(".")

        // Extract parameters from filename or modelId
        val params = extractParameters(baseName) ?: extractParameters(modelId)

        // Extract quantization
        val quant = extractQuantization(baseName) ?: "unknown"

        // Extract context length from filename
        val context = extractContextLength(baseName)

        // Extract family from modelId (more reliable than filename)
        val family = extractFamily(modelId)

        // Generate human-readable display name
        val displayName = buildDisplayName(family, params)

        return ParsedMetadata(
            modelFamily = family,
            parameterCount = params,
            quantization = quant,
            contextLength = context,
            displayName = displayName,
        )
    }

    /**
     * Extracts parameter count (in billions) from text. Handles both "B" (billions) and "M"
     * (millions) suffixes.
     */
    private fun extractParameters(text: String): Float? {
        // Try to match billions first
        PARAM_REGEX_B.find(text)?.groupValues?.get(1)?.toFloatOrNull()?.let {
            return it
        }
        // Try to match millions and convert to billions
        PARAM_REGEX_M.find(text)?.groupValues?.get(1)?.toFloatOrNull()?.let {
            return it / 1000f
        }
        return null
    }

    /** Extracts quantization format from filename. */
    private fun extractQuantization(baseName: String): String? {
        return QUANT_REGEX.find(baseName)?.groupValues?.get(1)?.lowercase()
    }

    /** Extracts context/KV cache length from filename. */
    private fun extractContextLength(baseName: String): Int? {
        return CONTEXT_REGEX.find(baseName)?.groupValues?.get(1)?.toIntOrNull()
    }

    private val FAMILY_PREFIXES =
        listOf(
            "qwen3",
            "qwen2.5",
            "qwen2",
            "gemma3",
            "gemma2",
            "gemma",
            "smollm",
            "smolvlm",
            "hammer",
            "tinyllama",
            "phi",
            "deepseek",
            "fastvlm",
            "functiongemma",
            // Listed explicitly so tool-call dialect selection keys off a deliberate value; the
            // fallback below would also yield "lfm2.5", but only by accident of the repo name.
            "lfm2",
        )

    /** Extracts model family from the Hugging Face model ID. */
    private fun extractFamily(modelId: String): String {
        val name = modelId.substringAfter("/").lowercase()
        return FAMILY_PREFIXES.find { name.startsWith(it) }
            ?: name.substringBefore("-").substringBefore("_")
    }

    /** Builds a human-readable display name from parsed metadata. */
    private fun buildDisplayName(family: String, params: Float?): String {
        val familyDisplay = family.replaceFirstChar { it.uppercase() }
        val paramsDisplay = params?.let { formatParams(it) } ?: ""

        val parts = mutableListOf<String>()
        parts.add(familyDisplay)
        if (paramsDisplay.isNotEmpty()) parts.add(paramsDisplay)

        return parts.joinToString("-")
    }

    /** Formats parameter count for display. */
    private fun formatParams(params: Float): String {
        return if (params == params.toInt().toFloat()) {
            "${params.toInt()}B"
        } else {
            "${params}B"
        }
    }

    /** Formats context length for display. */
    private fun formatContext(context: Int): String {
        return when {
            context >= 1024 && context % 1024 == 0 -> "${context / 1024}K"
            context >= 1000 -> "${context / 1000}K"
            else -> context.toString()
        }
    }
}
