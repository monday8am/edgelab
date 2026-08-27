package com.monday8am.edgelab.agent.playground

import com.monday8am.edgelab.data.testing.ToolSpecification

/**
 * A live multi-turn conversation with a cloud model that can call functions.
 *
 * Deliberately narrow and provider-free: the Firebase AI Logic adapter lives in `:core` (it needs
 * Android + Firebase), while [CloudPlaygroundBackend] — which owns the actual tool-call loop —
 * stays here in pure Kotlin where it can be tested against a fake.
 *
 * Tools are bound when the session is opened, so a change to the tool set needs a new session.
 */
interface CloudChatSession {
    /** Sends the dev's prompt and returns the model's first reply. */
    suspend fun send(prompt: String): CloudReply

    /** Returns tool results to the model and reads whatever it says next. */
    suspend fun respondToCalls(responses: List<CloudFunctionResponse>): CloudReply
}

/** Opens a [CloudChatSession] with [tools] registered as the callable functions. */
fun interface CloudChatFactory {
    fun open(tools: List<ToolSpecification>): CloudChatSession
}

/**
 * One reply from the cloud model. [calls] is empty when the model answered in text; when it is
 * non-empty the model is waiting on tool results and [text] is usually blank.
 */
data class CloudReply(val text: String, val calls: List<CloudFunctionCall>)

/** A function the cloud model asked to call. */
data class CloudFunctionCall(val name: String, val args: Map<String, Any?>)

/**
 * The mock output being handed back to the model for one call. [jsonResponse] must be a JSON
 * object.
 */
data class CloudFunctionResponse(val name: String, val jsonResponse: String)
