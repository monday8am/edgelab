package com.monday8am.edgelab.agent.playground

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class LlmToolOutputJudgeTest {

    private val mock = """{"tempC": 21}"""
    private val answer = "It's 21 degrees in Madrid."

    @Test
    fun `YES reply counts as used`() = runTest {
        val judge = LlmToolOutputJudge(CloudChatFactory { SingleReplyChat(reply("YES")) })

        assertEquals(true, judge.isUsed(mock, answer))
    }

    @Test
    fun `NO reply counts as ignored`() = runTest {
        val judge = LlmToolOutputJudge(CloudChatFactory { SingleReplyChat(reply("No.")) })

        assertEquals(false, judge.isUsed(mock, answer))
    }

    @Test
    fun `lowercase verdict with surrounding prose still counts`() = runTest {
        val judge =
            LlmToolOutputJudge(
                CloudChatFactory { SingleReplyChat(reply("yes, it uses the output")) }
            )

        assertEquals(true, judge.isUsed(mock, answer))
    }

    @Test
    fun `a reply that is not YES or NO abstains`() = runTest {
        val judge = LlmToolOutputJudge(CloudChatFactory { SingleReplyChat(reply("Probably.")) })

        assertNull(judge.isUsed(mock, answer))
    }

    @Test
    fun `a judge that asks for a tool abstains`() = runTest {
        val withCall = CloudReply("", listOf(CloudFunctionCall("get_weather", emptyMap())))
        val judge = LlmToolOutputJudge(CloudChatFactory { SingleReplyChat(withCall) })

        assertNull(judge.isUsed(mock, answer))
    }

    @Test
    fun `a transport failure abstains instead of throwing`() = runTest {
        val judge =
            LlmToolOutputJudge(
                CloudChatFactory {
                    object : CloudChatSession {
                        override suspend fun send(prompt: String): CloudReply =
                            throw RuntimeException("network down")

                        override suspend fun respondToCalls(
                            responses: List<CloudFunctionResponse>
                        ): CloudReply = error("not reached")
                    }
                }
            )

        assertNull(judge.isUsed(mock, answer))
    }

    @Test
    fun `each check opens a fresh session`() = runTest {
        var opened = 0
        val judge =
            LlmToolOutputJudge(
                CloudChatFactory {
                    opened++
                    SingleReplyChat(reply("YES"))
                }
            )

        judge.isUsed(mock, answer)
        judge.isUsed(mock, answer)

        assertEquals(2, opened)
    }

    private fun reply(text: String): CloudReply = CloudReply(text, emptyList())

    private class SingleReplyChat(private val reply: CloudReply) : CloudChatSession {
        override suspend fun send(prompt: String): CloudReply = reply

        override suspend fun respondToCalls(responses: List<CloudFunctionResponse>): CloudReply =
            error("the judge session never has tools")
    }
}
