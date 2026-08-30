package com.monday8am.edgelab.core.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallDialectTest {

    @Test
    fun `lfm families get the lfm2 dialect`() {
        listOf("lfm2", "lfm2.5", "LFM2.5").forEach { family ->
            assertSame(family, Lfm2ToolCallDialect, toolCallDialectFor(family))
        }
    }

    @Test
    fun `every other family trusts the runtime`() {
        listOf("gemma3", "qwen3", "hammer", "functiongemma", "").forEach { family ->
            assertSame(family, RuntimeHandled, toolCallDialectFor(family))
        }
    }

    @Test
    fun `the runtime dialect never parses or rewrites anything`() {
        val raw = "Sure.\n<|tool_call_start|>[get_time()]<|tool_call_end|>"

        assertTrue(RuntimeHandled.recover(raw).isEmpty())
        assertEquals(raw, RuntimeHandled.strip(raw))
    }
}
