package com.monday8am.edgelab.agent.cycling

import com.monday8am.edgelab.agent.core.LocalInferenceEngine
import com.monday8am.edgelab.data.model.ModelConfiguration
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
class CopilotAgentTest {

    // region Fakes

    private class FakeLocalInferenceEngine(
        private val promptResult: Result<String> = Result.success("Test response"),
        private val simulateFirstToolCall: Boolean = false,
        private val setToolsResult: Result<Unit> = Result.success(Unit),
    ) : LocalInferenceEngine {
        var setToolsCallCount = 0
        var promptCallCount = 0
        var lastPrompt: String = ""
        var registeredTools: List<Any> = emptyList()
            private set

        override fun setToolsAndResetConversation(tools: List<Any>): Result<Unit> {
            setToolsCallCount++
            registeredTools = tools
            return setToolsResult
        }

        override suspend fun prompt(prompt: String): Result<String> {
            promptCallCount++
            lastPrompt = prompt
            if (simulateFirstToolCall) {
                registeredTools.filterIsInstance<CyclingOpenApiTool>().firstOrNull()?.execute("{}")
            }
            return promptResult
        }

        override suspend fun initialize(
            modelConfig: ModelConfiguration,
            modelPath: String,
        ): Result<Unit> = Result.success(Unit)

        override fun promptStreaming(prompt: String): Flow<String> = flowOf()

        override fun initializeAsFlow(
            modelConfig: ModelConfiguration,
            modelPath: String,
        ): Flow<LocalInferenceEngine> = flowOf(this)

        override fun closeSession(): Result<Unit> = Result.success(Unit)
    }

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
                    )
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
                    )
                ),
        )

    private val testWeather =
        WeatherData(
            routeId = "test-route",
            location = "Siena, Italy",
            date = "2026-03-01",
            timezone = "Europe/Rome",
            summary = "Partly cloudy",
            hourly = listOf(HourlyWeather(8, 12, 10, 15, "NW", 65, "Partly cloudy", 0f, 3)),
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

    private fun createExecutor(
        fakeSegments: FakeSegmentRepository = FakeSegmentRepository(testSegments)
    ) =
        CyclingToolExecutor(
            segmentRepository = fakeSegments,
            weatherRepository = FakeWeatherRepository(testWeather),
        )

    private fun createAgent(
        engine: FakeLocalInferenceEngine = FakeLocalInferenceEngine(),
        fakeSegments: FakeSegmentRepository = FakeSegmentRepository(testSegments),
    ): Pair<CopilotAgent, FakeLocalInferenceEngine> =
        CopilotAgent(engine, createExecutor(fakeSegments)) to engine

    // endregion

    // region Initialization Tests

    @Test
    fun `initialize loads route data via toolExecutor`() = runTest {
        val fakeSegments = FakeSegmentRepository(testSegments)
        val (agent, _) = createAgent(fakeSegments = fakeSegments)

        agent.initialize("test-route")

        assertEquals(1, fakeSegments.getSegmentsCallCount)
    }

    @Test
    fun `initialize registers tools with inference engine`() = runTest {
        val (agent, engine) = createAgent()

        agent.initialize("test-route")

        assertEquals(1, engine.setToolsCallCount)
    }

    @Test
    fun `initialize registers all 6 tools with engine`() = runTest {
        val (agent, engine) = createAgent()

        agent.initialize("test-route")

        assertEquals(6, engine.registeredTools.size)
    }

    @Test
    fun `initialize returns failure when engine tool registration fails`() = runTest {
        val (agent, _) =
            createAgent(
                FakeLocalInferenceEngine(
                    setToolsResult = Result.failure(RuntimeException("engine not ready"))
                )
            )

        val result = agent.initialize("test-route")

        assertTrue(result.isFailure)
    }

    @Test
    fun `ask returns fallback when called before initialize`() = runTest {
        val (agent, _) = createAgent()
        // No initialize() call

        val response = agent.ask("What's ahead?", defaultContext)

        assertEquals(CopilotAgent.MODEL_NOT_READY_MESSAGE, response.text)
        assertTrue(response.toolCalls.isEmpty())
    }

    // endregion

    // region ask() Tests

    @Test
    fun `ask returns engine response text`() = runTest {
        val (agent, _) =
            createAgent(FakeLocalInferenceEngine(promptResult = Result.success("Great pace!")))
        agent.initialize("test-route")

        val response = agent.ask("How am I doing?", defaultContext)

        assertEquals("Great pace!", response.text)
    }

    @Test
    fun `ask returns fallback message when engine fails`() = runTest {
        val (agent, _) =
            createAgent(
                FakeLocalInferenceEngine(
                    promptResult = Result.failure(RuntimeException("engine error"))
                )
            )
        agent.initialize("test-route")

        val response = agent.ask("What's ahead?", defaultContext)

        assertEquals(CopilotAgent.MODEL_NOT_READY_MESSAGE, response.text)
    }

    @Test
    fun `ask includes question in prompt sent to engine`() = runTest {
        val (agent, engine) = createAgent()
        agent.initialize("test-route")

        agent.ask("Is there gravel ahead?", defaultContext)

        assertTrue("Is there gravel ahead?" in engine.lastPrompt)
    }

    @Test
    fun `ask includes ride context speed in prompt`() = runTest {
        val (agent, engine) = createAgent()
        agent.initialize("test-route")

        agent.ask("How am I doing?", defaultContext.copy(speedKmh = 32.5f))

        assertTrue("32.5" in engine.lastPrompt)
    }

    @Test
    fun `ask includes power in prompt when available`() = runTest {
        val (agent, engine) = createAgent()
        agent.initialize("test-route")

        agent.ask("Am I overexerting?", defaultContext.copy(power = 280))

        assertTrue("280" in engine.lastPrompt)
    }

    @Test
    fun `ask omits power from prompt when null`() = runTest {
        val (agent, engine) = createAgent()
        agent.initialize("test-route")

        agent.ask("How am I doing?", defaultContext.copy(power = null))

        assertFalse("Power" in engine.lastPrompt)
    }

    // endregion

    // region Tool call Tests

    @Test
    fun `ask records tool calls from model in response`() = runTest {
        val (agent, _) = createAgent(FakeLocalInferenceEngine(simulateFirstToolCall = true))
        agent.initialize("test-route")

        val response = agent.ask("What's the status?", defaultContext)

        assertEquals(1, response.toolCalls.size)
        assertEquals(CyclingToolDefinitions.GET_RIDE_STATUS, response.toolCalls.first().toolName)
    }

    @Test
    fun `ask returns empty tool calls when model makes no tool calls`() = runTest {
        val (agent, _) = createAgent()
        agent.initialize("test-route")

        val response = agent.ask("Thanks!", defaultContext)

        assertTrue(response.toolCalls.isEmpty())
    }

    @Test
    fun `ask clears tool calls between questions so counts do not accumulate`() = runTest {
        // Engine always simulates 1 tool call per prompt
        val (agent, _) = createAgent(FakeLocalInferenceEngine(simulateFirstToolCall = true))
        agent.initialize("test-route")

        agent.ask("First question?", defaultContext) // 1 tool call
        val second = agent.ask("Second question?", defaultContext) // clearCalls() + 1 call = 1

        assertEquals(1, second.toolCalls.size) // not 2
    }

    @Test
    fun `ask sets current context on tools so tool result reflects rider state`() = runTest {
        // The simulated tool call is get_ride_status (first in CyclingToolDefinitions.ALL),
        // which returns the speed from RideContext. Verify the result contains the expected speed.
        val (agent, _) = createAgent(FakeLocalInferenceEngine(simulateFirstToolCall = true))
        agent.initialize("test-route")

        val ctx = defaultContext.copy(speedKmh = 42.0f)
        val response = agent.ask("What's my speed?", ctx)

        assertTrue(response.toolCalls.isNotEmpty())
        assertTrue("42" in response.toolCalls.first().resultJson)
    }

    // endregion
}
