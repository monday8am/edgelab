package com.monday8am.edgelab.presentation.testing

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.agent.core.LocalInferenceEngine
import com.monday8am.edgelab.data.model.HardwareBackend
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.testing.AssetsTestRepository
import com.monday8am.edgelab.data.testing.TestDomain
import com.monday8am.edgelab.data.testing.TestRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

data class TestUiState(
    val frames: ImmutableMap<String, TestResultFrame> = persistentMapOf(),
    val selectedModel: ModelConfiguration,
    val isRunning: Boolean = false,
    val isInitializing: Boolean = false,
    val testStatuses: ImmutableList<TestStatus> = persistentListOf(),
    val availableDomains: ImmutableList<TestDomain> = persistentListOf(),
)

data class TestStatus(
    val name: String,
    val domain: TestDomain,
    val state: State,
    val currentTokensPerSecond: Double? = null,
    val averageTokensPerSecond: Double? = null,
    val totalTokens: Int? = null,
) {
    enum class State {
        IDLE,
        RUNNING,
        PASS,
        FAIL,
    }
}

sealed class TestUiAction {
    data class RunTests(val filterDomain: TestDomain?) : TestUiAction()

    data object CancelTests : TestUiAction()
}

interface TestViewModel {
    val uiState: Flow<TestUiState>

    fun onUiAction(uiAction: TestUiAction)

    fun dispose()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestViewModelImpl(
    private val initialModel: ModelConfiguration,
    testRepository: TestRepository = AssetsTestRepository(),
    private val modelPath: String,
    private val inferenceEngine: LocalInferenceEngine,
) : TestViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private sealed class ViewModelState {
        data object Idle : ViewModelState()

        data class Running(val filterDomain: TestDomain?) : ViewModelState()
    }

    private var currentTestEngine: ToolCallingTestEngine? = null
    private val viewModelState = MutableStateFlow<ViewModelState>(ViewModelState.Idle)

    // Load tests on init
    private val loadedTests: StateFlow<List<TestCase>> =
        testRepository
            .getTestsAsFlow()
            .map { definitions -> TestRuleValidator.convert(definitions) }
            .catch { e ->
                Logger.e("Failed to load tests", e)
                emit(emptyList())
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val uiState: StateFlow<TestUiState> =
        combine(viewModelState, loadedTests) { state, tests -> state to tests }
            .flatMapLatest { (state, tests) ->
                when (state) {
                    is ViewModelState.Idle -> flow { emit(createIdleUiState(tests)) }
                    is ViewModelState.Running ->
                        executeTests(filterDomain = state.filterDomain, testCases = tests).map {
                            execState ->
                            deriveUiState(execState, tests)
                        }
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = createIdleUiState(emptyList()),
            )

    override fun onUiAction(uiAction: TestUiAction) {
        when (uiAction) {
            is TestUiAction.RunTests -> {
                viewModelState.value = ViewModelState.Running(filterDomain = uiAction.filterDomain)
            }
            TestUiAction.CancelTests -> {
                currentTestEngine?.cancel()
                viewModelState.value = ViewModelState.Idle
            }
        }
    }

    /**
     * Pure flow transformation: triggers → ExecutionState emissions. Handles backend configuration,
     * engine initialization, and test execution.
     */
    private fun executeTests(
        filterDomain: TestDomain?,
        testCases: List<TestCase>,
    ): Flow<ExecutionState> = flow {
        if (testCases.isEmpty()) {
            Logger.w("No tests loaded to run")
            return@flow
        }

        val filteredTests =
            if (filterDomain == null) testCases else testCases.filter { it.domain == filterDomain }

        val modelConfig = prepareModelConfig()
        val initialResults =
            TestResults(
                statuses =
                    testCases
                        .map { TestStatus(it.name, it.domain, TestStatus.State.IDLE) }
                        .toImmutableList()
            )

        inferenceEngine
            .initializeAsFlow(modelConfig, modelPath)
            .onStart { emit(ExecutionState.Initializing(modelConfig)) }
            .flatMapConcat { engine ->
                ToolCallingTestEngine(
                        streamPromptExecutor = engine::promptStreaming,
                        setToolsAndReset = engine::setToolsAndResetConversation,
                    )
                    .also { currentTestEngine = it }
                    .runAllTests(filteredTests)
            }
            .runningFold(initialResults) { current, frame ->
                TestResults(
                    frames = (current.frames + (frame.id to frame)).toImmutableMap(),
                    statuses = updateTestStatuses(current.statuses, frame).toImmutableList(),
                )
            }
            .map { results ->
                if (
                    results.statuses.last().state in
                        listOf(TestStatus.State.FAIL, TestStatus.State.PASS)
                ) {
                    ExecutionState.Finish(modelConfig, results)
                } else {
                    ExecutionState.Running(modelConfig, results)
                }
            }
            .catch { e ->
                if (e is TestCancelledException) {
                    emit(ExecutionState.Idle(modelConfig))
                } else {
                    Logger.e("Error: ${e.message}")
                    val errorFrame =
                        TestResultFrame.Validation(
                            testName = "Error",
                            result = ValidationResult.Fail("Error: ${e.message}"),
                            duration = 0,
                            fullContent = "",
                        )
                    val errorResults =
                        TestResults(
                            frames = persistentMapOf(errorFrame.id to errorFrame),
                            statuses = initialResults.statuses,
                        )
                    emit(ExecutionState.Finish(modelConfig, errorResults))
                }
            }
            .collect { emit(it) }
    }

    private fun prepareModelConfig(): ModelConfiguration {
        return initialModel.copy(hardwareAcceleration = HardwareBackend.GPU_SUPPORTED)
    }

    private fun deriveUiState(state: ExecutionState, tests: List<TestCase>): TestUiState {
        val availableDomains = tests.map { it.domain }.distinct().toImmutableList()
        val defaultStatuses =
            tests.map { TestStatus(it.name, it.domain, TestStatus.State.IDLE) }.toImmutableList()

        return when (state) {
            is ExecutionState.Idle ->
                TestUiState(
                    selectedModel = state.model,
                    isRunning = false,
                    isInitializing = false,
                    frames = persistentMapOf(),
                    testStatuses = defaultStatuses,
                    availableDomains = availableDomains,
                )

            is ExecutionState.Initializing ->
                TestUiState(
                    selectedModel = state.model,
                    isRunning = true,
                    isInitializing = true,
                    frames = persistentMapOf(),
                    testStatuses = defaultStatuses,
                    availableDomains = availableDomains,
                )

            is ExecutionState.Running ->
                TestUiState(
                    selectedModel = state.model,
                    isRunning = true,
                    isInitializing = false,
                    frames = state.results.frames,
                    testStatuses = state.results.statuses.ifEmpty { defaultStatuses },
                    availableDomains = availableDomains,
                )

            is ExecutionState.Finish ->
                TestUiState(
                    selectedModel = state.model,
                    isRunning = false,
                    isInitializing = false,
                    frames = state.results.frames,
                    testStatuses = state.results.statuses.ifEmpty { defaultStatuses },
                    availableDomains = availableDomains,
                )
        }
    }

    private fun updateTestStatuses(
        currentStatuses: List<TestStatus>,
        frame: TestResultFrame,
    ): List<TestStatus> {
        return when (frame) {
            is TestResultFrame.Description -> {
                currentStatuses.map {
                    if (it.name == frame.testName) it.copy(state = TestStatus.State.RUNNING) else it
                }
            }

            is TestResultFrame.Content,
            is TestResultFrame.Thinking -> {
                currentStatuses.map {
                    if (it.name == frame.testName) {
                        val (currentSpeed, totalTokens) = calculateCurrentSpeed(frame)
                        it.copy(currentTokensPerSecond = currentSpeed, totalTokens = totalTokens)
                    } else {
                        it
                    }
                }
            }

            is TestResultFrame.Validation -> {
                currentStatuses.map {
                    if (it.name == frame.testName) {
                        val state =
                            if (frame.result is ValidationResult.Pass) {
                                TestStatus.State.PASS
                            } else {
                                TestStatus.State.FAIL
                            }
                        val (avgSpeed, totalTokens) = calculateAverageSpeed(frame)
                        it.copy(
                            state = state,
                            averageTokensPerSecond = avgSpeed,
                            totalTokens = totalTokens,
                            currentTokensPerSecond = null,
                        )
                    } else {
                        it
                    }
                }
            }

            else -> currentStatuses
        }
    }

    private fun calculateCurrentSpeed(frame: TestResultFrame): Pair<Double?, Int?> {
        val (elapsedMillis, tokens) =
            when (frame) {
                is TestResultFrame.Content -> frame.elapsedMillis to frame.accumulator.length
                is TestResultFrame.Thinking -> frame.elapsedMillis to frame.accumulator.length
                else -> return Pair(null, null)
            }

        val elapsedSeconds = elapsedMillis / 1000.0
        if (elapsedSeconds <= 0) return Pair(null, tokens)

        val speed = tokens / elapsedSeconds
        return Pair(speed, tokens)
    }

    private fun calculateAverageSpeed(
        validationFrame: TestResultFrame.Validation
    ): Pair<Double?, Int?> {
        val totalTokens = validationFrame.fullContent.length
        val durationSeconds = validationFrame.duration / 1000.0

        if (durationSeconds <= 0) return Pair(null, totalTokens)

        val avgSpeed = totalTokens / durationSeconds
        return Pair(avgSpeed, totalTokens)
    }

    private fun createIdleUiState(tests: List<TestCase>): TestUiState {
        return TestUiState(
            selectedModel = initialModel.copy(hardwareAcceleration = HardwareBackend.GPU_SUPPORTED),
            isRunning = false,
            isInitializing = false,
            frames = persistentMapOf(),
            testStatuses =
                tests
                    .map { TestStatus(it.name, it.domain, TestStatus.State.IDLE) }
                    .toImmutableList(),
            availableDomains = tests.map { it.domain }.distinct().toImmutableList(),
        )
    }

    override fun dispose() {
        inferenceEngine.closeSession()
        scope.cancel()
    }
}

private sealed class ExecutionState {
    abstract val model: ModelConfiguration

    data class Idle(override val model: ModelConfiguration) : ExecutionState()

    data class Initializing(override val model: ModelConfiguration) : ExecutionState()

    data class Running(override val model: ModelConfiguration, val results: TestResults) :
        ExecutionState()

    data class Finish(override val model: ModelConfiguration, val results: TestResults) :
        ExecutionState()
}

private data class TestResults(
    val frames: ImmutableMap<String, TestResultFrame> = persistentMapOf(),
    val statuses: ImmutableList<TestStatus> = persistentListOf(),
)
