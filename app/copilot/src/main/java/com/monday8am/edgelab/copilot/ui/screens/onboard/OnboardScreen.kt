package com.monday8am.edgelab.copilot.ui.screens.onboard

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.edgelab.core.permissions.NotificationPermissionHandler
import com.monday8am.edgelab.copilot.Dependencies
import com.monday8am.edgelab.copilot.Dependencies.oAuthManager
import com.monday8am.edgelab.copilot.ui.theme.CyclingCopilotTheme
import com.monday8am.edgelab.presentation.onboard.DownloadStatus
import com.monday8am.edgelab.presentation.onboard.ModelInfo
import com.monday8am.edgelab.presentation.onboard.OnboardViewModelImpl
import com.monday8am.edgelab.presentation.onboard.UiAction
import com.monday8am.edgelab.presentation.onboard.UiState
import kotlinx.collections.immutable.persistentListOf

@Composable
fun OnboardScreen(
    onNavigateToSetup: () -> Unit,
    viewModel: AndroidOnboardViewModel =
        viewModel {
            AndroidOnboardViewModel(
                OnboardViewModelImpl(
                    modelDownloadManager = Dependencies.modelDownloadManager,
                    authRepository = Dependencies.authRepository,
                )
            )
        },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionHandler = remember {
        NotificationPermissionHandler(
            context,
            onDenied = {
                Toast.makeText(
                        context,
                        "Enable notifications in Settings to see download progress in background",
                        Toast.LENGTH_LONG,
                    )
                    .show()
            },
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            permissionHandler.onPermissionResult(isGranted)
        }
    DisposableEffect(permissionLauncher) {
        permissionHandler.attachLauncher(permissionLauncher)
        onDispose { permissionHandler.detachLauncher() }
    }

    val onActionWithPermission: (UiAction) -> Unit = { action ->
        if (action is UiAction.DownloadModel) {
            permissionHandler.request { viewModel.onUiAction(action) }
        } else {
            viewModel.onUiAction(action)
        }
    }

    LaunchedEffect(Unit) {
        oAuthManager.oAuthResultFlow.collect { intent ->
            try {
                val token = oAuthManager.handleAuthorizationResponse(intent)
                viewModel.onUiAction(UiAction.SubmitToken(token))
                Toast.makeText(context, "Logged in to HuggingFace", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    OnboardScreenContent(
        uiState = uiState,
        onAction = onActionWithPermission,
        onNavigateToSetup = onNavigateToSetup,
        onStartOAuth = { oAuthManager.startAuthorization() },
    )
}

@Composable
private fun OnboardScreenContent(
    uiState: UiState,
    onAction: (UiAction) -> Unit = {},
    onNavigateToSetup: () -> Unit = {},
    onStartOAuth: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Text(
            text = "🚴 Cycling Copilot",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = "Your AI Riding Companion",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Feature highlights
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "On-device AI for smarter rides",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FeatureItem("Route suggestions")
                FeatureItem("Weather-aware planning")
                FeatureItem("Performance insights")
                FeatureItem("Works offline")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Step 1: Authentication
        WizardStep(
            stepNumber = 1,
            title = "Sign in to HuggingFace",
            description = "Required to download Google LLM models",
            isCompleted = uiState.isLoggedIn,
            isEnabled = true,
        ) {
            if (uiState.isLoggedIn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Signed in",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "Signed in",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Button(
                    onClick = onStartOAuth,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Sign in with HuggingFace")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step 2: Download Models
        WizardStep(
            stepNumber = 2,
            title = "Download AI Models",
            description = "2 models required for offline functionality",
            isCompleted = uiState.models.all { it.isDownloaded },
            isEnabled = uiState.isLoggedIn,
        ) {
            if (uiState.isLoadingCatalog) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    uiState.models.forEach { modelInfo ->
                        ModelDownloadCard(
                            modelInfo = modelInfo,
                            onAction = onAction,
                            isEnabled = uiState.isLoggedIn,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Download summary
                    val totalSize = uiState.models.sumOf { it.config.fileSizeBytes ?: 0L }
                    val downloadedSize =
                        uiState.models.sumOf { modelInfo ->
                            val fileSize = modelInfo.config.fileSizeBytes ?: 0L
                            when (val status = modelInfo.downloadStatus) {
                                is DownloadStatus.Completed -> fileSize
                                is DownloadStatus.Downloading ->
                                    (fileSize * status.progress / 100f).toLong()
                                else -> 0L
                            }
                        }

                    Text(
                        text =
                            "Downloaded: ${formatSize(downloadedSize)} / ${formatSize(totalSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step 3: Continue to Setup
        val allModelsReady = uiState.models.all { it.isDownloaded }
        WizardStep(
            stepNumber = 3,
            title = "Start Using Copilot",
            description = "All set! Ready to configure your first ride",
            isCompleted = false,
            isEnabled = allModelsReady && uiState.isLoggedIn,
        ) {
            Button(
                onClick = onNavigateToSetup,
                enabled = allModelsReady && uiState.isLoggedIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue to Setup")
            }

            if (!allModelsReady || !uiState.isLoggedIn) {
                Text(
                    text =
                        when {
                            !uiState.isLoggedIn -> "Complete Step 1 to continue"
                            else -> "Complete Step 2 to continue"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun WizardStep(
    stepNumber: Int,
    title: String,
    description: String,
    isCompleted: Boolean,
    isEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isEnabled) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Step header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STEP #$stepNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (isCompleted) {
                                MaterialTheme.colorScheme.primary
                            } else if (isEnabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color =
                            if (isEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            },
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (isEnabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                    )
                }

                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step content
            content()
        }
    }
}

@Composable
private fun FeatureItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModelDownloadCard(
    modelInfo: ModelInfo,
    onAction: (UiAction) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelInfo.config.displayName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = formatSize(modelInfo.config.fileSizeBytes ?: 0L),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when (modelInfo.downloadStatus) {
                    is DownloadStatus.Completed -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is DownloadStatus.Downloading -> {
                        TextButton(onClick = { onAction(UiAction.CancelDownload(modelInfo.config.modelId)) }) {
                            Text("Cancel")
                        }
                    }
                    is DownloadStatus.Failed -> {
                        OutlinedButton(
                            onClick = {
                                onAction(UiAction.DownloadModel(modelInfo.config.modelId))
                            },
                            enabled = isEnabled,
                        ) {
                            Text("Retry")
                        }
                    }
                    else -> {
                        FilledTonalButton(
                            onClick = {
                                onAction(UiAction.DownloadModel(modelInfo.config.modelId))
                            },
                            enabled = isEnabled,
                        ) {
                            Text("Download")
                        }
                    }
                }
            }

            when (val status = modelInfo.downloadStatus) {
                is DownloadStatus.Downloading -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { status.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${status.progress.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                is DownloadStatus.Failed -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = status.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024 * 1024)
    return if (mb > 1024) {
        "%.1f GB".format(mb / 1024.0)
    } else {
        "$mb MB"
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardScreenPreview() {
    CyclingCopilotTheme {
        OnboardScreenContent(
            uiState =
                UiState(
                    models =
                        persistentListOf(
                            ModelInfo(
                                config =
                                    com.monday8am.edgelab.data.model.ModelConfiguration(
                                        displayName = "User HF Model",
                                        modelFamily = "gemma",
                                        parameterCount = 2.0f,
                                        quantization = "int8",
                                        contextLength = 4096,
                                        downloadUrl = "",
                                        bundleFilename = "user-model.bin",
                                        fileSizeBytes = 2_200_000_000L,
                                    ),
                                isDownloaded = false,
                                downloadStatus = DownloadStatus.NotStarted,
                            ),
                            ModelInfo(
                                config =
                                    com.monday8am.edgelab.data.model.ModelConfiguration(
                                        displayName = "Gemma 3 1B",
                                        modelFamily = "gemma",
                                        parameterCount = 1.0f,
                                        quantization = "int8",
                                        contextLength = 4096,
                                        downloadUrl = "",
                                        bundleFilename = "gemma3-1b.bin",
                                        fileSizeBytes = 1_000_000_000L,
                                    ),
                                isDownloaded = false,
                                downloadStatus = DownloadStatus.NotStarted,
                            ),
                        ),
                    isLoggedIn = false,
                ),
        )
    }
}

@Preview(showBackground = true, name = "Step 1 Complete")
@Composable
private fun OnboardScreenPreviewStep1Complete() {
    CyclingCopilotTheme {
        OnboardScreenContent(
            uiState =
                UiState(
                    models =
                        persistentListOf(
                            ModelInfo(
                                config =
                                    com.monday8am.edgelab.data.model.ModelConfiguration(
                                        displayName = "User HF Model",
                                        modelFamily = "gemma",
                                        parameterCount = 2.0f,
                                        quantization = "int8",
                                        contextLength = 4096,
                                        downloadUrl = "",
                                        bundleFilename = "user-model.bin",
                                        fileSizeBytes = 2_200_000_000L,
                                    ),
                                isDownloaded = false,
                                downloadStatus = DownloadStatus.Downloading(45f),
                            ),
                            ModelInfo(
                                config =
                                    com.monday8am.edgelab.data.model.ModelConfiguration(
                                        displayName = "Gemma 3 1B",
                                        modelFamily = "gemma",
                                        parameterCount = 1.0f,
                                        quantization = "int8",
                                        contextLength = 4096,
                                        downloadUrl = "",
                                        bundleFilename = "gemma3-1b.bin",
                                        fileSizeBytes = 1_000_000_000L,
                                    ),
                                isDownloaded = true,
                                downloadStatus = DownloadStatus.Completed,
                            ),
                        ),
                    isLoggedIn = true,
                ),
        )
    }
}
