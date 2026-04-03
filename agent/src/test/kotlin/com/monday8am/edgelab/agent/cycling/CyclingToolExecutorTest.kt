package com.monday8am.edgelab.agent.cycling

import com.monday8am.edgelab.data.route.GravelSector
import com.monday8am.edgelab.data.route.HourlyWeather
import com.monday8am.edgelab.data.route.SegmentData
import com.monday8am.edgelab.data.route.SegmentRepository
import com.monday8am.edgelab.data.route.SegmentsData
import com.monday8am.edgelab.data.route.WeatherData
import com.monday8am.edgelab.data.route.WeatherRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CyclingToolExecutorTest {

    // region Fakes

    private class FakeSegmentRepository(private val data: SegmentsData) : SegmentRepository {
        var getSegmentsCallCount = 0

        override suspend fun getSegments(routeId: String): Result<SegmentsData> {
            getSegmentsCallCount++
            return Result.success(data)
        }

        override fun segmentsFlow(routeId: String): Flow<SegmentsData> = flowOf(data)
    }

    private class FakeWeatherRepository(private val data: WeatherData) : WeatherRepository {
        override suspend fun getWeather(routeId: String): Result<WeatherData> = Result.success(data)

        override fun weatherFlow(routeId: String): Flow<WeatherData> = flowOf(data)
    }

    // endregion

    // region Test fixtures

    private val testSegments =
        SegmentsData(
            routeId = "test-route",
            namedSectors =
                listOf(
                    SegmentData(
                        id = "s1",
                        name = "Monte Sante Marie",
                        category = "strade_bianche",
                        fromIndex = 100,
                        toIndex = 200,
                        startLat = 43.3,
                        startLng = 11.2,
                        distanceM = 8500,
                        elevationUpM = 290,
                        surface = "unpaved",
                        kmFromStart = 10.0f,
                    ),
                    SegmentData(
                        id = "s2",
                        name = "Bagnaia Gravel Sector",
                        category = "strade_bianche",
                        fromIndex = 400,
                        toIndex = 520,
                        startLat = 43.4,
                        startLng = 11.3,
                        distanceM = 7800,
                        elevationUpM = 220,
                        surface = "gravel",
                        kmFromStart = 30.0f,
                    ),
                ),
            gravelSectors =
                listOf(
                    GravelSector(
                        fromIndex = 90,
                        toIndex = 210,
                        startLat = 43.3,
                        startLng = 11.2,
                        distanceM = 9000,
                        elevationUpM = 300,
                        surface = "gravel",
                        kmFromStart = 9.5f,
                    ),
                    GravelSector(
                        fromIndex = 380,
                        toIndex = 530,
                        startLat = 43.4,
                        startLng = 11.3,
                        distanceM = 8200,
                        elevationUpM = 250,
                        surface = "gravel",
                        kmFromStart = 29.0f,
                    ),
                ),
        )

    private val testWeather =
        WeatherData(
            routeId = "test-route",
            location = "Siena, Italy",
            date = "2026-03-01",
            timezone = "Europe/Rome",
            summary = "Partly cloudy with light winds",
            hourly =
                listOf(
                    HourlyWeather(8, 12, 10, 15, "NW", 65, "Partly cloudy", 0f, 3),
                    HourlyWeather(9, 14, 12, 18, "NW", 60, "Sunny", 0f, 4),
                    HourlyWeather(10, 15, 13, 20, "N", 55, "Sunny", 0.2f, 5),
                    HourlyWeather(11, 14, 12, 22, "N", 58, "Cloudy", 0.5f, 4),
                ),
        )

    private fun createExecutor() =
        CyclingToolExecutor(
            segmentRepository = FakeSegmentRepository(testSegments),
            weatherRepository = FakeWeatherRepository(testWeather),
        )

    private val defaultContext =
        RideContext(
            routePointIndex = 50,
            distanceTravelledKm = 5.0f,
            speedKmh = 28.5f,
            power = 215,
            elapsedMs = 720_000L,
            totalDistanceKm = 184f,
            rideStartHour = 8,
        )

    // endregion

    // region Initialization Tests

    @Test
    fun `initialize loads data from all repositories`() = runTest {
        val fakeSegments = FakeSegmentRepository(testSegments)
        val executor =
            CyclingToolExecutor(
                segmentRepository = fakeSegments,
                weatherRepository = FakeWeatherRepository(testWeather),
            )
        executor.initialize("test-route")
        assertEquals(1, fakeSegments.getSegmentsCallCount)
    }

    @Test
    fun `toolDefinitions returns all 6 tools`() {
        val executor = createExecutor()
        assertEquals(6, executor.toolDefinitions.size)
        val names = executor.toolDefinitions.map { it.function.name }
        assertTrue(CyclingToolDefinitions.GET_RIDE_STATUS in names)
        assertTrue(CyclingToolDefinitions.GET_SEGMENT_AHEAD in names)
        assertTrue(CyclingToolDefinitions.GET_WEATHER_FORECAST in names)
        assertTrue(CyclingToolDefinitions.GET_ROUTE_ALTERNATIVES in names)
        assertTrue(CyclingToolDefinitions.FIND_NEARBY_POI in names)
        assertTrue(CyclingToolDefinitions.GET_RIDER_PROFILE in names)
    }

    // endregion

    // region get_ride_status Tests

    @Test
    fun `get_ride_status returns current speed and distance`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        val result = executor.execute(CyclingToolDefinitions.GET_RIDE_STATUS, "{}", defaultContext)
        assertTrue("speed_kmh" in result)
        assertTrue("distance_km" in result)
        assertTrue("elapsed_ms" in result)
    }

    @Test
    fun `get_ride_status includes power when available`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        val result = executor.execute(CyclingToolDefinitions.GET_RIDE_STATUS, "{}", defaultContext)
        assertTrue("power_watts" in result)
        assertTrue("215" in result)
    }

    @Test
    fun `get_ride_status omits power when null`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        val ctx = defaultContext.copy(power = null)
        val result = executor.execute(CyclingToolDefinitions.GET_RIDE_STATUS, "{}", ctx)
        assertFalse("power_watts" in result)
    }

    // endregion

    // region get_segment_ahead Tests

    @Test
    fun `get_segment_ahead returns next named sector ahead`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        // routePointIndex=50, before fromIndex=100 of "Monte Sante Marie"
        val result =
            executor.execute(CyclingToolDefinitions.GET_SEGMENT_AHEAD, "{}", defaultContext)
        assertTrue("Monte Sante Marie" in result)
        assertTrue("next_named_sector" in result)
    }

    @Test
    fun `get_segment_ahead reports current sector when inside one`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        val ctx = defaultContext.copy(routePointIndex = 150) // inside fromIndex=100..toIndex=200
        val result = executor.execute(CyclingToolDefinitions.GET_SEGMENT_AHEAD, "{}", ctx)
        assertTrue("current_named_sector" in result)
        assertTrue("Monte Sante Marie" in result)
    }

    @Test
    fun `get_segment_ahead returns no more segments at end of route`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        val ctx = defaultContext.copy(routePointIndex = 9999)
        val result = executor.execute(CyclingToolDefinitions.GET_SEGMENT_AHEAD, "{}", ctx)
        assertTrue("No more segments ahead" in result)
    }

    @Test
    fun `get_segment_ahead returns error when not initialized`() {
        val executor = createExecutor()
        // No initialize() call
        val result =
            executor.execute(CyclingToolDefinitions.GET_SEGMENT_AHEAD, "{}", defaultContext)
        assertTrue("error" in result)
    }

    // endregion

    // region get_weather_forecast Tests

    @Test
    fun `get_weather_forecast returns location and summary`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        val result =
            executor.execute(CyclingToolDefinitions.GET_WEATHER_FORECAST, "{}", defaultContext)
        assertTrue("Siena, Italy" in result)
        assertTrue("Partly cloudy" in result)
        assertTrue("hourly" in result)
    }

    @Test
    fun `get_weather_forecast filters by current clock hour`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        // rideStartHour=8, elapsedMs=3_600_000 (1 hour) → currentHour=9
        // Hourly data has hours 8,9,10,11 → filter hour >= 9 → returns hours 9,10,11
        val ctx = defaultContext.copy(rideStartHour = 8, elapsedMs = 3_600_000L)
        val result = executor.execute(CyclingToolDefinitions.GET_WEATHER_FORECAST, "{}", ctx)
        assertTrue("hourly" in result)
        // Hour 8 should be excluded (already past)
        assertFalse("\"hour\":8" in result.replace(" ", ""))
    }

    @Test
    fun `get_weather_forecast respects hours_ahead parameter`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        val result =
            executor.execute(
                CyclingToolDefinitions.GET_WEATHER_FORECAST,
                """{"hours_ahead": 1}""",
                defaultContext.copy(rideStartHour = 8, elapsedMs = 0L),
            )
        assertTrue("hourly" in result)
        assertTrue("temp_c" in result)
    }

    // endregion

    // region get_route_alternatives Tests

    @Test
    fun `get_route_alternatives returns upcoming gravel sectors`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        val result =
            executor.execute(CyclingToolDefinitions.GET_ROUTE_ALTERNATIVES, "{}", defaultContext)
        assertTrue("upcoming_challenging_sectors" in result)
        assertTrue("has_paved_alternative" in result)
    }

    // endregion

    // region find_nearby_poi Tests

    @Test
    fun `find_nearby_poi returns pois within default radius`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        // distanceTravelledKm=5.0, radius=5 → Fonte di Siena at km 0 (5km away, at boundary)
        // and Cicli Pieri at km 4 (1km away, within)
        val ctx = defaultContext.copy(distanceTravelledKm = 5.0f)
        val result = executor.execute(CyclingToolDefinitions.FIND_NEARBY_POI, "{}", ctx)
        assertTrue("pois" in result)
        assertTrue("Fonte di Siena" in result)
    }

    @Test
    fun `find_nearby_poi filters by category`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        val result =
            executor.execute(
                CyclingToolDefinitions.FIND_NEARBY_POI,
                """{"category": "cafe", "radius_km": 100}""",
                defaultContext,
            )
        assertTrue("cafe" in result)
        assertFalse("bike_shop" in result)
        assertFalse("water" in result)
    }

    // endregion

    // region get_rider_profile Tests

    @Test
    fun `get_rider_profile returns ftp and zones`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        val result =
            executor.execute(CyclingToolDefinitions.GET_RIDER_PROFILE, "{}", defaultContext)
        assertTrue("ftp_watts" in result)
        assertTrue("zones" in result)
        assertTrue("250" in result)
    }

    // endregion

    // region Unknown Tool Tests

    @Test
    fun `execute returns error for unknown tool`() = runTest {
        val executor = createExecutor()
        executor.initialize("test-route")
        val result = executor.execute("unknown_tool", "{}", defaultContext)
        assertTrue("error" in result)
        assertTrue("unknown_tool" in result)
    }

    // endregion
}
