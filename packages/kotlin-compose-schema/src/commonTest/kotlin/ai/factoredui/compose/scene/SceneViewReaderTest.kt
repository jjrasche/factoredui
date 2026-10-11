package ai.factoredui.compose.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private val RECORDED = Provenance(ProvenanceKind.MEASURED, source = "survey", asOf = "2026-05-01", sourceSha256 = "ab12", observedAt = "2026-05-01T10:00:00Z", registrationErrorMm = 0)

private fun everyLayerScene(): SceneView = SceneView(
    levelFeet = 1,
    tileFeet = 1.0,
    cols = 2,
    rows = 2,
    window = WindowMm(0, 0, 610, 610),
    types = listOf(
        SceneType("block", "Block", tags = listOf("support"), footprintMm = 305L to 305L, heightMm = 375),
        SceneType("bar", "Bar"),
    ),
    ground = GroundLayer(7, 2, 2, List(9) { it * 10L }, cutFillMm = null, datum = "site", provenance = RECORDED),
    surface = SurfaceLayer(3, 2, 2, listOf(SurfaceClass(0, "empty"), SurfaceClass(1, "block")), listOf(0, 1, 1, 0), RECORDED),
    footprints = FootprintLayer(
        4,
        listOf(SceneFootprint("p1", "block", 0, 0, 1, 1, stackLevel = 0, zMm = 0), SceneFootprint("b1", "bar", 0, 1, 2, 1, heightMm = 286, stackLevel = 1, zMm = 375)),
        RECORDED,
    ),
    instances = InstanceLayer(
        5,
        listOf(
            SceneInstance("t1", "oak", 1, 2, 3, 4.5, 9000, 2500, listOf("tree"), ProvenanceKind.MEASURED),
            SceneInstance("t2", "oak", 4, 5, 0, 0.0, null, null, emptyList(), ProvenanceKind.DERIVED),
        ),
        provenance = RECORDED,
    ),
    water = WaterLayer(6, listOf(Pond("w", 100, listOf(PondTile(1, 1, 300)))), listOf(FlowLine("f", listOf(0L to 0L, 10L to 20L), 1.5, "L/s", "swale")), RECORDED),
    boundaries = BoundaryLayer(8, listOf(BoundaryPolygon("b", "parcel", listOf(0L to 0L, 610L to 0L, 610L to 610L))), RECORDED),
    grid = GridSpec("square", 1.0, 2, 2),
    annotations = AnnotationLayer(9, listOf(Annotation("FOOTPRINT", "b1", "share", "32% of its limit", "the host", 0.32), Annotation("INSTANCE", null, "note", "leaning", null))),
    stackLevels = listOf(StackLevel(0, 0, "on the ground"), StackLevel(1, 375, null)),
    dropped = listOf("a part with no footprint"),
)

class SceneViewReaderTest {
    @Test
    fun a_scene_written_and_read_back_is_the_same_scene() {
        val scene = everyLayerScene()
        assertEquals(scene, readSceneView(sceneViewJson(scene)))
    }

    @Test
    fun an_annotation_with_no_value_reads_back_with_no_value_never_zero() {
        val read = readSceneView(sceneViewJson(everyLayerScene()))
        assertNull(read.annotations!!.items.first { it.kind == "note" }.value)
        assertNull(read.types.first { it.id == "bar" }.heightMm)
    }

    @Test
    fun keys_a_type_does_not_declare_are_ignored_rather_than_carried() {
        val scene = readSceneView(sceneWith(""""types":[{"id":"block","unit_cost_usd":92.37,"unit_weight_kg":null}]"""))
        assertEquals(SceneType("block", "block"), scene.types.single())
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

    @Test
    fun a_layer_provenance_naming_no_known_kind_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneView(sceneWith(""""footprints":{"items":[],"provenance":{"tag":"rumoured"}}""")) }
        assertEquals("provenance names no kind this reader knows: 'rumoured'", failure.message)
    }

    @Test
    fun a_fractional_height_mm_is_refused_not_rounded() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneView(sceneWith(""""types":[{"id":"block","height_mm":2438.4}]""")) }
        assertEquals("height_mm must be a whole number, not 2438.4", failure.message)
    }

    @Test
    fun a_fractional_z_mm_on_a_footprint_is_refused_not_rounded() {
        val failure = assertFailsWith<SceneViewFormatException> {
            readSceneView(sceneWith(""""footprints":{"items":[{"id":"a","type":"t","col":0,"row":0,"z_mm":375.5}]}"""))
        }
        assertEquals("z_mm must be a whole number, not 375.5", failure.message)
    }

    @Test
    fun a_number_given_as_text_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> {
            readSceneView(sceneWith(""""annotations":{"items":[{"ref_kind":"FOOTPRINT","ref_id":"a","kind":"share","value":"0.32"}]}"""))
        }
        assertEquals("value must be a number, not \"0.32\"", failure.message)
    }

    @Test
    fun a_whole_number_beyond_an_int_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> {
            readSceneView(sceneWith(""""footprints":{"items":[{"id":"a","type":"t","col":4294967296,"row":0}]}"""))
        }
        assertEquals("col must be a whole number that fits an int, not 4294967296", failure.message)
    }

    @Test
    fun a_non_array_where_an_array_is_read_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneView(sceneWith(""""types":"block"""")) }
        assertEquals("types must be an array, not \"block\"", failure.message)
    }

    @Test
    fun a_non_object_entry_in_an_object_list_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneView(sceneWith(""""types":[{"id":"block"},"bar"]""")) }
        assertEquals("types[1] must be an object, not \"bar\"", failure.message)
    }

    @Test
    fun a_non_object_layer_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneView(sceneWith(""""ground":[1,2]""")) }
        assertEquals("ground must be an object, not [1,2]", failure.message)
    }

    @Test
    fun a_points_mm_entry_that_is_not_a_pair_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> {
            readSceneView(sceneWith(""""boundaries":{"polygons":[{"id":"b","kind":"parcel","points_mm":[[0,0],[610]]}]}"""))
        }
        assertEquals("points_mm[1] must be a pair of whole millimetres, not [610]", failure.message)
    }

    @Test
    fun a_points_mm_pair_holding_a_fraction_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> {
            readSceneView(sceneWith(""""water":{"flow":[{"id":"f","points_mm":[[0,0.5]],"quantity":1.0,"unit":"L/s","kind":"swale"}]}"""))
        }
        assertEquals("points_mm[0] must be a pair of whole millimetres, not [0,0.5]", failure.message)
    }

    @Test
    fun a_footprint_mm_that_is_not_a_pair_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneView(sceneWith(""""types":[{"id":"block","footprint_mm":[305]}]""")) }
        assertEquals("footprint_mm must be a pair of whole millimetres, not [305]", failure.message)
    }

    @Test
    fun a_non_string_entry_in_a_string_list_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneView(sceneWith(""""types":[{"id":"block","tags":["support",3]}]""")) }
        assertEquals("tags[1] must be text, not 3", failure.message)
    }

    @Test
    fun absent_or_null_keys_read_as_their_documented_defaults() {
        val text = sceneWith(
            """"types":[{"id":"block","label":null,"tags":null,"height_mm":null}],
               "footprints":{"version":null,"items":[{"id":"a","type":"block","col":0,"row":0,"width":null,"z_mm":null}]}""",
        )
        val scene = readSceneView(text)
        assertEquals(SceneType("block", "block"), scene.types.single())
        assertEquals(SceneFootprint("a", "block", 0, 0, 1, 1), scene.footprints!!.items.single())
        assertEquals(0L, scene.footprints!!.version)
    }

    @Test
    fun an_instance_provenance_reads_its_tag_first_then_its_kind_name() {
        val kinds = listOf("recorded", "computed", "guessed", "measured", "derived", "proposed").map { word ->
            readSceneView(sceneWith(instanceWithProvenance("\"$word\""))).instances!!.items.single().provenance
        }
        val expected = listOf(ProvenanceKind.MEASURED, ProvenanceKind.DERIVED, ProvenanceKind.PROPOSED, ProvenanceKind.MEASURED, ProvenanceKind.DERIVED, ProvenanceKind.PROPOSED)
        assertEquals(expected, kinds)
        assertEquals(ProvenanceKind.PROPOSED, readSceneView(sceneWith(instanceWithProvenance("null"))).instances!!.items.single().provenance)
    }

    @Test
    fun the_grid_keeps_the_edge_its_row_0_stands_at_and_a_grid_without_one_says_nothing() {
        val north = readSceneView(sceneWith(""""grid":{"shape":"square","north":"row_0","tile_mm":305,"cols":1,"rows":1}"""))
        assertEquals("row_0", north.grid!!.north)
        assertEquals(null, readSceneView(sceneWith(""""grid":{"shape":"square","tile_mm":305,"cols":1,"rows":1}""")).grid!!.north)
        assertEquals("row_0", readSceneView(sceneViewJson(north)).grid!!.north, "the grid's north survives a round trip")
    }

    @Test
    fun a_grid_north_that_is_not_text_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneView(sceneWith(""""grid":{"shape":"square","north":0,"tile_mm":305,"cols":1,"rows":1}""")) }
        assertEquals("north must be text, not 0", failure.message)
    }

    @Test
    fun an_instance_provenance_naming_no_known_kind_is_refused() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneView(sceneWith(instanceWithProvenance("\"rumoured\""))) }
        assertEquals("provenance names no kind this reader knows: 'rumoured'", failure.message)
    }
}

private fun sceneWith(fields: String): String =
    """{"units":"mm","level_feet":1,"tile_feet":1.0,"cols":1,"rows":1,"window":{"x0":0,"y0":0,"x1":305,"y1":305},$fields}"""

private fun instanceWithProvenance(provenance: String): String =
    """"instances":{"items":[{"id":"t","type":"oak","x_mm":0,"y_mm":0,"provenance":$provenance}]}"""
