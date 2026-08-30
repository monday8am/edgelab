package com.monday8am.edgelab.core.inference

/** A tool call recovered from a model's raw text, with arguments normalised to JSON. */
internal data class TextualToolCall(val name: String, val argumentsJson: String)

/**
 * How to recover tool calls that a model emitted as plain text instead of a structured `tool_calls`
 * block.
 *
 * litert-lm parses tool calls natively for the model families it has configuration for, so the
 * default is [RuntimeHandled] — do nothing. A dialect exists only for families the runtime cannot
 * parse yet.
 */
internal interface ToolCallDialect {
    /** Tool calls hiding in [raw]; empty when the runtime already handled them. */
    fun recover(raw: String): List<TextualToolCall>

    /** Removes the dialect's markup so it can never reach the UI. */
    fun strip(raw: String): String
}

/** Trusts litert-lm to parse tool calls itself. Correct for every family the runtime supports. */
internal object RuntimeHandled : ToolCallDialect {
    override fun recover(raw: String): List<TextualToolCall> = emptyList()

    override fun strip(raw: String): String = raw
}

/**
 * Picks a dialect for [modelFamily] as reported by `ModelConfiguration`.
 *
 * Only LFM2 needs one: litert-lm ships an `Lfm2DataProcessor` that renders the chat template but
 * carries no tool-call configuration, and its native library contains none of LFM2's tool tokens
 * (verified against 0.16.1 and 0.17.0-alpha1). Revisit when upstream adds it.
 */
internal fun toolCallDialectFor(modelFamily: String): ToolCallDialect =
    if (modelFamily.startsWith("lfm", ignoreCase = true)) Lfm2ToolCallDialect else RuntimeHandled
