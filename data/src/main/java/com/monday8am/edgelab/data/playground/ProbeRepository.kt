package com.monday8am.edgelab.data.playground

import com.monday8am.edgelab.data.testing.ToolSpecification
import kotlinx.coroutines.flow.Flow

/**
 * Source for the Playground's **preset tool library** — 1-tap add → tweak. Seeded from the
 * already-authored `ToolSpecification` objects in `data/.../tool_tests.json`.
 *
 * Implementations may later add paste-import or remote sources; v1 ships the bundled preset list.
 */
interface ProbeRepository {
    /** Preset tool definitions available to add to a Playground session. */
    fun getToolsAsFlow(): Flow<List<ToolSpecification>>

    /** Mock response to return for each preset tool, keyed by tool name. */
    fun getMockResponsesAsFlow(): Flow<Map<String, String>>
}
