package com.monday8am.edgelab.presentation.liveride

import kotlinx.coroutines.flow.Flow

data class GpsPosition(
    val latLng: LatLng,
    val heading: Float,
    val speedKmh: Float,
    val distanceTravelledKm: Float,
    val power: Int?,
    val routePointIndex: Int,
)

interface GpsSource {
    val positions: Flow<GpsPosition>

    fun start()

    fun pause()

    fun setSpeedMultiplier(multiplier: Float)
}

fun interface GpsSourceFactory {
    fun create(routePoints: List<LatLng>): GpsSource
}
