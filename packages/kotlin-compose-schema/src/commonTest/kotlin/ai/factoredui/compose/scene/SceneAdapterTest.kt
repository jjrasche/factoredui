package ai.factoredui.compose.scene

import ai.factoredui.compose.layout.TileFootprint
import ai.factoredui.compose.layout.TileInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneAdapterTest {

    private fun parcel(vararg extra: Pair<String, Any?>): Map<String, Any?> =
        mapOf("cols" to 2, "rows" to 2, "tile_area" to 625.0, "shape" to "square") + extra

    @Test
    fun aTileOf625SquareFeetIsTwentyFiveFeetAndSevenThousandSixHundredTwentyMillimetres() {
        val scene = adaptRenderProps(parcel()).scene
        assertEquals(25.0, scene.tileFeet)
        assertEquals(25, scene.levelFeet)
        assertEquals(WindowMm(0, 0, 15240, 15240), scene.window)
    }

    @Test
    fun instancePositionsRoundHalfUpTowardPositiveInfinity() {
        val rounded = listOf(0.5, 1.5, 2.5, -0.5, -1.5, 2.4).map { x ->
            val instance = mapOf("id" to "i", "type" to "tree", "x_mm" to x, "y_mm" to 0)
            adaptRenderProps(parcel("instances" to listOf(instance))).scene.instances!!.items.single().xMm
        }
        assertEquals(listOf(1L, 2L, 3L, 0L, -1L, 2L), rounded)
    }

    @Test
    fun anInstanceWithoutAPositionIsDroppedAndTheReasonIsReported() {
        val adapted = adaptRenderProps(
            parcel(
                "instances" to listOf(
                    mapOf("id" to "kept", "type" to "tree", "x_mm" to 10, "y_mm" to 20),
                    mapOf("id" to "lost", "type" to "tree", "x_mm" to 10),
                ),
            ),
        )
        assertEquals(listOf("kept"), adapted.scene.instances!!.items.map { it.id })
        assertEquals(1, adapted.dropped.size)
        assertTrue(adapted.dropped.single().startsWith("instance 1"))
    }

    @Test
    fun measuredInstancesKeepTheirProvenanceAndEverythingElseIsProposed() {
        val items = adaptRenderProps(
            parcel(
                "instances" to listOf(
                    mapOf("id" to "a", "type" to "tree", "x_mm" to 0, "y_mm" to 0, "provenance" to "measured"),
                    mapOf("id" to "b", "type" to "tree", "x_mm" to 0, "y_mm" to 0, "provenance" to "proposed"),
                ),
            ),
        ).scene.instances!!.items
        assertEquals(listOf(ProvenanceKind.MEASURED, ProvenanceKind.PROPOSED), items.map { it.provenance })
    }

    @Test
    fun plainCellsBecomeOneByOneFootprintsAndAreKeptBesideExplicitFootprints() {
        val cell = mapOf("col" to 1, "row" to 0, "use" to "path")
        val fromCells = adaptRenderProps(parcel("cells" to listOf(cell))).scene.footprints!!.items
        assertEquals(listOf(SceneFootprint("1,0", "path", 1, 0, 1, 1)), fromCells)

        val wide = mapOf("id" to "barn", "col" to 0, "row" to 0, "use" to "shed", "width" to 2, "height" to 1)
        val both = adaptRenderProps(parcel("cells" to listOf(cell), "footprints" to listOf(wide))).scene.footprints!!.items
        assertEquals(listOf(SceneFootprint("1,0", "path", 1, 0, 1, 1), SceneFootprint("barn", "shed", 0, 0, 2, 1)), both)
    }

    @Test
    fun theLookTableCarriesEachUsesColourAndSprite() {
        val adapted = adaptRenderProps(
            parcel("uses" to listOf(mapOf("id" to "pond", "label" to "Pond", "color" to "#3B82C4", "sprite" to "water"), mapOf("label" to "no id"))),
        )
        assertEquals(mapOf("pond" to LookEntry(color = "#3B82C4", sprite = "water")), adapted.look)
        assertEquals(listOf("pond"), adapted.scene.types.map { it.id })
    }

    @Test
    fun groundArrivesWithItsCutAndFillAndAProvenanceNamingTheSource() {
        val heights = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        val adapted = adaptRenderProps(
            parcel(
                "ground" to mapOf("unit" to "mm", "datum" to "NAVD88", "source" to "usgs-3dep", "vertex_cols" to 3, "vertex_rows" to 3, "heights_mm" to heights, "version" to 4),
                "ground_base" to mapOf("heights_mm" to heights, "cut_fill_mm" to heights.map { -it }),
            ),
        )
        val ground = assertNotNull(adapted.scene.ground)
        assertEquals(4L, ground.version)
        assertEquals(7L, ground.heightAt(1, 2))
        assertEquals(-7L, ground.cutFillMm!![7])
        assertEquals(ProvenanceKind.MEASURED, ground.provenance.kind)
        assertEquals("usgs-3dep", ground.provenance.source)
        assertEquals("NAVD88", ground.datum)
    }

    @Test
    fun aGroundWhoseHeightCountDisagreesWithTheGridIsLeftOutRatherThanMisread() {
        val short = mapOf("heights_mm" to listOf(0, 1, 2), "version" to 1)
        assertNull(adaptRenderProps(parcel("ground" to short)).scene.ground)
    }

    @Test
    fun aParcelThatNamesNoSizeIsTenByTenOfOneSquareFootTiles() {
        val scene = adaptRenderProps(emptyMap()).scene
        assertEquals(10, scene.cols)
        assertEquals(10, scene.rows)
        assertEquals(1.0, scene.tileFeet)
    }

    @Test
    fun theTileModelsTheRendererDrawsComeBackOutOfTheScene() {
        val scene = adaptRenderProps(
            parcel(
                "cells" to listOf(mapOf("col" to 1, "row" to 0, "use" to "path")),
                "footprints" to listOf(mapOf("id" to "barn", "col" to 0, "row" to 1, "use" to "shed", "width" to 2, "height" to 0)),
                "instances" to listOf(mapOf("id" to "oak", "type" to "tree", "x_mm" to 100.5, "y_mm" to 200, "crown_radius_mm" to 900, "rotation_deg" to 45)),
            ),
        ).scene
        assertEquals(
            listOf(TileFootprint("1,0", "path", 1, 0, 1, 1), TileFootprint("barn", "shed", 0, 1, 2, 1)),
            tileFootprintsOf(scene),
        )
        assertEquals(listOf(TileInstance("oak", "tree", 101.0, 200.0, null, 900.0, 45.0)), tileInstancesOf(scene))
    }

    @Test
    fun aParcelWithNothingDrawnHasNoLayers() {
        val scene = adaptRenderProps(parcel()).scene
        assertNull(scene.footprints)
        assertNull(scene.instances)
        assertNull(scene.ground)
        assertEquals(GridSpec("square", 25.0, 2, 2), scene.grid)
    }
}
