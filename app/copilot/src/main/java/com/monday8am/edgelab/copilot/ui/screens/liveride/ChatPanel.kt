package com.monday8am.edgelab.copilot.ui.screens.liveride

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.edgelab.copilot.ui.theme.CyclingCopilotTheme
import com.monday8am.edgelab.presentation.liveride.ChatMessage
import com.monday8am.edgelab.presentation.liveride.LiveRideAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ChatPanel(
    chatMessages: ImmutableList<ChatMessage>,
    isChatExpanded: Boolean,
    isProcessing: Boolean,
    onAction: (LiveRideAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        if (isChatExpanded) {
            Column(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                // Drag handle / collapse button
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onAction(LiveRideAction.CollapseChat) }
                            .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Message list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(chatMessages, key = { it.id }) { message ->
                        ChatMessageItem(message)
                    }
                }

                // Input bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = { /* voice recording — future feature */ }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice input")
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask Copilot\u2026") },
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onAction(LiveRideAction.SendTextMessage(inputText))
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isProcessing,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        } else {
            // Collapsed: drag handle + last copilot message preview
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(80.dp)
                        .clickable { onAction(LiveRideAction.ExpandChat) },
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Expand chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val lastCopilotMsg =
                    chatMessages.filterIsInstance<ChatMessage.Copilot>().lastOrNull()
                if (lastCopilotMsg != null) {
                    Text(
                        text = lastCopilotMsg.text,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    when (message) {
        is ChatMessage.User -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                    shape = RoundedCornerShape(12.dp, 2.dp, 12.dp, 12.dp),
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(8.dp).widthIn(max = 240.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        is ChatMessage.Copilot -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    shape = RoundedCornerShape(2.dp, 12.dp, 12.dp, 12.dp),
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(8.dp).widthIn(max = 240.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        is ChatMessage.ToolCallDebug -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(8.dp),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Collapsed - empty")
@Composable
private fun ChatPanelCollapsedEmptyPreview() {
    CyclingCopilotTheme(dynamicColor = false) {
        ChatPanel(
            chatMessages = persistentListOf(),
            isChatExpanded = false,
            isProcessing = false,
            onAction = {},
        )
    }
}

@Preview(showBackground = true, name = "Collapsed - with message")
@Composable
private fun ChatPanelCollapsedWithMessagePreview() {
    CyclingCopilotTheme(dynamicColor = false) {
        ChatPanel(
            chatMessages =
                persistentListOf(
                    ChatMessage.Copilot(
                        id = "1",
                        text =
                            "Strade Bianche starts with a brutal climb. Stay in zone 2 for the first 5 km.",
                    )
                ),
            isChatExpanded = false,
            isProcessing = false,
            onAction = {},
        )
    }
}

@Preview(showBackground = true, name = "Expanded")
@Composable
private fun ChatPanelExpandedPreview() {
    CyclingCopilotTheme(dynamicColor = false) {
        ChatPanel(
            chatMessages =
                persistentListOf(
                    ChatMessage.Copilot(
                        id = "1",
                        text = "Ride started! Strade Bianche. Ready when you are \uD83D\uDEB4",
                    ),
                    ChatMessage.User(id = "2", text = "How far to the next climb?"),
                    ChatMessage.Copilot(
                        id = "3",
                        text = "About 3.2 km ahead. It's a short punchy one, 8% average.",
                    ),
                    ChatMessage.ToolCallDebug(
                        id = "4",
                        text = "route.get_elevation_profile(from=14.2, to=17.4)",
                    ),
                ),
            isChatExpanded = true,
            isProcessing = false,
            onAction = {},
        )
    }
}

@Preview(showBackground = true, name = "Expanded - processing")
@Composable
private fun ChatPanelExpandedProcessingPreview() {
    CyclingCopilotTheme(dynamicColor = false) {
        ChatPanel(
            chatMessages =
                persistentListOf(
                    ChatMessage.Copilot(
                        id = "1",
                        text = "Ride started! Strade Bianche. Ready when you are \uD83D\uDEB4",
                    ),
                    ChatMessage.User(id = "2", text = "What's my current power zone?"),
                ),
            isChatExpanded = true,
            isProcessing = true,
            onAction = {},
        )
    }
}
