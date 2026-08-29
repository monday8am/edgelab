package com.monday8am.edgelab.agent.playground

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException

/**
 * Semantic second opinion: asks a cloud model whether the answer used the tool output even when
 * paraphrased, rounded, or reformatted — the cases the deterministic heuristic cannot see.
 */
class LlmToolOutputJudge(private val chatFactory: CloudChatFactory) : ToolOutputJudge {

    private val logger = Logger.withTag("LlmToolOutputJudge")

    override suspend fun isUsed(mockResponse: String, modelText: String): Boolean? =
        try {
            val reply = chatFactory.open(emptyList()).send(judgePrompt(mockResponse, modelText))
            if (reply.calls.isNotEmpty()) {
                logger.w("Judge requested a tool; abstaining")
                null
            } else {
                parseVerdict(reply.text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Judge call failed", e)
            null
        }

    private fun judgePrompt(mockResponse: String, modelText: String): String =
        """
        You are a strict judge. Tool output(s): $mockResponse. Assistant answer: $modelText.
        Did the answer use the tool output(s), even paraphrased, rounded, or reformatted?
        Reply YES or NO only.
        """
            .trimIndent()

    private fun parseVerdict(replyText: String): Boolean? =
        when (replyText.trim().uppercase().split(NON_LETTERS).firstOrNull { it.isNotBlank() }) {
            "YES" -> true
            "NO" -> false
            else -> {
                logger.w("Judge reply was not YES/NO: '$replyText'")
                null
            }
        }

    private companion object {
        val NON_LETTERS = Regex("[^A-Z]+")
    }
}
