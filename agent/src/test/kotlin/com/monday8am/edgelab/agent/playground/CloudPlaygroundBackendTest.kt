package com.monday8am.edgelab.agent.playground

import com.monday8am.edgelab.data.testing.FunctionSpec
import com.monday8am.edgelab.data.testing.ToolSpecification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

class CloudPlaygroundBackendTest {

    @Test
    fun `run returns the model text when no tool is called`() = runTest {
        val chat = ScriptedChat(listOf(CloudReply("Madrid is sunny.", emptyList())))
        val backend = CloudPlaygroundBackend(CloudChatFactory { chat })

        val result = backend.run("What's the weather?", emptyList(), emptyMap()).getOrThrow()

        assertEquals("Madrid is sunny.", result.text)
        assertTrue(result.toolCalls.isEmpty())
    }

    @Test
    fun `run feeds the tool mock back to the model and returns its follow-up text`() = runTest {
        val weather = tool("get_weather")
        val mocks = mapOf("get_weather" to """{"tempC": 21}""")
        val chat =
            ScriptedChat(
                listOf(
                    CloudReply(
                        "",
                        listOf(CloudFunctionCall("get_weather", mapOf("city" to "Madrid"))),
                    ),
                    CloudReply("It's 21 degrees in Madrid.", emptyList()),
                )
            )
        val backend = CloudPlaygroundBackend(CloudChatFactory { chat })

        val result = backend.run("Weather in Madrid?", listOf(weather), mocks).getOrThrow()

        // The mock the dev authored is exactly what went back over the wire.
        assertEquals(
            listOf(CloudFunctionResponse("get_weather", """{"tempC": 21}""")),
            chat.sentResponses.single(),
        )
        assertEquals("It's 21 degrees in Madrid.", result.text)
    }

    @Test
    fun `run records each tool call with the args and the mock it received`() = runTest {
        val weather = tool("get_weather")
        val mocks = mapOf("get_weather" to """{"tempC": 21}""")
        val chat =
            ScriptedChat(
                listOf(
                    CloudReply(
                        "",
                        listOf(CloudFunctionCall("get_weather", mapOf("city" to "Madrid"))),
                    ),
                    CloudReply("Done.", emptyList()),
                )
            )
        val backend = CloudPlaygroundBackend(CloudChatFactory { chat })

        val result = backend.run("Weather?", listOf(weather), mocks).getOrThrow()

        assertEquals(
            listOf(TurnToolCall("get_weather", mapOf("city" to "Madrid"), """{"tempC": 21}""")),
            result.toolCalls,
        )
    }

    @Test
    fun `run handles several tool calls in one round`() = runTest {
        val location = tool("get_location")
        val weather = tool("get_weather")
        val mocks =
            mapOf("get_location" to """{"city": "Madrid"}""", "get_weather" to """{"tempC": 21}""")
        val chat =
            ScriptedChat(
                listOf(
                    CloudReply(
                        "",
                        listOf(
                            CloudFunctionCall("get_location", emptyMap()),
                            CloudFunctionCall("get_weather", emptyMap()),
                        ),
                    ),
                    CloudReply("It's 21 in Madrid.", emptyList()),
                )
            )
        val backend = CloudPlaygroundBackend(CloudChatFactory { chat })

        val result = backend.run("Weather here?", listOf(location, weather), mocks).getOrThrow()

        assertEquals(listOf("get_location", "get_weather"), result.toolCalls.map { it.name })
        assertEquals(2, chat.sentResponses.single().size)
        assertEquals("It's 21 in Madrid.", result.text)
    }

    @Test
    fun `run keeps looping while the model chains tool calls`() = runTest {
        val location = tool("get_location")
        val weather = tool("get_weather")
        val mocks =
            mapOf("get_location" to """{"city": "Madrid"}""", "get_weather" to """{"tempC": 21}""")
        val chat =
            ScriptedChat(
                listOf(
                    CloudReply("", listOf(CloudFunctionCall("get_location", emptyMap()))),
                    CloudReply("", listOf(CloudFunctionCall("get_weather", emptyMap()))),
                    CloudReply("21 degrees in Madrid.", emptyList()),
                )
            )
        val backend = CloudPlaygroundBackend(CloudChatFactory { chat })

        val result = backend.run("Weather here?", listOf(location, weather), mocks).getOrThrow()

        assertEquals(listOf("get_location", "get_weather"), result.toolCalls.map { it.name })
        assertEquals("21 degrees in Madrid.", result.text)
    }

    @Test
    fun `run fails loudly instead of truncating when the model will not stop calling tools`() =
        runTest {
            val weather = tool("get_weather")
            val mocks = mapOf("get_weather" to """{"tempC": 21}""")
            val forever =
                List(20) { CloudReply("", listOf(CloudFunctionCall("get_weather", emptyMap()))) }
            val backend =
                CloudPlaygroundBackend(
                    CloudChatFactory { ScriptedChat(forever) },
                    maxToolRounds = 3,
                )

            val error = backend.run("Weather?", listOf(weather), mocks).exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertTrue(error.message.orEmpty().contains("3 rounds"))
        }

    @Test
    fun `run tells the model when it invents an unregistered tool`() = runTest {
        val chat =
            ScriptedChat(
                listOf(
                    CloudReply("", listOf(CloudFunctionCall("send_email", emptyMap()))),
                    CloudReply("Sorry, I can't do that.", emptyList()),
                )
            )
        val backend = CloudPlaygroundBackend(CloudChatFactory { chat })

        val result = backend.run("Email someone", emptyList(), emptyMap()).getOrThrow()

        // The turn survives, and the Trace still shows the model reaching for a tool it never had.
        assertEquals("send_email", result.toolCalls.single().name)
        assertTrue(result.toolCalls.single().mockResponse.contains("No such tool"))
        assertEquals("Sorry, I can't do that.", result.text)
    }

    @Test
    fun `run reuses the session across turns so follow-ups keep their history`() = runTest {
        val weather = tool("get_weather")
        val mocks = mapOf("get_weather" to """{"tempC": 21}""")
        var opened = 0
        val chat = ScriptedChat(List(4) { CloudReply("ok", emptyList()) })
        val backend =
            CloudPlaygroundBackend(
                CloudChatFactory {
                    opened++
                    chat
                }
            )

        backend.run("first", listOf(weather), mocks).getOrThrow()
        backend.run("second", listOf(weather), mocks).getOrThrow()

        assertEquals(1, opened)
    }

    @Test
    fun `run opens a fresh session when the tool set changes`() = runTest {
        // Tools are bound when the session opens, so a changed tool set needs a new one.
        val first = tool("get_weather")
        val second = tool("get_location")
        val sessions = mutableListOf<ScriptedChat>()
        val backend =
            CloudPlaygroundBackend(
                CloudChatFactory {
                    ScriptedChat(List(4) { CloudReply("ok", emptyList()) }).also { sessions += it }
                }
            )

        backend.run("first", listOf(first), emptyMap()).getOrThrow()
        backend.run("second", listOf(first, second), emptyMap()).getOrThrow()

        assertEquals(2, sessions.size)
        assertNotSame(sessions[0], sessions[1])
    }

    @Test
    fun `run wraps a transport failure in a failed Result rather than throwing`() = runTest {
        val backend =
            CloudPlaygroundBackend(
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

        val error = backend.run("hi", emptyList(), emptyMap()).exceptionOrNull()

        assertEquals("network down", error?.message)
    }

    @Test
    fun `close drops the session so the next turn starts a new one`() = runTest {
        val weather = tool("get_weather")
        val sessions = mutableListOf<ScriptedChat>()
        val backend =
            CloudPlaygroundBackend(
                CloudChatFactory {
                    ScriptedChat(List(4) { CloudReply("ok", emptyList()) }).also { sessions += it }
                }
            )

        backend.run("first", listOf(weather), emptyMap()).getOrThrow()
        backend.close()
        backend.run("second", listOf(weather), emptyMap()).getOrThrow()

        assertEquals(2, sessions.size)
    }

    @Test
    fun `initialize succeeds without touching the network`() = runTest {
        val backend = CloudPlaygroundBackend(CloudChatFactory { error("must not open a session") })

        assertTrue(backend.initialize().isSuccess)
    }

    @Test
    fun `run reuses the session when the same tools are passed as an equal list`() = runTest {
        val weather = tool("get_weather")
        val sessions = mutableListOf<ScriptedChat>()
        val backend =
            CloudPlaygroundBackend(
                CloudChatFactory {
                    ScriptedChat(List(4) { CloudReply("ok", emptyList()) }).also { sessions += it }
                }
            )

        backend.run("first", listOf(weather), emptyMap()).getOrThrow()
        backend.run("second", listOf(weather.copy()), emptyMap()).getOrThrow()

        assertEquals(1, sessions.size)
        assertSame(sessions[0], sessions[0])
    }
}

/** Replays a fixed script of replies and records every batch of tool results sent back. */
private class ScriptedChat(replies: List<CloudReply>) : CloudChatSession {
    private val remaining = ArrayDeque(replies)
    val sentResponses = mutableListOf<List<CloudFunctionResponse>>()

    override suspend fun send(prompt: String): CloudReply = next()

    override suspend fun respondToCalls(responses: List<CloudFunctionResponse>): CloudReply {
        sentResponses += responses
        return next()
    }

    private fun next(): CloudReply =
        remaining.removeFirstOrNull() ?: error("ScriptedChat ran out of replies")
}

private fun tool(name: String): ToolSpecification =
    ToolSpecification(
        function =
            FunctionSpec(
                name = name,
                description = "test tool",
                parameters = JsonObject(emptyMap()),
            )
    )
