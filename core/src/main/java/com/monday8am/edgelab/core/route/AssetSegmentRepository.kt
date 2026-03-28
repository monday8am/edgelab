package com.monday8am.edgelab.core.route

import android.content.Context
import com.monday8am.edgelab.data.route.GravelSector
import com.monday8am.edgelab.data.route.SegmentData
import com.monday8am.edgelab.data.route.SegmentRepository
import com.monday8am.edgelab.data.route.SegmentsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class SegmentsJsonDto(
    @SerialName("route_id") val routeId: String,
    @SerialName("named_sectors") val namedSectors: List<NamedSectorDto>,
    @SerialName("gravel_sectors") val gravelSectors: List<GravelSectorDto>,
)

@Serializable
private data class NamedSectorDto(
    val id: String,
    val name: String,
    val category: String,
    @SerialName("from_index") val fromIndex: Int,
    @SerialName("to_index") val toIndex: Int,
    @SerialName("start_lat") val startLat: Double,
    @SerialName("start_lng") val startLng: Double,
    @SerialName("distance_m") val distanceM: Int,
    @SerialName("elevation_up_m") val elevationUpM: Int,
    val surface: String,
    @SerialName("km_from_start") val kmFromStart: Float,
)

@Serializable
private data class GravelSectorDto(
    @SerialName("from_index") val fromIndex: Int,
    @SerialName("to_index") val toIndex: Int,
    @SerialName("start_lat") val startLat: Double,
    @SerialName("start_lng") val startLng: Double,
    @SerialName("distance_m") val distanceM: Int,
    @SerialName("elevation_up_m") val elevationUpM: Int,
    val surface: String,
    @SerialName("km_from_start") val kmFromStart: Float,
)

/**
 * Loads segment data from bundled JSON assets at routes/{routeId}/segments.json.
 */
class AssetSegmentRepository(private val context: Context) : SegmentRepository {

    override suspend fun getSegments(routeId: String): Result<SegmentsData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text =
                    context.assets.open("routes/$routeId/segments.json").bufferedReader().use {
                        it.readText()
                    }
                val dto = json.decodeFromString<SegmentsJsonDto>(text)
                SegmentsData(
                    routeId = dto.routeId,
                    namedSectors =
                        dto.namedSectors.map {
                            SegmentData(
                                id = it.id,
                                name = it.name,
                                category = it.category,
                                fromIndex = it.fromIndex,
                                toIndex = it.toIndex,
                                startLat = it.startLat,
                                startLng = it.startLng,
                                distanceM = it.distanceM,
                                elevationUpM = it.elevationUpM,
                                surface = it.surface,
                                kmFromStart = it.kmFromStart,
                            )
                        },
                    gravelSectors =
                        dto.gravelSectors.map {
                            GravelSector(
                                fromIndex = it.fromIndex,
                                toIndex = it.toIndex,
                                startLat = it.startLat,
                                startLng = it.startLng,
                                distanceM = it.distanceM,
                                elevationUpM = it.elevationUpM,
                                surface = it.surface,
                                kmFromStart = it.kmFromStart,
                            )
                        },
                )
            }
        }

    override fun segmentsFlow(routeId: String): Flow<SegmentsData> = flow {
        emit(getSegments(routeId).getOrThrow())
    }
}
