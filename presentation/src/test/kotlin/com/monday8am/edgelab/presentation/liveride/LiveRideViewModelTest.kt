package com.monday8am.edgelab.presentation.liveride

import com.monday8am.edgelab.data.route.RouteCoordinate
import com.monday8am.edgelab.data.route.RouteData
import com.monday8am.edgelab.data.route.RouteRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

internal class FakeRouteRepository(private val route: RouteData? = null) : RouteRepository {
    override suspend fun getRoute(routeId: String): Result<RouteData> =
        if (route != null) Result.success(route)
        else Result.failure(NoSuchElementException("not found"))

    override fun routeFlow(routeId: String): Flow<RouteData> = flow {
        val data = getRoute(routeId).getOrThrow()
        emit(data)
    }
}

internal class FakeGpsSource : GpsSource {
    private val _positions = MutableSharedFlow<GpsPosition>()
    override val positions: Flow<GpsPosition> = _positions

    var started = false
        private set

    var lastSpeedMultiplier = 1.0f
        private set

    override fun start() {
        started = true
    }

    override fun pause() {
        started = false
    }

    override fun setSpeedMultiplier(multiplier: Float) {
        lastSpeedMultiplier = multiplier
    }

    suspend fun emit(position: GpsPosition) = _positions.emit(position)
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveRideViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val twoPointRoute =
        RouteData(
            routeId = "strade-bianche",
            name = "Strade Bianche",
            distanceKm = 2.96f,
            coordinates =
                listOf(
                    RouteCoordinate(lat = 43.32, lng = 11.33, alt = 300.0, t = 0L),
                    RouteCoordinate(lat = 43.33, lng = 11.34, alt = 310.0, t = 60000L),
                    RouteCoordinate(lat = 43.34, lng = 11.35, alt = 320.0, t = 120000L),
                ),
        )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        route: RouteData? = twoPointRoute,
        fakeGpsSource: FakeGpsSource = FakeGpsSource(),
    ): LiveRideViewModelImpl =
        LiveRideViewModelImpl(
            routeId = "strade-bianche",
            routeRepository = FakeRouteRepository(route),
            gpsSourceFactory = GpsSourceFactory { _ -> fakeGpsSource },
            dispatcher = testDispatcher,
        )

    // region Initialization Tests

    @Test
    fun `Initialize should start in loading state`() = runTest {
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should load route and clear loading state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Strade Bianche", state.routeName)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should start playback after route loads`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.playbackState.isPlaying)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should apply custom playback speed`() = runTest {
        val viewModel =
            LiveRideViewModelImpl(
                routeId = "strade-bianche",
                routeRepository = FakeRouteRepository(twoPointRoute),
                playbackSpeed = 2.0f,
                gpsSourceFactory = GpsSourceFactory { _ -> FakeGpsSource() },
                dispatcher = testDispatcher,
            )
        advanceUntilIdle()

        assertEquals(2.0f, viewModel.uiState.value.playbackState.speedMultiplier)

        viewModel.dispose()
    }

    @Test
    fun `Initialize should clear loading when route not found`() = runTest {
        val viewModel = createViewModel(route = null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.routePolyline.isEmpty())

        viewModel.dispose()
    }

    @Test
    fun `Initialize should post welcome chat message after route loads`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val messages = viewModel.uiState.value.chatMessages
        assertTrue(messages.isNotEmpty())
        assertTrue(messages.first() is ChatMessage.Copilot)

        viewModel.dispose()
    }

    // endregion

    // region GPS Position Tests

    @Test
    fun `GPS position update should advance current position and HUD metrics`() = runTest {
        val fakeGps = FakeGpsSource()
        val viewModel = createViewModel(fakeGpsSource = fakeGps)
        advanceUntilIdle()

        val position =
            GpsPosition(
                latLng = LatLng(43.33, 11.34),
                heading = 45f,
                speedKmh = 28f,
                distanceTravelledKm = 1.5f,
                power = 195,
                routePointIndex = 1,
            )
        fakeGps.emit(position)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LatLng(43.33, 11.34), state.currentPosition)
        assertEquals(45f, state.currentHeading)
        assertEquals(28f, state.hudMetrics.speed)
        assertEquals(1.5f, state.hudMetrics.distance)
        assertEquals(195, state.hudMetrics.power)
        assertEquals(1.5f, state.playbackState.currentKm)

        viewModel.dispose()
    }

    @Test
    fun `GPS position update should extend completed polyline`() = runTest {
        val fakeGps = FakeGpsSource()
        val viewModel = createViewModel(fakeGpsSource = fakeGps)
        advanceUntilIdle()

        fakeGps.emit(
            GpsPosition(
                latLng = LatLng(43.33, 11.34),
                heading = 45f,
                speedKmh = 25f,
                distanceTravelledKm = 1.2f,
                power = 180,
                routePointIndex = 1,
            )
        )
        advanceUntilIdle()

        // routePointIndex=1 → take(2) → 2 points in completed polyline
        assertEquals(2, viewModel.uiState.value.completedPolyline.size)

        viewModel.dispose()
    }

    // endregion

    // region Action Tests

    @Test
    fun `TogglePlayback should pause when playing`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.playbackState.isPlaying)

        viewModel.onUiAction(LiveRideAction.TogglePlayback)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.playbackState.isPlaying)

        viewModel.dispose()
    }

    @Test
    fun `TogglePlayback should resume when paused`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(LiveRideAction.TogglePlayback)
        viewModel.onUiAction(LiveRideAction.TogglePlayback)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.playbackState.isPlaying)

        viewModel.dispose()
    }

    @Test
    fun `CycleSpeed should advance to next speed multiplier`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val initialSpeed = viewModel.uiState.value.playbackState.speedMultiplier

        viewModel.onUiAction(LiveRideAction.CycleSpeed)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.playbackState.speedMultiplier != initialSpeed)

        viewModel.dispose()
    }

    @Test
    fun `ExpandChat should set isChatExpanded true`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(LiveRideAction.ExpandChat)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isChatExpanded)

        viewModel.dispose()
    }

    @Test
    fun `CollapseChat should set isChatExpanded false`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(LiveRideAction.ExpandChat)
        viewModel.onUiAction(LiveRideAction.CollapseChat)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isChatExpanded)

        viewModel.dispose()
    }

    @Test
    fun `SendTextMessage should append user message to chat`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(LiveRideAction.SendTextMessage("How far to the climb?"))
        advanceUntilIdle()

        val userMessage =
            viewModel.uiState.value.chatMessages.filterIsInstance<ChatMessage.User>().firstOrNull()
        assertTrue(userMessage != null)
        assertEquals("How far to the climb?", userMessage.text)

        viewModel.dispose()
    }

    @Test
    fun `EndRide action should not throw`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(LiveRideAction.EndRide)

        viewModel.dispose()
    }

    // endregion
}
