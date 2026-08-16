package com.monday8am.edgelab.presentation.playground

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.agent.playground.PlaygroundBackend
import com.monday8am.edgelab.agent.playground.TurnResult
import com.monday8am.edgelab.agent.playground.TurnToolCall
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.model.ModelRepository
import com.monday8am.edgelab.data.playground.Probe
import com.monday8am.edgelab.data.playground.ProbeRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One node in the Playground Trace — the annotated transcript of a single turn. */
sealed interface TraceEntry {
    val id: String

    /** The dev's prompt that started this turn. */
    data class UserPrompt(override val id: String, val text: String) : TraceEntry

    /** The model's final natural-language text for the turn. */
    data class ModelText(override val id: String, val text: String) : TraceEntry

    /** A tool the model invoked, with its arguments pretty-printed as name/value pairs. */
    data class ToolCallCard(
        override val id: String,
        val toolName: String,
        val args: ImmutableList<ArgValue>,
    ) : TraceEntry

    /** The mock response returned to the model for a tool call. */
    data class ToolOutput(override val id: String, val toolName: String, val mockResponse: String) :
        TraceEntry

    /** A failure surfaced during the turn (engine not ready, inference error, etc). */
    data class Error(override val id: String, val message: String) : TraceEntry
}

/** A tool-call argument pretty-printed as a name/value pair for the Trace card. */
data class ArgValue(val name: String, val value: String)

/**
 * Which model answers the dev's prompts.
 *
 * v1 defaults to [Cloud] so a dev reaches the Playground with zero download and learns the game
 * immediately; [Local] is the on-device leg they switch to once they have a `.litertlm` (plan.md
 * "Onboarding", ADR-0003).
 */
sealed interface PlaygroundTarget {
    data object Cloud : PlaygroundTarget

    data class Local(val model: ModelConfiguration) : PlaygroundTarget
}

/** Builds the backend for a [PlaygroundTarget]. Implemented in `:core`, which owns both providers. */
fun interface PlaygroundBackendFactory {
    fun create(target: PlaygroundTarget): PlaygroundBackend
}

data class PlaygroundUiState(
    val availableProbes: ImmutableList<Probe> = persistentListOf(),
    val activeProbes: ImmutableList<Probe> = persistentListOf(),
    val prompt: String = "",
    val trace: ImmutableList<TraceEntry> = persistentListOf(),
    val target: PlaygroundTarget = PlaygroundTarget.Cloud,
    /** Downloaded `.litertlm` models the dev can switch to; empty until they download one. */
    val availableModels: ImmutableList<ModelConfiguration> = persistentListOf(),
    val isRunning: Boolean = false,
    val error: String? = null,
)

sealed class PlaygroundUiAction {
    data class AddProbe(val probe: Probe) : PlaygroundUiAction()

    data class RemoveProbe(val probe: Probe) : PlaygroundUiAction()

    data class PromptChanged(val text: String) : PlaygroundUiAction()

    data class SelectTarget(val target: PlaygroundTarget) : PlaygroundUiAction()

    data object RunPrompt : PlaygroundUiAction()

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

    /** Cached backend for the current target; rebuilt when the dev switches. */
    @Volatile private var currentBackend: PlaygroundBackend? = null
    @Volatile private var currentTarget: PlaygroundTarget? = null

    private data class ViewModelState(
        val prompt: String = "",
        val activeProbes: PersistentList<Probe> = persistentListOf(),
        val trace: PersistentList<TraceEntry> = persistentListOf(),
        val target: PlaygroundTarget = PlaygroundTarget.Cloud,
        val isRunning: Boolean = false,
        val error: String? = null,
    )

    private val viewModelState = MutableStateFlow(ViewModelState())

    private val availableProbes: StateFlow<List<Probe>> =
        probeRepository.getProbesAsFlow().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val downloadedModels: StateFlow<List<ModelConfiguration>> =
        modelDownloadManager.modelsStatus
            .map { statuses ->
                statuses
                    .filterValues { it is ModelDownloadManager.Status.Completed }
                    .keys
                    .mapNotNull { modelRepository.findById(it) }
            }
            .flowOn(ioDispatcher)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

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
                    if (state.activeProbes.any { it.id == action.probe.id }) state
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
                                .filterNot { it.id == action.probe.id }
                                .toPersistentList()
                    )
                }
            is PlaygroundUiAction.PromptChanged ->
                viewModelState.update { it.copy(prompt = action.text) }
            is PlaygroundUiAction.SelectTarget ->
                viewModelState.update { it.copy(target = action.target, error = null) }
            PlaygroundUiAction.RunPrompt -> runPrompt()
            PlaygroundUiAction.ClearTrace ->
                viewModelState.update { it.copy(trace = persistentListOf(), error = null) }
        }
    }

    private fun runPrompt() {
        val state = viewModelState.value
        if (state.isRunning) return
        val promptText = state.prompt
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
                prompt = "",
            )
        }

        scope.launch { executeTurn(state.target, state.activeProbes, promptText) }
    }

    private suspend fun executeTurn(
        target: PlaygroundTarget,
        activeProbes: List<Probe>,
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
                backend.run(promptText, activeProbes).getOrElse { e ->
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

    /** Lazily builds (and caches) the backend for [target], rebuilding when the dev switches. */
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
        entries += TraceEntry.ModelText(id = nextTraceId(), text = text)
        return entries
    }

    private fun TurnToolCall.toArgValues(): ImmutableList<ArgValue> =
        args.entries.map { ArgValue(it.key, it.value?.toString() ?: "null") }.toImmutableList()

    private fun appendTrace(entry: TraceEntry) {
        viewModelState.update { it.copy(trace = (it.trace + entry).toPersistentList()) }
    }

    private fun nextTraceId(): String = "trace_${traceIdCounter.getAndIncrement()}"

    private fun deriveUiState(
        probes: List<Probe>,
        models: List<ModelConfiguration>,
        state: ViewModelState,
    ): PlaygroundUiState =
        PlaygroundUiState(
            availableProbes = probes.toImmutableList(),
            activeProbes = state.activeProbes,
            prompt = state.prompt,
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
