package ai.factoredui.worldbuilder

import ai.factoredui.compose.scene.BoundaryLayer
import ai.factoredui.compose.scene.BoundaryPolygon
import ai.factoredui.compose.scene.FlowLine
import ai.factoredui.compose.scene.InstanceLayer
import ai.factoredui.compose.scene.Pond
import ai.factoredui.compose.scene.PondTile
import ai.factoredui.compose.scene.Provenance
import ai.factoredui.compose.scene.ProvenanceKind
import ai.factoredui.compose.scene.SceneInstance
import ai.factoredui.compose.scene.SceneType
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.SurfaceClass
import ai.factoredui.compose.scene.SurfaceLayer
import ai.factoredui.compose.scene.WaterLayer
import ai.factoredui.compose.scene.adaptRenderProps
import ai.factoredui.compose.scene.sceneViewJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val EXAMPLE_TWIN_PATH = "../../tools/yard-overlay/web/data/example-twin.json"
private const val TILE_MM = 1524L
private const val GRASS = 1
private const val GRAVEL = 2
private const val FOREST = 3
private val EXAMPLE_PROVENANCE = Provenance(ProvenanceKind.PROPOSED, source = "yard-overlay example fixture", note = "synthetic layers drawn over the five-foot example world for the phone page")
private val USE_HEIGHTS_MM = mapOf("hoop_house" to 3000L, "commons_building" to 4500L, "van_pad" to 0L)

private fun exampleHost(): WorldBuilderHost = WorldBuilderHost(openSession(File("examples/parcel-five-acre-5ft.world.json").path)).apply {
    tap(10, 10, "hoop_house")
    tap(30, 50, "paddock")
}

@Suppress("UNCHECKED_CAST")
private fun worldScene(host: WorldBuilderHost): SceneView {
    val bindings = host.bindings()
    val parcel = bindings["parcel"] as Map<String, Any?>
    return adaptRenderProps(
        mapOf(
            "cols" to parcel["cols"],
            "rows" to parcel["rows"],
            "tile_area" to parcel["tile_area"],
            "uses" to bindings["uses"],
            "footprints" to bindings["footprints"],
            "instances" to bindings["instances"],
        ),
    ).scene
}

private fun landCover(cols: Int, rows: Int): SurfaceLayer {
    val classIds = List(cols * rows) { index ->
        val column = index % cols
        val row = index / cols
        when {
            row < 2 -> GRAVEL
            row >= rows - 30 && column >= cols - 25 -> FOREST
            else -> GRASS
        }
    }
    val classes = listOf(SurfaceClass(GRASS, "grass"), SurfaceClass(GRAVEL, "gravel"), SurfaceClass(FOREST, "forest"))
    return SurfaceLayer(1, cols, rows, classes, classIds, EXAMPLE_PROVENANCE)
}

private fun pondAndSwale(): WaterLayer {
    val tiles = (45..48).flatMap { column -> (60..62).map { row -> PondTile(column, row, 400) } }
    val swale = FlowLine("swale-1", listOf(30 * TILE_MM to 100 * TILE_MM, 38 * TILE_MM to 80 * TILE_MM, 47 * TILE_MM to 61 * TILE_MM), 1.5, "L/s", "swale")
    return WaterLayer(1, listOf(Pond("pond-1", 0, tiles)), listOf(swale), EXAMPLE_PROVENANCE)
}

private fun oakTrees(cols: Int, rows: Int): InstanceLayer {
    var seed = 20261006L
    fun next(limit: Long): Long {
        seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        return seed % limit
    }
    val items = (1..16).map { number ->
        val isInForest = number <= 12
        val column = if (isInForest) cols - 25 + next(24) else 4 + next(20)
        val row = if (isInForest) rows - 29 + next(28) else 4 + next(20)
        SceneInstance(
            id = "oak-$number",
            type = "oak",
            xMm = column * TILE_MM + next(TILE_MM),
            yMm = row * TILE_MM + next(TILE_MM),
            zMm = 0,
            rotationDeg = 0.0,
            heightMm = 9000 + next(5000),
            crownRadiusMm = 2500 + next(1500),
            tags = listOf("tree"),
            provenance = if (number % 2 == 0) ProvenanceKind.MEASURED else ProvenanceKind.PROPOSED,
        )
    }
    return InstanceLayer(1, items, provenance = EXAMPLE_PROVENANCE)
}

private fun parcelEdge(scene: SceneView): BoundaryLayer {
    val width = scene.window.x1
    val depth = scene.window.y1
    val ring = listOf(0L to 0L, width to 0L, width to depth, 0L to depth)
    return BoundaryLayer(1, listOf(BoundaryPolygon("parcel", "parcel", ring)), EXAMPLE_PROVENANCE)
}

private fun exampleTwin(): SceneView {
    val scene = worldScene(exampleHost())
    val heightenedTypes = scene.types.map { it.copy(heightMm = USE_HEIGHTS_MM[it.id]) }
    val oak = SceneType("oak", "Oak", tags = listOf("tree"), heightMm = 11000)
    return scene.copy(
        types = heightenedTypes + oak,
        surface = landCover(scene.cols, scene.rows),
        water = pondAndSwale(),
        boundaries = parcelEdge(scene),
        instances = oakTrees(scene.cols, scene.rows),
    )
}

class YardTwinExportTest {

    @Test
    fun theExampleTwinIsTheSameEveryTimeItIsMade() {
        assertEquals(sceneViewJson(exampleTwin()), sceneViewJson(exampleTwin()))
    }

    @Test
    fun theExampleTwinCarriesEveryLayerThePhonePageToggles() {
        val json = Json.parseToJsonElement(sceneViewJson(exampleTwin())).jsonObject
        listOf("surface", "water", "footprints", "boundaries", "instances").forEach { layer ->
            assertTrue(json.containsKey(layer), "$layer layer present")
        }
        assertEquals(2, json.getValue("footprints").jsonObject.getValue("items").jsonArray.size)
        assertEquals(16, json.getValue("instances").jsonObject.getValue("items").jsonArray.size)
    }

    @Test
    fun theExampleTwinIsWrittenWhereThePhonePageReadsIt() {
        val target = File(EXAMPLE_TWIN_PATH)
        target.parentFile.mkdirs()
        target.writeText(sceneViewJson(exampleTwin()))
        assertTrue(target.length() > 10_000, "written ${target.length()} bytes")
    }
}
