package com.monday8am.edgelab.core.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Lfm2ToolCallDialectTest {

    @Test
    fun `parses a no-argument call`() {
        val calls = Lfm2ToolCallDialect.recover("<|tool_call_start|>[get_time()]<|tool_call_end|>")

        assertEquals(1, calls.size)
        assertEquals("get_time", calls[0].name)
        assertEquals("{}", calls[0].argumentsJson)
    }

    @Test
    fun `parses typed arguments`() {
        val calls =
            Lfm2ToolCallDialect.recover(
                """<|tool_call_start|>[get_weather(city="Madrid", days=3, metric=True, note=None)]<|tool_call_end|>"""
            )

        assertEquals(1, calls.size)
        assertEquals(
            """{"city":"Madrid","days":3,"metric":true,"note":null}""",
            calls[0].argumentsJson,
        )
    }

    @Test
    fun `keeps commas inside quoted values`() {
        val calls =
            Lfm2ToolCallDialect.recover(
                """<|tool_call_start|>[search(query="pizza, pasta", limit=2)]<|tool_call_end|>"""
            )

        assertEquals("""{"query":"pizza, pasta","limit":2}""", calls[0].argumentsJson)
    }

    @Test
    fun `parses list arguments`() {
        val calls =
            Lfm2ToolCallDialect.recover(
                """<|tool_call_start|>[plan(stops=["a", "b"], count=2)]<|tool_call_end|>"""
            )

        assertEquals("""{"stops":["a","b"],"count":2}""", calls[0].argumentsJson)
    }

    @Test
    fun `parses several calls in one block`() {
        val calls =
            Lfm2ToolCallDialect.recover(
                """<|tool_call_start|>[get_time(), get_weather(city="Madrid")]<|tool_call_end|>"""
            )

        assertEquals(listOf("get_time", "get_weather"), calls.map { it.name })
    }

    @Test
    fun `ignores plain text`() {
        assertTrue(Lfm2ToolCallDialect.recover("It is 11:20 in Madrid.").isEmpty())
    }

    @Test
    fun `strips the markup from surrounding prose`() {
        val raw = "Sure.\n<|tool_call_start|>[get_time()]<|tool_call_end|>"

        assertEquals("Sure.", Lfm2ToolCallDialect.strip(raw))
    }
}
