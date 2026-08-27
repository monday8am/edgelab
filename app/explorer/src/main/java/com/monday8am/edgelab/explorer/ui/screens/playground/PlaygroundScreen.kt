package com.monday8am.edgelab.explorer.ui.screens.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.edgelab.data.model.ModelCatalog
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.testing.FunctionSpec
import com.monday8am.edgelab.data.testing.ToolSpecification
import com.monday8am.edgelab.explorer.di.ServiceLocator
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme
import com.monday8am.edgelab.presentation.playground.ArgValue
import com.monday8am.edgelab.presentation.playground.PlaygroundTarget
import com.monday8am.edgelab.presentation.playground.PlaygroundUiAction
import com.monday8am.edgelab.presentation.playground.PlaygroundUiState
import com.monday8am.edgelab.presentation.playground.PlaygroundViewModelImpl
import com.monday8am.edgelab.presentation.playground.TraceEntry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject

@Composable
fun PlaygroundScreen(
    onNavigateToModelSelector: () -> Unit,
    viewModel: AndroidPlaygroundViewModel =
        viewModel {
            AndroidPlaygroundViewModel(
                PlaygroundViewModelImpl(
                    probeRepository = ServiceLocator.probeRepository,
                    modelDownloadManager = ServiceLocator.modelDownloadManager,
                    modelRepository = ServiceLocator.modelRepository,
                    backendFactory = ServiceLocator.playgroundBackendFactory,
                )
            )
        },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlaygroundScreenContent(
        uiState = uiState,
        onAction = viewModel::onUiAction,
        onNavigateToModelSelector = onNavigateToModelSelector,
    )
}

@Composable
private fun PlaygroundScreenContent(
    uiState: PlaygroundUiState,
    onAction: (PlaygroundUiAction) -> Unit,
    onNavigateToModelSelector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.trace.size) {
        if (uiState.trace.isNotEmpty()) {
            listState.animateScrollToItem(uiState.trace.lastIndex)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize().padding(16.dp).imePadding(),
    ) {
        ModelHeader(
            target = uiState.target,
            availableModels = uiState.availableModels,
            onSelectTarget = { onAction(PlaygroundUiAction.SelectTarget(it)) },
            onNavigateToModelSelector = onNavigateToModelSelector,
        )

        ProbeLibrary(
            availableProbes = uiState.availableProbes,
            activeProbes = uiState.activeProbes,
            onAddProbe = { onAction(PlaygroundUiAction.AddProbe(it)) },
            onRemoveProbe = { onAction(PlaygroundUiAction.RemoveProbe(it)) },
        )

        TraceList(
            trace = uiState.trace,
            isRunning = uiState.isRunning,
            modifier = Modifier.fillMaxWidth().weight(1f),
            listState = listState,
        )

        uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        PromptBar(
            prompt = uiState.prompt,
            isRunning = uiState.isRunning,
            onPromptChanged = { onAction(PlaygroundUiAction.PromptChanged(it)) },
            onRun = { onAction(PlaygroundUiAction.RunPrompt) },
            onClear = { onAction(PlaygroundUiAction.ClearTrace) },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ModelHeader(
    target: PlaygroundTarget,
    availableModels: ImmutableList<ModelConfiguration>,
    onSelectTarget: (PlaygroundTarget) -> Unit,
    onNavigateToModelSelector: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Playground",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            when (target) {
                                PlaygroundTarget.Cloud -> "Cloud: Gemini Flash — no download needed"
                                is PlaygroundTarget.Local -> "On-device: ${target.model.displayName}"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onNavigateToModelSelector) { Text("Models") }
            }

            // Local AI is weaker than cloud today, and that gap is why this app exists — so the
            // switch sits in the open rather than being hidden behind a settings screen.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InputChip(
                    selected = target is PlaygroundTarget.Cloud,
                    onClick = { onSelectTarget(PlaygroundTarget.Cloud) },
                    label = { Text("Cloud") },
                )
                availableModels.forEach { model ->
                    InputChip(
                        selected = target is PlaygroundTarget.Local && target.model.modelId == model.modelId,
                        onClick = { onSelectTarget(PlaygroundTarget.Local(model)) },
                        label = { Text(model.displayName) },
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProbeLibrary(
    availableProbes: ImmutableList<ToolSpecification>,
    activeProbes: ImmutableList<ToolSpecification>,
    onAddProbe: (ToolSpecification) -> Unit,
    onRemoveProbe: (ToolSpecification) -> Unit,
) {
    if (availableProbes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Probes (preset library — tap to add)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            availableProbes.forEach { probe ->
                val isActive =
                    activeProbes.any { it.function.name == probe.function.name }
                InputChip(
                    selected = isActive,
                    onClick = {
                        if (isActive) onRemoveProbe(probe) else onAddProbe(probe)
                    },
                    label = { Text(probe.function.name) },
                )
            }
        }
        if (activeProbes.isNotEmpty()) {
            Text(
                text =
                    "Active: ${activeProbes.joinToString(", ") { it.function.name }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TraceList(
    trace: ImmutableList<TraceEntry>,
    isRunning: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(trace, key = { it.id }) { entry -> TraceEntryRow(entry) }
        if (isRunning) {
            item("running") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(4.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Thinking...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (trace.isEmpty() && !isRunning) {
            item("empty") {
                Text(
                    text =
                        "Add a probe, type a prompt, and hit Run to watch the model call your " +
                            "tools. The Trace shows what it did.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TraceEntryRow(entry: TraceEntry) {
    when (entry) {
        is TraceEntry.UserPrompt ->
            TraceBubble(
                label = "You",
                text = entry.text,
                container = MaterialTheme.colorScheme.primaryContainer,
            )
        is TraceEntry.ModelText ->
            TraceBubble(
                label = "Model",
                text = entry.text,
                container = MaterialTheme.colorScheme.secondaryContainer,
            )
        is TraceEntry.ToolCallCard -> ToolCallCardRow(entry)
        is TraceEntry.ToolOutput -> ToolOutputRow(entry)
        is TraceEntry.Error ->
            Text(
                text = entry.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
    }
}

@Composable
private fun TraceBubble(
    label: String,
    text: String,
    container: androidx.compose.ui.graphics.Color,
) {
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ToolCallCardRow(entry: TraceEntry.ToolCallCard) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🔧 ${entry.toolName}(",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (entry.args.isEmpty()) {
                Text(
                    text = "  no args",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entry.args.forEach { arg ->
                    Text(
                        text = "  ${arg.name}: ${arg.value}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = ")",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ToolOutputRow(entry: TraceEntry.ToolOutput) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp)) {
        Text(
            text = "↓ ${entry.toolName} → mock output",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = entry.mockResponse,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PromptBar(
    prompt: String,
    isRunning: Boolean,
    onPromptChanged: (String) -> Unit,
    onRun: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChanged,
            label = { Text("Ask the model...") },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRun, enabled = !isRunning && prompt.isNotBlank()) {
                Text("Run")
            }
            AssistChip(onClick = onClear, label = { Text("Clear Trace") })
        }
    }
}

// region Previews

@Preview(showBackground = true, name = "Empty — no model")
@Composable
private fun PlaygroundPreviewEmpty() {
    EdgeLabTheme {
        PlaygroundScreenContent(
            uiState = PlaygroundUiState(),
            onAction = {},
            onNavigateToModelSelector = {},
        )
    }
}

@Preview(showBackground = true, name = "With probe and trace", widthDp = 380)
@Composable
private fun PlaygroundPreviewWithTrace() {
    val model: ModelConfiguration = ModelCatalog.GEMMA3_1B
    val probe =
        ToolSpecification(
            function =
                FunctionSpec(
                    name = "get_location",
                    description = "Get the user's location",
                    parameters = JsonObject(emptyMap()),
                ),
        )
    val trace = persistentListOf(
        TraceEntry.UserPrompt(id = "t1", text = "Where am I?"),
        TraceEntry.ToolCallCard(
            id = "t2",
            toolName = "get_location",
            args =
                persistentListOf(
                    ArgValue(name = "unused", value = "true"),
                ).toImmutableList(),
        ),
        TraceEntry.ToolOutput(
            id = "t3",
            toolName = "get_location",
            mockResponse = "{\"latitude\": 40.4168, \"longitude\": -3.7038}",
        ),
        TraceEntry.ModelText(id = "t4", text = "You are in Madrid at 40.42, -3.70."),
    )
    EdgeLabTheme {
        PlaygroundScreenContent(
            uiState =
                PlaygroundUiState(
                    availableProbes = persistentListOf(probe),
                    activeProbes = persistentListOf(probe),
                    prompt = "",
                    trace = trace,
                    availableModels = persistentListOf(model),
                    target = PlaygroundTarget.Local(model),
                    isRunning = false,
                    error = null,
                ),
            onAction = {},
            onNavigateToModelSelector = {},
        )
    }
}

@Preview(showBackground = true, name = "Running")
@Composable
private fun PlaygroundPreviewRunning() {
    val model: ModelConfiguration = ModelCatalog.GEMMA3_1B
    EdgeLabTheme {
        PlaygroundScreenContent(
            uiState =
                PlaygroundUiState(
                    target = PlaygroundTarget.Local(model),
                    availableModels = persistentListOf(model),
                    trace = persistentListOf(TraceEntry.UserPrompt(id = "t1", text = "Where am I?")),
                    isRunning = true,
                ),
            onAction = {},
            onNavigateToModelSelector = {},
        )
    }
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun PlaygroundPreviewError() {
    val model: ModelConfiguration = ModelCatalog.GEMMA3_1B
    EdgeLabTheme {
        PlaygroundScreenContent(
            uiState =
                PlaygroundUiState(
                    target = PlaygroundTarget.Local(model),
                    availableModels = persistentListOf(model),
                    trace =
                        persistentListOf(
                            TraceEntry.UserPrompt(id = "t1", text = "Hi"),
                            TraceEntry.Error(id = "t2", message = "Inference failed: timeout"),
                        ),
                    error = "Inference failed: timeout",
                ),
            onAction = {},
            onNavigateToModelSelector = {},
        )
    }
}

// endregion