package com.monday8am.edgelab.agent.playground

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.agent.core.LocalInferenceEngine
import com.monday8am.edgelab.agent.tools.OpenApiToolHandler
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.testing.ToolSpecification
import kotlinx.coroutines.CancellationException

/**
 * The on-device Playground backend. litert-lm invokes the tool handler in-process, so there is no
 * explicit tool loop here — the mock responses come back already recorded on the handlers. This is
 * also the boundary that keeps `OpenApiToolHandler` (a `litertlm.OpenApiTool`) out of
 * `:presentation`.
 *
 * Single-turn in v1a: each [run] resets the conversation and registers tools afresh.
 */
class LocalPlaygroundBackend(
    private val engine: LocalInferenceEngine,
    private val model: ModelConfiguration,
    private val modelPath: String,
) : PlaygroundBackend {
    private val logger = Logger.withTag("LocalPlaygroundBackend")

    @Volatile private var initialized = false

    override suspend fun initialize(): Result<Unit> {
        if (initialized) return Result.success(Unit)
        return engine
            .initialize(model, modelPath)
            .onSuccess { initialized = true }
            .onFailure { e -> logger.e("Engine initialize failed", e) }
    }

    override suspend fun run(
        prompt: String,
        tools: List<ToolSpecification>,
        mockResponses: Map<String, String>,
    ): Result<TurnResult> {
        return try {
            val handlersWithMocks = tools.map { tool ->
                val mock = mockResponses[tool.function.name].orEmpty()
                OpenApiToolHandler(tool, mock) to mock
            }

            engine.setToolsAndResetConversation(handlersWithMocks.map { it.first })

            val text =
                engine.prompt(prompt).getOrElse { e ->
                    return Result.failure(e)
                }

            val calls = handlersWithMocks.flatMap { (handler, mock) ->
                handler.calls.map { call ->
                    TurnToolCall(name = call.name, args = call.args, mockResponse = mock)
                }
            }

            Result.success(TurnResult(text = text, toolCalls = calls))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Playground run failed", e)
            Result.failure(e)
        }
    }

    override fun close() {
        engine.closeSession()
        initialized = false
    }
}
