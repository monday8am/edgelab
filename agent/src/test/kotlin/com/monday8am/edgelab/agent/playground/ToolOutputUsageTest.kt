package com.monday8am.edgelab.agent.playground

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolOutputUsageTest {

    // region Used

    @Test
    fun `string value from the mock appearing in the text counts as used`() {
        assertTrue(ToolOutputUsage.isUsed("""{"city": "Madrid"}""", "You are in Madrid right now."))
    }

    @Test
    fun `word match is case-insensitive`() {
        assertTrue(ToolOutputUsage.isUsed("""{"city": "MADRID"}""", "You are in madrid."))
    }

    @Test
    fun `rounded numbers count as used`() {
        assertTrue(ToolOutputUsage.isUsed("""{"latitude": 40.4168}""", "You are at 40.42."))
    }

    @Test
    fun `negative rounded numbers count as used`() {
        assertTrue(ToolOutputUsage.isUsed("""{"longitude": -3.7038}""", "longitude -3.7"))
    }

    @Test
    fun `large numbers match within relative tolerance`() {
        assertTrue(ToolOutputUsage.isUsed("""{"population": 21000}""", "About 21050 people."))
    }

    @Test
    fun `plain-string mock counts as used when its words surface`() {
        assertTrue(ToolOutputUsage.isUsed("The weather is sunny", "It's sunny today!"))
    }

    @Test
    fun `values inside nested objects count as used`() {
        assertTrue(ToolOutputUsage.isUsed("""{"result": {"name": "Alice"}}""", "Hello Alice."))
    }

    @Test
    fun `any one distinctive value in a multi-value mock counts as used`() {
        assertTrue(
            ToolOutputUsage.isUsed(
                """{"latitude": 40.4168, "longitude": -3.7038}""",
                "You are near latitude 40.4.",
            )
        )
    }

    @Test
    fun `bare date-string mock contributes evidence instead of being dropped as a literal`() {
        assertTrue(ToolOutputUsage.isUsed("2024-02-15", "The meeting is in 2024."))
    }

    @Test
    fun `bare single-word mock contributes evidence instead of being dropped as a literal`() {
        assertTrue(ToolOutputUsage.isUsed("sunny", "It is sunny today."))
    }

    // endregion

    // region Ignored

    @Test
    fun `text with none of the mock content counts as ignored`() {
        assertFalse(ToolOutputUsage.isUsed("""{"tempC": 21}""", "I cannot check the weather."))
    }

    @Test
    fun `word stems do not count as matches`() {
        assertFalse(ToolOutputUsage.isUsed("""{"city": "Paris"}""", "Comparison of cities."))
    }

    @Test
    fun `trivially-common integers in the mock are not evidence`() {
        assertFalse(ToolOutputUsage.isUsed("""{"count": 2}""", "There are 2 of them."))
    }

    @Test
    fun `booleans in the mock are not evidence`() {
        assertFalse(ToolOutputUsage.isUsed("""{"sunny": true}""", "That's true."))
    }

    @Test
    fun `sign difference is not a match`() {
        assertFalse(ToolOutputUsage.isUsed("""{"tempC": -15}""", "It's 15 degrees."))
    }

    @Test
    fun `empty mock is ignored`() {
        assertFalse(ToolOutputUsage.isUsed("{}", "Anything at all."))
    }

    @Test
    fun `mock with only non-distinctive content is ignored`() {
        assertFalse(ToolOutputUsage.isUsed("""{"ok": true, "count": 3}""", "It is 3, ok."))
    }

    @Test
    fun `null values in the mock are not evidence`() {
        assertFalse(ToolOutputUsage.isUsed("""{"city": null}""", "The value is null."))
    }

    // endregion
}
