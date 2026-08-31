package com.monday8am.edgelab.core.inference

internal data class TextualToolCall(val name: String, val argumentsJson: String)

internal interface ToolCallDialect {
    fun recover(raw: String): List<TextualToolCall>

    fun strip(raw: String): String
}

internal object RuntimeHandled : ToolCallDialect {
    override fun recover(raw: String): List<TextualToolCall> = emptyList()

    override fun strip(raw: String): String = raw
}

// litert-lm parses tool calls natively for every family it has config for. LFM2 is the exception:
// its native lib carries none of LFM2's tool tokens (checked in 0.16.1 and 0.17.0-alpha1).
internal fun toolCallDialectFor(modelFamily: String): ToolCallDialect =
    if (modelFamily.startsWith("lfm", ignoreCase = true)) Lfm2ToolCallDialect else RuntimeHandled
