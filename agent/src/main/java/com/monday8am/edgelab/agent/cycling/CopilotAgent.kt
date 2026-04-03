package com.monday8am.edgelab.agent.cycling

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.agent.core.LocalInferenceEngine
import kotlin.math.roundToInt

/**
 * Orchestrates the full copilot pipeline: rider question → tool calls → natural language response.
 *
 * Call [initialize] once per route session to pre-load data and register tools. Then call [ask] for
 * each rider question during the ride.
 */
class CopilotAgent(
    private val inferenceEngine: LocalInferenceEngine,
    private val toolExecutor: CyclingToolExecutor,
) {
    /** The result of a [ask] call: the reply text and all tool calls the model made. */
    data class AgentResponse(val text: String, val toolCalls: List<ToolCallRecord>)

    private val logger = Logger.withTag("CopilotAgent")
    private val tools: List<CyclingOpenApiTool> =
        CyclingToolDefinitions.ALL.map { spec -> CyclingOpenApiTool(spec, toolExecutor) }

    /** Pre-loads route data and registers tools with the inference engine. */
    suspend fun initialize(routeId: String) {
        toolExecutor.initialize(routeId)
        inferenceEngine.setToolsAndResetConversation(tools)
    }

    /**
     * Asks the copilot a [question] given the current [rideContext].
     *
     * @return [AgentResponse] with the natural language reply and any tool calls made.
     */
    suspend fun ask(question: String, rideContext: RideContext): AgentResponse {
        tools.forEach { tool ->
            tool.currentContext = rideContext
            tool.clearCalls()
        }

        val prompt = buildPrompt(question, rideContext)
        val result = inferenceEngine.prompt(prompt)

        val text =
            result.getOrElse { e ->
                logger.e("Inference failed", e)
                MODEL_NOT_READY_MESSAGE
            }

        val toolCalls = tools.flatMap { it.calls }
        return AgentResponse(text = text, toolCalls = toolCalls)
    }

    private fun buildPrompt(question: String, ctx: RideContext): String {
        val contextLine = buildString {
            append("Distance: ${ctx.distanceTravelledKm} km")
            append(" | Speed: ${ctx.speedKmh} kph")
            ctx.power?.let { append(" | Power: ${it}W") }
            if (ctx.totalDistanceKm > 0) {
                val pct = (ctx.distanceTravelledKm / ctx.totalDistanceKm * 100).roundToInt()
                append(" | Progress: $pct%")
            }
        }
        return "$SYSTEM_PROMPT\n\nRide context: $contextLine\n\nRider question: $question"
    }

    companion object {
        const val MODEL_NOT_READY_MESSAGE = "Model not ready, please complete onboarding first."

        private const val SYSTEM_PROMPT =
            "You are a friendly cycling copilot for the Strade Bianche race course. " +
                "Answer questions concisely — the rider is on the bike. " +
                "Use the available tools to look up real ride data when needed. " +
                "Keep responses under 3 sentences unless the rider asks for detail."
    }
}
