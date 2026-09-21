package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.GeoPoint
import ai.factoredui.compose.schema.GeomapFeature
import ai.factoredui.compose.schema.GeomapLayer
import ai.factoredui.compose.schema.GeomapLayerKind
import ai.factoredui.compose.schema.GeomapViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val VIEW_WIDTH = 400f
private const val VIEW_HEIGHT = 400f

private fun squareAt(id: String, lon: Double, lat: Double, halfDegrees: Double = 0.001) = GeomapFeature(
    id = id,
    rings = listOf(
        listOf(
            GeoPoint(lon - halfDegrees, lat - halfDegrees),
            GeoPoint(lon + halfDegrees, lat - halfDegrees),
            GeoPoint(lon + halfDegrees, lat + halfDegrees),
            GeoPoint(lon - halfDegrees, lat + halfDegrees),
        ),
    ),
    fill = "#3366FF",
)

private fun parcels(vararg features: GeomapFeature) =
    tessellateGeomapLayers(listOf(GeomapLayer(id = "parcels", kind = GeomapLayerKind.FILL, features = features.toList())))

private val JOLLY_RD = GeomapViewport(lon = -85.60, lat = 42.93, zoom = 15f)

class GeomapCullingTest {

    @Test
    fun a_feature_under_the_view_is_drawn() {
        val drawn = drawnGeomapFeatureIds(parcels(squareAt("here", -85.60, 42.93)), JOLLY_RD, VIEW_WIDTH, VIEW_HEIGHT)
        assertEquals(listOf("here"), drawn)
    }

    @Test
    fun a_feature_a_county_away_is_not_drawn() {
        val drawn = drawnGeomapFeatureIds(
            parcels(squareAt("here", -85.60, 42.93), squareAt("across-the-state", -83.00, 42.30)),
            JOLLY_RD,
            VIEW_WIDTH,
            VIEW_HEIGHT,
        )
        assertEquals(listOf("here"), drawn)
    }

    @Test
    fun a_feature_straddling_the_edge_of_the_view_is_still_drawn() {
        val viewEdgeLon = -85.60 + (VIEW_WIDTH / 2.0) / geomapScalePx(JOLLY_RD.zoom) * 360.0
        val straddler = squareAt("on-the-edge", viewEdgeLon, 42.93, halfDegrees = 0.002)
        val drawn = drawnGeomapFeatureIds(parcels(straddler), JOLLY_RD, VIEW_WIDTH, VIEW_HEIGHT)
        assertEquals(listOf("on-the-edge"), drawn, "a parcel half on screen must not vanish at the edge")
    }

    @Test
    fun zooming_out_brings_distant_features_back_into_the_draw() {
        val both = parcels(squareAt("here", -85.60, 42.93), squareAt("across-the-state", -83.00, 42.30))
        val stateView = JOLLY_RD.copy(zoom = 6f)
        assertEquals(setOf("here", "across-the-state"), drawnGeomapFeatureIds(both, stateView, VIEW_WIDTH, VIEW_HEIGHT).toSet())
    }

    @Test
    fun culling_a_large_field_draws_only_the_neighbourhood() {
        val grid = (0 until 100).flatMap { row ->
            (0 until 100).map { column ->
                squareAt("p-$row-$column", -85.60 + column * 0.01, 42.93 + row * 0.01)
            }
        }
        val drawn = drawnGeomapFeatureIds(parcels(*grid.toTypedArray()), JOLLY_RD, VIEW_WIDTH, VIEW_HEIGHT)
        assertTrue(drawn.isNotEmpty(), "the parcel under the view must still be drawn")
        assertTrue(drawn.size < 50, "drew ${drawn.size} of 10,000 parcels for one street-level view")
    }
}
