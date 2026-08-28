package com.monday8am.edgelab.core.inference

import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.monday8am.edgelab.agent.playground.CloudChatFactory
import com.monday8am.edgelab.agent.playground.CloudChatSession
import com.monday8am.edgelab.agent.playground.CloudFunctionCall
import com.monday8am.edgelab.agent.playground.CloudFunctionResponse
import com.monday8am.edgelab.agent.playground.CloudReply
import com.monday8am.edgelab.data.testing.ToolSpecification
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Reaches Gemini through Firebase AI Logic, which holds the API key server-side. Requires the
 * consuming app to provide a `google-services.json` and apply the `com.google.gms.google-services`
 * plugin; without it [open] throws at first use.
 */
class FirebaseAiChatFactory(private val modelName: String = DEFAULT_CLOUD_MODEL) :
    CloudChatFactory {

    override fun open(tools: List<ToolSpecification>): CloudChatSession {
        val declarations =
            if (tools.isEmpty()) emptyList()
            else listOf(Tool.functionDeclarations(tools.map { it.toDeclaration() }))

        val ai =
            try {
                Firebase.ai(backend = GenerativeBackend.googleAI())
            } catch (e: IllegalStateException) {
                // Firebase is unconfigured far more often than it is broken, and the SDK's own
                // message ("Default FirebaseApp is not initialized") doesn't say what to do.
                throw IllegalStateException(SETUP_REQUIRED_MESSAGE, e)
            }

        return FirebaseAiChatSession(
            ai.generativeModel(modelName = modelName, tools = declarations).startChat()
        )
    }

    companion object {
        /**
         * Free-tier eligible on the Gemini Developer API path, with tool calling verified on the
         * seeded probes. Cheaper tiers exist — see the model table in
         * `docs/edgelab/research-cloud-models-interactions-api.md`.
         */
        const val DEFAULT_CLOUD_MODEL = "gemini-3.5-flash-lite"

        internal const val SETUP_REQUIRED_MESSAGE =
            "Cloud Playground is not configured. Add google-services.json to app/explorer and " +
                "apply the com.google.gms.google-services plugin — see docs/edgelab/plan.md. " +
                "Until then, download a model and switch to the on-device target."
    }
}

private class FirebaseAiChatSession(private val chat: Chat) : CloudChatSession {

    override suspend fun send(prompt: String): CloudReply = chat.sendMessage(prompt).toCloudReply()

    override suspend fun respondToCalls(responses: List<CloudFunctionResponse>): CloudReply {
        // The Google AI backend only accepts "user"/"model" as roles — "function" is a Vertex-ism
        // that it rejects outright. A FunctionResponsePart still reads as a tool result to Gemini.
        val turn =
            content(role = "user") {
                responses.forEach { part(FunctionResponsePart(it.name, it.jsonResponse.asJsonObject())) }
            }
        return chat.sendMessage(turn).toCloudReply()
    }
}

private fun GenerateContentResponse.toCloudReply(): CloudReply =
    CloudReply(
        text = text.orEmpty(),
        calls = functionCalls.map { CloudFunctionCall(it.name, it.args.mapValues { (_, v) -> v.unwrap() }) },
    )

/**
 * A tool's mock output is free-form text the dev typed. Gemini requires a JSON *object*, so
 * anything that isn't one is wrapped rather than rejected — a plain-string mock is a legitimate
 * thing to probe with.
 */
private fun String.asJsonObject(): JsonObject =
    runCatching { kotlinx.serialization.json.Json.parseToJsonElement(this).jsonObject }
        .getOrElse { buildJsonObject { put("result", this@asJsonObject) } }

private fun JsonElement.unwrap(): Any? =
    when (this) {
        is JsonPrimitive ->
            if (isString) content
            else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content.takeIf { it != "null" }
        is JsonArray -> map { it.unwrap() }
        is JsonObject -> mapValues { (_, v) -> v.unwrap() }
    }

/**
 * Converts an OpenAI-style tool spec into Gemini's declaration shape. The two agree on
 * structure, so this is a mechanical walk of the JSON Schema — the only real translation is that
 * Gemini names the *optional* properties where OpenAI names the required ones.
 */
private fun ToolSpecification.toDeclaration(): FunctionDeclaration {
    val params = function.parameters as? JsonObject
    val properties = params?.get("properties") as? JsonObject ?: JsonObject(emptyMap())
    val required =
        (params?.get("required") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            ?.toSet()
            .orEmpty()

    return FunctionDeclaration(
        function.name,
        function.description,
        properties.mapValues { (_, schema) -> (schema as? JsonObject).toGeminiSchema() },
        properties.keys.filterNot { it in required },
    )
}

private fun JsonObject?.toGeminiSchema(): Schema {
    val description = this?.stringField("description")
    val enumValues =
        (this?.get("enum") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
    if (!enumValues.isNullOrEmpty()) {
        return Schema.enumeration(values = enumValues, description = description)
    }

    return when (this?.stringField("type")) {
        "integer" -> Schema.long(description = description)
        "number" -> Schema.double(description = description)
        "boolean" -> Schema.boolean(description = description)
        "array" ->
            Schema.array(
                items = (this["items"] as? JsonObject).toGeminiSchema(),
                description = description,
            )
        "object" -> {
            val nested = this["properties"] as? JsonObject ?: JsonObject(emptyMap())
            val nestedRequired =
                (this["required"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                    ?.toSet()
                    .orEmpty()
            Schema.obj(
                properties = nested.mapValues { (_, v) -> (v as? JsonObject).toGeminiSchema() },
                optionalProperties = nested.keys.filterNot { it in nestedRequired },
                description = description,
            )
        }
        else -> Schema.string(description = description)
    }
}

private fun JsonObject.stringField(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
