package com.monday8am.edgelab.explorer.ui.screens.modelselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme
import com.monday8am.edgelab.presentation.modelselector.GroupingMode
import com.monday8am.edgelab.presentation.modelselector.UiAction

@Composable
internal fun ModelSelectorHeader(
    statusMessage: String,
    groupingMode: GroupingMode,
    isAllExpanded: Boolean,
    isLoggedIn: Boolean,
    onIntent: (UiAction) -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
    onManageAuthorsClick: () -> Unit = {}
) {
    Column(modifier = modifier) {
        // Action buttons row at the top
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Manage model sources button
            IconButton(onClick = onManageAuthorsClick) {
                Icon(
                    imageVector = Icons.Outlined.ManageAccounts,
                    contentDescription = "Manage model sources",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // User login/logout button
            IconButton(onClick = { if (isLoggedIn) onLogoutClick() else onLoginClick() }) {
                Icon(
                    imageVector =
                        if (isLoggedIn) Icons.Default.Person else Icons.Default.PersonOff,
                    contentDescription =
                        if (isLoggedIn) "Logout from HuggingFace" else "Login to HuggingFace",
                    tint =
                        if (isLoggedIn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (groupingMode != GroupingMode.None) {
                IconButton(onClick = { onIntent(UiAction.ToggleAllGroups) }) {
                    Icon(
                        imageVector =
                            if (isAllExpanded) Icons.Default.UnfoldLess
                            else Icons.Default.UnfoldMore,
                        contentDescription =
                            if (isAllExpanded) "Collapse All" else "Expand All",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Box {
                var expanded by remember { mutableStateOf(false) }
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Group models",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    GroupingMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.name) },
                            onClick = {
                                onIntent(UiAction.SetGroupingMode(mode))
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        // Title with full width, no competition with buttons
        Text(
            text = "Select a Model",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Preview
@Composable
private fun ModelSelectorHeaderPreview() {
    EdgeLabTheme {
        Surface {
            ModelSelectorHeader(
                statusMessage = "Select a model to start chat",
                groupingMode = GroupingMode.Family,
                isAllExpanded = true,
                isLoggedIn = false,
                onIntent = {},
                onLoginClick = {},
                onLogoutClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
