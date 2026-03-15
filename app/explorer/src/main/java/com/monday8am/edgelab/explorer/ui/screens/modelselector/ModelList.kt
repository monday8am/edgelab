package com.monday8am.edgelab.explorer.ui.screens.modelselector

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.edgelab.data.model.HardwareBackend
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme
import com.monday8am.edgelab.presentation.modelselector.DownloadStatus
import com.monday8am.edgelab.presentation.modelselector.ModelGroup
import com.monday8am.edgelab.presentation.modelselector.ModelInfo
import com.monday8am.edgelab.presentation.modelselector.UiAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ModelList(
    groups: ImmutableList<ModelGroup>,
    selectedModelId: String?,
    onIntent: (UiAction) -> Unit,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        groups.forEach { group ->
            stickyHeader(key = group.id) {
                GroupHeader(
                    title = group.title,
                    isExpanded = group.isExpanded,
                    onToggle = { onIntent(UiAction.ToggleGroup(group.id)) },
                )
            }

            if (group.isExpanded) {
                items(items = group.models, key = { it.config.modelId }) { modelInfo ->
                    ModelCard(
                        modelInfo = modelInfo,
                        isSelected = modelInfo.config.modelId == selectedModelId,
                        onDownloadClick = {
                            onIntent(UiAction.DownloadModel(modelInfo.config.modelId))
                        },
                        onSelectClick = { onSelectModel(modelInfo.config.modelId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, isExpanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector =
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview
@Composable
private fun ModelListPreview() {
    val sampleModels =
        listOf(
            ModelInfo(
                config =
                    ModelConfiguration(
                        displayName = "Qwen3 0.6B (LiteRT, 4K context)",
                        modelFamily = "qwen3",
                        parameterCount = 0.6f,
                        quantization = "int8",
                        contextLength = 4096,
                        downloadUrl = "https://example.com/model.litertlm",
                        bundleFilename = "model1.litertlm",
                        hardwareAcceleration = HardwareBackend.CPU_ONLY,
                    ),
                isDownloaded = true,
                downloadStatus = DownloadStatus.Completed,
            ),
            ModelInfo(
                config =
                    ModelConfiguration(
                        displayName = "Gemma3 1B (LiteRT, 4K context)",
                        modelFamily = "gemma3",
                        parameterCount = 1.0f,
                        quantization = "int4",
                        contextLength = 4096,
                        downloadUrl = "https://example.com/model2.litertlm",
                        bundleFilename = "model2.litertlm",
                        hardwareAcceleration = HardwareBackend.GPU_SUPPORTED,
                    ),
                isDownloaded = false,
                downloadStatus = DownloadStatus.NotStarted,
            ),
        )

    EdgeLabTheme {
        ModelList(
            groups =
                listOf(
                        ModelGroup(
                            id = "group1",
                            title = "Test Group",
                            models = sampleModels.toImmutableList(),
                            isExpanded = true,
                        )
                    )
                    .toImmutableList(),
            selectedModelId = sampleModels.first().config.modelId,
            onIntent = {},
            onSelectModel = {},
        )
    }
}
