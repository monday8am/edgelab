package com.monday8am.edgelab.presentation.playground

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.agent.playground.PlaygroundBackend
import com.monday8am.edgelab.agent.playground.ToolOutputUsage
import com.monday8am.edgelab.agent.playground.TurnResult
import com.monday8am.edgelab.agent.playground.TurnToolCall
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.model.ModelRepository
import com.monday8am.edgelab.data.playground.ProbeRepository
import com.monday8am.edgelab.data.testing.ToolSpecification
import com.monday8am.edgelab.presentation.modelselector.ModelDownloadManager
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TraceEntry {
    val id: String

    data class UserPrompt(override val id: String, val text: String) : TraceEntry

    /** [usedToolOutput] is null when no tool was called. */
    data class ModelText(
        override val id: String,
        val text: String,
        val usedToolOutput: Boolean? = null,
    ) : TraceEntry

    data class ToolCallCard(
        override val id: String,
        val toolName: String,
        val args: ImmutableList<ArgValue>,
    ) : TraceEntry

    data class ToolOutput(override val id: String, val toolName: String, val mockResponse: String) :
        TraceEntry

    data class Error(override val id: String, val message: String) : TraceEntry
}

data class ArgValue(val name: String, val value: String)

/**
 * v1 defaults to [Cloud] — zero download before the first prompt; [Local] once a `.litertlm` lands.
 */
sealed interface PlaygroundTarget {
    data object Cloud : PlaygroundTarget

    data class Local(val model: ModelConfiguration) : PlaygroundTarget
}

/** Implemented in `:core`, which owns both providers. */
fun interface PlaygroundBackendFactory {
    fun create(target: PlaygroundTarget): PlaygroundBackend
}

data class PlaygroundUiState(
    val availableProbes: ImmutableList<ToolSpecification> = persistentListOf(),
    val activeProbes: ImmutableList<ToolSpecification> = persistentListOf(),
    val trace: ImmutableList<TraceEntry> = persistentListOf(),
    val target: PlaygroundTarget = PlaygroundTarget.Cloud,
    /** Downloaded `.litertlm` models the dev can switch to; empty until they download one. */
    val availableModels: ImmutableList<ModelConfiguration> = persistentListOf(),
    val isRunning: Boolean = false,
    val error: String? = null,
)

sealed class PlaygroundUiAction {
    data class AddProbe(val probe: ToolSpecification) : PlaygroundUiAction()

    data class RemoveProbe(val probe: ToolSpecification) : PlaygroundUiAction()

    data class SelectTarget(val target: PlaygroundTarget) : PlaygroundUiAction()

    data class RunPrompt(val text: String) : PlaygroundUiAction()

    data object ClearTrace : PlaygroundUiAction()
}

interface PlaygroundViewModel {
    val uiState: StateFlow<PlaygroundUiState>

    fun onUiAction(action: PlaygroundUiAction)

    fun dispose()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaygroundViewModelImpl(
    private val probeRepository: ProbeRepository,
    private val modelDownloadManager: ModelDownloadManager,
    private val modelRepository: ModelRepository,
    private val backendFactory: PlaygroundBackendFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlaygroundViewModel {

    private val logger = Logger.withTag("PlaygroundViewModel")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Trace ids are minted from coroutines that may resume off the main thread. */
    private val traceIdCounter = AtomicInteger(1)

    @Volatile private var currentBackend: PlaygroundBackend? = null
    @Volatile private var currentTarget: PlaygroundTarget? = null

    private data class ViewModelState(
        val activeProbes: PersistentList<ToolSpecification> = persistentListOf(),
        val trace: PersistentList<TraceEntry> = persistentListOf(),
        val target: PlaygroundTarget = PlaygroundTarget.Cloud,
        val isRunning: Boolean = false,
        val error: String? = null,
    )

    private val viewModelState = MutableStateFlow(ViewModelState())

    private val availableProbes: StateFlow<List<ToolSpecification>> =
        probeRepository.getToolsAsFlow().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val mockResponses: StateFlow<Map<String, String>> =
        probeRepository.getMockResponsesAsFlow().stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val downloadedModels: StateFlow<List<ModelConfiguration>> =
        combine(modelRepository.models, modelDownloadManager.modelsStatus) { models, statuses ->
                models.filter {
                    statuses[it.bundleFilename] is ModelDownloadManager.Status.Completed
                }
            }
            .flowOn(ioDispatcher)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // The catalog is only loaded when the Model Selector is opened; the Playground needs it
        // too, or downloaded models never appear as switchable targets on a fresh start.
        modelRepository.refreshModels()
    }

    override val uiState: StateFlow<PlaygroundUiState> =
        combine(availableProbes, downloadedModels, viewModelState) { probes, models, state ->
                deriveUiState(probes, models, state)
            }
            .flowOn(ioDispatcher)
            .stateIn(scope, SharingStarted.Eagerly, PlaygroundUiState())

    override fun onUiAction(action: PlaygroundUiAction) {
        when (action) {
            is PlaygroundUiAction.AddProbe ->
                viewModelState.update { state ->
                    if (state.activeProbes.any { it.function.name == action.probe.function.name })
                        state
                    else
                        state.copy(
                            activeProbes = (state.activeProbes + action.probe).toPersistentList()
                        )
                }
            is PlaygroundUiAction.RemoveProbe ->
                viewModelState.update { state ->
                    state.copy(
                        activeProbes =
                            state.activeProbes
                                .filterNot { it.function.name == action.probe.function.name }
                                .toPersistentList()
                    )
                }
            is PlaygroundUiAction.SelectTarget ->
                viewModelState.update { it.copy(target = action.target, error = null) }
            is PlaygroundUiAction.RunPrompt -> runPrompt(action.text)
            PlaygroundUiAction.ClearTrace ->
                viewModelState.update { it.copy(trace = persistentListOf(), error = null) }
        }
    }

    private fun runPrompt(promptText: String) {
        val state = viewModelState.value
        if (state.isRunning) return
        if (promptText.isBlank()) {
            viewModelState.update { it.copy(error = "Type a prompt first") }
            return
        }

        val userPromptEntry = TraceEntry.UserPrompt(id = nextTraceId(), text = promptText)
        viewModelState.update {
            it.copy(
                isRunning = true,
                error = null,
                trace = (it.trace + userPromptEntry).toPersistentList(),
            )
        }

        scope.launch {
            executeTurn(state.target, state.activeProbes, mockResponses.value, promptText)
        }
    }

    private suspend fun executeTurn(
        target: PlaygroundTarget,
        activeProbes: List<ToolSpecification>,
        mockResponses: Map<String, String>,
        promptText: String,
    ) {
        try {
            val backend = backendFor(target)
            backend.initialize().getOrElse { e ->
                logger.e("Backend initialize failed", e)
                appendTrace(TraceEntry.Error(nextTraceId(), "Model not ready: ${e.message ?: ""}"))
                return
            }

            val turn =
                backend.run(promptText, activeProbes, mockResponses).getOrElse { e ->
                    logger.e("Inference failed", e)
                    appendTrace(
                        TraceEntry.Error(nextTraceId(), "Inference failed: ${e.message ?: ""}")
                    )
                    return
                }

            viewModelState.update { state ->
                state.copy(trace = (state.trace + turn.toTraceEntries()).toPersistentList())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Playground turn failed", e)
            appendTrace(TraceEntry.Error(nextTraceId(), e.message ?: "Unknown error"))
        } finally {
            viewModelState.update { it.copy(isRunning = false) }
        }
    }

    private fun backendFor(target: PlaygroundTarget): PlaygroundBackend {
        val existing = currentBackend
        if (existing != null && target == currentTarget) return existing
        existing?.close()
        currentTarget = target
        return backendFactory.create(target).also { currentBackend = it }
    }

    private fun TurnResult.toTraceEntries(): List<TraceEntry> {
        val entries = mutableListOf<TraceEntry>()
        toolCalls.forEach { call ->
            entries +=
                TraceEntry.ToolCallCard(
                    id = nextTraceId(),
                    toolName = call.name,
                    args = call.toArgValues(),
                )
            entries +=
                TraceEntry.ToolOutput(
                    id = nextTraceId(),
                    toolName = call.name,
                    mockResponse = call.mockResponse,
                )
        }
        val usedToolOutput =
            if (toolCalls.isEmpty()) null
            else toolCalls.any { ToolOutputUsage.isUsed(it.mockResponse, text) }
        entries +=
            TraceEntry.ModelText(id = nextTraceId(), text = text, usedToolOutput = usedToolOutput)
        return entries
    }

    private fun TurnToolCall.toArgValues(): ImmutableList<ArgValue> =
        args.entries.map { ArgValue(it.key, it.value?.toString() ?: "null") }.toImmutableList()

    private fun appendTrace(entry: TraceEntry) {
        viewModelState.update { it.copy(trace = (it.trace + entry).toPersistentList()) }
    }

    private fun nextTraceId(): String = "trace_${traceIdCounter.getAndIncrement()}"

    private fun deriveUiState(
        probes: List<ToolSpecification>,
        models: List<ModelConfiguration>,
        state: ViewModelState,
    ): PlaygroundUiState =
        PlaygroundUiState(
            availableProbes = probes.toImmutableList(),
            activeProbes = state.activeProbes,
            trace = state.trace,
            target = state.target,
            availableModels = models.toImmutableList(),
            isRunning = state.isRunning,
            error = state.error,
        )

    override fun dispose() {
        currentBackend?.close()
        scope.cancel()
    }
}
