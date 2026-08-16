package com.monday8am.edgelab.data.playground

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.data.testing.TestSuiteDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Bundled preset Probe library, seeded from `tool_tests.json`.
 *
 * For each test case that defines tools with matching mock responses, one Probe is produced per
 * tool. Probes are deduplicated by tool name (first definition wins), so the library is a flat,
 * unique set of presets for 1-tap add.
 *
 * Tools without a corresponding mock entry are skipped — a Probe is meaningless without a response
 * for the model to integrate.
 */
class AssetsProbeRepository(
    private val resourcePath: String = "com/monday8am/edgelab/data/testing/tool_tests.json"
) : ProbeRepository {

    private val logger = Logger.withTag("AssetsProbeRepository")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getProbesAsFlow(): Flow<List<Probe>> =
        flow { emit(loadProbes()) }
            .catch { e ->
                if (e is CancellationException) throw e
                logger.e(e) { "Failed to load preset probes" }
                emit(emptyList())
            }

    private fun loadProbes(): List<Probe> {
        val resource =
            this::class.java.classLoader?.getResource(resourcePath)?.readText()
                ?: run {
                    logger.w { "tool_tests.json not found at $resourcePath" }
                    return emptyList()
                }

        val tests = json.decodeFromString<TestSuiteDefinition>(resource).tests
        val seen = mutableSetOf<String>()
        val probes = mutableListOf<Probe>()

        for (test in tests) {
            val mocks = test.mockToolResponses ?: emptyMap()
            for (tool in test.tools.orEmpty()) {
                val mock = mocks[tool.function.name] ?: continue
                val mockString = mockToString(mock) ?: continue
                if (tool.function.name !in seen) {
                    seen += tool.function.name
                    probes += Probe(toolSpec = tool, mockResponse = mockString)
                }
            }
        }

        logger.i { "Loaded ${probes.size} preset probes: ${probes.map { it.name }}" }
        return probes
    }

    private fun mockToString(element: JsonElement): String? =
        when (element) {
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
}
