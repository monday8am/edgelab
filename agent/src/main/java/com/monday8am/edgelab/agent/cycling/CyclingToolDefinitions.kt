package com.monday8am.edgelab.agent.cycling

import com.monday8am.edgelab.data.testing.FunctionSpec
import com.monday8am.edgelab.data.testing.ToolSpecification
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** OpenAPI tool schemas for the 6 cycling copilot tools. */
object CyclingToolDefinitions {

    const val GET_RIDE_STATUS = "get_ride_status"
    const val GET_SEGMENT_AHEAD = "get_segment_ahead"
    const val GET_WEATHER_FORECAST = "get_weather_forecast"
    const val GET_ROUTE_ALTERNATIVES = "get_route_alternatives"
    const val FIND_NEARBY_POI = "find_nearby_poi"
    const val GET_RIDER_PROFILE = "get_rider_profile"

    val ALL: List<ToolSpecification> =
        listOf(
            ToolSpecification(
                function =
                    FunctionSpec(
                        name = GET_RIDE_STATUS,
                        description =
                            "Returns current ride metrics: speed in km/h, distance covered in km," +
                                " elapsed time in ms, and power in watts if available.",
                        parameters =
                            buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {})
                                put("required", buildJsonArray {})
                            },
                    )
            ),
            ToolSpecification(
                function =
                    FunctionSpec(
                        name = GET_SEGMENT_AHEAD,
                        description =
                            "Returns the current and upcoming terrain segments (gravel sectors" +
                                " and named sectors) relative to the rider's current position.",
                        parameters =
                            buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {})
                                put("required", buildJsonArray {})
                            },
                    )
            ),
            ToolSpecification(
                function =
                    FunctionSpec(
                        name = GET_WEATHER_FORECAST,
                        description =
                            "Returns the weather forecast for current and upcoming hours," +
                                " including temperature, wind, precipitation, and conditions.",
                        parameters =
                            buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put(
                                            "hours_ahead",
                                            buildJsonObject {
                                                put("type", "integer")
                                                put(
                                                    "description",
                                                    "Number of upcoming hours to include (1-8). Defaults to 3.",
                                                )
                                            },
                                        )
                                    },
                                )
                                put("required", buildJsonArray {})
                            },
                    )
            ),
            ToolSpecification(
                function =
                    FunctionSpec(
                        name = GET_ROUTE_ALTERNATIVES,
                        description =
                            "Returns information about upcoming challenging road surfaces and" +
                                " whether paved alternatives exist.",
                        parameters =
                            buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {})
                                put("required", buildJsonArray {})
                            },
                    )
            ),
            ToolSpecification(
                function =
                    FunctionSpec(
                        name = FIND_NEARBY_POI,
                        description =
                            "Finds cafes, water sources, bike shops, and shelters near the" +
                                " rider's current position.",
                        parameters =
                            buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put(
                                            "category",
                                            buildJsonObject {
                                                put("type", "string")
                                                put(
                                                    "enum",
                                                    buildJsonArray {
                                                        add(JsonPrimitive("cafe"))
                                                        add(JsonPrimitive("water"))
                                                        add(JsonPrimitive("bike_shop"))
                                                        add(JsonPrimitive("shelter"))
                                                    },
                                                )
                                                put("description", "Type of POI to search for.")
                                            },
                                        )
                                        put(
                                            "radius_km",
                                            buildJsonObject {
                                                put("type", "number")
                                                put(
                                                    "description",
                                                    "Search radius in km. Defaults to 5.",
                                                )
                                            },
                                        )
                                    },
                                )
                                put("required", buildJsonArray {})
                            },
                    )
            ),
            ToolSpecification(
                function =
                    FunctionSpec(
                        name = GET_RIDER_PROFILE,
                        description =
                            "Returns the rider's baseline metrics: FTP, weight, and training" +
                                " zones for pacing guidance.",
                        parameters =
                            buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {})
                                put("required", buildJsonArray {})
                            },
                    )
            ),
        )
}
