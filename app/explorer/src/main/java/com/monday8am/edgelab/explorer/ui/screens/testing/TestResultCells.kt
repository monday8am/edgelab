package com.monday8am.edgelab.explorer.ui.screens.testing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme
import com.monday8am.edgelab.presentation.testing.TestResultFrame
import com.monday8am.edgelab.presentation.testing.ValidationResult

@Composable
internal fun DescriptionCell(frame: TestResultFrame.Description) {
    val colorScheme = MaterialTheme.colorScheme
    // Description: surfaceVariant
    val container = colorScheme.surfaceVariant
    val onContainer = colorScheme.onSurfaceVariant
    val border = colorScheme.outlineVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${frame.testName} • Description",
                style = MaterialTheme.typography.labelSmall,
                color = onContainer.copy(alpha = 0.6f),
            )
            if (frame.description.isNotEmpty()) {
                Text(
                    text = frame.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                )
            }
            Text(
                text = "System Prompt: ${frame.systemPrompt}",
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
internal fun QueryCell(frame: TestResultFrame.Query) {
    val colorScheme = MaterialTheme.colorScheme
    // Query: surface
    val container = colorScheme.surface
    val onContainer = colorScheme.onSurface
    val border = colorScheme.primary
    val text = colorScheme.onSurface

    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "User • Query", style = MaterialTheme.typography.labelSmall, color = border)
            Text(text = frame.query, style = MaterialTheme.typography.bodyMedium, color = text)
        }
    }
}

@Composable
internal fun ThinkingCell(frame: TestResultFrame.Thinking) {
    val colorScheme = MaterialTheme.colorScheme
    // Thinking: primaryContainer
    val container = colorScheme.primaryContainer
    val onContainer = colorScheme.onPrimaryContainer
    val border = colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${frame.testName} • Thinking",
                style = MaterialTheme.typography.labelSmall,
                color = border,
            )
            Text(
                text = frame.accumulator,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
        }
    }
}

@Composable
internal fun ToolCell(frame: TestResultFrame.Tool) {
    val colorScheme = MaterialTheme.colorScheme
    // Tool: secondaryContainer
    val container = colorScheme.secondaryContainer
    val onContainer = colorScheme.onSecondaryContainer
    val border = colorScheme.secondary

    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${frame.testName} • Tool Call",
                style = MaterialTheme.typography.labelSmall,
                color = border,
            )
            Text(
                text = frame.accumulator,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
        }
    }
}

@Composable
internal fun ContentCell(frame: TestResultFrame.Content) {
    val colorScheme = MaterialTheme.colorScheme
    // Success/Content: tertiaryContainer
    val container = colorScheme.tertiaryContainer
    val onContainer = colorScheme.onTertiaryContainer
    val border = colorScheme.onTertiaryContainer.copy(alpha = 0.8f)

    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${frame.testName} • Response",
                style = MaterialTheme.typography.labelSmall,
                color = border,
            )
            Text(
                text = frame.accumulator,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
        }
    }
}

@Composable
internal fun ValidationCell(frame: TestResultFrame.Validation) {
    val colorScheme = MaterialTheme.colorScheme
    val (container, onContainer, border) =
        when (frame.result) {
            is ValidationResult.Pass ->
                Triple(
                    colorScheme.tertiaryContainer,
                    colorScheme.onTertiaryContainer,
                    colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )

            is ValidationResult.Fail ->
                Triple(colorScheme.errorContainer, colorScheme.onErrorContainer, colorScheme.error)
        }

    val message =
        when (val result = frame.result) {
            is ValidationResult.Pass -> result.message
            is ValidationResult.Fail -> result.message
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${frame.testName} • Validation",
                style = MaterialTheme.typography.labelSmall,
                color = border,
            )
            Text(
                text = "$message (${frame.duration}ms)",
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun ComponentsPreviewLight() {
    EdgeLabTheme(darkTheme = false) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            DescriptionCell(
                TestResultFrame.Description(
                    testName = "Test 01",
                    description = "This is a description",
                    systemPrompt = "This is a system prompt",
                )
            )
            QueryCell(TestResultFrame.Query(testName = "Test 01", query = "This is a query"))
            ThinkingCell(
                TestResultFrame.Thinking(
                    testName = "Test 01",
                    chunk = "...",
                    accumulator = "I am thinking about the problem...",
                )
            )
            ToolCell(
                TestResultFrame.Tool(
                    testName = "Test 01",
                    content = "get_weather",
                    accumulator = "Calling weather tool...",
                )
            )
            ContentCell(
                TestResultFrame.Content(
                    testName = "Test 01",
                    chunk = "Hello",
                    accumulator = "Hello, world!",
                )
            )
            ValidationCell(
                TestResultFrame.Validation(
                    testName = "Test 01",
                    result = ValidationResult.Pass("Test Passed"),
                    duration = 123L,
                    fullContent = "Complete",
                )
            )
            ValidationCell(
                TestResultFrame.Validation(
                    testName = "Test 01",
                    result = ValidationResult.Fail("Test Failed", "Error details"),
                    duration = 456L,
                    fullContent = "Failed content",
                )
            )
        }
    }
}

@Preview(name = "Dark Mode", showBackground = true)
@Composable
private fun ComponentsPreviewDark() {
    EdgeLabTheme(darkTheme = true) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            DescriptionCell(
                TestResultFrame.Description(
                    testName = "Test 01",
                    description = "This is a description",
                    systemPrompt = "This is a system prompt",
                )
            )
            QueryCell(TestResultFrame.Query(testName = "Test 01", query = "This is a query"))
            ThinkingCell(
                TestResultFrame.Thinking(
                    testName = "Test 01",
                    chunk = "...",
                    accumulator = "I am thinking about the problem...",
                )
            )
            ToolCell(
                TestResultFrame.Tool(
                    testName = "Test 01",
                    content = "get_weather",
                    accumulator = "Calling weather tool...",
                )
            )
            ContentCell(
                TestResultFrame.Content(
                    testName = "Test 01",
                    chunk = "Hello",
                    accumulator = "Hello, world!",
                )
            )
            ValidationCell(
                TestResultFrame.Validation(
                    testName = "Test 01",
                    result = ValidationResult.Pass("Test Passed"),
                    duration = 123L,
                    fullContent = "Complete",
                )
            )
            ValidationCell(
                TestResultFrame.Validation(
                    testName = "Test 01",
                    result = ValidationResult.Fail("Test Failed", "Error details"),
                    duration = 456L,
                    fullContent = "Failed content",
                )
            )
        }
    }
}
