package com.monday8am.edgelab.data.model

@kotlinx.serialization.Serializable
enum class HardwareBackend {
    GPU_SUPPORTED,
    NPU_SUPPORTED,
}

/**
 * Complete configuration for an on-device language model. This is platform-agnostic metadata that
 * describes what the model is and how to obtain it.
 *
 * @property modelId Unique identifier (e.g., "qwen3-0.6b-litert-q8-4k")
 * @property displayName Human-readable name (e.g., "Qwen3 0.6B (LiteRT, 4K context)")
 * @property modelFamily Base model family (e.g., "qwen3", "gemma3", "hammer2")
 * @property parameterCount Model parameters in billions (e.g., 0.6, 1.0, 0.5)
 * @property quantization Quantization format (e.g., "int8", "int4", "fp16")
 * @property contextLength Maximum context window in tokens (e.g., 4096, 1024)
 * @property downloadUrl Remote URL for model download
 * @property bundleFilename Expected filename after download (e.g., "model.litertlm")
 * @property hardwareAcceleration Supported hardware acceleration
 * @property defaultTopK Default top-K sampling parameter
 * @property defaultTopP Default top-P sampling parameter
 * @property defaultTemperature Default temperature for generation
 * @property defaultMaxOutputTokens Default max tokens to generate
 * @property isGated Whether the model requires Hugging Face authentication
 * @property description Short description of the model from HuggingFace (optional)
 * @property fileSizeBytes Download file size in bytes (optional)
 * @property huggingFaceUrl Link to the HuggingFace model page (optional)
 */
@kotlinx.serialization.Serializable
data class ModelConfiguration(
    val displayName: String,
    val modelFamily: String,
    val parameterCount: Float,
    val quantization: String,
    val contextLength: Int,
    val downloadUrl: String,
    val bundleFilename: String,
    val hardwareAcceleration: HardwareBackend = HardwareBackend.GPU_SUPPORTED,
    val defaultTopK: Int = 40,
    val defaultTopP: Float = 0.85f,
    val defaultTemperature: Float = 0.2f,
    val defaultMaxOutputTokens: Int = (contextLength * 0.25).toInt(),
    val author: String? = null,
    val isGated: Boolean = false,
    val description: String? = null,
    val fileSizeBytes: Long? = null,
    val huggingFaceUrl: String? = null,
) {
    /**
     * Model identifier without file extension (used by NotificationAgent). Examples:
     * "qwen3_0.6b_q8_ekv4096", "gemma3-1b-it-int4"
     */
    val modelId: String
        get() = bundleFilename

    /** Helper to format file size into human readable string. */
    val readableFileSize: String?
        get() = fileSizeBytes?.let { bytes ->
            if (bytes >= 1024 * 1024 * 1024) {
                val sizeGB = bytes / (1024.0 * 1024.0 * 1024.0)
                "Size: ${"%.2f".format(sizeGB)} GB"
            } else {
                val sizeMB = bytes / (1024 * 1024)
                "Size: $sizeMB MB"
            }
        }
}
