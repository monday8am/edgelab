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
    fun `getToolsAsFlow should load preset tools from bundled json`() = runTest {
        val tools = repository.getToolsAsFlow().first()

        assertTrue(tools.isNotEmpty(), "Expected preset tools seeded from tool_tests.json")
    }

    @Test
    fun `loaded tools should be deduplicated by tool name`() = runTest {
        val tools = repository.getToolsAsFlow().first()

        val names = tools.map { it.function.name }
        assertEquals(names.size, names.toSet().size, "Tool names must be unique")
    }

    @Test
    fun `getMockResponsesAsFlow should carry a mock for every preset tool`() = runTest {
        val tools = repository.getToolsAsFlow().first()
        val mocks = repository.getMockResponsesAsFlow().first()

        tools.forEach { tool ->
            val mock = mocks[tool.function.name]
            assertNotNull(mock, "Expected a mock response for '${tool.function.name}'")
            assertTrue(mock.isNotBlank(), "Mock response must not be blank")
        }
    }

    @Test
    fun `get_location tool should be present with a mock response`() = runTest {
        val tools = repository.getToolsAsFlow().first()
        val mocks = repository.getMockResponsesAsFlow().first()

        val location = tools.firstOrNull { it.function.name == "get_location" }
        assertNotNull(location, "Expected get_location preset seeded from test 1")
        assertTrue(mocks["get_location"].orEmpty().isNotBlank())
        assertTrue(location.function.parameters.toString().contains("object"))
    }

    @Test
    fun `every tool should have a name and description`() = runTest {
        val tools = repository.getToolsAsFlow().first()

        tools.forEach { tool ->
            assertTrue(tool.function.name.isNotBlank(), "Tool name must not be blank")
            assertTrue(tool.function.description.isNotBlank(), "Tool description must not be blank")
        }
    }
}
