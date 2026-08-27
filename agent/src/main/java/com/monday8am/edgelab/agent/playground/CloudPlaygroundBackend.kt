package com.monday8am.edgelab.agent.playground

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.data.testing.ToolSpecification
import kotlinx.coroutines.CancellationException

/**
 * The cloud Playground backend. Unlike the local backend, where litert-lm invokes the tool handler
 * in-process, a cloud model only *asks* for a call and waits — so this class owns the tool loop
 * explicitly: send prompt → read `functionCall`s → hand back each tool's mock output → repeat while
 * the model keeps calling tools.
 */
class CloudPlaygroundBackend(
    private val chatFactory: CloudChatFactory,
    private val maxToolRounds: Int = DEFAULT_MAX_TOOL_ROUNDS,
) : PlaygroundBackend {

    private val logger = Logger.withTag("CloudPlaygroundBackend")

    private var session: CloudChatSession? = null

    /** The tool set the live [session] was opened with; a change forces a fresh session. */
    private var sessionTools: List<ToolSpecification> = emptyList()

    override suspend fun initialize(): Result<Unit> = Result.success(Unit)

    override suspend fun run(
        prompt: String,
        tools: List<ToolSpecification>,
        mockResponses: Map<String, String>,
    ): Result<TurnResult> {
        return try {
            val chat = sessionFor(tools)
            val recorded = mutableListOf<TurnToolCall>()

            var reply = chat.send(prompt)
            var round = 0
            while (reply.calls.isNotEmpty()) {
                if (round++ >= maxToolRounds) {
                    // A model that never stops calling tools usually means a mock output that
                    // doesn't answer the question. Surface it rather than truncating in silence.
                    return Result.failure(
                        IllegalStateException(
                            "Model kept calling tools for more than $maxToolRounds rounds. " +
                                "Check that the tool mock outputs actually answer the prompt."
                        )
                    )
                }

                val responses =
                    reply.calls.map { call ->
                        val mock = mockResponses[call.name]
                        if (mock == null) {
                            // The model invented a tool we never registered. Tell it so, rather
                            // than failing the turn — that reaction is itself worth seeing.
                            logger.w("Model called unregistered tool '${call.name}'")
                            recorded +=
                                TurnToolCall(call.name, call.args, UNREGISTERED_TOOL_RESPONSE)
                            CloudFunctionResponse(call.name, UNREGISTERED_TOOL_RESPONSE)
                        } else {
                            recorded += TurnToolCall(call.name, call.args, mock)
                            CloudFunctionResponse(call.name, mock)
                        }
                    }

                reply = chat.respondToCalls(responses)
            }

            Result.success(TurnResult(text = reply.text, toolCalls = recorded))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Cloud playground turn failed", e)
            Result.failure(e)
        }
    }

    /**
     * Reuses the open session so follow-up prompts keep their history — that continuity is what
     * makes "did the model actually use the tool output?" probeable. Tools are bound at session
     * open, so a changed tool set has to start a new one.
     */
    private fun sessionFor(tools: List<ToolSpecification>): CloudChatSession {
        val existing = session
        if (existing != null && tools.sameToolsAs(sessionTools)) return existing
        sessionTools = tools
        return chatFactory.open(tools).also { session = it }
    }

    private fun List<ToolSpecification>.sameToolsAs(other: List<ToolSpecification>): Boolean =
        size == other.size && zip(other).all { (a, b) -> a == b }

    override fun close() {
        session = null
        sessionTools = emptyList()
    }

    private companion object {
        /** Enough for a genuine multi-tool answer, low enough to catch a runaway loop. */
        const val DEFAULT_MAX_TOOL_ROUNDS = 5

        const val UNREGISTERED_TOOL_RESPONSE =
            """{"error": "No such tool is registered in this Playground session."}"""
    }
}
