package com.monday8am.edgelab.presentation.modelselector

import com.monday8am.edgelab.data.auth.AuthRepository
import com.monday8am.edgelab.data.model.ModelCatalog
import com.monday8am.edgelab.data.model.ModelCatalogProvider
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.model.ModelRepositoryImpl
import com.monday8am.edgelab.presentation.FakeModelDownloadManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

internal class FakeModelCatalogProvider(
    private val models: List<ModelConfiguration> = emptyList(),
    private val shouldFail: Boolean = false,
    private val failureMessage: String = "Test failure",
) : ModelCatalogProvider {
    override fun getModels(): Flow<List<ModelConfiguration>> = flow {
        if (shouldFail) {
            throw Exception(failureMessage)
        } else {
            emit(models)
        }
    }
}

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
class ModelSelectorViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val model1 = ModelCatalog.QWEN3_0_6B
    private val model2 = ModelCatalog.GEMMA3_1B
    private val testModels = listOf(model1, model2)

    private lateinit var fakeRepository: ModelRepositoryImpl
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
        fakeRepository = ModelRepositoryImpl(FakeModelCatalogProvider(testModels), testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        catalogProvider: ModelCatalogProvider = FakeModelCatalogProvider(models = testModels),
        downloadManager: ModelDownloadManager = fakeDownloadManager,
        authRepository: AuthRepository = fakeAuthRepository,
    ): ModelSelectorViewModelImpl {
        fakeRepository = ModelRepositoryImpl(catalogProvider, testDispatcher)
        return ModelSelectorViewModelImpl(
            modelDownloadManager = downloadManager,
            modelRepository = fakeRepository,
            authRepository = authRepository,
            ioDispatcher = testDispatcher,
        )
    }

    // region Initialization Tests

    @Test
    fun `Initialize should load catalog and show models`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.models.size)
        assertFalse(state.isLoadingCatalog)
        assertNull(state.catalogError)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should handle catalog load failure`() = runTest {
        val errorMessage = "Network error"
        val viewModel =
            createViewModel(
                catalogProvider =
                    FakeModelCatalogProvider(shouldFail = true, failureMessage = errorMessage)
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoadingCatalog)
        assertEquals(errorMessage, state.catalogError)
        assertTrue(state.statusMessage.contains(errorMessage))

        viewModel.dispose()
    }

    @Test
    fun `Initialize should allow retry after failure`() = runTest {
        var shouldFail = true
        val catalogProvider =
            object : ModelCatalogProvider {
                override fun getModels(): Flow<List<ModelConfiguration>> = flow {
                    if (shouldFail) {
                        throw Exception("Network error")
                    } else {
                        emit(testModels)
                    }
                }
            }

        val viewModel = createViewModel(catalogProvider = catalogProvider)
        advanceUntilIdle()

        // Verify failure
        assertNotNull(viewModel.uiState.value.catalogError)

        // Retry
        shouldFail = false
        viewModel.onUiAction(UiAction.Initialize)
        advanceUntilIdle()

        // Verify success
        assertNull(viewModel.uiState.value.catalogError)
        assertEquals(2, viewModel.uiState.value.models.size)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should populate repository with models`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2, fakeRepository.getAllModels().size)
        assertNotNull(fakeRepository.findById(model1.modelId))
        assertNotNull(fakeRepository.findById(model2.modelId))

        viewModel.dispose()
    }

    @Test
    fun `Initialize should re-attach to active download`() = runTest {
        // Setup manager with an active download for model1
        modelsStatusFlow.value =
            mapOf(model1.bundleFilename to ModelDownloadManager.Status.InProgress(progress = 42f))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(42f, state.currentDownload?.progress)
        assertEquals(model1.modelId, state.currentDownload?.modelId)

        val model1Info = state.models.find { it.config.modelId == model1.modelId }
        assertNotNull(model1Info)
        assertTrue(model1Info.downloadStatus is DownloadStatus.Downloading)
        assertEquals(42f, model1Info.downloadStatus.progress)

        viewModel.dispose()
    }

    // endregion

    // region Download Flow Tests

    @Test
    fun `DownloadModel should start download when no active download`() = runTest {
        // VM needs models in repository to start download
        fakeRepository.setModels(testModels)

        val downloadManager =
            FakeModelDownloadManager(
                progressSteps = listOf(25f, 50f, 75f),
                modelsStatusFlow = modelsStatusFlow,
            )
        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.DownloadModel(model1.modelId))

        // Wait for progress updates and completion
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Download should complete because fake manager sends Completed at the end
        assertNull(state.currentDownload)
        val model = state.models.find { it.config.modelId == model1.modelId }
        assertNotNull(model)
        assertTrue(model.isDownloaded)
        assertEquals(DownloadStatus.Completed, model.downloadStatus)

        viewModel.dispose()
    }

    @Test
    fun `DownloadModel should queue when another download is active`() = runTest {
        fakeRepository.setModels(testModels)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // In the new reactive architecture, queue is derived from modelsStatus.
        // Set up the flow to show model1 in progress and model2 pending.
        modelsStatusFlow.value =
            mapOf(
                model1.bundleFilename to ModelDownloadManager.Status.InProgress(progress = 10f),
                model2.bundleFilename to ModelDownloadManager.Status.Pending,
            )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.queuedDownloads.size)
        assertEquals(model2.modelId, state.queuedDownloads.first())
        assertEquals(model1.modelId, state.currentDownload?.modelId)

        viewModel.dispose()
    }

    @Test
    fun `CancelDownload should call manager`() = runTest {
        fakeRepository.setModels(testModels)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.DownloadModel(model1.modelId))
        // Trigger cancel
        viewModel.onUiAction(UiAction.CancelDownload(model1.modelId))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.currentDownload)
        assertTrue(state.queuedDownloads.isEmpty())

        viewModel.dispose()
    }

    @Test
    fun `DownloadModel should show limit dialog when rejected`() = runTest {
        fakeRepository.setModels(testModels)
        val downloadManager = FakeModelDownloadManager(shouldReject = true)
        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.DownloadModel(model1.modelId))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showDownloadLimitDialog)

        viewModel.dispose()
    }

    @Test
    fun `DismissDownloadLimitDialog should hide dialog`() = runTest {
        fakeRepository.setModels(testModels)
        val downloadManager = FakeModelDownloadManager(shouldReject = true)
        val viewModel = createViewModel(downloadManager = downloadManager)
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.DownloadModel(model1.modelId))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDownloadLimitDialog)

        viewModel.onUiAction(UiAction.DismissDownloadLimitDialog)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showDownloadLimitDialog)

        viewModel.dispose()
    }

    // endregion

    // region Delete Model Tests

    @Test
    fun `DeleteModel should reset model status`() = runTest {
        fakeRepository.setModels(testModels)

        // Start with model1 as downloaded
        modelsStatusFlow.value =
            mapOf(
                model1.bundleFilename to
                    ModelDownloadManager.Status.Completed(java.io.File("/fake/path"))
            )

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Verify model is initially downloaded
        val initialModel =
            viewModel.uiState.value.models.find { it.config.modelId == model1.modelId }
        assertNotNull(initialModel)
        assertTrue(initialModel.isDownloaded)

        // Delete model - FakeModelDownloadManager removes from modelsStatusFlow
        viewModel.onUiAction(UiAction.DeleteModel(model1.modelId))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val deletedModel = state.models.find { it.config.modelId == model1.modelId }
        assertNotNull(deletedModel)
        // After deletion, model should be NotStarted
        assertEquals(DownloadStatus.NotStarted, deletedModel.downloadStatus)
        assertFalse(deletedModel.isDownloaded)

        viewModel.dispose()
    }

    @Test
    fun `isReady should update reactively when downloadedFilenames changes`() = runTest {
        fakeRepository.setModels(testModels)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Initially not downloaded
        val initialModel =
            viewModel.uiState.value.models.find { it.config.modelId == model1.modelId }
        assertNotNull(initialModel)
        assertFalse(updatedModelIsReady(viewModel, model1.modelId))

        // Simulate file appearing
        fakeDownloadManager.setDownloadedFilenames(setOf(model1.bundleFilename))
        advanceUntilIdle()

        assertTrue(updatedModelIsReady(viewModel, model1.modelId))

        viewModel.dispose()
    }

    // region Auth Tests

    @Test
    fun `SubmitToken should save token and close dialog`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.SubmitToken("test-token"))
        advanceUntilIdle()

        assertEquals("test-token", fakeAuthRepository.authToken.value)
        assertTrue(viewModel.uiState.value.isLoggedIn)

        viewModel.dispose()
    }

    @Test
    fun `Logout should clear token`() = runTest {
        fakeAuthRepository.saveToken("existing-token")
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoggedIn)

        viewModel.onUiAction(UiAction.Logout)
        advanceUntilIdle()

        assertNull(fakeAuthRepository.authToken.value)
        assertFalse(viewModel.uiState.value.isLoggedIn)

        viewModel.dispose()
    }

    private fun updatedModelIsReady(
        viewModel: ModelSelectorViewModelImpl,
        modelId: String,
    ): Boolean {
        return viewModel.uiState.value.models.find { it.config.modelId == modelId }?.isDownloaded
            ?: false
    }

    // endregion

    // region Grouping Tests

    @Test
    fun `SetGroupingMode should update groupedModels`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Default mode is Family
        val stateFamily = viewModel.uiState.value
        assertEquals(GroupingMode.Family, stateFamily.groupingMode)
        // model1 is Qwen3, model2 is Gemma3 => 2 families.
        assertEquals(2, stateFamily.groupedModels.size)
        assertEquals("family_gemma3", stateFamily.groupedModels.first().id)

        // Switch to None
        viewModel.onUiAction(UiAction.SetGroupingMode(GroupingMode.None))
        advanceUntilIdle()

        val stateNone = viewModel.uiState.value
        assertEquals(GroupingMode.None, stateNone.groupingMode)
        assertEquals(1, stateNone.groupedModels.size)
        assertEquals("all", stateNone.groupedModels.first().id)

        // Verify groups are populated
        assertTrue(stateNone.groupedModels.all { it.models.isNotEmpty() })

        viewModel.dispose()
    }

    @Test
    fun `ToggleGroup should update isExpanded state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.SetGroupingMode(GroupingMode.Family))
        advanceUntilIdle()

        val initialGroup = viewModel.uiState.value.groupedModels.first()
        assertTrue(initialGroup.isExpanded)

        viewModel.onUiAction(UiAction.ToggleGroup(initialGroup.id))
        advanceUntilIdle()

        val collapsedGroup =
            viewModel.uiState.value.groupedModels.first { it.id == initialGroup.id }
        assertFalse(collapsedGroup.isExpanded)

        viewModel.dispose()
    }

    @Test
    fun `ToggleAllGroups should collapse all then expand all`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.SetGroupingMode(GroupingMode.Family))
        advanceUntilIdle()

        // Initial state: all expanded
        assertTrue(viewModel.uiState.value.isAllExpanded)
        assertTrue(viewModel.uiState.value.groupedModels.all { it.isExpanded })

        // Collapse all
        viewModel.onUiAction(UiAction.ToggleAllGroups)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAllExpanded)
        assertTrue(viewModel.uiState.value.groupedModels.all { !it.isExpanded })

        // Expand all
        viewModel.onUiAction(UiAction.ToggleAllGroups)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAllExpanded)
        assertTrue(viewModel.uiState.value.groupedModels.all { it.isExpanded })

        viewModel.dispose()
    }

    // endregion
}
