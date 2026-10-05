package ai.factoredui.compose.schema

import ai.factoredui.compose.layout.TileCell
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import kotlin.test.Test
import kotlin.test.assertEquals

class TilemapPropsTest {

    @Test
    fun aUseNeedsAnIdAndDefaultsItsLabelAndSprite() {
        val uses = resolveTilemapUses(
            listOf(
                mapOf("id" to "pond", "label" to "Pond", "color" to "#3B82C4", "sprite" to "water"),
                mapOf("id" to "path"),
                mapOf("label" to "no id"),
            ),
        )
        assertEquals(2, uses.size)
        assertEquals(TilemapUse("pond", "Pond", "#3B82C4", TileSprite.WATER, null), uses[0])
        assertEquals(TilemapUse("path", "path", null, TileSprite.FLAT, null), uses[1])
    }

    @Test
    fun everySpriteNameIsRecognisedAndAnUnknownOneIsFlat() {
        val sprites = listOf("flat", "block", "tree", "arch", "water", "fence", "dragon").map {
            resolveTilemapUses(listOf(mapOf("id" to "u", "sprite" to it))).single().sprite
        }
        assertEquals(
            listOf(TileSprite.FLAT, TileSprite.BLOCK, TileSprite.TREE, TileSprite.ARCH, TileSprite.WATER, TileSprite.FENCE, TileSprite.FLAT),
            sprites,
        )
    }

    @Test
    fun aBlockMayCarryAHeightInTiles() {
        assertEquals(1.5f, resolveTilemapUses(listOf(mapOf("id" to "b", "sprite" to "block", "height" to 1.5))).single().height)
    }

    @Test
    fun aUseMayNameAnAnimalThatWandersItsTiles() {
        val uses = resolveTilemapUses(listOf(mapOf("id" to "paddock", "sprite" to "fence", "critter" to "sheep"), mapOf("id" to "path")))
        assertEquals("sheep", uses[0].critter)
        assertEquals(null, uses[1].critter)
    }

    @Test
    fun cellsNeedAColumnARowAndAUse() {
        val cells = resolveTilemapCells(
            listOf(
                mapOf("col" to 2, "row" to 3.0, "use" to "tree"),
                mapOf("col" to 1, "row" to 1),
                mapOf("row" to 1, "use" to "path"),
                "junk",
            ),
        )
        assertEquals(listOf(TileCell(2, 3, "tree")), cells)
    }

    @Test
    fun shapeAndViewDefaultToSquareAndIso() {
        assertEquals(TileShape.SQUARE, resolveTilemapShape(null))
        assertEquals(TileShape.HEX, resolveTilemapShape("hex"))
        assertEquals(TileShape.SQUARE, resolveTilemapShape("triangle"))
        assertEquals(TileView.ISO, resolveTilemapView(null))
        assertEquals(TileView.TOP, resolveTilemapView("top"))
    }

    @Test
    fun gridSizeFallsBackToOneAndNeverGoesBelowIt() {
        assertEquals(13, resolveTilemapSize(13, 1))
        assertEquals(1, resolveTilemapSize(0, 1))
        assertEquals(7, resolveTilemapSize("seven", 7))
    }

    @Test
    fun tileAreaDefaultsToOneAndMustBePositive() {
        assertEquals(625.0, resolveTileArea(625))
        assertEquals(1.0, resolveTileArea(null))
        assertEquals(1.0, resolveTileArea(-3))
    }

    @Test
    fun theTileTapActionIsReadFromTheProps() {
        val props = mapOf("on_tile_tap" to SpecValue.StringValue("world.tileTapped"))
        assertEquals("world.tileTapped", props.asTilemapProps().onTileTapped)
    }
}
