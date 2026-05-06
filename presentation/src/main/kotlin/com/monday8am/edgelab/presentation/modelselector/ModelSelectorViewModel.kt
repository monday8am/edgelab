package com.monday8am.edgelab.presentation.modelselector

import com.monday8am.edgelab.data.auth.AuthRepository
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.model.ModelRepository
import com.monday8am.edgelab.data.model.RepositoryState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
import kotlinx.coroutines.launch

/** UI State for model selector screen */
data class UiState(
    val models: ImmutableList<ModelInfo> = persistentListOf(),
    val groupedModels: ImmutableList<ModelGroup> = persistentListOf(),
    val groupingMode: GroupingMode = GroupingMode.Family,
    val isAllExpanded: Boolean = true,
    val currentDownload: DownloadInfo? = null,
    val queuedDownloads: ImmutableList<String> = persistentListOf(),
    val statusMessage: String = "Select a model to get started",
    val isLoadingCatalog: Boolean = true,
    val catalogError: String? = null,
    val isLoggedIn: Boolean = false,
    val showDownloadLimitDialog: Boolean = false,
)

enum class GroupingMode {
    None,
    Family,
    Access,
    Author,
}

data class ModelGroup(
    val id: String,
    val title: String,
    val models: ImmutableList<ModelInfo>,
    val isExpanded: Boolean = true,
)

data class ModelInfo(
    val config: ModelConfiguration,
    val isDownloaded: Boolean = false,
    val downloadStatus: DownloadStatus = DownloadStatus.NotStarted,
) {
    val isGated: Boolean
        get() = config.isGated
}

data class DownloadInfo(val modelId: String, val progress: Float)

sealed interface DownloadStatus {
    data object NotStarted : DownloadStatus

    data object Queued : DownloadStatus

    data class Downloading(val progress: Float) : DownloadStatus

    data object Completed : DownloadStatus

    data class Failed(val error: String) : DownloadStatus
}

sealed class UiAction {
    data class DownloadModel(val modelId: String) : UiAction()

    data class CancelDownload(val modelId: String) : UiAction()

    data class DeleteModel(val modelId: String) : UiAction()

    data class SubmitToken(val token: String) : UiAction()

    data object Logout : UiAction()

    data class SetGroupingMode(val mode: GroupingMode) : UiAction()

    data class ToggleGroup(val groupId: String) : UiAction()

    data object ToggleAllGroups : UiAction()

    data object DismissDownloadLimitDialog : UiAction()

    internal data object Initialize : UiAction()
}

interface ModelSelectorViewModel {
    val uiState: StateFlow<UiState>

    fun onUiAction(action: UiAction)

    fun dispose()
}

class ModelSelectorViewModelImpl(
    private val modelDownloadManager: ModelDownloadManager,
    private val modelRepository: ModelRepository,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelSelectorViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private data class ViewModelState(
        val groupingMode: GroupingMode = GroupingMode.Family,
        val collapsedGroupIds: Set<String> = emptySet(),
        val showDownloadLimitDialog: Boolean = false,
    )

    private val viewModelState = MutableStateFlow(ViewModelState())

    override val uiState: StateFlow<UiState> =
        combine(
                modelRepository.loadingState,
                modelDownloadManager.modelsStatus,
                authRepository.authToken,
                viewModelState,
            ) { loadingState, modelsStatus, authToken, viewModelState ->
                deriveUiState(
                    loadingState = loadingState,
                    modelsStatus = modelsStatus,
                    isLoggedIn = authToken != null,
                    viewModelState = viewModelState,
                )
            }
            .flowOn(ioDispatcher)
            .stateIn(scope, SharingStarted.Eagerly, UiState())

    init {
        loadCatalog()
    }

    override fun onUiAction(action: UiAction) {
        when (action) {
            is UiAction.Initialize -> loadCatalog()
            is UiAction.DownloadModel -> startDownload(action.modelId)
            is UiAction.CancelDownload -> cancelDownload(action.modelId)
            is UiAction.DeleteModel -> deleteModel(action.modelId)
            is UiAction.SubmitToken -> submitToken(action.token)
            is UiAction.Logout -> logout()
            is UiAction.SetGroupingMode -> setGroupingMode(action.mode)
            is UiAction.ToggleGroup -> toggleGroup(action.groupId)
            is UiAction.ToggleAllGroups -> toggleAllGroups()
            is UiAction.DismissDownloadLimitDialog ->
                viewModelState.value = viewModelState.value.copy(showDownloadLimitDialog = false)
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun loadCatalog() {
        modelRepository.refreshModels()
    }

    private fun startDownload(modelId: String) {
        val model = modelRepository.findById(modelId) ?: return
        scope.launch {
            val accepted =
                modelDownloadManager.downloadModel(
                    model.modelId,
                    model.downloadUrl,
                    model.bundleFilename,
                )
            if (!accepted) {
                viewModelState.value = viewModelState.value.copy(showDownloadLimitDialog = true)
            }
        }
    }

    private fun cancelDownload(modelId: String) {
        modelDownloadManager.cancelDownload(modelId)
        deleteModel(modelId)
    }

    private fun deleteModel(modelId: String) {
        val model = modelRepository.findById(modelId) ?: return
        scope.launch { modelDownloadManager.deleteModel(model.bundleFilename) }
    }

    private fun submitToken(token: String) {
        scope.launch {
            authRepository.saveToken(token)
            modelRepository.refreshModels()
        }
    }

    private fun logout() {
        scope.launch {
            authRepository.clearToken()
            modelRepository.refreshModels()
        }
    }

    private fun setGroupingMode(mode: GroupingMode) {
        viewModelState.value = viewModelState.value.copy(groupingMode = mode)
    }

    private fun toggleGroup(groupId: String) {
        val currentCollapsed = viewModelState.value.collapsedGroupIds
        val newCollapsed =
            if (currentCollapsed.contains(groupId)) {
                currentCollapsed - groupId
            } else {
                currentCollapsed + groupId
            }
        viewModelState.value = viewModelState.value.copy(collapsedGroupIds = newCollapsed)
    }

    private fun toggleAllGroups() {
        val currentState = viewModelState.value
        val currentGroups = uiState.value.groupedModels

        if (currentState.collapsedGroupIds.isEmpty()) {
            // Collapse all - collect all group IDs from current groups
            val allGroupIds = currentGroups.map { it.id }.toSet()
            viewModelState.value = currentState.copy(collapsedGroupIds = allGroupIds)
        } else {
            // Expand all
            viewModelState.value = currentState.copy(collapsedGroupIds = emptySet())
        }
    }

    private fun deriveUiState(
        loadingState: RepositoryState,
        modelsStatus: Map<String, ModelDownloadManager.Status>,
        isLoggedIn: Boolean,
        viewModelState: ViewModelState,
    ): UiState =
        when (loadingState) {
            is RepositoryState.Idle,
            is RepositoryState.Loading ->
                UiState(
                    isLoadingCatalog = true,
                    statusMessage = "Loading models...",
                    isLoggedIn = isLoggedIn,
                    groupingMode = viewModelState.groupingMode,
                    showDownloadLimitDialog = viewModelState.showDownloadLimitDialog,
                )

            is RepositoryState.Error ->
                UiState(
                    isLoadingCatalog = false,
                    catalogError = loadingState.message,
                    statusMessage = "Error: ${loadingState.message}",
                    isLoggedIn = isLoggedIn,
                    groupingMode = viewModelState.groupingMode,
                    showDownloadLimitDialog = viewModelState.showDownloadLimitDialog,
                )

            is RepositoryState.Success -> {
                var currentDownload: DownloadInfo? = null
                val queuedIds = mutableListOf<String>()

                val modelsInfo =
                    loadingState.models
                        .map { config ->
                            val status =
                                modelsStatus[config.bundleFilename]
                                    ?: ModelDownloadManager.Status.NotStarted
                            val (downloadStatus, isDownloaded) = mapDownloadStatus(status)

                            if (
                                downloadStatus is DownloadStatus.Downloading &&
                                    currentDownload == null
                            ) {
                                currentDownload =
                                    DownloadInfo(config.modelId, downloadStatus.progress)
                            } else if (downloadStatus is DownloadStatus.Queued) {
                                queuedIds.add(config.modelId)
                            }

                            ModelInfo(
                                config = config,
                                isDownloaded = isDownloaded,
                                downloadStatus = downloadStatus,
                            )
                        }
                        .toImmutableList()

                val groupedModels =
                    groupModels(
                        modelsInfo,
                        viewModelState.groupingMode,
                        viewModelState.collapsedGroupIds,
                    )

                UiState(
                    models = modelsInfo,
                    groupedModels = groupedModels.toImmutableList(),
                    groupingMode = viewModelState.groupingMode,
                    isAllExpanded = viewModelState.collapsedGroupIds.isEmpty(),
                    currentDownload = currentDownload,
                    queuedDownloads = queuedIds.toImmutableList(),
                    isLoadingCatalog = false,
                    catalogError = null,
                    isLoggedIn = isLoggedIn,
                    statusMessage =
                        currentDownload?.let { "Downloading: ${it.modelId.take(20)}..." }
                            ?: "Select a model",
                    showDownloadLimitDialog = viewModelState.showDownloadLimitDialog,
                )
            }
        }

    private fun mapDownloadStatus(
        status: ModelDownloadManager.Status
    ): Pair<DownloadStatus, Boolean> {
        val downloadStatus =
            when (status) {
                is ModelDownloadManager.Status.InProgress -> {
                    val progress = status.progress ?: 0f
                    DownloadStatus.Downloading(progress)
                }

                is ModelDownloadManager.Status.Pending -> DownloadStatus.Queued
                is ModelDownloadManager.Status.Completed -> DownloadStatus.Completed
                is ModelDownloadManager.Status.Failed -> DownloadStatus.Failed(status.message)
                is ModelDownloadManager.Status.Cancelled -> DownloadStatus.NotStarted
                ModelDownloadManager.Status.NotStarted -> DownloadStatus.NotStarted
            }
        val isDownloaded = status is ModelDownloadManager.Status.Completed
        return Pair(downloadStatus, isDownloaded)
    }

    private fun groupModels(
        models: List<ModelInfo>,
        groupingMode: GroupingMode,
        collapsedGroupIds: Set<String>,
    ): List<ModelGroup> {
        return when (groupingMode) {
            GroupingMode.None -> {
                listOf(
                    ModelGroup(
                        id = "all",
                        title = "All Models",
                        models = models.toImmutableList(),
                        isExpanded = true,
                    )
                )
            }

            GroupingMode.Family -> {
                models
                    .groupBy { it.config.modelFamily }
                    .map { (family, groupModels) ->
                        ModelGroup(
                            id = "family_$family",
                            title =
                                family.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase() else it.toString()
                                },
                            models = groupModels.toImmutableList(),
                            isExpanded = !collapsedGroupIds.contains("family_$family"),
                        )
                    }
                    .sortedBy { it.title }
            }

            GroupingMode.Access -> {
                models
                    .groupBy { it.config.isGated }
                    .map { (isGated, groupModels) ->
                        ModelGroup(
                            id = "acc_$isGated",
                            title =
                                if (isGated) {
                                    "Need HF token"
                                } else {
                                    "Free for all"
                                },
                            models = groupModels.toImmutableList(),
                            isExpanded = !collapsedGroupIds.contains("acc_$isGated"),
                        )
                    }
                    .sortedBy { it.title }
            }

            GroupingMode.Author -> {
                models
                    .groupBy { it.config.author ?: "Unknown" }
                    .map { (author, groupModels) ->
                        ModelGroup(
                            id = "author_$author",
                            title = author,
                            models = groupModels.toImmutableList(),
                            isExpanded = !collapsedGroupIds.contains("author_$author"),
                        )
                    }
                    .sortedBy { it.title }
            }
        }
    }
}
