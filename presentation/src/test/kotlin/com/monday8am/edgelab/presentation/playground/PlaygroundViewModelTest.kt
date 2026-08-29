package com.monday8am.edgelab.presentation.playground

import com.monday8am.edgelab.agent.playground.HeuristicToolOutputJudge
import com.monday8am.edgelab.agent.playground.TurnToolCall
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PlaygroundViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var probes: FakeProbeRepository
    private lateinit var backendFactory: RecordingBackendFactory
    private lateinit var backend: FakePlaygroundBackend
    private lateinit var downloadManager: FakeModelDownloadManagerForPlayground
    private lateinit var modelRepo: FakeModelRepository
    private lateinit var judgeFactory: RecordingJudgeFactory
    private lateinit var secondOpinionJudge: FakeToolOutputJudge

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        probes = FakeProbeRepository()
        backendFactory = RecordingBackendFactory()
        backend = backendFactory.backend()
        downloadManager =
            FakeModelDownloadManagerForPlayground(
                downloadedFilenames = setOf(TEST_MODEL.bundleFilename)
            )
        modelRepo = FakeModelRepository(listOf(TEST_MODEL))
        secondOpinionJudge = FakeToolOutputJudge()
        judgeFactory = RecordingJudgeFactory().apply { judge = secondOpinionJudge }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PlaygroundViewModelImpl =
        PlaygroundViewModelImpl(
            probeRepository = probes,
            modelDownloadManager = downloadManager,
            modelRepository = modelRepo,
            backendFactory = backendFactory,
            heuristicJudge = HeuristicToolOutputJudge(),
            judgeFactory = judgeFactory,
            ioDispatcher = testDispatcher,
        )

    // region Initialization Tests

    @Test
    fun `Initialize should default to the cloud target so no download is needed`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(PlaygroundTarget.Cloud, viewModel.uiState.value.target)
        assertFalse(viewModel.uiState.value.isRunning)
        assertNull(viewModel.uiState.value.error)
        viewModel.dispose()
    }

    @Test
    fun `RunPrompt should work with no model downloaded at all`() = runTest {
        downloadManager = FakeModelDownloadManagerForPlayground(downloadedFilenames = emptySet())
        val viewModel = createViewModel()
        advanceUntilIdle()

        backend.text = "You are in Siena."
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Where am I?"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals<List<PlaygroundTarget>>(
            listOf(PlaygroundTarget.Cloud),
            backendFactory.requestedTargets,
        )
        assertTrue(viewModel.uiState.value.trace.any { it is TraceEntry.ModelText })
        viewModel.dispose()
    }

    @Test
    fun `Initialize should expose downloaded models as switchable targets`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.availableModels.size)
        assertEquals(TEST_MODEL.modelId, viewModel.uiState.value.availableModels.first().modelId)
        viewModel.dispose()
    }

    @Test
    fun `Initialize should load the catalog so a downloaded model is switchable on a fresh start`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(1, modelRepo.refreshCallCount)
            assertEquals(1, viewModel.uiState.value.availableModels.size)
            viewModel.dispose()
        }

    @Test
    fun `Initialize should expose preset probes from the repository`() = runTest {
        val preset = testTool("get_location")
        probes.setProbes(listOf(preset), mapOf("get_location" to "{\"lat\":1}"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.availableProbes.size)
        assertEquals("get_location", viewModel.uiState.value.availableProbes.first().function.name)
        viewModel.dispose()
    }

    // endregion

    // region Target switching Tests

    @Test
    fun `SelectTarget should switch to a local model and rebuild the backend`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("one"))
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.SelectTarget(PlaygroundTarget.Local(TEST_MODEL)))
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("two"))
        advanceUntilIdle()

        assertEquals(
            listOf(PlaygroundTarget.Cloud, PlaygroundTarget.Local(TEST_MODEL)),
            backendFactory.requestedTargets,
        )
        assertEquals(1, backend.closeCallCount)
        viewModel.dispose()
    }

    // endregion

    // region Probe add/remove Tests

    @Test
    fun `AddProbe then RemoveProbe should toggle active probes`() = runTest {
        val preset = testTool("get_weather")
        probes.setProbes(listOf(preset), mapOf("get_weather" to "{\"ok\":true}"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.AddProbe(preset))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.activeProbes.size)

        viewModel.onUiAction(PlaygroundUiAction.RemoveProbe(preset))
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.activeProbes.size)
        viewModel.dispose()
    }

    @Test
    fun `AddProbe should be idempotent for the same probe`() = runTest {
        val preset = testTool("get_weather")
        probes.setProbes(listOf(preset), mapOf("get_weather" to "{\"ok\":true}"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.AddProbe(preset))
        viewModel.onUiAction(PlaygroundUiAction.AddProbe(preset))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.activeProbes.size)
        viewModel.dispose()
    }

    @Test
    fun `RunPrompt should hand the active probes and mocks to the backend`() = runTest {
        val preset = testTool("get_weather")
        val mocks = mapOf("get_weather" to "{\"ok\":true}")
        probes.setProbes(listOf(preset), mocks)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.AddProbe(preset))
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Weather?"))
        advanceUntilIdle()

        assertEquals(listOf(preset), backend.lastTools)
        assertEquals(mocks, backend.lastMockResponses)
        viewModel.dispose()
    }

    // endregion

    // region Run prompt Tests

    @Test
    fun `RunPrompt with blank prompt should set an error and not call the backend`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.RunPrompt(""))
        advanceUntilIdle()

        assertEquals("Type a prompt first", viewModel.uiState.value.error)
        assertEquals(0, backend.runCallCount)
        viewModel.dispose()
    }

    @Test
    fun `RunPrompt should append user prompt and model text to the trace`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        backend.text = "You are in Siena."
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Where am I?"))
        advanceUntilIdle()

        val trace = viewModel.uiState.value.trace
        assertEquals(2, trace.size)
        assertTrue(trace[0] is TraceEntry.UserPrompt)
        assertEquals("Where am I?", (trace[0] as TraceEntry.UserPrompt).text)
        assertTrue(trace[1] is TraceEntry.ModelText)
        assertEquals("You are in Siena.", (trace[1] as TraceEntry.ModelText).text)
        assertEquals(null, (trace[1] as TraceEntry.ModelText).usedToolOutput)
        assertFalse(viewModel.uiState.value.isRunning)
        viewModel.dispose()
    }

    @Test
    fun `RunPrompt should surface an error trace entry when inference fails`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        backend.runShouldFail = true
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Hi"))
        advanceUntilIdle()

        val trace = viewModel.uiState.value.trace
        assertTrue(trace.any { it is TraceEntry.Error })
        assertFalse(viewModel.uiState.value.isRunning)
        viewModel.dispose()
    }

    @Test
    fun `RunPrompt should surface an error when the backend fails to initialize`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        backend.initializeShouldFail = true
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Hi"))
        advanceUntilIdle()

        val trace = viewModel.uiState.value.trace
        assertTrue(trace.any { it is TraceEntry.Error })
        assertEquals(0, backend.runCallCount)
        viewModel.dispose()
    }

    @Test
    fun `RunPrompt should reuse the same backend across turns for the same target`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("one"))
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("two"))
        advanceUntilIdle()

        assertEquals<List<PlaygroundTarget>>(
            listOf(PlaygroundTarget.Cloud),
            backendFactory.requestedTargets,
        )
        assertEquals(2, backend.runCallCount)
        viewModel.dispose()
    }

    // endregion

    // region Used/ignored tool output Tests

    @Test
    fun `RunPrompt should tag model text as used when the heuristic finds the mock output`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            backend.toolCalls = listOf(TurnToolCall("get_weather", emptyMap(), """{"tempC": 21}"""))
            backend.text = "It's 21 degrees in Madrid."
            viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Weather?"))
            advanceUntilIdle()

            val modelText =
                viewModel.uiState.value.trace.filterIsInstance<TraceEntry.ModelText>().single()
            assertEquals(ToolOutputVerdict.USED, modelText.usedToolOutput)
            assertEquals(0, secondOpinionJudge.callCount)
            viewModel.dispose()
        }

    @Test
    fun `RunPrompt should ask for a second opinion when the heuristic finds nothing`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        backend.toolCalls = listOf(TurnToolCall("get_weather", emptyMap(), """{"tempC": 21}"""))
        backend.text = "I cannot check the weather right now."
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Weather?"))
        advanceUntilIdle()

        val modelText =
            viewModel.uiState.value.trace.filterIsInstance<TraceEntry.ModelText>().single()
        assertEquals(ToolOutputVerdict.IGNORED, modelText.usedToolOutput)
        assertEquals(1, secondOpinionJudge.callCount)
        assertEquals("get_weather: {\"tempC\": 21}", secondOpinionJudge.lastMock)
        assertEquals("I cannot check the weather right now.", secondOpinionJudge.lastText)
        viewModel.dispose()
    }

    @Test
    fun `RunPrompt should keep apparently-ignored while the second opinion is pending`() = runTest {
        val gated = GatedToolOutputJudge().apply { verdict = true }
        judgeFactory.judge = gated
        val viewModel = createViewModel()
        advanceUntilIdle()

        backend.toolCalls = listOf(TurnToolCall("get_weather", emptyMap(), """{"tempC": 21}"""))
        backend.text = "I cannot check the weather right now."
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Weather?"))
        advanceUntilIdle()

        val pending =
            viewModel.uiState.value.trace.filterIsInstance<TraceEntry.ModelText>().single()
        assertEquals(ToolOutputVerdict.APPARENTLY_IGNORED, pending.usedToolOutput)

        gated.release()
        advanceUntilIdle()

        val resolved =
            viewModel.uiState.value.trace.filterIsInstance<TraceEntry.ModelText>().single()
        assertEquals(ToolOutputVerdict.USED, resolved.usedToolOutput)
        viewModel.dispose()
    }

    @Test
    fun `RunPrompt should keep apparently-ignored when the second opinion abstains`() = runTest {
        secondOpinionJudge.verdict = null
        val viewModel = createViewModel()
        advanceUntilIdle()

        backend.toolCalls = listOf(TurnToolCall("get_weather", emptyMap(), """{"tempC": 21}"""))
        backend.text = "I cannot check the weather right now."
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Weather?"))
        advanceUntilIdle()

        val modelText =
            viewModel.uiState.value.trace.filterIsInstance<TraceEntry.ModelText>().single()
        assertEquals(ToolOutputVerdict.APPARENTLY_IGNORED, modelText.usedToolOutput)
        assertEquals(1, secondOpinionJudge.callCount)
        viewModel.dispose()
    }

    @Test
    fun `Local target should keep apparently-ignored and never consult a judge`() = runTest {
        judgeFactory.judge = null
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.SelectTarget(PlaygroundTarget.Local(TEST_MODEL)))
        advanceUntilIdle()
        backend.toolCalls = listOf(TurnToolCall("get_weather", emptyMap(), """{"tempC": 21}"""))
        backend.text = "I cannot check the weather right now."
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Weather?"))
        advanceUntilIdle()

        val modelText =
            viewModel.uiState.value.trace.filterIsInstance<TraceEntry.ModelText>().single()
        assertEquals(ToolOutputVerdict.APPARENTLY_IGNORED, modelText.usedToolOutput)
        viewModel.dispose()
    }

    // endregion

    // region Clear + dispose Tests

    @Test
    fun `ClearTrace should empty the trace and error`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Hi"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.trace.isNotEmpty())

        viewModel.onUiAction(PlaygroundUiAction.ClearTrace)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.trace.size)
        assertNull(viewModel.uiState.value.error)
        viewModel.dispose()
    }

    @Test
    fun `dispose should close the backend`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.RunPrompt("Hi"))
        advanceUntilIdle()

        viewModel.dispose()
        assertTrue(backend.closeCallCount >= 1)
    }

    // endregion
}
