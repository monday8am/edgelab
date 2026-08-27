package com.monday8am.edgelab.agent.playground

import com.monday8am.edgelab.data.testing.ToolSpecification

/**
 * One Playground inference backend — cloud (Gemini) or local (LiteRT-LM).
 *
 * This is the seam `:presentation` talks to. Neither litertlm types nor Firebase types leak past
 * it, so the ViewModel stays pure Kotlin and testable against a fake.
 */
interface PlaygroundBackend {
    /** Prepares the backend. Idempotent — safe to call before each turn. */
    suspend fun initialize(): Result<Unit>

    /**
     * Runs one turn: registers [tools] as callable tools, sends [prompt], and reports the model's
     * final text plus every tool call it made (paired with the mock response it received from
     * [mockResponses], keyed by tool name).
     */
    suspend fun run(
        prompt: String,
        tools: List<ToolSpecification>,
        mockResponses: Map<String, String>,
    ): Result<TurnResult>

    /** Releases the underlying session. */
    fun close()
}

/** Result of one Playground turn. */
data class TurnResult(val text: String, val toolCalls: List<TurnToolCall>)

/** One tool invocation the model made during a turn, with the mock response it received. */
data class TurnToolCall(val name: String, val args: Map<String, Any?>, val mockResponse: String)
