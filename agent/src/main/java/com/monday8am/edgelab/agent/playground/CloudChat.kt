package com.monday8am.edgelab.agent.playground

import com.monday8am.edgelab.data.testing.ToolSpecification

/**
 * A live multi-turn conversation with a cloud model that can call functions.
 *
 * Deliberately provider-free: the Firebase adapter lives in `:core` (it needs Android + Firebase),
 * while the tool loop in [CloudPlaygroundBackend] stays here in pure Kotlin where it is testable
 * against a fake. Tools are bound when the session is opened, so a changed tool set needs a new
 * session.
 */
interface CloudChatSession {
    suspend fun send(prompt: String): CloudReply

    suspend fun respondToCalls(responses: List<CloudFunctionResponse>): CloudReply
}

fun interface CloudChatFactory {
    fun open(tools: List<ToolSpecification>): CloudChatSession
}

/**
 * [calls] is empty when the model answered in text; non-empty means it is waiting on tool results.
 */
data class CloudReply(val text: String, val calls: List<CloudFunctionCall>)

data class CloudFunctionCall(val name: String, val args: Map<String, Any?>)

/** [jsonResponse] must be a JSON object. */
data class CloudFunctionResponse(val name: String, val jsonResponse: String)
