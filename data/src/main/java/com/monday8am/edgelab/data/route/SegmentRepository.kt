package com.monday8am.edgelab.data.route

import kotlinx.coroutines.flow.Flow

data class SegmentData(
    val id: String,
    val name: String,
    val category: String,
    val fromIndex: Int,
    val toIndex: Int,
    val startLat: Double,
    val startLng: Double,
    val distanceM: Int,
    val elevationUpM: Int,
    val surface: String,
    val kmFromStart: Float,
)

data class GravelSector(
    val fromIndex: Int,
    val toIndex: Int,
    val startLat: Double,
    val startLng: Double,
    val distanceM: Int,
    val elevationUpM: Int,
    val surface: String,
    val kmFromStart: Float,
)

data class SegmentsData(
    val routeId: String,
    val namedSectors: List<SegmentData>,
    val gravelSectors: List<GravelSector>,
)

interface SegmentRepository {
    suspend fun getSegments(routeId: String): Result<SegmentsData>

    fun segmentsFlow(routeId: String): Flow<SegmentsData>
}
