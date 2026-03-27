package com.monday8am.edgelab.presentation.liveride

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class LiveRideUiState(
    val routeName: String,
    val isLoading: Boolean,
    val routePolyline: ImmutableList<LatLng>,
    val completedPolyline: ImmutableList<LatLng>,
    val currentPosition: LatLng,
    val currentHeading: Float,
    val hudMetrics: HudMetrics,
    val pois: ImmutableList<PoiMarker>,
    val chatMessages: ImmutableList<ChatMessage>,
    val isChatExpanded: Boolean,
    val isVoiceRecording: Boolean,
    val isProcessing: Boolean,
    val playbackState: PlaybackState,
) {
    companion object {
        fun empty(speedMultiplier: Float = 1.0f): LiveRideUiState =
            LiveRideUiState(
                routeName = "",
                isLoading = true,
                routePolyline = persistentListOf(),
                completedPolyline = persistentListOf(),
                currentPosition = LatLng(0.0, 0.0),
                currentHeading = 0f,
                hudMetrics =
                    HudMetrics(speed = 0f, distance = 0f, power = null, batteryPercent = 88),
                pois = persistentListOf(),
                chatMessages = persistentListOf(),
                isChatExpanded = false,
                isVoiceRecording = false,
                isProcessing = false,
                playbackState =
                    PlaybackState(
                        isPlaying = false,
                        speedMultiplier = speedMultiplier,
                        currentKm = 0f,
                        totalKm = 0f,
                    ),
            )
    }
}
