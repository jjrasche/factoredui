package ai.factoredui.compose.renderer

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GeomapCheck {

    private val viewportCenterLon = -84.60
    private val viewportCenterLat = 42.69
    private val viewportZoom = 13.0
    private val canvasSizePx = 400

    private val westParcelFill = 0xFFE74C3C.toInt()
    private val eastParcelFill = 0xFFF39C12.toInt()
    private val ghostParcelFill = 0xFF8E44AD.toInt()

    private fun parcelChoroplethSpec() = SpecNode(
        id = "jolly-map",
        type = SpecNodeType.GEOMAP,
        props = mapOf(
            "layers" to SpecValue.ArrayValue(
                listOf(
                    fillLayer(
                        "scored", visible = true,
                        squareFeature("parcel-west", "#E74C3C", lonMin = -84.63, lonMax = -84.61),
                        squareFeature("parcel-east", "#F39C12", lonMin = -84.59, lonMax = -84.57),
                    ),
                    fillLayer(
                        "hidden", visible = false,
                        squareFeature("parcel-ghost", "#8E44AD", lonMin = -84.61, lonMax = -84.59),
                    ),
                ),
            ),
            "viewport" to SpecValue.ObjectValue(
                mapOf(
                    "lon" to SpecValue.NumberValue(viewportCenterLon),
                    "lat" to SpecValue.NumberValue(viewportCenterLat),
                    "zoom" to SpecValue.NumberValue(viewportZoom),
                ),
            ),
            "on_feature_tap" to SpecValue.StringValue("map.featureTapped"),
            "on_viewport_changed" to SpecValue.StringValue("map.viewportChanged"),
        ),
    )

    private fun fillLayer(id: String, visible: Boolean, vararg features: SpecValue): SpecValue =
        SpecValue.ObjectValue(
            mapOf(
                "id" to SpecValue.StringValue(id),
                "kind" to SpecValue.StringValue("fill"),
                "visible" to SpecValue.BooleanValue(visible),
                "features" to SpecValue.ArrayValue(features.toList()),
            ),
        )

    private fun squareFeature(id: String, fill: String, lonMin: Double, lonMax: Double): SpecValue {
        val latMin = 42.68
        val latMax = 42.70
        val ring = SpecValue.ArrayValue(
            listOf(
                geoPointValue(lonMin, latMin),
                geoPointValue(lonMax, latMin),
                geoPointValue(lonMax, latMax),
                geoPointValue(lonMin, latMax),
            ),
        )
        return SpecValue.ObjectValue(
            mapOf(
                "id" to SpecValue.StringValue(id),
                "rings" to SpecValue.ArrayValue(listOf(ring)),
                "fill" to SpecValue.StringValue(fill),
                "stroke" to SpecValue.StringValue("#2C3E50"),
                "stroke_width" to SpecValue.NumberValue(1.0),
            ),
        )
    }

    private fun geoPointValue(lon: Double, lat: Double): SpecValue =
        SpecValue.ArrayValue(listOf(SpecValue.NumberValue(lon), SpecValue.NumberValue(lat)))

    private fun screenXofLon(lon: Double): Float {
        val scale = geomapScalePx(viewportZoom.toFloat())
        return ((lonToWorldX(lon) - lonToWorldX(viewportCenterLon)) * scale + canvasSizePx / 2.0).toFloat()
    }

    private fun screenYofLat(lat: Double): Float {
        val scale = geomapScalePx(viewportZoom.toFloat())
        return ((latToWorldY(lat) - latToWorldY(viewportCenterLat)) * scale + canvasSizePx / 2.0).toFloat()
    }

    private fun assertPixelNear(actualArgb: Int, expectedArgb: Int, where: String) {
        for (shift in intArrayOf(16, 8, 0)) {
            val actualChannel = (actualArgb shr shift) and 0xFF
            val expectedChannel = (expectedArgb shr shift) and 0xFF
            assertTrue(
                abs(actualChannel - expectedChannel) <= 8,
                "$where: expected argb ${expectedArgb.toUInt().toString(16)}, got ${actualArgb.toUInt().toString(16)}",
            )
        }
    }

    @Test
    fun fillsLandOnThePixelsWhereTheParcelsProject() {
        runComposeUiTest {
            val check = SpecVisualCheck(this)
            check.render(parcelChoroplethSpec(), viewport = canvasSizePx.dp)
            check.assertRenderedToPixels()
            val pixels = check.png().toPixelMap()
            val parcelCenterY = screenYofLat(42.69).roundToInt()
            val westCenter = pixels[screenXofLon(-84.62).roundToInt(), parcelCenterY].toArgb()
            val eastCenter = pixels[screenXofLon(-84.58).roundToInt(), parcelCenterY].toArgb()
            assertPixelNear(westCenter, westParcelFill, "west parcel center")
            assertPixelNear(eastCenter, eastParcelFill, "east parcel center")
        }
    }

    @Test
    fun strokesOutlineTheParcelEdges() {
        runComposeUiTest {
            val check = SpecVisualCheck(this)
            check.render(parcelChoroplethSpec(), viewport = canvasSizePx.dp)
            val pixels = check.png().toPixelMap()
            val westEdgeX = screenXofLon(-84.63).roundToInt()
            val parcelCenterY = screenYofLat(42.69).roundToInt()
            val darkestRedNearEdge = (westEdgeX - 1..westEdgeX + 1).minOf { x ->
                (pixels[x, parcelCenterY].toArgb() shr 16) and 0xFF
            }
            assertTrue(
                darkestRedNearEdge < 0xB0,
                "the west edge must carry dark stroke pixels (stroke #2C3E50), darkest red channel was ${darkestRedNearEdge.toString(16)}",
            )
        }
    }

    @Test
    fun aToggledOffLayerLeavesItsRegionUnpainted() {
        runComposeUiTest {
            val check = SpecVisualCheck(this)
            check.render(parcelChoroplethSpec(), viewport = canvasSizePx.dp)
            val pixels = check.png().toPixelMap()
            val ghostCenter = pixels[screenXofLon(-84.60).roundToInt(), screenYofLat(42.69).roundToInt()].toArgb()
            for (shift in intArrayOf(16, 8, 0)) {
                val actualChannel = (ghostCenter shr shift) and 0xFF
                val hiddenChannel = (ghostParcelFill shr shift) and 0xFF
                if (abs(actualChannel - hiddenChannel) > 8) return@runComposeUiTest
            }
            throw AssertionError("hidden layer's fill leaked to pixels: ${ghostCenter.toUInt().toString(16)}")
        }
    }

    @Test
    fun aTapInsideAParcelDispatchesOnFeatureTapWithItsIds() {
        runComposeUiTest {
            var tappedLayerId: String? = null
            var tappedFeatureId: String? = null
            val captureTap: ActionHandler = { params ->
                tappedLayerId = params["layer_id"] as? String
                tappedFeatureId = params["feature_id"] as? String
            }
            val context = RenderContext(actions = mapOf("map.featureTapped" to captureTap))
            val check = SpecVisualCheck(this, context)
            check.render(parcelChoroplethSpec(), viewport = canvasSizePx.dp)
            check.tapAt("jolly-map", screenXofLon(-84.62), screenYofLat(42.69))
            assertEquals("scored", tappedLayerId)
            assertEquals("parcel-west", tappedFeatureId)
        }
    }

    @Test
    fun aTapOnAHiddenLayersParcelHitsNothing() {
        runComposeUiTest {
            var tappedFeatureId: String? = null
            val captureTap: ActionHandler = { params -> tappedFeatureId = params["feature_id"] as? String }
            val context = RenderContext(actions = mapOf("map.featureTapped" to captureTap))
            val check = SpecVisualCheck(this, context)
            check.render(parcelChoroplethSpec(), viewport = canvasSizePx.dp)
            check.tapAt("jolly-map", screenXofLon(-84.60), screenYofLat(42.69))
            assertNull(tappedFeatureId, "a parcel on an invisible layer must not be tappable")
        }
    }

    @Test
    fun draggingTheMapFiresOnViewportChangedWithANewCenter() {
        runComposeUiTest {
            var changedLon: Double? = null
            var changedZoom: Double? = null
            val captureViewport: ActionHandler = { params ->
                changedLon = (params["lon"] as? Number)?.toDouble()
                changedZoom = (params["zoom"] as? Number)?.toDouble()
            }
            val context = RenderContext(actions = mapOf("map.viewportChanged" to captureViewport))
            val check = SpecVisualCheck(this, context)
            check.render(parcelChoroplethSpec(), viewport = canvasSizePx.dp)
            check.drag("jolly-map", 120f, 0f)
            assertNotNull(changedLon, "a pan must fire on_viewport_changed")
            assertTrue(changedLon!! < viewportCenterLon, "dragging content east moves the camera west")
            assertEquals(viewportZoom, changedZoom!!, 1e-4)
        }
    }
}
