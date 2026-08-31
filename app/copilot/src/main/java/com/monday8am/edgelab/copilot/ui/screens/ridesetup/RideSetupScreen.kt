package com.monday8am.edgelab.copilot.ui.screens.ridesetup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.edgelab.copilot.ui.theme.CyclingCopilotTheme
import com.monday8am.edgelab.presentation.ridesetup.AdvancedSettings
import com.monday8am.edgelab.presentation.ridesetup.Difficulty
import com.monday8am.edgelab.presentation.ridesetup.GpsMode
import com.monday8am.edgelab.presentation.ridesetup.PlaybackSpeed
import com.monday8am.edgelab.presentation.ridesetup.RideSetupViewModelImpl
import com.monday8am.edgelab.presentation.ridesetup.RideSetupViewModelImpl.Companion.ROUTE_CATALOG
import com.monday8am.edgelab.presentation.ridesetup.RouteInfo
import com.monday8am.edgelab.presentation.ridesetup.UiAction
import com.monday8am.edgelab.presentation.ridesetup.UiState

@Composable
fun RideSetupScreen(
    onNavigateToLiveRide: (routeId: String, playbackSpeed: Float) -> Unit,
    viewModel: AndroidRideSetupViewModel = viewModel {
        AndroidRideSetupViewModel(RideSetupViewModelImpl())
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RideSetupScreenContent(
        uiState = uiState,
        onAction = viewModel::onUiAction,
        onNavigateToLiveRide = onNavigateToLiveRide,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RideSetupScreenContent(
    uiState: UiState,
    onAction: (UiAction) -> Unit = {},
    onNavigateToLiveRide: (routeId: String, playbackSpeed: Float) -> Unit = { _, _ -> },
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "Set Up Your Ride",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        // GPS Mode
        SectionCard(title = "GPS Mode") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                GpsMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.gpsMode == mode,
                        onClick = {
                            if (mode == GpsMode.SIMULATION) {
                                onAction(UiAction.SelectGpsMode(mode))
                            }
                        },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = GpsMode.entries.size,
                            ),
                        enabled = mode == GpsMode.SIMULATION,
                        label = {
                            Text(
                                text =
                                    when (mode) {
                                        GpsMode.SIMULATION -> "Simulation"
                                        GpsMode.DEVICE_GPS -> "Device GPS (coming soon)"
                                    }
                            )
                        },
                    )
                }
            }
        }

        // Route selection — only in simulation mode
        AnimatedVisibility(visible = uiState.gpsMode == GpsMode.SIMULATION) {
            SectionCard(title = "Select Route") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.routes.forEach { route ->
                        RouteCard(
                            route = route,
                            isSelected = uiState.selectedRouteId == route.id,
                            onClick = { onAction(UiAction.SelectRoute(route.id)) },
                        )
                    }
                }
            }
        }

        // Playback speed — only in simulation mode
        AnimatedVisibility(visible = uiState.gpsMode == GpsMode.SIMULATION) {
            SectionCard(title = "Playback Speed") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PlaybackSpeed.entries.forEachIndexed { index, speed ->
                        SegmentedButton(
                            selected = uiState.playbackSpeed == speed,
                            onClick = { onAction(UiAction.SetPlaybackSpeed(speed)) },
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = PlaybackSpeed.entries.size,
                                ),
                            label = {
                                Text(
                                    text =
                                        when (speed) {
                                            PlaybackSpeed.SLOW -> "0.5×"
                                            PlaybackSpeed.NORMAL -> "1×"
                                            PlaybackSpeed.FAST -> "2×"
                                            PlaybackSpeed.VERY_FAST -> "5×"
                                            PlaybackSpeed.ULTRA_FAST -> "10×"
                                        }
                                )
                            },
                        )
                    }
                }
            }
        }

        // Advanced settings
        AdvancedSettingsSection(
            settings = uiState.advancedSettings,
            isExpanded = uiState.isAdvancedExpanded,
            onToggleExpanded = { onAction(UiAction.ToggleAdvancedExpanded) },
            onSettingsChange = { onAction(UiAction.UpdateAdvancedSettings(it)) },
        )

        // Start Ride button
        Button(
            onClick = {
                onNavigateToLiveRide(
                    uiState.selectedRouteId ?: "strade-bianche",
                    uiState.playbackSpeed.multiplier,
                )
            },
            enabled = uiState.isStartEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start Ride")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

@Composable
private fun RouteCard(route: RouteInfo, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = isSelected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${route.distanceKm.toInt()} km · +${route.elevationGainM} m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DifficultyBadge(difficulty = route.difficulty)
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: Difficulty) {
    val (label, color) =
        when (difficulty) {
            Difficulty.EASY -> "Easy" to MaterialTheme.colorScheme.tertiary
            Difficulty.MEDIUM -> "Medium" to MaterialTheme.colorScheme.secondary
            Difficulty.HARD -> "Hard" to MaterialTheme.colorScheme.error
            Difficulty.EXPERT -> "Expert" to MaterialTheme.colorScheme.error
        }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AdvancedSettingsSection(
    settings: AdvancedSettings,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSettingsChange: (AdvancedSettings) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(
                onClick = onToggleExpanded,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Advanced Settings",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector =
                            if (isExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingToggleRow(
                        label = "Use Remote LLM",
                        checked = settings.useRemoteLLM,
                        onCheckedChange = { onSettingsChange(settings.copy(useRemoteLLM = it)) },
                    )
                    SettingToggleRow(
                        label = "Show Developer HUD",
                        checked = settings.showDeveloperHUD,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(showDeveloperHUD = it))
                        },
                    )
                    SettingToggleRow(
                        label = "Enable Auto Voice",
                        checked = settings.enableAutoVoice,
                        onCheckedChange = { onSettingsChange(settings.copy(enableAutoVoice = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true, name = "Default State")
@Composable
private fun RideSetupPreview() {
    CyclingCopilotTheme { RideSetupScreenContent(uiState = UiState(routes = ROUTE_CATALOG)) }
}

@Preview(showBackground = true, name = "Route Selected")
@Composable
private fun RideSetupPreviewRouteSelected() {
    CyclingCopilotTheme {
        RideSetupScreenContent(
            uiState =
                UiState(
                    routes = ROUTE_CATALOG,
                    selectedRouteId = "strade-bianche",
                    playbackSpeed = PlaybackSpeed.FAST,
                    isStartEnabled = true,
                )
        )
    }
}
