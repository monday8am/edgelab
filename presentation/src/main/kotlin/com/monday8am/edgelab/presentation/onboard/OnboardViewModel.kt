package com.monday8am.edgelab.presentation.onboard

import com.monday8am.edgelab.data.auth.AuthRepository
import com.monday8am.edgelab.data.model.HardwareBackend
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.presentation.modelselector.ModelDownloadManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val models: ImmutableList<ModelInfo> = persistentListOf(),
    val isLoadingCatalog: Boolean = false,
    val isLoggedIn: Boolean = false,
    val statusMessage: String = "Sign in to download models",
)

data class ModelInfo(
    val config: ModelConfiguration,
    val isDownloaded: Boolean = false,
    val downloadStatus: DownloadStatus = DownloadStatus.NotStarted,
)

sealed interface DownloadStatus {
    data object NotStarted : DownloadStatus

    data class Downloading(val progress: Float) : DownloadStatus

    data object Completed : DownloadStatus

    data class Failed(val error: String) : DownloadStatus
}

sealed class UiAction {
    data class DownloadModel(val modelId: String) : UiAction()

    data class CancelDownload(val modelId: String) : UiAction()

    data class SubmitToken(val token: String) : UiAction()

    data object StartOAuth : UiAction()
}

interface OnboardViewModel {
    val uiState: StateFlow<UiState>

    fun onUiAction(action: UiAction)

    fun dispose()
}

class OnboardViewModelImpl(
    private val modelDownloadManager: ModelDownloadManager,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OnboardViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        // TODO: Replace with actual URLs once provided by user
        private val COPILOT_MODELS =
            listOf(
                ModelConfiguration(
                    displayName = "Gemma3-1B",
                    modelFamily = "gemma3",
                    parameterCount = 1.0f,
                    quantization = "q4",
                    contextLength = 4096,
                    downloadUrl =
                        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
                    bundleFilename = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
                    hardwareAcceleration = HardwareBackend.GPU_SUPPORTED,
                    defaultTopK = 40,
                    defaultTopP = 0.85f,
                    defaultTemperature = 0.2f,
                    defaultMaxOutputTokens = 1024,
                    author = "litert-community",
                    isGated = true,
                    description = null,
                    fileSizeBytes = 584417280,
                    huggingFaceUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT)",
                ),
                ModelConfiguration(
                    displayName = "Cycling",
                    modelFamily = "gemma3",
                    parameterCount = 1.0f,
                    quantization = "q8",
                    contextLength = 1024,
                    downloadUrl =
                        "https://huggingface.co/monday8am/cycling-copilot-functiongemma/resolve/main/cycling-copilot_q8_ekv1024.litertlm",
                    bundleFilename = "cycling-copilot_q8_ekv1024.litertlm",
                    hardwareAcceleration = HardwareBackend.GPU_SUPPORTED,
                    defaultTopK = 40,
                    defaultTopP = 0.85f,
                    defaultTemperature = 0.2f,
                    defaultMaxOutputTokens = 256,
                    author = "monday8am",
                    isGated = false,
                    description = null,
                    fileSizeBytes = 284426240,
                    huggingFaceUrl =
                        "https://huggingface.co/monday8am/cycling-copilot-functiongemma",
                ),
            )
    }

    override val uiState: StateFlow<UiState> =
        combine(modelDownloadManager.modelsStatus, authRepository.authToken) {
                modelsStatus,
                authToken ->
                deriveUiState(modelsStatus = modelsStatus, isLoggedIn = authToken != null)
            }
            .flowOn(ioDispatcher)
            .stateIn(scope, SharingStarted.Eagerly, UiState())

    override fun onUiAction(action: UiAction) {
        when (action) {
            is UiAction.DownloadModel -> startDownload(action.modelId)
            is UiAction.CancelDownload -> cancelDownload(action.modelId)
            is UiAction.SubmitToken -> submitToken(action.token)
            is UiAction.StartOAuth -> {
                /* OAuth flow handled by UI layer */
            }
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun startDownload(modelId: String) {
        val model = COPILOT_MODELS.find { it.modelId == modelId } ?: return
        scope.launch { modelDownloadManager.downloadModel(model.downloadUrl, model.bundleFilename) }
    }

    private fun cancelDownload(modelId: String) {
        val model = COPILOT_MODELS.find { it.modelId == modelId } ?: return
        modelDownloadManager.cancelDownload(model.bundleFilename)
        scope.launch { modelDownloadManager.deleteModel(model.bundleFilename) }
    }

    private fun submitToken(token: String) {
        scope.launch { authRepository.saveToken(token) }
    }

    private fun deriveUiState(
        modelsStatus: Map<String, ModelDownloadManager.Status>,
        isLoggedIn: Boolean,
    ): UiState {
        val modelsInfo =
            COPILOT_MODELS.map { config ->
                    val status =
                        modelsStatus[config.bundleFilename]
                            ?: ModelDownloadManager.Status.NotStarted
                    val (downloadStatus, isDownloaded) = mapDownloadStatus(status)

                    ModelInfo(
                        config = config,
                        isDownloaded = isDownloaded,
                        downloadStatus = downloadStatus,
                    )
                }
                .toImmutableList()

        val anyDownloading = modelsInfo.any { it.downloadStatus is DownloadStatus.Downloading }
        val allDownloaded = modelsInfo.all { it.isDownloaded }

        val statusMessage =
            when {
                !isLoggedIn -> "Sign in with HuggingFace to download models"
                anyDownloading -> "Downloading models..."
                allDownloaded -> "All models ready"
                else -> "Ready to download"
            }

        return UiState(
            models = modelsInfo,
            isLoadingCatalog = false,
            isLoggedIn = isLoggedIn,
            statusMessage = statusMessage,
        )
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

                is ModelDownloadManager.Status.Completed -> DownloadStatus.Completed
                is ModelDownloadManager.Status.Failed -> DownloadStatus.Failed(status.message)
                is ModelDownloadManager.Status.Cancelled,
                ModelDownloadManager.Status.NotStarted,
                is ModelDownloadManager.Status.Pending -> DownloadStatus.NotStarted
            }
        val isDownloaded = status is ModelDownloadManager.Status.Completed
        return Pair(downloadStatus, isDownloaded)
    }
}
