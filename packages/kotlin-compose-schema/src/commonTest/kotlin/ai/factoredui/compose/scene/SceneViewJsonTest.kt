package ai.factoredui.compose.scene

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val SURVEYED = Provenance(ProvenanceKind.MEASURED, source = "lidar", asOf = "2026-05-01")

private fun everyLayerScene(): SceneView = SceneView(
    levelFeet = 5,
    tileFeet = 5.0,
    cols = 2,
    rows = 2,
    window = WindowMm(0, 0, 3048, 3048),
    types = listOf(SceneType("oak", "Oak", tags = listOf("tree"), footprintMm = 1000L to 1000L, heightMm = 9000)),
    ground = GroundLayer(7, 2, 2, List(9) { it * 10L }, null, "NAVD88", SURVEYED),
    surface = SurfaceLayer(3, 2, 2, listOf(SurfaceClass(1, "grass"), SurfaceClass(2, "gravel")), listOf(1, 1, 2, 2), SURVEYED),
    footprints = FootprintLayer(4, listOf(SceneFootprint("shed-1", "shed", 0, 1, 1, 1, heightMm = 2400)), SURVEYED),
    instances = InstanceLayer(
        5,
        listOf(SceneInstance("t1", "oak", 1500, 1600, 12, 0.0, 9000, 2500, listOf("tree"), ProvenanceKind.MEASURED)),
        provenance = SURVEYED,
    ),
    water = WaterLayer(6, listOf(Pond("p1", 100, listOf(PondTile(1, 1, 300)))), listOf(FlowLine("f1", listOf(0L to 0L, 10L to 20L), 1.5, "L/s", "swale")), SURVEYED),
    boundaries = BoundaryLayer(8, listOf(BoundaryPolygon("b1", "parcel", listOf(0L to 0L, 3048L to 0L, 3048L to 3048L))), SURVEYED),
    grid = GridSpec("square", 5.0, 2, 2),
    annotations = AnnotationLayer(9, listOf(Annotation("instance", "t1", "note", "leaning", null))),
)

private fun parsed(scene: SceneView, georeference: Georeference? = null): JsonObject =
    Json.parseToJsonElement(sceneViewJson(scene, georeference)).jsonObject

class SceneViewJsonTest {

    @Test
    fun theSceneFrameIsWrittenInMillimetres() {
        val json = parsed(everyLayerScene())
        assertEquals("mm", json.getValue("units").jsonPrimitive.content)
        assertEquals(5, json.getValue("level_feet").jsonPrimitive.int)
        assertEquals(3048, json.getValue("window").jsonObject.getValue("x1").jsonPrimitive.long)
        assertEquals("oak", json.getValue("types").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun everyLayerKeepsItsVersionAndItsProvenance() {
        val json = parsed(everyLayerScene())
        val expectedVersions = mapOf("ground" to 7, "surface" to 3, "footprints" to 4, "instances" to 5, "water" to 6, "boundaries" to 8, "annotations" to 9)
        expectedVersions.forEach { (layer, version) ->
            assertEquals(version, json.getValue(layer).jsonObject.getValue("version").jsonPrimitive.int, layer)
        }
        val provenance = json.getValue("ground").jsonObject.getValue("provenance").jsonObject
        assertEquals("measured", provenance.getValue("kind").jsonPrimitive.content)
        assertEquals("2026-05-01", provenance.getValue("as_of").jsonPrimitive.content)
    }

    @Test
    fun aTreeKeepsItsPositionHeightAndCrownInMillimetres() {
        val tree = parsed(everyLayerScene()).getValue("instances").jsonObject.getValue("items").jsonArray.single().jsonObject
        assertEquals(1500, tree.getValue("x_mm").jsonPrimitive.long)
        assertEquals(1600, tree.getValue("y_mm").jsonPrimitive.long)
        assertEquals(9000, tree.getValue("height_mm").jsonPrimitive.long)
        assertEquals(2500, tree.getValue("crown_radius_mm").jsonPrimitive.long)
        assertEquals("measured", tree.getValue("provenance").jsonPrimitive.content)
    }

    @Test
    fun landCoverAndWaterAndFootprintsCarryTheirOwnShapes() {
        val json = parsed(everyLayerScene())
        assertEquals(listOf(1, 1, 2, 2), json.getValue("surface").jsonObject.getValue("class_ids").jsonArray.map { it.jsonPrimitive.int })
        assertEquals(300, json.getValue("water").jsonObject.getValue("ponds").jsonArray.single().jsonObject.getValue("tiles").jsonArray.single().jsonObject.getValue("depth_mm").jsonPrimitive.long)
        assertEquals(2400, json.getValue("footprints").jsonObject.getValue("items").jsonArray.single().jsonObject.getValue("height_mm").jsonPrimitive.long)
    }

    @Test
    fun aLayerTheSceneLacksIsLeftOutNotWrittenEmpty() {
        val bare = SceneView(25, 25.0, 1, 1, WindowMm(0, 0, 7620, 7620), emptyList())
        val json = parsed(bare)
        assertFalse(json.containsKey("water"))
        assertFalse(json.containsKey("georeference"))
    }

    @Test
    fun theGeoreferenceTiesTheMillimetreOriginToTheEarth() {
        val georeference = parsed(everyLayerScene(), Georeference(42.9634, -85.6681, 12.5)).getValue("georeference").jsonObject
        assertEquals(42.9634, georeference.getValue("origin_latitude").jsonPrimitive.double)
        assertEquals(-85.6681, georeference.getValue("origin_longitude").jsonPrimitive.double)
        assertEquals(12.5, georeference.getValue("north_rotation_deg").jsonPrimitive.double)
    }

    @Test
    fun anAbsentOptionalValueIsWrittenAsNullSoThePageCanTellItFromZero() {
        val oak = parsed(everyLayerScene()).getValue("types").jsonArray.single().jsonObject
        assertTrue(oak.getValue("height_mm").jsonPrimitive.long == 9000L)
        val shedType = SceneType("shed", "Shed")
        val noHeight = parsed(SceneView(5, 5.0, 1, 1, WindowMm(0, 0, 1524, 1524), listOf(shedType))).getValue("types").jsonArray.single().jsonObject
        assertEquals(JsonNull, noHeight.getValue("height_mm"))
        assertEquals(JsonNull, noHeight.getValue("footprint_mm"))
    }
}
