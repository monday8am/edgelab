package com.monday8am.edgelab.presentation.testing

import com.monday8am.edgelab.data.testing.TestDomain
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class TestViewModelTest {

    @Test
    fun `TestStatus should store domain as enum`() {
        val status = TestStatus("Test 1", TestDomain.GENERIC, TestStatus.State.IDLE)

        assertEquals("Test 1", status.name)
        assertEquals(TestDomain.GENERIC, status.domain)
        assertEquals(TestStatus.State.IDLE, status.state)
    }

    @Test
    fun `TestStatus State enum should have all expected values`() {
        val states = TestStatus.State.entries.toTypedArray()

        assertEquals(4, states.size)
        assertTrue(TestStatus.State.IDLE in states)
        assertTrue(TestStatus.State.RUNNING in states)
        assertTrue(TestStatus.State.PASS in states)
        assertTrue(TestStatus.State.FAIL in states)
    }

    @Test
    fun `TestDomain enum should serialize with correct JSON names`() {
        // TestDomain.GENERIC should serialize to "generic" in JSON
        // TestDomain.YAZIO should serialize to "yazio" in JSON
        assertEquals(2, TestDomain.entries.size)
        assertTrue(TestDomain.GENERIC in TestDomain.entries.toTypedArray())
        assertTrue(TestDomain.YAZIO in TestDomain.entries.toTypedArray())
    }

    @Test
    fun `TestUiAction RunTests should accept nullable domain filter`() {
        val actionWithFilter = TestUiAction.RunTests(filterDomain = TestDomain.YAZIO)
        val actionWithoutFilter = TestUiAction.RunTests(filterDomain = null)

        assertEquals(TestDomain.YAZIO, actionWithFilter.filterDomain)
        assertEquals(null, actionWithoutFilter.filterDomain)
    }

    @Test
    fun `TestCase should have correct default domain`() {
        val testCase =
            TestCase(
                name = "Test",
                query = TestQuery("Test query"),
                systemPrompt = "Prompt",
                validator = { _, _ -> ValidationResult.Pass("OK") },
            )

        assertEquals(TestDomain.GENERIC, testCase.domain)
    }

    @Test
    fun `TestCase can be created with YAZIO domain`() {
        val testCase =
            TestCase(
                name = "YAZIO Test",
                query = TestQuery("Test query"),
                systemPrompt = "Prompt",
                validator = { _, _ -> ValidationResult.Pass("OK") },
                domain = TestDomain.YAZIO,
            )

        assertEquals(TestDomain.YAZIO, testCase.domain)
    }

    @Test
    fun `availableDomains should be distinct and ordered`() {
        val domains =
            persistentListOf(
                TestDomain.GENERIC,
                TestDomain.YAZIO,
                TestDomain.GENERIC, // duplicate
                TestDomain.YAZIO, // duplicate
            )

        val distinct = domains.distinct()

        assertEquals(2, distinct.size)
        assertTrue(TestDomain.GENERIC in distinct)
        assertTrue(TestDomain.YAZIO in distinct)
    }
}
