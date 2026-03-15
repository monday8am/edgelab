package com.monday8am.edgelab.explorer.ui.screens.authormanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.edgelab.data.auth.AuthorInfo
import com.monday8am.edgelab.explorer.Dependencies
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme
import com.monday8am.edgelab.presentation.authormanager.AuthorManagerViewModelImpl
import com.monday8am.edgelab.presentation.authormanager.AuthorUiAction
import com.monday8am.edgelab.presentation.authormanager.AuthorUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun AuthorManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: AndroidAuthorManagerViewModel = viewModel {
        AndroidAuthorManagerViewModel(
            AuthorManagerViewModelImpl(authorRepository = Dependencies.authorRepository)
        )
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AuthorManagerScreenContent(
        uiState = uiState,
        onAction = viewModel::onUiAction,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthorManagerScreenContent(
    uiState: AuthorUiState,
    onAction: (AuthorUiAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Sources") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            items(uiState.authors, key = { it.name }) { author ->
                AuthorItem(
                    author = author,
                    onRemove = { onAction(AuthorUiAction.RemoveAuthor(author.name)) },
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AddAuthorRow(
                    fieldText = uiState.addFieldText,
                    isAdding = uiState.isAdding,
                    error = uiState.addError,
                    onTextChange = { onAction(AuthorUiAction.UpdateAddField(it)) },
                    onAdd = { onAction(AuthorUiAction.SubmitAdd) },
                )
            }
        }
    }
}

@Composable
private fun AuthorItem(
    author: AuthorInfo,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuthorAvatar(name = author.name)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = author.name, fontWeight = FontWeight.Bold)
            author.modelCount?.let { count ->
                Text(
                    text = "$count models",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (author.isDefault) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Default author, cannot be removed",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove ${author.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AuthorAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    val initials = name.take(2).uppercase()
    Box(
        modifier = modifier
            .size(40.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun AddAuthorRow(
    fieldText: String,
    isAdding: Boolean,
    error: String?,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = fieldText,
                onValueChange = onTextChange,
                placeholder = { Text("HuggingFace username or org") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = error != null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAdd,
                enabled = !isAdding && fieldText.isNotBlank(),
            ) {
                if (isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Add")
                }
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthorManagerScreenPreview() {
    EdgeLabTheme {
        AuthorManagerScreenContent(
            uiState = AuthorUiState(
                authors = listOf(
                    AuthorInfo(name = "litert-community", isDefault = true, modelCount = 12),
                    AuthorInfo(name = "google", modelCount = 45),
                ).toImmutableList(),
                addFieldText = "",
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
