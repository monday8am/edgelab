package com.monday8am.edgelab.data.playground

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.data.testing.TestSuiteDefinition
import com.monday8am.edgelab.data.testing.ToolSpecification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Bundled preset tool library, seeded from `tool_tests.json`. Tools are deduplicated by name (first
 * definition wins); tools without a matching mock entry are skipped — a preset is meaningless
 * without a response for the model to integrate.
 */
class AssetsProbeRepository(
    private val resourcePath: String = "com/monday8am/edgelab/data/testing/tool_tests.json"
) : ProbeRepository {

    private val logger = Logger.withTag("AssetsProbeRepository")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getToolsAsFlow(): Flow<List<ToolSpecification>> = flow {
        emit(loadLibrary().tools)
    }
        .catch { e ->
            if (e is CancellationException) throw e
            logger.e(e) { "Failed to load preset tools" }
            emit(emptyList())
        }

    override fun getMockResponsesAsFlow(): Flow<Map<String, String>> = flow {
        emit(loadLibrary().mockResponses)
    }
        .catch { e ->
            if (e is CancellationException) throw e
            logger.e(e) { "Failed to load preset mock responses" }
            emit(emptyMap())
        }

    private fun loadLibrary(): PresetLibrary {
        val resource =
            this::class.java.classLoader?.getResource(resourcePath)?.readText()
                ?: run {
                    logger.w { "tool_tests.json not found at $resourcePath" }
                    return PresetLibrary(emptyList(), emptyMap())
                }

        val tests = json.decodeFromString<TestSuiteDefinition>(resource).tests
        val seen = mutableSetOf<String>()
        val tools = mutableListOf<ToolSpecification>()
        val mocks = mutableMapOf<String, String>()

        for (test in tests) {
            val testMocks = test.mockToolResponses ?: emptyMap()
            for (tool in test.tools.orEmpty()) {
                val name = tool.function.name
                val mock = testMocks[name] ?: continue
                val mockString = mockToString(mock) ?: continue
                if (name !in seen) {
                    seen += name
                    tools += tool
                    mocks[name] = mockString
                }
            }
        }

        logger.i { "Loaded ${tools.size} preset tools: ${tools.map { it.function.name }}" }
        return PresetLibrary(tools, mocks)
    }

    private fun mockToString(element: JsonElement): String? =
        when (element) {
            is JsonPrimitive -> element.content
            else -> element.toString()
        }

    private data class PresetLibrary(
        val tools: List<ToolSpecification>,
        val mockResponses: Map<String, String>,
    )
}
