package ai.factoredui.compose.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileGeometryTest {

    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) < 0.001f, "$message: expected $expected, got $actual")

    @Test
    fun aSquareTileCentreSitsHalfATileIntoItsCell() {
        val centre = tileCenter(TileShape.SQUARE, 3, 5)
        assertNear(3.5f, centre.x, "x")
        assertNear(5.5f, centre.y, "y")
    }

    @Test
    fun aSquareTileHasFourCornersSpanningOneTile() {
        val corners = tileCorners(TileShape.SQUARE, 2, 4)
        assertEquals(4, corners.size)
        assertNear(1f, corners.maxOf { it.x } - corners.minOf { it.x }, "width")
        assertNear(1f, corners.maxOf { it.y } - corners.minOf { it.y }, "height")
    }

    @Test
    fun aHexTileHasSixCornersAllEquidistantFromItsCentre() {
        val centre = tileCenter(TileShape.HEX, 4, 3)
        val distances = tileCorners(TileShape.HEX, 4, 3).map { kotlin.math.hypot(it.x - centre.x, it.y - centre.y) }
        assertEquals(6, distances.size)
        distances.forEach { assertNear(distances.first(), it, "corner radius") }
    }

    @Test
    fun aHexTileIsOneTileWideAcrossItsFlats() {
        val corners = tileCorners(TileShape.HEX, 0, 0)
        assertNear(1f, corners.maxOf { it.x } - corners.minOf { it.x }, "width across the flats")
    }

    @Test
    fun oddHexRowsShiftHalfATileRight() {
        val even = tileCenter(TileShape.HEX, 2, 0)
        val odd = tileCenter(TileShape.HEX, 2, 1)
        assertNear(0.5f, odd.x - even.x, "odd row shift")
    }

    @Test
    fun neighbouringHexCentresAreOneTileApart() {
        val here = tileCenter(TileShape.HEX, 3, 2)
        val east = tileCenter(TileShape.HEX, 4, 2)
        val southEast = tileCenter(TileShape.HEX, 3, 3)
        assertNear(1f, kotlin.math.hypot(east.x - here.x, east.y - here.y), "east neighbour")
        assertNear(1f, kotlin.math.hypot(southEast.x - here.x, southEast.y - here.y), "south-east neighbour")
    }

    @Test
    fun isoProjectionMakesAUnitSquareADiamondTwiceAsWideAsTall() {
        val corners = tileCorners(TileShape.SQUARE, 0, 0).map { project(TileView.ISO, it, tileWidth = 64f) }
        assertNear(64f, corners.maxOf { it.x } - corners.minOf { it.x }, "diamond width")
        assertNear(32f, corners.maxOf { it.y } - corners.minOf { it.y }, "diamond height")
    }

    @Test
    fun unprojectingAProjectedPointReturnsTheOriginalForBothViews() {
        val ground = GroundPoint(3.25f, 7.75f)
        TileView.entries.forEach { view ->
            val back = unproject(view, project(view, ground, 48f), 48f)
            assertNear(ground.x, back.x, "$view x")
            assertNear(ground.y, back.y, "$view y")
        }
    }

    @Test
    fun theTopViewIsAPlainScaledGrid() {
        val screen = project(TileView.TOP, GroundPoint(2f, 3f), tileWidth = 10f)
        assertNear(20f, screen.x, "x")
        assertNear(30f, screen.y, "y")
    }

    @Test
    fun pickingTheCentreOfEverySquareTileReturnsThatTile() {
        for (col in 0 until 13) for (row in 0 until 26) {
            assertEquals(TileCoord(col, row), pickTile(TileShape.SQUARE, 13, 26, tileCenter(TileShape.SQUARE, col, row)))
        }
    }

    @Test
    fun pickingTheCentreOfEveryHexTileReturnsThatTile() {
        for (col in 0 until 13) for (row in 0 until 26) {
            assertEquals(TileCoord(col, row), pickTile(TileShape.HEX, 13, 26, tileCenter(TileShape.HEX, col, row)))
        }
    }

    @Test
    fun pickingJustInsideAHexCornerStillReturnsThatHex() {
        val centre = tileCenter(TileShape.HEX, 5, 4)
        val corner = tileCorners(TileShape.HEX, 5, 4).first()
        val nearCorner = GroundPoint(centre.x + (corner.x - centre.x) * 0.9f, centre.y + (corner.y - centre.y) * 0.9f)
        assertEquals(TileCoord(5, 4), pickTile(TileShape.HEX, 13, 26, nearCorner))
    }

    @Test
    fun pickingOutsideTheGridReturnsNothing() {
        TileShape.entries.forEach { shape ->
            assertNull(pickTile(shape, 13, 26, GroundPoint(-0.2f, 3f)), "$shape left of grid")
            assertNull(pickTile(shape, 13, 26, GroundPoint(5f, -0.4f)), "$shape above grid")
            assertNull(pickTile(shape, 13, 26, GroundPoint(40f, 3f)), "$shape right of grid")
            assertNull(pickTile(shape, 13, 26, GroundPoint(5f, 90f)), "$shape below grid")
        }
    }

    @Test
    fun tilesDrawBackToFrontSoNearerTilesPaintOverFartherOnes() {
        val order = tileDrawOrder(TileShape.SQUARE, 3, 3)
        assertEquals(9, order.size)
        assertEquals(TileCoord(0, 0), order.first(), "the far corner paints first")
        assertEquals(TileCoord(2, 2), order.last(), "the near corner paints last")
        val sums = order.map { it.col + it.row }
        assertEquals(sums.sorted(), sums, "never a nearer tile before a farther one")
    }

    @Test
    fun theDrawOrderCoversEveryTileOnceForBothShapes() {
        TileShape.entries.forEach { shape ->
            assertEquals(13 * 26, tileDrawOrder(shape, 13, 26).toSet().size, "$shape")
        }
    }

    @Test
    fun theScreenBoundsEncloseEveryProjectedCorner() {
        TileShape.entries.forEach { shape ->
            val bounds = tilemapScreenBounds(shape, TileView.ISO, 13, 26, tileWidth = 64f)
            for (col in 0 until 13) for (row in 0 until 26) {
                tileCorners(shape, col, row).map { project(TileView.ISO, it, 64f) }.forEach {
                    assertTrue(it.x >= bounds.minX - 0.01f && it.x <= bounds.maxX + 0.01f, "$shape x inside")
                    assertTrue(it.y >= bounds.minY - 0.01f && it.y <= bounds.maxY + 0.01f, "$shape y inside")
                }
            }
        }
    }
}
