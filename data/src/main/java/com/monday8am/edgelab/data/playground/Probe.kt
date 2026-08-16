package com.monday8am.edgelab.data.playground

import com.monday8am.edgelab.data.testing.ToolSpecification

/**
 * A Playground **Probe** — the dev's fake tool.
 *
 * A Probe does no real work; its only job is to record whether/how the model invoked it. It is the
 * exact shape the inference engine needs ([ToolSpecification] + a mock response string), so the
 * existing [com.monday8am.edgelab.agent.tools.OpenApiToolHandler] runs it unmodified.
 *
 * @property toolSpec OpenAI-style tool definition (name, description, parameter schema).
 * @property mockResponse String returned to the model when it calls the tool.
 */
data class Probe(val toolSpec: ToolSpecification, val mockResponse: String) {
    /** Stable identifier — the tool's function name. */
    val id: String
        get() = toolSpec.function.name

    val name: String
        get() = toolSpec.function.name

    val description: String
        get() = toolSpec.function.description
}
