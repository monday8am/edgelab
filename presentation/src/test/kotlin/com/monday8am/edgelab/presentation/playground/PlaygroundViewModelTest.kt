package com.monday8am.edgelab.presentation.playground

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
        // The whole point of the cloud onboarding leg: zero download, straight to a Trace.
        downloadManager = FakeModelDownloadManagerForPlayground(downloadedFilenames = emptySet())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.PromptChanged("Where am I?"))
        backend.text = "You are in Siena."
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals<List<PlaygroundTarget>>(listOf(PlaygroundTarget.Cloud), backendFactory.requestedTargets)
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
    fun `Initialize should expose preset probes from the repository`() = runTest {
        val preset = testProbe("get_location", mockResponse = "{\"lat\":1}")
        probes.setProbes(listOf(preset))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.availableProbes.size)
        assertEquals("get_location", viewModel.uiState.value.availableProbes.first().name)
        viewModel.dispose()
    }

    // endregion

    // region Target switching Tests

    @Test
    fun `SelectTarget should switch to a local model and rebuild the backend`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.PromptChanged("one"))
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.SelectTarget(PlaygroundTarget.Local(TEST_MODEL)))
        viewModel.onUiAction(PlaygroundUiAction.PromptChanged("two"))
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
        advanceUntilIdle()

        assertEquals(
            listOf(PlaygroundTarget.Cloud, PlaygroundTarget.Local(TEST_MODEL)),
            backendFactory.requestedTargets,
        )
        // Switching away must release the backend that was in use.
        assertEquals(1, backend.closeCallCount)
        viewModel.dispose()
    }

    // endregion

    // region Probe add/remove Tests

    @Test
    fun `AddProbe then RemoveProbe should toggle active probes`() = runTest {
        val preset = testProbe("get_weather")
        probes.setProbes(listOf(preset))
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
        val preset = testProbe("get_weather")
        probes.setProbes(listOf(preset))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.AddProbe(preset))
        viewModel.onUiAction(PlaygroundUiAction.AddProbe(preset))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.activeProbes.size)
        viewModel.dispose()
    }

    @Test
    fun `RunPrompt should hand the active probes to the backend`() = runTest {
        val preset = testProbe("get_weather")
        probes.setProbes(listOf(preset))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.AddProbe(preset))
        viewModel.onUiAction(PlaygroundUiAction.PromptChanged("Weather?"))
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
        advanceUntilIdle()

        assertEquals(listOf(preset), backend.lastProbes)
        viewModel.dispose()
    }

    // endregion

    // region Run prompt Tests

    @Test
    fun `RunPrompt with blank prompt should set an error and not call the backend`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
        advanceUntilIdle()

        assertEquals("Type a prompt first", viewModel.uiState.value.error)
        assertEquals(0, backend.runCallCount)
        viewModel.dispose()
    }

    @Test
    fun `RunPrompt should append user prompt and model text to the trace`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.PromptChanged("Where am I?"))
        backend.text = "You are in Siena."
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
        advanceUntilIdle()

        val trace = viewModel.uiState.value.trace
        assertEquals(2, trace.size)
        assertTrue(trace[0] is TraceEntry.UserPrompt)
        assertEquals("Where am I?", (trace[0] as TraceEntry.UserPrompt).text)
        assertTrue(trace[1] is TraceEntry.ModelText)
        assertEquals("You are in Siena.", (trace[1] as TraceEntry.ModelText).text)
        // The prompt field is cleared after sending.
        assertEquals("", viewModel.uiState.value.prompt)
        assertFalse(viewModel.uiState.value.isRunning)
        viewModel.dispose()
    }

    @Test
    fun `RunPrompt should surface an error trace entry when inference fails`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.PromptChanged("Hi"))
        backend.runShouldFail = true
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
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

        viewModel.onUiAction(PlaygroundUiAction.PromptChanged("Hi"))
        backend.initializeShouldFail = true
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
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

        viewModel.onUiAction(PlaygroundUiAction.PromptChanged("one"))
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.PromptChanged("two"))
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
        advanceUntilIdle()

        assertEquals<List<PlaygroundTarget>>(listOf(PlaygroundTarget.Cloud), backendFactory.requestedTargets)
        assertEquals(2, backend.runCallCount)
        viewModel.dispose()
    }

    // endregion

    // region Clear + dispose Tests

    @Test
    fun `ClearTrace should empty the trace and error`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(PlaygroundUiAction.PromptChanged("Hi"))
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
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

        viewModel.onUiAction(PlaygroundUiAction.PromptChanged("Hi"))
        viewModel.onUiAction(PlaygroundUiAction.RunPrompt)
        advanceUntilIdle()

        viewModel.dispose()
        assertTrue(backend.closeCallCount >= 1)
    }

    // endregion
}
