package com.monday8am.edgelab.data.route

import kotlinx.coroutines.flow.Flow

data class RouteCoordinate(
    val lat: Double,
    val lng: Double,
    val alt: Double,
    val t: Long, // ms elapsed from route start (Komoot pace)
)

data class RouteData(
    val routeId: String,
    val name: String,
    val distanceKm: Float,
    val coordinates: List<RouteCoordinate>,
)

interface RouteRepository {
    suspend fun getRoute(routeId: String): Result<RouteData>

    fun routeFlow(routeId: String): Flow<RouteData>
}
