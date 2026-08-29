package com.monday8am.edgelab.agent.playground

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.data.testing.FunctionSpec
import com.monday8am.edgelab.data.testing.ToolSpecification
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Semantic second opinion: a cloud model classifies whether the answer used the tool output, via a
 * structured report_usage function call with a YES/NO text fallback.
 */
class LlmToolOutputJudge(private val chatFactory: CloudChatFactory) : ToolOutputJudge {

    private val logger = Logger.withTag("LlmToolOutputJudge")

    override suspend fun isUsed(mockResponse: String, modelText: String): Boolean? =
        try {
            val reply =
                chatFactory.open(listOf(VERDICT_TOOL)).send(judgePrompt(mockResponse, modelText))
            structuredVerdict(reply) ?: parseVerdict(reply.text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Judge call failed", e)
            null
        }

    private fun structuredVerdict(reply: CloudReply): Boolean? =
        reply.calls.firstOrNull()?.args?.get(USED_ARG) as? Boolean

    private fun judgePrompt(mockResponse: String, modelText: String): String =
        """
        You are a strict judge. Tool output(s): $mockResponse. Assistant answer: $modelText.
        Did the answer use the tool output(s), even paraphrased, rounded, or reformatted?
        Call report_usage with your verdict.
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
        const val USED_ARG = "used"

        val NON_LETTERS = Regex("[^A-Z]+")

        /**
         * The verdict travels inside the call args, so no server-side function exists in AI Logic —
         * this declaration is sent with the request like any Playground probe tool.
         */
        val VERDICT_TOOL =
            ToolSpecification(
                function =
                    FunctionSpec(
                        name = "report_usage",
                        description = "Report whether the assistant answer used the tool output",
                        parameters =
                            buildJsonObject {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("used") {
                                        put("type", "boolean")
                                        put(
                                            "description",
                                            "True when the answer used the tool output",
                                        )
                                    }
                                }
                                putJsonArray("required") { add(USED_ARG) }
                            },
                    )
            )
    }
}
