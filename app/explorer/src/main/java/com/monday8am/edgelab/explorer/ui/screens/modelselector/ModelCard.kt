package com.monday8am.edgelab.explorer.ui.screens.modelselector

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.monday8am.edgelab.data.model.ModelCatalog
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme
import com.monday8am.edgelab.presentation.modelselector.DownloadStatus
import com.monday8am.edgelab.presentation.modelselector.ModelInfo

/** Card displaying model information and download status */
@SuppressLint("DefaultLocale")
@Composable
internal fun ModelCard(
    modelInfo: ModelInfo,
    isSelected: Boolean,
    onDownloadClick: () -> Unit,
    onSelectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backgroundColor =
        when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            modelInfo.isDownloaded -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

    Card(
        modifier = modifier.fillMaxWidth().clickable(true) { onSelectClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Model info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = modelInfo.config.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (modelInfo.isGated) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription =
                                "Gated model - may require Hugging Face authentication",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 6.dp).size(18.dp),
                        )
                    }
                    // HuggingFace link button
                    modelInfo.config.huggingFaceUrl?.let { url ->
                        IconButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            },
                            modifier = Modifier.size(48.dp), // Accessibility min touch target
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "View on HuggingFace",
                                tint =
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // Description
                modelInfo.config.description?.let { desc ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        "${modelInfo.config.parameterCount}B params • ${modelInfo.config.contextLength} tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )

                // File size
                modelInfo.config.readableFileSize?.let { sizeText ->
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            when (val downloadStatus = modelInfo.downloadStatus) {
                is DownloadStatus.NotStarted -> {
                    Button(onClick = onDownloadClick) { Text("Download") }
                }

                is DownloadStatus.Queued -> {
                    Text(
                        text = "Queued",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                is DownloadStatus.Downloading -> {
                    CircularProgressWithText(
                        progress = downloadStatus.progress / 100f,
                        text = "${String.format("%.1f", downloadStatus.progress)}%",
                    )
                }

                is DownloadStatus.Completed -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary, // Using primary for success
                        modifier = Modifier.size(32.dp),
                    )
                }

                is DownloadStatus.Failed -> {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = downloadStatus.error,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Failed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 4.dp).size(16.dp),
                            )
                        }
                        TextButton(onClick = onDownloadClick, modifier = Modifier.height(32.dp)) {
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularProgressWithText(progress: Float, text: String, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(48.dp)) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 4.dp,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(name = "Not Downloaded", showBackground = true, widthDp = 380)
@Composable
private fun ModelCardPreview_NotDownloaded() {
    EdgeLabTheme {
        ModelCard(
            modelInfo =
                ModelInfo(
                    config = ModelCatalog.QWEN3_0_6B,
                    isDownloaded = false,
                    downloadStatus = DownloadStatus.NotStarted,
                ),
            isSelected = false,
            onDownloadClick = {},
            onSelectClick = {},
        )
    }
}

@Preview(name = "Downloading", showBackground = true, widthDp = 380)
@Composable
private fun ModelCardPreview_Downloading() {
    EdgeLabTheme {
        ModelCard(
            modelInfo =
                ModelInfo(
                    config = ModelCatalog.GEMMA3_1B,
                    isDownloaded = false,
                    downloadStatus = DownloadStatus.Downloading(42f),
                ),
            isSelected = false,
            onDownloadClick = {},
            onSelectClick = {},
        )
    }
}

@Preview(name = "Queued", showBackground = true, widthDp = 380)
@Composable
private fun ModelCardPreview_Queued() {
    EdgeLabTheme {
        ModelCard(
            modelInfo =
                ModelInfo(
                    config = ModelCatalog.GEMMA3_1B,
                    isDownloaded = false,
                    downloadStatus = DownloadStatus.Queued,
                ),
            isSelected = false,
            onDownloadClick = {},
            onSelectClick = {},
        )
    }
}

@Preview(name = "Downloaded & Selected", showBackground = true, widthDp = 380)
@Composable
private fun ModelCardPreview_DownloadedSelected() {
    EdgeLabTheme {
        ModelCard(
            modelInfo =
                ModelInfo(
                    config = ModelCatalog.GEMMA3_1B,
                    isDownloaded = true,
                    downloadStatus = DownloadStatus.Completed,
                ),
            isSelected = true,
            onDownloadClick = {},
            onSelectClick = {},
        )
    }
}

@Preview(name = "Download Failed", showBackground = true, widthDp = 380)
@Composable
private fun ModelCardPreview_Failed() {
    EdgeLabTheme {
        ModelCard(
            modelInfo =
                ModelInfo(
                    config = ModelCatalog.GEMMA3_1B,
                    isDownloaded = false,
                    downloadStatus = DownloadStatus.Failed("Network error"),
                ),
            isSelected = false,
            onDownloadClick = {},
            onSelectClick = {},
        )
    }
}

@Preview(name = "Gated Model", showBackground = true, widthDp = 380)
@Composable
private fun ModelCardPreview_Gated() {
    EdgeLabTheme {
        ModelCard(
            modelInfo =
                ModelInfo(
                    config = ModelCatalog.GEMMA3_1B.copy(isGated = true),
                    isDownloaded = false,
                    downloadStatus = DownloadStatus.NotStarted,
                ),
            isSelected = false,
            onDownloadClick = {},
            onSelectClick = {},
        )
    }
}
