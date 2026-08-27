package com.monday8am.edgelab.data.playground

import com.monday8am.edgelab.data.testing.ToolSpecification
import kotlinx.coroutines.flow.Flow

/** Source for the Playground's preset tool library, seeded from `data/.../tool_tests.json`. */
interface ProbeRepository {
    fun getToolsAsFlow(): Flow<List<ToolSpecification>>

    fun getMockResponsesAsFlow(): Flow<Map<String, String>>
}
