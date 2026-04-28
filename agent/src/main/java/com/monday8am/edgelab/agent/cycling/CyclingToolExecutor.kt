package com.monday8am.edgelab.agent.cycling

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.data.route.SegmentRepository
import com.monday8am.edgelab.data.route.SegmentsData
import com.monday8am.edgelab.data.route.WeatherData
import com.monday8am.edgelab.data.route.WeatherRepository
import com.monday8am.edgelab.data.testing.ToolSpecification
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Executes the 6 cycling copilot tools against pre-loaded route, segment, and weather data.
 *
 * Call [initialize] once before a ride session to pre-load data. Then call [execute] for each tool
 * invocation during the ride.
 */
class CyclingToolExecutor(
    private val segmentRepository: SegmentRepository,
    private val weatherRepository: WeatherRepository,
) {

    private val logger = Logger.withTag("CyclingToolExecutor")

    private var cachedSegments: SegmentsData? = null
    private var cachedWeather: WeatherData? = null

    val toolDefinitions: List<ToolSpecification>
        get() = CyclingToolDefinitions.ALL

    /** Pre-loads segment and weather data for [routeId]. Call once per ride session. */
    suspend fun initialize(routeId: String) {
        cachedSegments = segmentRepository.getSegments(routeId).getOrNull()
        cachedWeather = weatherRepository.getWeather(routeId).getOrNull()
        logger.i {
            "Initialized: route=$routeId segments=${cachedSegments != null}" +
                " weather=${cachedWeather != null}"
        }
    }

    /**
     * Executes a tool by name and returns a JSON string result.
     *
     * @param toolName One of the constants in [CyclingToolDefinitions]
     * @param paramsJson JSON object string with tool parameters (may be `"{}"`)
     * @param rideContext Current rider state snapshot
     */
    fun execute(toolName: String, paramsJson: String, rideContext: RideContext): String {
        val params =
            try {
                Json.parseToJsonElement(paramsJson).jsonObject
            } catch (e: SerializationException) {
                JsonObject(emptyMap())
            } catch (e: IllegalArgumentException) {
                JsonObject(emptyMap())
            }
        return when (toolName) {
            CyclingToolDefinitions.GET_RIDE_STATUS -> getRideStatus(rideContext)
            CyclingToolDefinitions.GET_SEGMENT_AHEAD -> getSegmentAhead(rideContext)
            CyclingToolDefinitions.GET_WEATHER_FORECAST -> getWeatherForecast(params, rideContext)
            CyclingToolDefinitions.GET_ROUTE_ALTERNATIVES -> getRouteAlternatives(rideContext)
            CyclingToolDefinitions.FIND_NEARBY_POI -> findNearbyPoi(params, rideContext)
            CyclingToolDefinitions.GET_RIDER_PROFILE -> getRiderProfile()
            else -> buildJsonObject { put("error", "Unknown tool: $toolName") }.toString()
        }
    }

    private fun getRideStatus(ctx: RideContext): String =
        buildJsonObject {
                put("speed_kmh", ctx.speedKmh)
                put("distance_km", ctx.distanceTravelledKm)
                put("elapsed_ms", ctx.elapsedMs)
                put("total_distance_km", ctx.totalDistanceKm)
                if (ctx.totalDistanceKm > 0) {
                    put("progress_pct", ctx.distanceTravelledKm / ctx.totalDistanceKm * 100f)
                }
                ctx.power?.let { put("power_watts", it) }
            }
            .toString()

    private fun getSegmentAhead(ctx: RideContext): String {
        val segments =
            cachedSegments
                ?: return buildJsonObject { put("error", "Segment data not loaded") }.toString()

        val currentNamed =
            segments.namedSectors.firstOrNull { ctx.routePointIndex in it.fromIndex..it.toIndex }
        val currentGravel =
            segments.gravelSectors.firstOrNull { ctx.routePointIndex in it.fromIndex..it.toIndex }
        val nextNamed =
            segments.namedSectors
                .filter { it.fromIndex > ctx.routePointIndex }
                .minByOrNull { it.fromIndex }
        val nextGravel =
            segments.gravelSectors
                .filter { it.fromIndex > ctx.routePointIndex }
                .minByOrNull { it.fromIndex }

        return buildJsonObject {
                currentNamed?.let {
                    put(
                        "current_named_sector",
                        buildJsonObject {
                            put("name", it.name)
                            put("surface", it.surface)
                            put("distance_m", it.distanceM)
                            put("elevation_up_m", it.elevationUpM)
                        },
                    )
                }
                currentGravel?.let {
                    put(
                        "current_gravel_sector",
                        buildJsonObject {
                            put("surface", it.surface)
                            put("distance_m", it.distanceM)
                            put("elevation_up_m", it.elevationUpM)
                        },
                    )
                }
                nextNamed?.let {
                    put(
                        "next_named_sector",
                        buildJsonObject {
                            put("name", it.name)
                            put("surface", it.surface)
                            put("distance_m", it.distanceM)
                            put("elevation_up_m", it.elevationUpM)
                            put("km_from_start", it.kmFromStart)
                            put("km_ahead", it.kmFromStart - ctx.distanceTravelledKm)
                        },
                    )
                }
                nextGravel?.let {
                    put(
                        "next_gravel_sector",
                        buildJsonObject {
                            put("surface", it.surface)
                            put("distance_m", it.distanceM)
                            put("elevation_up_m", it.elevationUpM)
                            put("km_from_start", it.kmFromStart)
                            put("km_ahead", it.kmFromStart - ctx.distanceTravelledKm)
                        },
                    )
                }
                if (
                    currentNamed == null &&
                        currentGravel == null &&
                        nextNamed == null &&
                        nextGravel == null
                ) {
                    put("message", "No more segments ahead")
                }
            }
            .toString()
    }

    private fun getWeatherForecast(params: JsonObject, ctx: RideContext): String {
        val weather =
            cachedWeather
                ?: return buildJsonObject { put("error", "Weather data not loaded") }.toString()

        val hoursAhead = params["hours_ahead"]?.jsonPrimitive?.intOrNull ?: 3
        val currentHour = (ctx.rideStartHour + ctx.elapsedMs / 3_600_000L).toInt().coerceIn(0, 23)
        val hours = weather.hourly.filter { it.hour >= currentHour }.take(hoursAhead + 1)

        return buildJsonObject {
                put("location", weather.location)
                put("summary", weather.summary)
                put(
                    "hourly",
                    buildJsonArray {
                        hours.forEach { h ->
                            add(
                                buildJsonObject {
                                    put("hour", h.hour)
                                    put("temp_c", h.tempC)
                                    put("feels_like_c", h.feelsLikeC)
                                    put("wind_kph", h.windKph)
                                    put("wind_dir", h.windDir)
                                    put("humidity_pct", h.humidityPct)
                                    put("condition", h.condition)
                                    put("precip_mm", h.precipMm)
                                    put("uv_index", h.uvIndex)
                                }
                            )
                        }
                    },
                )
            }
            .toString()
    }

    private fun getRouteAlternatives(ctx: RideContext): String {
        val segments =
            cachedSegments
                ?: return buildJsonObject { put("error", "Segment data not loaded") }.toString()

        val upcoming = segments.gravelSectors.filter { it.fromIndex > ctx.routePointIndex }.take(3)

        return buildJsonObject {
                put(
                    "note",
                    "Official Strade Bianche route. Gravel sectors are integral to the race course.",
                )
                put(
                    "upcoming_challenging_sectors",
                    buildJsonArray {
                        upcoming.forEach { g ->
                            add(
                                buildJsonObject {
                                    put("surface", g.surface)
                                    put("distance_m", g.distanceM)
                                    put("elevation_up_m", g.elevationUpM)
                                    put("km_from_start", g.kmFromStart)
                                    put("km_ahead", g.kmFromStart - ctx.distanceTravelledKm)
                                    put("has_paved_alternative", false)
                                }
                            )
                        }
                    },
                )
            }
            .toString()
    }

    private fun findNearbyPoi(params: JsonObject, ctx: RideContext): String {
        val category = params["category"]?.jsonPrimitive?.contentOrNull
        val radiusKm = params["radius_km"]?.jsonPrimitive?.floatOrNull ?: 5f

        val filtered = STRADE_BIANCHE_POIS.filter { poi ->
            (category == null || poi.category == category) &&
                kotlin.math.abs(poi.kmFromStart - ctx.distanceTravelledKm) <= radiusKm
        }

        return buildJsonObject {
                category?.let { put("category_filter", it) }
                put("radius_km", radiusKm)
                put(
                    "pois",
                    buildJsonArray {
                        filtered.forEach { poi ->
                            add(
                                buildJsonObject {
                                    put("name", poi.name)
                                    put("category", poi.category)
                                    put("km_from_start", poi.kmFromStart)
                                    put("km_ahead", poi.kmFromStart - ctx.distanceTravelledKm)
                                }
                            )
                        }
                    },
                )
            }
            .toString()
    }

    private fun getRiderProfile(): String =
        buildJsonObject {
                put("ftp_watts", 250)
                put("weight_kg", 70)
                put(
                    "zones",
                    buildJsonObject {
                        put("z1_recovery", "< 140W")
                        put("z2_endurance", "140-188W")
                        put("z3_tempo", "188-213W")
                        put("z4_threshold", "213-250W")
                        put("z5_vo2max", "> 250W")
                    },
                )
                put("note", "Default profile. Customize in rider settings.")
            }
            .toString()

    private data class PoiInfo(val name: String, val category: String, val kmFromStart: Float)

    private companion object {
        // Hardcoded Strade Bianche POIs — replaced by PoiRepository in Day 7
        val STRADE_BIANCHE_POIS =
            listOf(
                PoiInfo("Fonte di Siena", "water", 0f),
                PoiInfo("Cicli Pieri Siena", "bike_shop", 4f),
                PoiInfo("Bar Il Casato", "cafe", 12f),
                PoiInfo("Rifugio Crete Senesi", "shelter", 25f),
                PoiInfo("Osteria del Grillo", "cafe", 34f),
                PoiInfo("Acqua Pubblica Buonconvento", "water", 48f),
                PoiInfo("Bar Sport Montalcino", "cafe", 82f),
                PoiInfo("Fontana del Casato", "water", 130f),
                PoiInfo("Ciclofficina Siena", "bike_shop", 160f),
            )
    }
}
