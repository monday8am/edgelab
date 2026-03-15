package com.monday8am.edgelab.explorer.ui.screens.modelselector

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.edgelab.explorer.Dependencies
import com.monday8am.edgelab.data.model.ModelCatalog
import com.monday8am.edgelab.explorer.ui.screens.testing.InitializationIndicator
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme
import com.monday8am.edgelab.presentation.modelselector.DownloadInfo
import com.monday8am.edgelab.presentation.modelselector.DownloadStatus
import com.monday8am.edgelab.presentation.modelselector.ModelInfo
import com.monday8am.edgelab.presentation.modelselector.ModelSelectorViewModelImpl
import com.monday8am.edgelab.presentation.modelselector.UiAction
import com.monday8am.edgelab.presentation.modelselector.UiState
import kotlinx.collections.immutable.toImmutableList

/** Model Selector Screen - Entry point for model selection. */
@Composable
fun ModelSelectorScreen(
    onNavigateToTesting: (String) -> Unit,
    onNavigateToAuthorManager: () -> Unit = {},
    viewModel: AndroidModelSelectorViewModel = viewModel {
        AndroidModelSelectorViewModel(
            ModelSelectorViewModelImpl(
                modelDownloadManager = Dependencies.modelDownloadManager,
                modelRepository = Dependencies.modelRepository,
                authRepository = Dependencies.authRepository,
            )
        )
    },
) {
    val context = LocalContext.current
    val oAuthManager = remember { Dependencies.oAuthManager }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedModelId by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isAuthenticating by remember { mutableStateOf(false) }

    // Handle OAuth result when received from flow
    LaunchedEffect(Unit) {
        oAuthManager.oAuthResultFlow.collect { intent ->
            isAuthenticating = true
            try {
                val token = oAuthManager.handleAuthorizationResponse(intent)
                viewModel.onUiAction(UiAction.SubmitToken(token))
                Toast.makeText(context, "Logged in to HuggingFace", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isAuthenticating = false
            }
        }
    }

    LaunchedEffect(uiState.models) {
        if (
            selectedModelId != null && uiState.models.none { it.config.modelId == selectedModelId }
        ) {
            selectedModelId = null
        }
    }

    // Function to launch OAuth flow
    val launchOAuth: () -> Unit = {
        oAuthManager.startAuthorization()
    }

    val displayStatusMessage =
        when {
            isAuthenticating -> "Verifying login with Hugging Face..."
            uiState.isLoadingCatalog -> "Loading models from Hugging Face..."
            uiState.currentDownload != null ->
                "Downloading: ${uiState.currentDownload?.modelId?.take(20)}..."
            selectedModelId != null -> {
                val name =
                    uiState.models
                        .find { it.config.modelId == selectedModelId }
                        ?.config
                        ?.displayName
                "Selected: $name"
            }

            else -> uiState.statusMessage
        }

    ModelSelectorScreenContent(
        uiState = uiState,
        selectedModelId = selectedModelId,
        statusMessage = displayStatusMessage,
        modifier = Modifier,
        onIntent = viewModel::onUiAction,
        onSelectModel = { selectedModelId = it },
        onLoginClick = launchOAuth,
        onLogoutClick = { showLogoutDialog = true },
        onNavigateToTesting = onNavigateToTesting,
        onNavigateToAuthorManager = onNavigateToAuthorManager,
    )

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = { viewModel.onUiAction(UiAction.Logout) },
        )
    }
}

@Composable
private fun ModelSelectorScreenContent(
    uiState: UiState,
    selectedModelId: String?,
    statusMessage: String,
    modifier: Modifier = Modifier,
    onIntent: (UiAction) -> Unit = {},
    onSelectModel: (String) -> Unit = {},
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onNavigateToTesting: (String) -> Unit = {},
    onNavigateToAuthorManager: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        ModelSelectorHeader(
            statusMessage = statusMessage,
            groupingMode = uiState.groupingMode,
            isAllExpanded = uiState.isAllExpanded,
            isLoggedIn = uiState.isLoggedIn,
            onIntent = onIntent,
            onLoginClick = onLoginClick,
            onLogoutClick = onLogoutClick,
            onManageAuthorsClick = onNavigateToAuthorManager,
            modifier = Modifier.fillMaxWidth(),
        )

        uiState.catalogError?.let { error ->
            Text(
                text = "Using cached models: $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (uiState.isLoadingCatalog) {
            InitializationIndicator(
                message = "Loading models from Hugging Face...",
                modifier = Modifier.weight(1f),
            )
        } else {
            ModelList(
                groups = uiState.groupedModels,
                selectedModelId = selectedModelId,
                onIntent = { action ->
                    if (action is UiAction.DownloadModel) {
                        val model = uiState.models.find { it.config.modelId == action.modelId }
                        if (model?.isGated == true && !uiState.isLoggedIn) {
                            // Trigger OAuth login for gated models
                            onLoginClick()
                        } else {
                            onSelectModel(action.modelId)
                            onIntent(action)
                        }
                    } else {
                        onIntent(action)
                    }
                },
                onSelectModel = onSelectModel,
                modifier = Modifier.weight(1f),
            )
        }

        ToolBar(
            models = uiState.models,
            selectedModelId = selectedModelId,
            onAction = onIntent,
            onNavigateToTesting = onNavigateToTesting,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelSelectorScreenPreview() {
    EdgeLabTheme {
        ModelSelectorScreenContent(
            uiState =
                UiState(
                    models =
                        ModelCatalog.ALL_MODELS.map {
                            ModelInfo(
                                config = it,
                                isDownloaded = it.modelId != ModelCatalog.GEMMA3_1B.modelId,
                                downloadStatus =
                                    if (it.modelId == ModelCatalog.GEMMA3_1B.modelId) {
                                        DownloadStatus.Downloading(10f)
                                    } else {
                                        DownloadStatus.Completed
                                    },
                            )
                        }
                            .toImmutableList(),
                    currentDownload = DownloadInfo(ModelCatalog.GEMMA3_1B.modelId, 10f),
                    statusMessage = "Downloading model: GEMMA3_1B",
                    isLoadingCatalog = false,
                ),
            selectedModelId = ModelCatalog.GEMMA3_1B.modelId,
            statusMessage = "Selected: GEMMA3_1B",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelSelectorScreenPreview_Loading() {
    EdgeLabTheme {
        ModelSelectorScreenContent(
            uiState =
                UiState(
                    isLoadingCatalog = true,
                    statusMessage = "Loading models from Hugging Face...",
                ),
            selectedModelId = null,
            statusMessage = "Loading models from Hugging Face...",
        )
    }
}
