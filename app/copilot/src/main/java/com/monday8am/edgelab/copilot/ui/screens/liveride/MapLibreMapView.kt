package com.monday8am.edgelab.copilot.ui.screens.liveride

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monday8am.edgelab.presentation.liveride.LatLng as RideLatLng
import com.monday8am.edgelab.presentation.liveride.PoiCategory
import com.monday8am.edgelab.presentation.liveride.PoiMarker
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.geojson.dsl.featureCollectionOf
import org.maplibre.spatialk.geojson.toJson

private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

@Composable
fun MapLibreMapView(
    routePolyline: ImmutableList<RideLatLng>,
    completedPolyline: ImmutableList<RideLatLng>,
    riderPosition: RideLatLng,
    pois: ImmutableList<PoiMarker>,
    modifier: Modifier = Modifier,
) {
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(riderPosition.longitude, riderPosition.latitude),
            zoom = 12.0,
        )
    )

    LaunchedEffect(riderPosition) {
        cameraState.animateTo(
            cameraState.position.copy(
                target = Position(riderPosition.longitude, riderPosition.latitude)
            )
        )
    }

    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
        cameraState = cameraState,
    ) {
        val routeSource = rememberGeoJsonSource(
            data = GeoJsonData.JsonString(routePolyline.toLineCollectionJson()),
        )
        val completedSource = rememberGeoJsonSource(
            data = GeoJsonData.JsonString(completedPolyline.toLineCollectionJson()),
        )
        val riderSource = rememberGeoJsonSource(
            data = GeoJsonData.JsonString(
                FeatureCollection(
                    listOf(
                        Feature(
                            geometry = Point(Position(riderPosition.longitude, riderPosition.latitude)),
                            properties = emptyMap<String, JsonElement>(),
                        )
                    )
                ).toJson()
            ),
        )
        val poisSource = rememberGeoJsonSource(
            data = GeoJsonData.JsonString(pois.toPoiCollectionJson()),
        )

        // Full route (dimmed — shows the route ahead)
        LineLayer(
            id = "layer-route",
            source = routeSource,
            color = const(Color(0xFF0EA5E9)),
            width = const(6.dp),
            opacity = const(0.35f),
            cap = const(LineCap.Round),
            join = const(LineJoin.Round),
        )

        // Completed segment (full opacity)
        LineLayer(
            id = "layer-completed",
            source = completedSource,
            color = const(Color(0xFF0EA5E9)),
            width = const(6.dp),
            cap = const(LineCap.Round),
            join = const(LineJoin.Round),
        )

        // Rider position dot
        CircleLayer(
            id = "layer-rider",
            source = riderSource,
            radius = const(10.dp),
            color = const(Color.White),
            strokeWidth = const(3.dp),
            strokeColor = const(Color(0xFF0EA5E9)),
        )

        // POI emoji labels
        SymbolLayer(
            id = "layer-pois",
            source = poisSource,
            textField = feature["label"].asString(),
            textSize = const(18.sp),
            textAllowOverlap = const(true),
            textIgnorePlacement = const(true),
        )
    }
}

private fun List<RideLatLng>.toLineCollectionJson(): String {
    if (size < 2) return featureCollectionOf().toJson()
    val line = LineString(map { Position(it.longitude, it.latitude) })
    return FeatureCollection(
        listOf(Feature(geometry = line, properties = emptyMap<String, JsonElement>()))
    ).toJson()
}

private fun List<PoiMarker>.toPoiCollectionJson(): String {
    val features = map { poi ->
        val emoji =
            when (poi.category) {
                PoiCategory.CAFE -> "\u2615"
                PoiCategory.WATER -> "\uD83D\uDCA7"
                PoiCategory.BIKE_SHOP -> "\uD83D\uDD27"
                PoiCategory.SHELTER -> "\u26FA"
            }
        Feature(
            geometry = Point(Position(poi.position.longitude, poi.position.latitude)),
            properties = mapOf("label" to JsonPrimitive(emoji)),
        )
    }
    return FeatureCollection(features).toJson()
}
