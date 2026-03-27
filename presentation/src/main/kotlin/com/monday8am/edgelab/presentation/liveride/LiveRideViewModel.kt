package com.monday8am.edgelab.presentation.liveride

import com.monday8am.edgelab.data.route.RouteData
import com.monday8am.edgelab.data.route.RouteRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LatLng(val latitude: Double, val longitude: Double)

data class HudMetrics(
    val speed: Float,
    val distance: Float,
    val power: Int?,
    val batteryPercent: Int,
)

enum class PoiCategory {
    CAFE,
    WATER,
    BIKE_SHOP,
    SHELTER,
}

data class PoiMarker(
    val id: String,
    val position: LatLng,
    val category: PoiCategory,
    val name: String,
)

sealed interface ChatMessage {
    val id: String

    data class User(override val id: String, val text: String) : ChatMessage

    data class Copilot(override val id: String, val text: String) : ChatMessage

    data class ToolCallDebug(override val id: String, val text: String) : ChatMessage
}

data class PlaybackState(
    val isPlaying: Boolean,
    val speedMultiplier: Float,
    val currentKm: Float,
    val totalKm: Float,
)

sealed interface LiveRideAction {
    data object TogglePlayback : LiveRideAction

    data object CycleSpeed : LiveRideAction

    data object ExpandChat : LiveRideAction

    data object CollapseChat : LiveRideAction

    data class SendTextMessage(val text: String) : LiveRideAction

    data object EndRide : LiveRideAction
}

interface LiveRideViewModel {
    val uiState: StateFlow<LiveRideUiState>

    fun onUiAction(action: LiveRideAction)

    fun dispose()
}

private sealed interface RouteState {
    data object Loading : RouteState

    data class Loaded(val data: RouteData) : RouteState

    data object NotFound : RouteState
}

private data class ViewModelState(
    val chatMessages: ImmutableList<ChatMessage> = persistentListOf(),
    val isPlaying: Boolean = false,
    val speedMultiplier: Float = 1.0f,
    val isChatExpanded: Boolean = false,
    val isVoiceRecording: Boolean = false,
    val isProcessing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LiveRideViewModelImpl(
    private val routeId: String,
    private val routeRepository: RouteRepository,
    private val playbackSpeed: Float = 1.0f,
    private val gpsSourceFactory: GpsSourceFactory = GpsSourceFactory { points ->
        SimulatedGpsSource(points)
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : LiveRideViewModel {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var messageIdCounter = 1
    private var gpsSource: GpsSource? = null

    companion object {
        private val SPEED_MULTIPLIERS = listOf(1.0f, 2.0f, 4.0f, 8.0f)
    }

    private val viewModelState = MutableStateFlow(ViewModelState(speedMultiplier = playbackSpeed))

    private val routeState: StateFlow<RouteState> =
        routeRepository
            .routeFlow(routeId)
            .map<RouteData, RouteState> { data ->
                if (data.coordinates.isNotEmpty()) RouteState.Loaded(data) else RouteState.NotFound
            }
            .catch { emit(RouteState.NotFound) }
            .stateIn(scope, SharingStarted.Eagerly, RouteState.Loading)

    override val uiState: StateFlow<LiveRideUiState> =
        routeState
            .flatMapLatest { state ->
                when (state) {
                    RouteState.Loading ->
                        viewModelState.map { vm -> deriveUiState(vm, route = null, gps = null) }
                    RouteState.NotFound ->
                        viewModelState.map { vm ->
                            deriveUiState(vm, route = null, gps = null, isLoading = false)
                        }
                    is RouteState.Loaded -> {
                        val route = state.data
                        val points = route.coordinates.map { LatLng(it.lat, it.lng) }
                        val routePolyline = points.toImmutableList()
                        val welcomeMessages =
                            persistentListOf(
                                ChatMessage.Copilot(
                                    id = "msg_0",
                                    text =
                                        "Ride started! ${route.name}. Ready when you are \uD83D\uDEB4",
                                )
                            )
                        val source = gpsSourceFactory.create(points)
                        gpsSource = source
                        source.setSpeedMultiplier(viewModelState.value.speedMultiplier)
                        source.start()
                        viewModelState.update { it.copy(isPlaying = true) }

                        combine(
                            source.positions.onStart<GpsPosition?> { emit(null) },
                            viewModelState,
                        ) { gps, vm ->
                            deriveUiState(vm, routePolyline, welcomeMessages, route, gps)
                        }
                    }
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, LiveRideUiState.empty(playbackSpeed))

    override fun onUiAction(action: LiveRideAction) {
        when (action) {
            LiveRideAction.TogglePlayback -> {
                val nowPlaying = !viewModelState.value.isPlaying
                if (nowPlaying) gpsSource?.start() else gpsSource?.pause()
                viewModelState.update { it.copy(isPlaying = nowPlaying) }
            }
            LiveRideAction.CycleSpeed -> {
                viewModelState.update { state ->
                    val idx = SPEED_MULTIPLIERS.indexOf(state.speedMultiplier)
                    val next = SPEED_MULTIPLIERS[(idx + 1) % SPEED_MULTIPLIERS.size]
                    gpsSource?.setSpeedMultiplier(next)
                    state.copy(speedMultiplier = next)
                }
            }
            LiveRideAction.ExpandChat -> viewModelState.update { it.copy(isChatExpanded = true) }
            LiveRideAction.CollapseChat -> viewModelState.update { it.copy(isChatExpanded = false) }
            is LiveRideAction.SendTextMessage -> sendTextMessage(action.text)
            LiveRideAction.EndRide -> {
                /* navigation handled in UI layer */
            }
        }
    }

    private fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage.User(id = "msg_${messageIdCounter++}", text = text)
        viewModelState.update {
            it.copy(chatMessages = (it.chatMessages + userMsg).toImmutableList())
        }
        scope.launch {
            delay(1500)
            val reply =
                ChatMessage.Copilot(
                    id = "msg_${messageIdCounter++}",
                    text = "Got it! Keeping an eye on the route ahead.",
                )
            viewModelState.update {
                it.copy(chatMessages = (it.chatMessages + reply).toImmutableList())
            }
        }
    }

    private fun deriveUiState(
        state: ViewModelState,
        route: RouteData?,
        gps: GpsPosition?,
        isLoading: Boolean = route == null,
    ): LiveRideUiState =
        deriveUiState(state, persistentListOf(), persistentListOf(), route, gps, isLoading)

    private fun deriveUiState(
        state: ViewModelState,
        routePolyline: ImmutableList<LatLng>,
        welcomeMessages: ImmutableList<ChatMessage>,
        route: RouteData?,
        gps: GpsPosition?,
        isLoading: Boolean = route == null,
    ): LiveRideUiState {
        val firstPoint = if (routePolyline.isNotEmpty()) routePolyline.first() else LatLng(0.0, 0.0)
        val chatMessages =
            if (welcomeMessages.isEmpty() && state.chatMessages.isEmpty()) {
                persistentListOf()
            } else {
                (welcomeMessages + state.chatMessages).toImmutableList()
            }

        return LiveRideUiState(
            routeName = route?.name ?: "",
            isLoading = isLoading,
            routePolyline = routePolyline,
            completedPolyline =
                if (gps != null) {
                    routePolyline.take(gps.routePointIndex + 1).toImmutableList()
                } else if (routePolyline.isNotEmpty()) {
                    persistentListOf(firstPoint)
                } else {
                    persistentListOf()
                },
            currentPosition = gps?.latLng ?: firstPoint,
            currentHeading = gps?.heading ?: 0f,
            hudMetrics =
                if (gps != null) {
                    HudMetrics(
                        speed = gps.speedKmh,
                        distance = gps.distanceTravelledKm,
                        power = gps.power,
                        batteryPercent = 88,
                    )
                } else {
                    HudMetrics(speed = 0f, distance = 0f, power = null, batteryPercent = 88)
                },
            pois = persistentListOf(),
            chatMessages = chatMessages,
            isChatExpanded = state.isChatExpanded,
            isVoiceRecording = state.isVoiceRecording,
            isProcessing = state.isProcessing,
            playbackState =
                PlaybackState(
                    isPlaying = state.isPlaying,
                    speedMultiplier = state.speedMultiplier,
                    currentKm = gps?.distanceTravelledKm ?: 0f,
                    totalKm = route?.distanceKm ?: 0f,
                ),
        )
    }

    override fun dispose() {
        scope.cancel()
    }
}
