package ai.factoredui.compose.layout

import ai.factoredui.compose.schema.resolveTilemapFootprints
import ai.factoredui.compose.schema.resolveTilemapInstances
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TILE_SIDE_MM = 7620.0
private const val ROWS = 26

private fun tree(id: String, xMm: Double, yMm: Double, crownRadiusMm: Double? = 3000.0) =
    TileInstance(id, "woodland_tree", xMm, yMm, 9000.0, crownRadiusMm, 0.0)

class TileInstancesTest {

    @Test
    fun aTwentyFiveFootTileIsSevenThousandSixHundredTwentyMillimetres() {
        assertEquals(TILE_SIDE_MM, tileSideMm(625.0), 1e-9)
    }

    @Test
    fun anInstanceStandsWhereItsMillimetresSayFromTheSouthWestCorner() {
        val ground = instanceGround(tree("a", TILE_SIDE_MM * 3, TILE_SIDE_MM * 2), TILE_SIDE_MM, ROWS)
        assertEquals(3f, ground.x, 1e-4f)
        assertEquals(24f, ground.y, 1e-4f)
    }

    @Test
    fun theSouthernEdgeIsTheLastRowEdgeAndTheNorthernEdgeIsRowZero() {
        assertEquals(26f, instanceGround(tree("south", 0.0, 0.0), TILE_SIDE_MM, ROWS).y, 1e-4f)
        assertEquals(0f, instanceGround(tree("north", 0.0, TILE_SIDE_MM * ROWS), TILE_SIDE_MM, ROWS).y, 1e-4f)
    }

    @Test
    fun theCrownRadiusInTilesIsTheMillimetreRadiusOverTheTileSide() {
        assertEquals(0.5f, instanceRadiusTiles(tree("a", 0.0, 0.0, crownRadiusMm = 3810.0), TILE_SIDE_MM), 1e-4f)
    }

    @Test
    fun aTapPicksTheNearestInstanceWithinItsCrown() {
        val near = tree("near", TILE_SIDE_MM * 5, TILE_SIDE_MM * 20)
        val far = tree("far", TILE_SIDE_MM * 9, TILE_SIDE_MM * 20)
        val tapped = pickInstance(listOf(far, near), TILE_SIDE_MM, ROWS, GroundPoint(5.2f, 6.1f))
        assertEquals("near", tapped?.id)
    }

    @Test
    fun aTapOutsideEveryCrownPicksNothing() {
        val only = tree("only", TILE_SIDE_MM * 5, TILE_SIDE_MM * 20)
        assertNull(pickInstance(listOf(only), TILE_SIDE_MM, ROWS, GroundPoint(12f, 12f)))
    }

    @Test
    fun overlappingCrownsGoToTheCloserCentre() {
        val left = tree("left", TILE_SIDE_MM * 5, TILE_SIDE_MM * 20, crownRadiusMm = TILE_SIDE_MM * 2)
        val right = tree("right", TILE_SIDE_MM * 6, TILE_SIDE_MM * 20, crownRadiusMm = TILE_SIDE_MM * 2)
        assertEquals("right", pickInstance(listOf(left, right), TILE_SIDE_MM, ROWS, GroundPoint(5.8f, 6f))?.id)
    }

    @Test
    fun aFootprintDrawsBetweenItsTilesFarCornerAndNearCorner() {
        val corners = footprintCorners(TileFootprint("hall", "commons_building", 4, 6, 2, 3))
        assertEquals(GroundPoint(4f, 6f), corners.first())
        assertEquals(GroundPoint(6f, 9f), corners[2])
        assertEquals(GroundPoint(5f, 7.5f), footprintCentre(TileFootprint("hall", "commons_building", 4, 6, 2, 3)))
    }

    @Test
    fun drawablesRunFromTheFarCornerToTheNearOneWhateverKindTheyAre() {
        val south = TileFootprint("south", "paddock", 8, 8, 1, 1)
        val north = TileFootprint("north", "paddock", 1, 1, 1, 1)
        val between = tree("between", TILE_SIDE_MM * 4.5, TILE_SIDE_MM * (ROWS - 4.5))
        val order = drawOrder(TileShape.SQUARE, listOf(south, north), listOf(between), TILE_SIDE_MM, ROWS).map { drawableId(it) }
        assertEquals(listOf("north", "between", "south"), order)
    }

    @Test
    fun aWholeBoardOfOneTileFootprintsDrawsInTheOrderTileDrawOrderGives() {
        val cells = (0 until 7).flatMap { row -> (0 until 5).map { col -> TileCell(col, row, "path") } }
        val footprints = cellsAsFootprints(cells.reversed())
        val drawn = drawOrder(TileShape.SQUARE, footprints, emptyList(), TILE_SIDE_MM, ROWS).map { (it as FootprintDrawable).footprint }
        assertEquals(tileDrawOrder(TileShape.SQUARE, 5, 7).map { it.col to it.row }, drawn.map { it.col to it.row })
    }

    @Test
    fun hexFootprintsOrderByTheirHexCentres() {
        val cells = (0 until 4).flatMap { row -> (0 until 3).map { col -> TileCell(col, row, "path") } }
        val drawn = drawOrder(TileShape.HEX, cellsAsFootprints(cells), emptyList(), TILE_SIDE_MM, ROWS).map { (it as FootprintDrawable).footprint }
        assertEquals(tileDrawOrder(TileShape.HEX, 3, 4).map { it.col to it.row }, drawn.map { it.col to it.row })
    }

    @Test
    fun aCellIsAOneTileFootprint() {
        val footprint = cellsAsFootprints(listOf(TileCell(3, 4, "path"))).single()
        assertEquals(TileFootprint("3,4", "path", 3, 4, 1, 1), footprint)
    }

    @Test
    fun footprintsAndInstancesResolveFromBoundMaps() {
        val footprints = resolveTilemapFootprints(listOf(mapOf("id" to "p1", "use" to "paddock", "col" to 2, "row" to 3, "width" to 5, "height" to 5), mapOf("use" to "path")))
        assertEquals(listOf(TileFootprint("p1", "paddock", 2, 3, 5, 5)), footprints)
        val instances = resolveTilemapInstances(
            listOf(
                mapOf("id" to "t1", "type" to "woodland_tree", "x_mm" to 1000, "y_mm" to 2000.5, "height_mm" to 9000, "crown_radius_mm" to 3000, "rotation_deg" to 90),
                mapOf("id" to "bad", "type" to "woodland_tree", "x_mm" to 1000),
            ),
        )
        assertEquals(1, instances.size)
        assertEquals(TileInstance("t1", "woodland_tree", 1000.0, 2000.5, 9000.0, 3000.0, 90.0), instances.single())
    }

    @Test
    fun anInstanceWithNoCrownIsStillPickableAtItsCentre() {
        val bare = tree("bare", TILE_SIDE_MM * 5, TILE_SIDE_MM * 20, crownRadiusMm = null)
        assertTrue(pickInstance(listOf(bare), TILE_SIDE_MM, ROWS, GroundPoint(5f, 6f)) != null)
    }
}

private fun drawableId(drawable: TileDrawable): String = when (drawable) {
    is FootprintDrawable -> drawable.footprint.id
    is InstanceDrawable -> drawable.instance.id
}
