package com.monday8am.edgelab.agent.cycling

import co.touchlab.kermit.Logger
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.gson.JsonElement
import com.monday8am.edgelab.data.testing.ToolSpecification
import java.util.Collections
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenApiTool that routes execution to [CyclingToolExecutor] with a live [RideContext].
 *
 * Set [currentContext] before each inference call so tools operate on accurate rider state.
 */
class CyclingOpenApiTool(
    private val toolSpec: ToolSpecification,
    private val executor: CyclingToolExecutor,
) : OpenApiTool {

    private val logger = Logger.withTag("CyclingOpenApiTool")
    private val _calls = Collections.synchronizedList(mutableListOf<ToolCallRecord>())

    @Volatile var currentContext: RideContext? = null

    val toolName: String
        get() = toolSpec.function.name

    val calls: List<ToolCallRecord>
        get() = synchronized(_calls) { _calls.toList() }

    fun clearCalls() = synchronized(_calls) { _calls.clear() }

    override fun getToolDescriptionJsonString(): String =
        Json.encodeToString(
            CyclingToolSchema(
                name = toolSpec.function.name,
                description = toolSpec.function.description,
                parameters = toolSpec.function.parameters,
            )
        )

    override fun execute(paramsJsonString: String): String {
        val ctx = currentContext
        if (ctx == null) {
            logger.w { "Tool '$toolName' called without ride context" }
            return "{\"error\": \"No ride context available\"}"
        }
        logger.d { "Tool '$toolName' called: $paramsJsonString" }
        val result = executor.execute(toolName, paramsJsonString, ctx)
        synchronized(_calls) { _calls.add(ToolCallRecord(toolName, result)) }
        return result
    }
}

/** A recorded tool invocation: the tool that was called and the JSON result it returned. */
data class ToolCallRecord(val toolName: String, val resultJson: String)

@Serializable
private data class CyclingToolSchema(
    val name: String,
    val description: String,
    val parameters: JsonElement,
)
