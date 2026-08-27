package com.monday8am.edgelab.agent.playground

import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Heuristic judge for the Trace's `[used/ignored tool output]` tag: did the model's final text
 * actually integrate the mock output it was handed?
 *
 * Evidence = distinctive mock content surfacing in the text: string values matched as whole words
 * (case-insensitive), numbers matched with rounding tolerance (models routinely round 40.4168 to
 * "40.42"). Trivially-common integers (|v| ≤ 10) and booleans are skipped — they match almost any
 * sentence. No evidence → ignored.
 */
object ToolOutputUsage {

    fun isUsed(mockResponse: String, modelText: String): Boolean {
        val content = parseMock(mockResponse)
        if (content.words.isEmpty() && content.numbers.isEmpty()) return false

        val textWords = modelText.lowercase().split(WORD_SPLIT).filter { it.isNotBlank() }.toSet()
        if (content.words.any { it in textWords }) return true

        val textNumbers =
            NUMBER_REGEX.findAll(modelText).mapNotNull { it.value.toDoubleOrNull() }.toList()
        return content.numbers.any { mock -> textNumbers.any { matchesNumber(mock, it) } }
    }

    private data class MockContent(val words: Set<String>, val numbers: List<Double>)

    private fun parseMock(mockResponse: String): MockContent {
        // A plain-string mock ("The weather is sunny") is legitimate; it just isn't JSON.
        val element =
            runCatching { Json.parseToJsonElement(mockResponse) }.getOrNull()
                ?: return MockContent(distinctiveWords(mockResponse).toSet(), emptyList())

        val values = collectValues(element)
        return MockContent(
            words = values.filterIsInstance<String>().flatMap { distinctiveWords(it) }.toSet(),
            numbers = values.filterIsInstance<Double>().filter(::isDistinctiveNumber),
        )
    }

    private fun collectValues(element: JsonElement): List<Any> =
        when (element) {
            is JsonObject -> element.values.flatMap { collectValues(it) }
            is JsonArray -> element.flatMap { collectValues(it) }
            is JsonPrimitive ->
                when {
                    element.isString -> listOf(element.content)
                    else -> element.doubleOrNull?.let { listOf(it) }.orEmpty()
                }
        }

    private fun distinctiveWords(value: String): List<String> =
        value.lowercase().split(WORD_SPLIT).filter {
            it.length >= MIN_WORD_LENGTH && it !in STOPWORDS
        }

    /**
     * Integers from 0 to ±10 appear in almost any sentence; larger or fractional ones are evidence.
     */
    private fun isDistinctiveNumber(value: Double): Boolean =
        value % 1.0 != 0.0 || abs(value) > 10.0

    private fun matchesNumber(mock: Double, text: Double): Boolean =
        abs(mock - text) <= ABS_TOLERANCE ||
            (mock != 0.0 && abs(mock - text) <= REL_TOLERANCE * abs(mock))

    private val WORD_SPLIT = Regex("[^a-z0-9']+")
    private val NUMBER_REGEX = Regex("-?\\d+(?:\\.\\d+)?")

    private const val MIN_WORD_LENGTH = 3
    private const val ABS_TOLERANCE = 0.05
    private const val REL_TOLERANCE = 0.01

    private val STOPWORDS =
        setOf(
            "the",
            "and",
            "for",
            "with",
            "that",
            "this",
            "from",
            "not",
            "are",
            "was",
            "were",
            "you",
            "your",
            "user",
            "current",
            "today",
            "have",
            "has",
            "had",
            "will",
            "can",
            "may",
            "its",
            "their",
            "they",
            "them",
            "who",
            "what",
            "when",
            "where",
            "which",
            "there",
            "here",
            "then",
            "than",
            "all",
            "any",
            "out",
            "get",
            "got",
            "now",
            "new",
            "how",
            "why",
            "yes",
            "but",
            "into",
            "over",
            "just",
            "also",
            "some",
            "more",
            "very",
        )
}
