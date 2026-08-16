package com.monday8am.edgelab.agent.playground

import com.monday8am.edgelab.agent.core.LocalInferenceEngine
import com.monday8am.edgelab.agent.tools.OpenApiToolHandler
import com.monday8am.edgelab.data.model.ModelCatalog
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.playground.Probe
import com.monday8am.edgelab.data.testing.FunctionSpec
import com.monday8am.edgelab.data.testing.ToolSpecification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

class LocalPlaygroundBackendTest {

    @Test
    fun `run should register probes as tools and return the model text`() = runTest {
        val engine = FakeEngine()
        val runner = LocalPlaygroundBackend(engine, TEST_MODEL, "/fake/model.litertlm")

        runner.initialize()
        val result = runner.run("Where am I?", listOf(probe("get_location"))).getOrThrow()

        assertEquals("You are at 40.4, -3.7.", result.text)
        assertEquals(1, engine.setToolsCallCount)
    }

    @Test
    fun `run should record each tool call with the mock response returned to the model`() =
        runTest {
            val engine = FakeEngine(simulateToolCallParams = """{"unused":true}""")
            val runner = LocalPlaygroundBackend(engine, TEST_MODEL, "/fake/model.litertlm")
            val locationProbe =
                probe("get_location", mock = "{\"latitude\":40.4,\"longitude\":-3.7}")

            runner.initialize()
            val result = runner.run("Where am I?", listOf(locationProbe)).getOrThrow()

            assertEquals(1, result.toolCalls.size)
            val call = result.toolCalls.first()
            assertEquals("get_location", call.name)
            assertEquals(locationProbe.mockResponse, call.mockResponse)
            assertEquals("unused", call.args.keys.first())
        }

    @Test
    fun `run with no probes should return model text and no tool calls`() = runTest {
        val engine = FakeEngine()
        val runner = LocalPlaygroundBackend(engine, TEST_MODEL, "/fake/model.litertlm")

        runner.initialize()
        val result = runner.run("Hello", emptyList()).getOrThrow()

        assertEquals("You are at 40.4, -3.7.", result.text)
        assertTrue(result.toolCalls.isEmpty())
        assertEquals(1, engine.setToolsCallCount)
    }

    @Test
    fun `run should report multiple tool calls when the model invokes a probe twice`() = runTest {
        val engine = FakeEngine(simulateToolCallParams = """{"x":1}""", callEachToolTimes = 2)
        val runner = LocalPlaygroundBackend(engine, TEST_MODEL, "/fake/model.litertlm")
        val weatherProbe = probe("get_weather", mock = "{\"temp\":22}")

        runner.initialize()
        val result = runner.run("Weather twice?", listOf(weatherProbe)).getOrThrow()

        assertEquals(2, result.toolCalls.size)
        result.toolCalls.forEach { call ->
            assertEquals("get_weather", call.name)
            assertEquals("{\"temp\":22}", call.mockResponse)
        }
    }

    @Test
    fun `initialize should be idempotent across turns`() = runTest {
        val engine = FakeEngine()
        val runner = LocalPlaygroundBackend(engine, TEST_MODEL, "/fake/model.litertlm")

        runner.initialize()
        runner.initialize()

        assertEquals(1, engine.initializeCallCount)
    }

    @Test
    fun `run should fail when inference fails`() = runTest {
        val engine = FakeEngine(promptShouldFail = true)
        val runner = LocalPlaygroundBackend(engine, TEST_MODEL, "/fake/model.litertlm")

        runner.initialize()
        val result = runner.run("Hi", emptyList())

        assertTrue(result.isFailure)
    }

    private fun probe(name: String, mock: String = "{\"ok\":true}"): Probe =
        Probe(
            toolSpec =
                ToolSpecification(
                    function =
                        FunctionSpec(
                            name = name,
                            description = "test probe $name",
                            parameters = JsonObject(emptyMap()),
                        )
                ),
            mockResponse = mock,
        )

    private val TEST_MODEL: ModelConfiguration = ModelCatalog.GEMMA3_1B

    /** Fake engine that can simulate the model calling each registered tool. */
    private class FakeEngine(
        private val simulateToolCallParams: String? = null,
        private val callEachToolTimes: Int = 1,
        private val promptShouldFail: Boolean = false,
    ) : LocalInferenceEngine {
        var initializeCallCount = 0
            private set

        var setToolsCallCount = 0
            private set

        private val registeredTools = mutableListOf<Any>()

        override suspend fun initialize(
            modelConfig: ModelConfiguration,
            modelPath: String,
        ): Result<Unit> {
            initializeCallCount++
            return Result.success(Unit)
        }

        override fun initializeAsFlow(
            modelConfig: ModelConfiguration,
            modelPath: String,
        ): Flow<LocalInferenceEngine> = flowOf(this)

        override fun setToolsAndResetConversation(tools: List<Any>): Result<Unit> {
            setToolsCallCount++
            registeredTools.clear()
            registeredTools.addAll(tools)
            return Result.success(Unit)
        }

        override suspend fun prompt(prompt: String): Result<String> {
            if (promptShouldFail) return Result.failure(RuntimeException("boom"))
            val params = simulateToolCallParams
            if (params != null) {
                registeredTools.filterIsInstance<OpenApiToolHandler>().forEach { handler ->
                    repeat(callEachToolTimes) { handler.execute(params) }
                }
            }
            return Result.success("You are at 40.4, -3.7.")
        }

        override fun promptStreaming(prompt: String): Flow<String> =
            flowOf("You are at 40.4, -3.7.")

        override fun closeSession(): Result<Unit> = Result.success(Unit)
    }
}
