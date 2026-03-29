package com.monday8am.edgelab.agent.cycling

/** Current snapshot of the rider's state, passed to each tool at execution time. */
data class RideContext(
    val routePointIndex: Int,
    val distanceTravelledKm: Float,
    val speedKmh: Float,
    val power: Int?,
    val elapsedMs: Long = 0L,
    val totalDistanceKm: Float,
)
