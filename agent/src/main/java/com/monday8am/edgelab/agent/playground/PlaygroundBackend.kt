package com.monday8am.edgelab.agent.playground

import com.monday8am.edgelab.data.testing.ToolSpecification

/**
 * One Playground inference backend — cloud (Gemini) or local (LiteRT-LM). The seam `:presentation`
 * talks to: neither litertlm nor Firebase types leak past it, so the ViewModel stays pure Kotlin.
 */
interface PlaygroundBackend {
    /** Idempotent — safe to call before each turn. */
    suspend fun initialize(): Result<Unit>

    suspend fun run(
        prompt: String,
        tools: List<ToolSpecification>,
        mockResponses: Map<String, String>,
    ): Result<TurnResult>

    fun close()
}

data class TurnResult(val text: String, val toolCalls: List<TurnToolCall>)

data class TurnToolCall(val name: String, val args: Map<String, Any?>, val mockResponse: String)
