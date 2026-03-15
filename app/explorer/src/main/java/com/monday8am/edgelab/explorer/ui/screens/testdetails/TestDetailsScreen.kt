package com.monday8am.edgelab.explorer.ui.screens.testdetails

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.edgelab.explorer.Dependencies
import com.monday8am.edgelab.data.testing.TestCaseDefinition
import com.monday8am.edgelab.data.testing.TestDomain
import com.monday8am.edgelab.data.testing.TestQueryDefinition
import com.monday8am.edgelab.data.testing.ValidationRule
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme
import com.monday8am.edgelab.presentation.testdetails.TestDetailsUiAction
import com.monday8am.edgelab.presentation.testdetails.TestDetailsViewModelImpl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun TestDetailsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AndroidTestDetailsViewModel =
        viewModel {
            AndroidTestDetailsViewModel(
                TestDetailsViewModelImpl(testRepository = Dependencies.testRepository),
            )
        },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TestDetailsContent(
        tests = state.tests,
        availableDomains = state.availableDomains,
        filterDomain = state.filterDomain,
        onSetDomainFilter = { domain ->
            viewModel.onUiAction(TestDetailsUiAction.SetDomainFilter(domain))
        },
        onNavigateBack = onNavigateBack,
        modifier = Modifier,
    )
}

@Composable
private fun TestDetailsContent(
    tests: ImmutableList<TestCaseDefinition>,
    availableDomains: ImmutableList<TestDomain>,
    filterDomain: TestDomain?,
    onSetDomainFilter: (TestDomain?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = spacedBy(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Available Tests",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "${tests.size} tests available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Filter Section
        if (availableDomains.isNotEmpty()) {
            LazyRow(horizontalArrangement = spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item {
                    FilterChip(
                        selected = filterDomain == null,
                        onClick = { onSetDomainFilter(null) },
                        label = { Text("All") },
                    )
                }
                items(availableDomains) { domain ->
                    FilterChip(
                        selected = filterDomain == domain,
                        onClick = { onSetDomainFilter(domain) },
                        label = {
                            Text(domain.name.lowercase().replaceFirstChar { it.uppercase() })
                        },
                    )
                }
            }
        }

        // Test List
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = spacedBy(8.dp),
        ) {
            items(items = tests, key = { it.id }) { test -> TestDetailsCard(test = test) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TestDetailsContentPreview() {
    EdgeLabTheme {
        TestDetailsContent(
            tests =
                listOf(
                    TestCaseDefinition(
                        id = "test_1",
                        name = "Basic Response Test",
                        description = listOf("This is a test description"),
                        domain = TestDomain.GENERIC,
                        query = TestQueryDefinition("Test query", "Query description"),
                        systemPrompt = "System prompt",
                        rules = listOf(ValidationRule.ChatValid, ValidationRule.NoToolCalls),
                    ),
                    TestCaseDefinition(
                        id = "test_2",
                        name = "Yazio Domain Test",
                        description = emptyList(),
                        domain = TestDomain.YAZIO,
                        query = TestQueryDefinition("Test query", "Query description"),
                        systemPrompt = "System prompt",
                        rules = listOf(ValidationRule.ChatValid),
                    ),
                    TestCaseDefinition(
                        id = "test_3",
                        name = "Tool Calling Test",
                        description = listOf("Tests tool calling functionality"),
                        domain = TestDomain.GENERIC,
                        query = TestQueryDefinition("Test query", "Query description"),
                        systemPrompt = "System prompt",
                        rules =
                            listOf(
                                ValidationRule.ToolMatch("tool_name"),
                                ValidationRule.ResponseLengthMin(10),
                            ),
                    ),
                )
                    .toImmutableList(),
            availableDomains = listOf(TestDomain.GENERIC, TestDomain.YAZIO).toImmutableList(),
            filterDomain = null,
            onSetDomainFilter = {},
            onNavigateBack = {},
        )
    }
}
