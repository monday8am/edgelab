package com.monday8am.edgelab.presentation.onboard

import com.monday8am.edgelab.data.auth.AuthRepository
import com.monday8am.edgelab.presentation.FakeModelDownloadManager
import com.monday8am.edgelab.presentation.modelselector.ModelDownloadManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

internal class FakeAuthRepository(initialToken: String? = null) : AuthRepository {
    private val _authToken = MutableStateFlow(initialToken)
    override val authToken: StateFlow<String?> = _authToken.asStateFlow()

    override suspend fun saveToken(token: String) {
        _authToken.value = token
    }

    override suspend fun clearToken() {
        _authToken.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeDownloadManager: FakeModelDownloadManager
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var modelsStatusFlow:
        MutableStateFlow<Map<String, ModelDownloadManager.Status>>

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        modelsStatusFlow = MutableStateFlow(emptyMap())
        fakeDownloadManager = FakeModelDownloadManager(modelsStatusFlow = modelsStatusFlow)
        fakeAuthRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        downloadManager: ModelDownloadManager = fakeDownloadManager,
        authRepository: AuthRepository = fakeAuthRepository,
    ): OnboardViewModelImpl {
        return OnboardViewModelImpl(
            modelDownloadManager = downloadManager,
            authRepository = authRepository,
            ioDispatcher = testDispatcher,
        )
    }

    // region Initialization Tests

    @Test
    fun `Initialize should show two models`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.models.size)
        assertFalse(state.isLoadingCatalog)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should show correct model configurations`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val modelIds = state.models.map { it.config.modelId }
        assertTrue(modelIds.contains("Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"))
        assertTrue(modelIds.contains("cycling-copilot_q8_ekv1024.litertlm"))

        viewModel.dispose()
    }

    @Test
    fun `Initialize should show not logged in when no auth token`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoggedIn)
        assertTrue(state.statusMessage.contains("Sign in"))

        viewModel.dispose()
    }

    @Test
    fun `Initialize should show logged in when auth token exists`() = runTest {
        fakeAuthRepository.saveToken("test-token")
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoggedIn)
        assertFalse(state.statusMessage.contains("Sign in"))

        viewModel.dispose()
    }

    // endregion

    // region Download Flow Tests

    @Test
    fun `DownloadModel should start download for gemma3-1b model`() = runTest {
        fakeAuthRepository.saveToken("test-token")
        val downloadManager =
            FakeModelDownloadManager(
                progressSteps = listOf(25f, 50f, 75f),
                modelsStatusFlow = modelsStatusFlow,
            )
        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        viewModel.onUiAction(
            UiAction.DownloadModel("Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm")
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val model =
            state.models.find {
                it.config.modelId == "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
            }
        assertNotNull(model)
        assertTrue(model.isDownloaded)
        assertEquals(DownloadStatus.Completed, model.downloadStatus)

        viewModel.dispose()
    }

    @Test
    fun `DownloadModel should start download for cycling-copilot model`() = runTest {
        fakeAuthRepository.saveToken("test-token")
        val downloadManager =
            FakeModelDownloadManager(
                progressSteps = listOf(25f, 50f, 75f),
                modelsStatusFlow = modelsStatusFlow,
            )
        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.DownloadModel("cycling-copilot_q8_ekv1024.litertlm"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val model = state.models.find { it.config.modelId == "cycling-copilot_q8_ekv1024.litertlm" }
        assertNotNull(model)
        assertTrue(model.isDownloaded)

        viewModel.dispose()
    }

    @Test
    fun `DownloadModel should show progress during download`() = runTest {
        fakeAuthRepository.saveToken("test-token")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Simulate download in progress
        modelsStatusFlow.value =
            mapOf(
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm" to
                    ModelDownloadManager.Status.InProgress(progress = 42f)
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val model =
            state.models.find {
                it.config.modelId == "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
            }
        assertNotNull(model)
        assertTrue(model.downloadStatus is DownloadStatus.Downloading)
        assertEquals(42f, (model.downloadStatus as DownloadStatus.Downloading).progress)

        viewModel.dispose()
    }

    @Test
    fun `DownloadModel should support parallel downloads`() = runTest {
        fakeAuthRepository.saveToken("test-token")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Simulate both models downloading simultaneously
        modelsStatusFlow.value =
            mapOf(
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm" to
                    ModelDownloadManager.Status.InProgress(progress = 30f),
                "cycling-copilot_q8_ekv1024.litertlm" to
                    ModelDownloadManager.Status.InProgress(progress = 50f),
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val userModel =
            state.models.find {
                it.config.modelId == "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
            }
        val gemmaModel =
            state.models.find { it.config.modelId == "cycling-copilot_q8_ekv1024.litertlm" }

        assertNotNull(userModel)
        assertNotNull(gemmaModel)
        assertTrue(userModel.downloadStatus is DownloadStatus.Downloading)
        assertTrue(gemmaModel.downloadStatus is DownloadStatus.Downloading)

        viewModel.dispose()
    }

    @Test
    fun `CancelDownload should call download manager`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Simulate download in progress
        modelsStatusFlow.value =
            mapOf(
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm" to
                    ModelDownloadManager.Status.InProgress(progress = 30f)
            )
        advanceUntilIdle()

        viewModel.onUiAction(
            UiAction.CancelDownload("Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm")
        )
        advanceUntilIdle()

        assertEquals(1, fakeDownloadManager.deleteModelCallCount)
        assertEquals(
            "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
            fakeDownloadManager.lastDeletedBundleFilename,
        )
        viewModel.dispose()
    }

    @Test
    fun `StatusMessage should update to downloading when download active`() = runTest {
        fakeAuthRepository.saveToken("test-token")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Simulate download in progress
        modelsStatusFlow.value =
            mapOf(
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm" to
                    ModelDownloadManager.Status.InProgress(progress = 30f)
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.statusMessage.contains("Downloading"))

        viewModel.dispose()
    }

    @Test
    fun `StatusMessage should update to all ready when both downloaded`() = runTest {
        fakeAuthRepository.saveToken("test-token")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Simulate both models completed
        fakeDownloadManager.setDownloadedFilenames(
            setOf(
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
                "cycling-copilot_q8_ekv1024.litertlm",
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.statusMessage.contains("All models ready"))

        viewModel.dispose()
    }

    @Test
    fun `Download should handle failure gracefully`() = runTest {
        fakeAuthRepository.saveToken("test-token")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Simulate download failure
        modelsStatusFlow.value =
            mapOf(
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm" to
                    ModelDownloadManager.Status.Failed("Network error")
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val model =
            state.models.find {
                it.config.modelId == "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
            }
        assertNotNull(model)
        assertTrue(model.downloadStatus is DownloadStatus.Failed)
        assertEquals("Network error", (model.downloadStatus as DownloadStatus.Failed).error)

        viewModel.dispose()
    }

    // endregion

    // region Auth Tests

    @Test
    fun `SubmitToken should save token`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoggedIn)

        viewModel.onUiAction(UiAction.SubmitToken("test-token"))
        advanceUntilIdle()

        assertEquals("test-token", fakeAuthRepository.authToken.value)
        assertTrue(viewModel.uiState.value.isLoggedIn)

        viewModel.dispose()
    }

    @Test
    fun `SubmitToken should update status message`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val beforeState = viewModel.uiState.value
        assertTrue(beforeState.statusMessage.contains("Sign in"))

        viewModel.onUiAction(UiAction.SubmitToken("test-token"))
        advanceUntilIdle()

        val afterState = viewModel.uiState.value
        assertFalse(afterState.statusMessage.contains("Sign in"))

        viewModel.dispose()
    }

    @Test
    fun `StartOAuth should not throw exception`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // StartOAuth is handled by UI layer, should not throw
        viewModel.onUiAction(UiAction.StartOAuth)
        advanceUntilIdle()

        viewModel.dispose()
    }

    // endregion

    // region State Derivation Tests

    @Test
    fun `Models should initially be not downloaded`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.models.all { !it.isDownloaded })
        assertTrue(state.models.all { it.downloadStatus is DownloadStatus.NotStarted })

        viewModel.dispose()
    }

    @Test
    fun `Download status should update reactively`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Initially not started
        var state = viewModel.uiState.value
        var model =
            state.models.find {
                it.config.modelId == "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
            }
        assertNotNull(model)
        assertTrue(model.downloadStatus is DownloadStatus.NotStarted)

        // Update to downloading
        modelsStatusFlow.value =
            mapOf(
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm" to
                    ModelDownloadManager.Status.InProgress(progress = 50f)
            )
        advanceUntilIdle()

        state = viewModel.uiState.value
        model =
            state.models.find {
                it.config.modelId == "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
            }
        assertNotNull(model)
        assertTrue(model.downloadStatus is DownloadStatus.Downloading)

        // Update to completed
        fakeDownloadManager.setDownloadedFilenames(
            setOf("Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm")
        )
        advanceUntilIdle()

        state = viewModel.uiState.value
        model =
            state.models.find {
                it.config.modelId == "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
            }
        assertNotNull(model)
        assertTrue(model.isDownloaded)
        assertEquals(DownloadStatus.Completed, model.downloadStatus)

        viewModel.dispose()
    }

    @Test
    fun `Cancelled download should reset to not started`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Simulate cancelled download
        modelsStatusFlow.value =
            mapOf(
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm" to
                    ModelDownloadManager.Status.Cancelled
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val model =
            state.models.find {
                it.config.modelId == "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
            }
        assertNotNull(model)
        assertEquals(DownloadStatus.NotStarted, model.downloadStatus)
        assertFalse(model.isDownloaded)

        viewModel.dispose()
    }

    // endregion
}
