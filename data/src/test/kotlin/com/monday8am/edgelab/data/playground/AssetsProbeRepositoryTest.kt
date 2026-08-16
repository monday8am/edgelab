package com.monday8am.edgelab.data.playground

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class AssetsProbeRepositoryTest {

    private val repository = AssetsProbeRepository()

    @Test
    fun `getProbesAsFlow should load preset probes from bundled json`() = runTest {
        val probes = repository.getProbesAsFlow().first()

        assertTrue(probes.isNotEmpty(), "Expected preset probes seeded from tool_tests.json")
    }

    @Test
    fun `loaded probes should be deduplicated by tool name`() = runTest {
        val probes = repository.getProbesAsFlow().first()

        val names = probes.map { it.name }
        assertEquals(names.size, names.toSet().size, "Probe names must be unique")
    }

    @Test
    fun `get_location probe should be present with a mock response`() = runTest {
        val probes = repository.getProbesAsFlow().first()

        val location = probes.firstOrNull { it.name == "get_location" }
        assertNotNull(location, "Expected get_location preset seeded from test 1")
        assertTrue(location.mockResponse.isNotBlank(), "Probe must carry a mock response")
        assertTrue(location.toolSpec.function.parameters.toString().contains("object"))
    }

    @Test
    fun `every probe should have a name and description`() = runTest {
        val probes = repository.getProbesAsFlow().first()

        probes.forEach { probe ->
            assertTrue(probe.name.isNotBlank(), "Probe name must not be blank")
            assertTrue(probe.description.isNotBlank(), "Probe description must not be blank")
        }
    }
}
