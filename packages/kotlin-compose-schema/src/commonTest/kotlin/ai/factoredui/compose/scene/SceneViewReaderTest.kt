package ai.factoredui.compose.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private val RECORDED = Provenance(ProvenanceKind.MEASURED, source = "survey", asOf = "2026-05-01", sourceSha256 = "ab12", observedAt = "2026-05-01T10:00:00Z", registrationErrorMm = 0)

private fun structureScene(): SceneView = SceneView(
    levelFeet = 1,
    tileFeet = 1.0,
    cols = 2,
    rows = 2,
    window = WindowMm(0, 0, 610, 610),
    types = listOf(
        SceneType("pier", "Pier", tags = listOf("support"), footprintMm = 305L to 305L, heightMm = 375, unitCostUsd = 92.37, unitWeightKg = null, unitWeightNullReason = "no weight is banked"),
        SceneType("beam", "Beam"),
    ),
    ground = GroundLayer(7, 2, 2, List(9) { it * 10L }, listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), "site", RECORDED),
    surface = SurfaceLayer(3, 2, 2, listOf(SurfaceClass(0, "empty"), SurfaceClass(1, "pier")), listOf(0, 1, 1, 0), RECORDED),
    footprints = FootprintLayer(
        4,
        listOf(SceneFootprint("p1", "pier", 0, 0, 1, 1, stackLevel = 0, zMm = 0), SceneFootprint("b1", "beam", 0, 1, 2, 1, heightMm = 286, stackLevel = 1, zMm = 375)),
        RECORDED,
    ),
    instances = InstanceLayer(5, listOf(SceneInstance("t1", "oak", 1, 2, 3, 4.5, 9000, 2500, listOf("tree"), ProvenanceKind.MEASURED)), provenance = RECORDED),
    water = WaterLayer(6, listOf(Pond("w", 100, listOf(PondTile(1, 1, 300)))), listOf(FlowLine("f", listOf(0L to 0L, 10L to 20L), 1.5, "L/s", "swale")), RECORDED),
    boundaries = BoundaryLayer(8, listOf(BoundaryPolygon("b", "parcel", listOf(0L to 0L, 610L to 0L, 610L to 610L))), RECORDED),
    grid = GridSpec("square", 1.0, 2, 2),
    annotations = AnnotationLayer(9, listOf(Annotation("FOOTPRINT", "b1", "utilisation", "carries 32%", "engine equations", 0.32), Annotation("INSTANCE", null, "note", "leaning", null))),
    stackLevels = listOf(StackLevel(0, 0, "on the ground"), StackLevel(1, 375, null)),
    dropped = listOf("a part with no footprint"),
)

class SceneViewReaderTest {
    @Test
    fun a_scene_written_and_read_back_is_the_same_scene() {
        val scene = structureScene()
        assertEquals(scene, readSceneView(sceneViewJson(scene)))
    }

    @Test
    fun a_part_type_with_no_price_reads_back_with_no_price_never_zero() {
        val read = readSceneView(sceneViewJson(structureScene()))
        assertNull(read.types.first { it.id == "beam" }.unitCostUsd)
        assertNull(read.types.first { it.id == "pier" }.unitWeightKg)
        assertEquals("no weight is banked", read.types.first { it.id == "pier" }.unitWeightNullReason)
    }

    @Test
    fun a_footprint_with_no_stack_fields_reads_as_level_zero_on_the_ground() {
        val text = """{"units":"mm","level_feet":1,"tile_feet":1.0,"cols":1,"rows":1,"window":{"x0":0,"y0":0,"x1":305,"y1":305},"types":[],
            "footprints":{"version":1,"items":[{"id":"a","type":"t","col":0,"row":0,"width":1,"height":1}]}}"""
        val item = readSceneView(text).footprints!!.items.single()
        assertEquals(0, item.stackLevel)
        assertEquals(0L, item.zMm)
    }

    @Test
    fun keys_the_reader_does_not_know_are_ignored() {
        val text = """{"units":"mm","level_feet":1,"tile_feet":1.0,"cols":1,"rows":1,"as_of":"x","tile_mm":305,"window":{"x0":0,"y0":0,"x1":305,"y1":305},"types":[],
            "ground":{"cols":1,"rows":1,"heights_mm":[0,0,0,0],"version":8421684800706510932,"stack_versions":[{"stack_level":0,"version":1}]}}"""
        assertEquals(8421684800706510932L, readSceneView(text).ground!!.version)
    }

    @Test
    fun a_scene_that_is_not_json_is_refused_with_a_reason() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneView("not json") }
        assertEquals("the scene is not valid JSON", failure.message)
    }

    @Test
    fun a_scene_in_another_unit_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneView("""{"units":"ft"}""") }
        assertEquals("the scene must be in millimetres, not ft", failure.message)
    }

    @Test
    fun a_scene_missing_its_grid_size_is_refused_naming_the_field() {
        val failure = assertFailsWith<SceneViewFormatException> {
            readSceneView("""{"units":"mm","level_feet":1,"tile_feet":1.0,"rows":1,"window":{"x0":0,"y0":0,"x1":1,"y1":1},"types":[]}""")
        }
        assertEquals("the scene lacks a whole number cols", failure.message)
    }

    @Test
    fun a_provenance_tag_decides_the_kind() {
        val text = """{"units":"mm","level_feet":1,"tile_feet":1.0,"cols":1,"rows":1,"window":{"x0":0,"y0":0,"x1":1,"y1":1},"types":[],
            "footprints":{"version":1,"items":[],"provenance":{"tag":"guessed","kind":"measured"}}}"""
        assertEquals(ProvenanceKind.PROPOSED, readSceneView(text).footprints!!.provenance.kind)
    }
}
