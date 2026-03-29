package com.monday8am.edgelab.data.route

import kotlinx.coroutines.flow.Flow

data class HourlyWeather(
    val hour: Int,
    val tempC: Int,
    val feelsLikeC: Int,
    val windKph: Int,
    val windDir: String,
    val humidityPct: Int,
    val condition: String,
    val precipMm: Float,
    val uvIndex: Int,
)

data class WeatherData(
    val routeId: String,
    val location: String,
    val date: String,
    val timezone: String,
    val summary: String,
    val hourly: List<HourlyWeather>,
)

interface WeatherRepository {
    suspend fun getWeather(routeId: String): Result<WeatherData>

    fun weatherFlow(routeId: String): Flow<WeatherData>
}
