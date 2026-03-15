package com.monday8am.edgelab.explorer.ui.screens.modelselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.edgelab.data.model.ModelCatalog
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme
import com.monday8am.edgelab.presentation.modelselector.DownloadStatus
import com.monday8am.edgelab.presentation.modelselector.ModelInfo
import com.monday8am.edgelab.presentation.modelselector.UiAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun ToolBar(
    models: ImmutableList<ModelInfo>,
    selectedModelId: String?,
    onAction: (UiAction) -> Unit,
    onNavigateToTesting: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconSizeModifier = Modifier.size(18.dp)

    Row(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Delete/Cancel button - visible only when downloaded model is selected
        val downloadStatus =
            models.find { it.config.modelId == selectedModelId }?.downloadStatus
                ?: DownloadStatus.NotStarted

        when (downloadStatus) {
            is DownloadStatus.Downloading,
            is DownloadStatus.Queued -> {
                Button(onClick = { onAction(UiAction.CancelCurrentDownload) }) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Cancel download",
                        modifier = iconSizeModifier,
                    )
                }
            }

            is DownloadStatus.Completed -> {
                Button(
                    onClick = {
                        selectedModelId?.let { modelId -> onAction(UiAction.DeleteModel(modelId)) }
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = iconSizeModifier,
                    )
                }
            }

            else -> {}
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { selectedModelId?.let(onNavigateToTesting) },
            enabled = downloadStatus == DownloadStatus.Completed,
        ) {
            Text("Test Function Call", modifier = Modifier.padding(horizontal = 8.dp))
            if (downloadStatus == DownloadStatus.Completed)
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "Go forward",
                    modifier = iconSizeModifier,
                )
        }
    }
}

@Preview(name = "Not Downloaded", showBackground = true)
@Composable
private fun ToolBarPreview_NotDownloaded() {
    EdgeLabTheme {
        ToolBar(
            models =
                listOf(
                        ModelInfo(
                            config = ModelCatalog.QWEN3_0_6B,
                            isDownloaded = false,
                            downloadStatus = DownloadStatus.NotStarted,
                        )
                    )
                    .toImmutableList(),
            selectedModelId = ModelCatalog.QWEN3_0_6B.modelId,
            onAction = {},
            onNavigateToTesting = {},
        )
    }
}

@Preview(name = "Downloaded & Selected", showBackground = true)
@Composable
private fun ToolBarPreview_DownloadedSelected() {
    EdgeLabTheme {
        ToolBar(
            models =
                listOf(
                        ModelInfo(
                            config = ModelCatalog.GEMMA3_1B,
                            isDownloaded = true,
                            downloadStatus = DownloadStatus.Completed,
                        )
                    )
                    .toImmutableList(),
            selectedModelId = ModelCatalog.GEMMA3_1B.modelId,
            onAction = {},
            onNavigateToTesting = {},
        )
    }
}

@Preview(name = "Downloading", showBackground = true)
@Composable
private fun ToolBarPreview_Downloading() {
    EdgeLabTheme {
        ToolBar(
            models =
                listOf(
                        ModelInfo(
                            config = ModelCatalog.GEMMA3_1B,
                            isDownloaded = false,
                            downloadStatus = DownloadStatus.Downloading(42f),
                        )
                    )
                    .toImmutableList(),
            selectedModelId = ModelCatalog.GEMMA3_1B.modelId,
            onAction = {},
            onNavigateToTesting = {},
        )
    }
}
