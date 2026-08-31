package com.monday8am.edgelab.core.inference

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val TOOL_CALL_BLOCK =
    Regex("""<\|tool_call_start\|>(.*?)<\|tool_call_end\|>""", RegexOption.DOT_MATCHES_ALL)

private val CALL_SIGNATURE =
    Regex("""^([A-Za-z_][A-Za-z0-9_.]*)\s*\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)

// LFM2 emits `<|tool_call_start|>[get_weather(city="Madrid")]<|tool_call_end|>`. The payload is
// Pythonic, not JSON — `True`, `None` and single quotes are all legal — hence the literal parser.
internal object Lfm2ToolCallDialect : ToolCallDialect {
    override fun recover(raw: String): List<TextualToolCall> =
        TOOL_CALL_BLOCK.findAll(raw)
            .flatMap { match -> parseCallList(match.groupValues[1].trim()).asSequence() }
            .toList()

    override fun strip(raw: String): String = TOOL_CALL_BLOCK.replace(raw, "").trim()
}

private fun parseCallList(block: String): List<TextualToolCall> {
    val body =
        if (block.startsWith("[") && block.endsWith("]")) block.substring(1, block.length - 1)
        else block
    return splitTopLevel(body).mapNotNull(::parseCall)
}

private fun parseCall(text: String): TextualToolCall? {
    val match = CALL_SIGNATURE.matchEntire(text.trim()) ?: return null
    val name = match.groupValues[1]
    val args = parseArguments(match.groupValues[2])
    return TextualToolCall(name = name, argumentsJson = JsonObject(args).toString())
}

private fun parseArguments(inner: String): Map<String, JsonElement> =
    splitTopLevel(inner)
        .mapNotNull { arg ->
            val separator = indexOfTopLevel(arg, '=').takeIf { it > 0 } ?: return@mapNotNull null
            val key = arg.substring(0, separator).trim().trim('"', '\'')
            key to parseLiteral(arg.substring(separator + 1))
        }
        .toMap()

private fun parseLiteral(raw: String): JsonElement {
    val value = raw.trim()
    return when {
        value.isEmpty() -> JsonPrimitive("")
        value.startsWith("\"") || value.startsWith("'") -> JsonPrimitive(unquote(value))
        value.startsWith("[") && value.endsWith("]") ->
            JsonArray(splitTopLevel(value.substring(1, value.length - 1)).map(::parseLiteral))
        value.startsWith("{") ->
            runCatching { Json.parseToJsonElement(value) }.getOrElse { JsonPrimitive(value) }
        value.equals("true", ignoreCase = true) -> JsonPrimitive(true)
        value.equals("false", ignoreCase = true) -> JsonPrimitive(false)
        value == "None" || value == "null" -> JsonNull
        value.toLongOrNull() != null -> JsonPrimitive(value.toLong())
        value.toDoubleOrNull() != null -> JsonPrimitive(value.toDouble())
        else -> JsonPrimitive(value)
    }
}

private fun unquote(value: String): String {
    val quote = value.first()
    val body =
        if (value.length >= 2 && value.last() == quote) value.substring(1, value.length - 1)
        else value.substring(1)
    return body.replace("\\\"", "\"").replace("\\'", "'").replace("\\n", "\n").replace("\\\\", "\\")
}

private fun splitTopLevel(text: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    scanTopLevel(text) { char, isTopLevel ->
        if (isTopLevel && char == ',') {
            parts.add(current.toString())
            current.clear()
        } else {
            current.append(char)
        }
    }
    parts.add(current.toString())
    return parts.map(String::trim).filter(String::isNotEmpty)
}

private fun indexOfTopLevel(text: String, target: Char): Int {
    var found = -1
    var index = 0
    scanTopLevel(text) { char, isTopLevel ->
        if (found < 0 && isTopLevel && char == target) found = index
        index++
    }
    return found
}

private inline fun scanTopLevel(text: String, onChar: (Char, Boolean) -> Unit) {
    var depth = 0
    var quote: Char? = null
    var escaped = false
    for (char in text) {
        val topLevel = quote == null && depth == 0
        when {
            escaped -> escaped = false
            quote != null && char == '\\' -> escaped = true
            quote != null -> if (char == quote) quote = null
            char == '"' || char == '\'' -> quote = char
            char == '(' || char == '[' || char == '{' -> depth++
            char == ')' || char == ']' || char == '}' -> depth--
        }
        onChar(char, topLevel)
    }
}
