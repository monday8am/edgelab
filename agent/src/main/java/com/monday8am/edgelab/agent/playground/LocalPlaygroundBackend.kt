package com.monday8am.edgelab.agent.playground

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.agent.core.LocalInferenceEngine
import com.monday8am.edgelab.agent.tools.OpenApiToolHandler
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.playground.Probe
import kotlinx.coroutines.CancellationException

/**
 * The on-device Playground backend, running a `.litertlm` model through [LocalInferenceEngine].
 *
 * litert-lm invokes the Probe handler in-process, so there is no explicit tool loop here — the mock
 * responses come back already recorded on the handlers. (Contrast [CloudPlaygroundBackend], which
 * has to drive that loop itself.)
 *
 * This is also the boundary that keeps `OpenApiToolHandler` — which extends `litertlm.OpenApiTool`
 * — out of `:presentation`. Callers see only [TurnResult].
 *
 * Single-turn in v1a: each [run] resets the conversation and registers tools afresh. The model is
 * loaded once via [initialize]; later turns reuse the loaded weights.
 */
class LocalPlaygroundBackend(
    private val engine: LocalInferenceEngine,
    private val model: ModelConfiguration,
    private val modelPath: String,
) : PlaygroundBackend {
    private val logger = Logger.withTag("LocalPlaygroundBackend")

    @Volatile private var initialized = false

    /** Loads the model. Idempotent — safe to call before each turn. */
    override suspend fun initialize(): Result<Unit> {
        if (initialized) return Result.success(Unit)
        return engine
            .initialize(model, modelPath)
            .onSuccess { initialized = true }
            .onFailure { e -> logger.e("Engine initialize failed", e) }
    }

    override suspend fun run(prompt: String, probes: List<Probe>): Result<TurnResult> {
        return try {
            val handlersWithProbes = probes.map {
                OpenApiToolHandler(it.toolSpec, it.mockResponse) to it
            }
            val handlers = handlersWithProbes.map { it.first }

            engine.setToolsAndResetConversation(handlers)

            val text =
                engine.prompt(prompt).getOrElse { e ->
                    return Result.failure(e)
                }

            val calls = handlersWithProbes.flatMap { (handler, probe) ->
                handler.calls.map { call ->
                    TurnToolCall(
                        name = call.name,
                        args = call.args,
                        mockResponse = probe.mockResponse,
                    )
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

    /** Releases the underlying inference session. */
    override fun close() {
        engine.closeSession()
        initialized = false
    }
}
