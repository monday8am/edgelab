package com.monday8am.edgelab.explorer.ui.screens.testdetails

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.edgelab.data.testing.TestCaseDefinition
import com.monday8am.edgelab.data.testing.TestDomain
import com.monday8am.edgelab.data.testing.TestQueryDefinition
import com.monday8am.edgelab.data.testing.ValidationRule
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme

@Composable
internal fun TestDetailsCard(
    test: TestCaseDefinition,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        when (test.domain) {
            TestDomain.GENERIC -> MaterialTheme.colorScheme.surfaceVariant
            TestDomain.YAZIO -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = spacedBy(8.dp)) {
            // Row 1: Domain icon + Test name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        when (test.domain) {
                            TestDomain.GENERIC -> Icons.Default.Memory
                            TestDomain.YAZIO -> Icons.Filled.Psychology
                        },
                    contentDescription = test.domain.name,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = test.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }

            // Row 2: Description
            Text(
                text = test.description.firstOrNull() ?: "No description",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )

            // Row 3: Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = spacedBy(12.dp),
            ) {
                Text(
                    text = "${test.rules.size} rules",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )

                test.tools?.let { tools ->
                    if (tools.isNotEmpty()) {
                        Text(
                            text = "${tools.size} tools",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun TestDetailsCardPreview() {
    EdgeLabTheme {
        TestDetailsCard(
            test =
                TestCaseDefinition(
                    id = "test_1",
                    name = "Basic Response Test",
                    description = listOf("This is a test description that shows how the card looks"),
                    domain = TestDomain.GENERIC,
                    query = TestQueryDefinition("Test query", "Query description"),
                    systemPrompt = "System prompt",
                    rules = listOf(ValidationRule.ChatValid, ValidationRule.NoToolCalls),
                ),
        )
    }
}

@Preview
@Composable
private fun TestDetailsCardYazioPreview() {
    EdgeLabTheme {
        TestDetailsCard(
            test =
                TestCaseDefinition(
                    id = "test_2",
                    name = "Yazio Domain Test",
                    description = emptyList(),
                    domain = TestDomain.YAZIO,
                    query = TestQueryDefinition("Test query", "Query description"),
                    systemPrompt = "System prompt",
                    rules = listOf(ValidationRule.ChatValid),
                ),
        )
    }
}
