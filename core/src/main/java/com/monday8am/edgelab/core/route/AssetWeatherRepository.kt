package com.monday8am.edgelab.core.route

import android.content.Context
import com.monday8am.edgelab.data.route.HourlyWeather
import com.monday8am.edgelab.data.route.WeatherData
import com.monday8am.edgelab.data.route.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class WeatherJsonDto(
    @SerialName("route_id") val routeId: String,
    val location: String,
    val date: String,
    val timezone: String,
    val summary: String,
    val hourly: List<HourlyWeatherDto>,
)

@Serializable
private data class HourlyWeatherDto(
    val hour: Int,
    @SerialName("temp_c") val tempC: Int,
    @SerialName("feels_like_c") val feelsLikeC: Int,
    @SerialName("wind_kph") val windKph: Int,
    @SerialName("wind_dir") val windDir: String,
    @SerialName("humidity_pct") val humidityPct: Int,
    val condition: String,
    @SerialName("precip_mm") val precipMm: Float,
    @SerialName("uv_index") val uvIndex: Int,
)

/**
 * Loads weather data from bundled JSON assets at routes/{routeId}/weather.json.
 */
class AssetWeatherRepository(private val context: Context) : WeatherRepository {

    override suspend fun getWeather(routeId: String): Result<WeatherData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text =
                    context.assets.open("routes/$routeId/weather.json").bufferedReader().use {
                        it.readText()
                    }
                val dto = routeJson.decodeFromString<WeatherJsonDto>(text)
                WeatherData(
                    routeId = dto.routeId,
                    location = dto.location,
                    date = dto.date,
                    timezone = dto.timezone,
                    summary = dto.summary,
                    hourly =
                        dto.hourly.map {
                            HourlyWeather(
                                hour = it.hour,
                                tempC = it.tempC,
                                feelsLikeC = it.feelsLikeC,
                                windKph = it.windKph,
                                windDir = it.windDir,
                                humidityPct = it.humidityPct,
                                condition = it.condition,
                                precipMm = it.precipMm,
                                uvIndex = it.uvIndex,
                            )
                        },
                )
            }
        }

    override fun weatherFlow(routeId: String): Flow<WeatherData> = flow {
        emit(getWeather(routeId).getOrThrow())
    }
}
