package com.monday8am.edgelab.agent.playground

/**
 * Judges whether a model's final text integrated the mock tool output it was handed. null = no
 * verdict (e.g. an unreachable judge): callers keep the tentative tag instead of fabricating one.
 */
fun interface ToolOutputJudge {
    suspend fun isUsed(mockResponse: String, modelText: String): Boolean?
}

/** Deterministic fast path: literal word and number overlap. Never abstains. */
class HeuristicToolOutputJudge : ToolOutputJudge {
    override suspend fun isUsed(mockResponse: String, modelText: String): Boolean =
        ToolOutputUsage.isUsed(mockResponse, modelText)
}
