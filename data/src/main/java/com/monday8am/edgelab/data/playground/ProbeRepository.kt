package com.monday8am.edgelab.data.playground

import kotlinx.coroutines.flow.Flow

/**
 * Source for the Playground's **preset Probe library** — 1-tap add → tweak. Seeded from the
 * already-authored `ToolSpecification` objects in `data/.../tool_tests.json`.
 *
 * Implementations may later add paste-import or remote sources; v1 ships the bundled preset list.
 */
interface ProbeRepository {
    fun getProbesAsFlow(): Flow<List<Probe>>
}
