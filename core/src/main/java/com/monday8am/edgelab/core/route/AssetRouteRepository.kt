package com.monday8am.edgelab.core.route

import android.content.Context
import com.monday8am.edgelab.data.route.RouteCoordinate
import com.monday8am.edgelab.data.route.RouteData
import com.monday8am.edgelab.data.route.RouteRepository
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val ROUTE_NAMES = mapOf(
    "strade-bianche" to "Strade Bianche GranFondo",
)

private val json = Json { ignoreUnknownKeys = true }

@Serializable private data class RouteJsonDto(val coordinates: List<CoordinateJsonDto>)

@Serializable
private data class CoordinateJsonDto(val lat: Double, val lng: Double, val alt: Double, val t: Long)

/**
 * Loads route data from bundled JSON assets at routes/{routeId}/route.json.
 */
class AssetRouteRepository(private val context: Context) : RouteRepository {

    override suspend fun getRoute(routeId: String): Result<RouteData> = withContext(Dispatchers.IO) {
        runCatching {
            val text =
                context.assets.open("routes/$routeId/route.json").bufferedReader().use {
                    it.readText()
                }
            val dto = json.decodeFromString<RouteJsonDto>(text)
            val coords = dto.coordinates.map { RouteCoordinate(it.lat, it.lng, it.alt, it.t) }
            RouteData(
                routeId = routeId,
                name = ROUTE_NAMES[routeId] ?: routeId,
                distanceKm = computeDistanceKm(coords),
                coordinates = coords,
            )
        }
    }

    override fun routeFlow(routeId: String): Flow<RouteData> = flow {
        emit(getRoute(routeId).getOrThrow())
    }

    private fun computeDistanceKm(coords: List<RouteCoordinate>): Float {
        var total = 0.0
        for (i in 1 until coords.size) {
            total += haversineKm(coords[i - 1].lat, coords[i - 1].lng, coords[i].lat, coords[i].lng)
        }
        return total.toFloat()
    }

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
