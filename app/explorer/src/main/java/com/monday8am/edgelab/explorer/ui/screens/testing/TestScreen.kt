package com.monday8am.edgelab.explorer.ui.screens.testing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.edgelab.explorer.Dependencies
import com.monday8am.edgelab.data.model.HardwareBackend
import com.monday8am.edgelab.data.model.ModelCatalog
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.testing.TestDomain
import com.monday8am.edgelab.core.download.ModelDownloadManagerImpl
import com.monday8am.edgelab.core.inference.LiteRTLmInferenceEngineImpl
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme
import com.monday8am.edgelab.presentation.testing.TestResultFrame
import com.monday8am.edgelab.presentation.testing.TestStatus
import com.monday8am.edgelab.presentation.testing.TestUiAction
import com.monday8am.edgelab.presentation.testing.TestViewModelImpl
import com.monday8am.edgelab.presentation.testing.ValidationResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun TestScreen(
    modelId: String,
    onNavigateToTestDetails: () -> Unit = {},
    viewModel: AndroidTestViewModel =
        viewModel(key = modelId) {
            val selectedModel =
                Dependencies.modelRepository.findById(modelId) ?: ModelCatalog.DEFAULT

            val inferenceEngine = LiteRTLmInferenceEngineImpl()

            val modelPath =
                (Dependencies.modelDownloadManager as ModelDownloadManagerImpl).getModelPath(
                    selectedModel.bundleFilename
                )

            AndroidTestViewModel(
                TestViewModelImpl(
                    initialModel = selectedModel,
                    testRepository = Dependencies.testRepository,
                    modelPath = modelPath,
                    inferenceEngine = inferenceEngine,
                ),
                selectedModel,
            )
        },
) {

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TestContent(
        frames = state.frames,
        testStatuses = state.testStatuses,
        availableDomains = state.availableDomains,
        selectedModel = state.selectedModel,
        isRunning = state.isRunning,
        isInitializing = state.isInitializing,
        onRunTests = { useGpu, filter ->
            viewModel.onUiAction(TestUiAction.RunTests(useGpu, filter))
        },
        onCancelTests = { viewModel.onUiAction(TestUiAction.CancelTests) },
        onNavigateToTestDetails = onNavigateToTestDetails,
        modifier = Modifier,
    )
}

@Composable
private fun TestContent(
    frames: ImmutableMap<String, TestResultFrame>,
    testStatuses: ImmutableList<TestStatus>,
    availableDomains: ImmutableList<TestDomain>,
    selectedModel: ModelConfiguration,
    isRunning: Boolean,
    isInitializing: Boolean,
    onRunTests: (Boolean, TestDomain?) -> Unit,
    onCancelTests: () -> Unit,
    onNavigateToTestDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filterDomain by rememberSaveable { mutableStateOf<TestDomain?>(null) }
    var useGpuBackend by rememberSaveable {
        mutableStateOf(selectedModel.hardwareAcceleration == HardwareBackend.GPU_SUPPORTED)
    }

    val displayedTests =
        if (isRunning || filterDomain == null) testStatuses
        else testStatuses.filter { it.domain == filterDomain }.toImmutableList()

    var isCancelling by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            isCancelling = false
        }
    }

    Column(
        verticalArrangement = spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize().padding(16.dp),
    ) {
        // 1. Top Card with Model Info
        ModelInfoCard(
            model = selectedModel,
            useGpu = useGpuBackend,
            isRunning = isRunning || isInitializing,
            onBackendToggle = { useGpuBackend = it },
        )

        if (isInitializing) {
            InitializationIndicator(message = "Initializing model...")
        }

        // 2. LazyColumn with cells (70% weight)
        TestResultsList(frames = frames, modifier = Modifier.fillMaxWidth().weight(1.0f))

        TestStatusList(
            testStatuses = displayedTests,
            filterDomain = filterDomain,
            availableDomains = availableDomains,
            onSetDomainFilter = { filterDomain = it },
            onNavigateToTestDetails = onNavigateToTestDetails,
            modifier = Modifier.fillMaxWidth(),
        )

        // 4. Run/Cancel Button
        Button(
            onClick = {
                if (isRunning) {
                    isCancelling = true
                    onCancelTests()
                } else {
                    onRunTests(useGpuBackend, filterDomain)
                }
            },
            enabled = !isInitializing && !isCancelling,
        ) {
            if (isCancelling) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(text = "Cancelling...", modifier = Modifier.padding(start = 8.dp))
            } else {
                Text(text = if (isRunning) "Cancel Tests" else "Run Tests")
            }
        }
    }
}

@Composable
private fun ModelInfoCard(
    model: ModelConfiguration,
    useGpu: Boolean,
    onBackendToggle: (Boolean) -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Model info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Model: ${model.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${model.parameterCount}B params • ${model.contextLength} tokens",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Right: CPU/GPU Toggle
            Column(horizontalAlignment = Alignment.End, verticalArrangement = spacedBy(4.dp)) {
                Text(
                    text = if (useGpu) "GPU" else "CPU",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Switch(
                    checked = useGpu,
                    enabled = !isRunning,
                    onCheckedChange = { isChecked -> onBackendToggle(isChecked) },
                )
            }
        }
    }
}

@Composable
private fun TestResultsList(
    frames: ImmutableMap<String, TestResultFrame>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(frames.size, frames.values.lastOrNull()) {
        if (frames.isNotEmpty()) {
            val lastIndex = frames.size - 1
            var lastItemInfo = listState.layoutInfo.visibleItemsInfo.lastOrNull()

            if (lastItemInfo != null && lastItemInfo.index != lastIndex) {
                listState.scrollToItem(lastIndex)
                lastItemInfo = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            }

            if (lastItemInfo != null && lastItemInfo.index == lastIndex) {
                val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                val scrollOffset = (lastItemInfo.size - viewportHeight).coerceAtLeast(0)
                listState.scrollToItem(lastIndex, scrollOffset)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = spacedBy(8.dp),
    ) {
        items(items = frames.values.toList(), key = { frame -> frame.id }) { frame ->
            when (frame) {
                is TestResultFrame.Description -> DescriptionCell(frame)
                is TestResultFrame.Query -> QueryCell(frame)
                is TestResultFrame.Thinking -> ThinkingCell(frame)
                is TestResultFrame.Tool -> ToolCell(frame)
                is TestResultFrame.Content -> ContentCell(frame)
                is TestResultFrame.Validation -> ValidationCell(frame)
            }
        }
    }
}

@Composable
fun InitializationIndicator(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = spacedBy(8.dp),
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TestContentPreview() {
    EdgeLabTheme {
        TestContent(
            frames =
                persistentMapOf(
                    "1" to
                            TestResultFrame.Content(
                                testName = "TEST 0: Basic Response",
                                chunk = "",
                                accumulator = "Hello! I'm doing great, thanks for asking!",
                            ),
                    "2" to
                            TestResultFrame.Validation(
                                testName = "TEST 0: Basic Response",
                                result = ValidationResult.Pass("Valid response received"),
                                duration = 1234,
                                fullContent = "Hello! I'm doing great, thanks for asking!",
                            ),
                ),
            testStatuses =
                listOf(
                    TestStatus(
                        name = "TEST 0: Basic Response",
                        domain = TestDomain.GENERIC,
                        state = TestStatus.State.PASS,
                    )
                )
                    .toImmutableList(),
            availableDomains = listOf(TestDomain.GENERIC, TestDomain.YAZIO).toImmutableList(),
            selectedModel = ModelCatalog.DEFAULT,
            isRunning = false,
            isInitializing = true,
            onRunTests = { _, _ -> },
            onCancelTests = {},
            onNavigateToTestDetails = {},
        )
    }
}
