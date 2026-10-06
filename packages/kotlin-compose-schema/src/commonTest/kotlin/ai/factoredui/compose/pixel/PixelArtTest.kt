package ai.factoredui.compose.pixel

import ai.factoredui.compose.layout.TileCoord
import ai.factoredui.compose.layout.TileFootprint
import ai.factoredui.compose.schema.TileSprite
import ai.factoredui.compose.schema.TilemapUse
import ai.factoredui.compose.schema.resolveTilemapUses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PixelArtTest {

    private fun use(id: String, sprite: TileSprite, critter: String? = null) = TilemapUse(id, id, sprite = sprite, critter = critter)

    @Test
    fun eachSpriteKindOfTheFiveAcreWorldMapsToItsArt() {
        assertEquals(PixelArtChoice(PixelGround.PATH, PixelStanding.NONE), pixelArtFor(use("path", TileSprite.FLAT)))
        assertEquals(PixelArtChoice(PixelGround.DIRT, PixelStanding.VAN), pixelArtFor(use("van_pad", TileSprite.FLAT)))
        assertEquals(PixelArtChoice(PixelGround.WATER, PixelStanding.NONE), pixelArtFor(use("pond", TileSprite.WATER)))
        assertEquals(PixelArtChoice(null, PixelStanding.FENCE, hasCritters = true), pixelArtFor(use("paddock", TileSprite.FENCE, critter = "sheep")))
        assertEquals(PixelArtChoice(null, PixelStanding.HOOP_HOUSE), pixelArtFor(use("hoop_house", TileSprite.ARCH)))
        assertEquals(PixelArtChoice(null, PixelStanding.COMMONS), pixelArtFor(use("commons_building", TileSprite.BLOCK)))
        assertEquals(PixelArtChoice(null, PixelStanding.WOODLAND), pixelArtFor(use("woodland_tree", TileSprite.TREE)))
    }

    @Test
    fun aBlockNamedShedIsAShedAndAFlatNamedForSandOrGravelTakesThatGround() {
        assertEquals(PixelStanding.SHED, pixelArtFor(use("shed", TileSprite.BLOCK)).standing)
        assertEquals(PixelGround.SAND, pixelArtFor(use("sand_strip", TileSprite.FLAT)).ground)
        assertEquals(PixelGround.DIRT, pixelArtFor(use("gravel_yard", TileSprite.FLAT)).ground)
    }

    @Test
    fun theUsesPathCarriesTheArtFieldThroughAndLeavesItNullWhenAbsent() {
        val uses = resolveTilemapUses(listOf(mapOf("id" to "glasshouse", "sprite" to "block", "art" to "hoop_house"), mapOf("id" to "path", "sprite" to "flat")))
        assertEquals(listOf("hoop_house", null), uses.map { it.art })
    }

    @Test
    fun aNamedArtWinsOverTheGuessFromTheSpriteKindAndId() {
        assertEquals(PixelStanding.HOOP_HOUSE, pixelArtFor(use("glasshouse", TileSprite.BLOCK).copy(art = "hoop_house")).standing)
        assertEquals(PixelArtChoice(PixelGround.SAND, PixelStanding.NONE), pixelArtFor(use("path", TileSprite.FLAT).copy(art = "ground-sand")))
        assertEquals(PixelArtChoice(null, PixelStanding.WOODLAND, treeClass = TreeClass.CONIFER), pixelArtFor(use("orchard", TileSprite.TREE).copy(art = "tree-conifer")))
        assertEquals(PixelArtChoice(null, PixelStanding.FENCE, hasCritters = true), pixelArtFor(use("pen", TileSprite.FLAT, critter = "sheep").copy(art = "fence")))
    }

    @Test
    fun anArtNameTheAtlasDoesNotKnowFallsBackToTheGuess() {
        assertEquals(PixelArtChoice(PixelGround.DIRT, PixelStanding.VAN), pixelArtFor(use("van_pad", TileSprite.FLAT).copy(art = "spaceship")))
    }

    @Test
    fun everyNamedStandingArtIsAClassInTheAtlas() {
        val classes = EMBEDDED_PIXEL_ATLAS.manifest.scales.getValue("32-5ft").sprites.map { it.cls }.toSet()
        for (name in listOf("fence", "hoop_house", "commons", "shed", "van", "cow", "person", "tractor", "tree-broadleaf", "tree-conifer", "tree-unknown", "ground-path", "ground-water")) {
            assertTrue(name in classes, name)
            assertTrue(namedArtFor(name, null) != null, name)
        }
    }

    @Test
    fun groundTilesExpandEveryFootprintAndSkipUsesWithNoGround() {
        val uses = listOf(use("path", TileSprite.FLAT), use("pond", TileSprite.WATER), use("hoop", TileSprite.ARCH)).associateBy { it.id }
        val footprints = listOf(TileFootprint("a", "path", 1, 2, 2, 1), TileFootprint("b", "pond", 0, 0, 1, 1), TileFootprint("c", "hoop", 4, 4, 2, 2))
        val tiles = pixelGroundTiles(footprints, uses)
        assertEquals(listOf(TileCoord(1, 2), TileCoord(2, 2)), tiles[PixelGround.PATH])
        assertEquals(listOf(TileCoord(0, 0)), tiles[PixelGround.WATER])
        assertEquals(setOf(PixelGround.PATH, PixelGround.WATER), tiles.keys)
    }

    @Test
    fun waterMovesOnlyWhenTheSpecAnimatesAndTheProfileAllowsIt() {
        val desktop = PixelCosts(isGroundTextured = true, isWaterAnimated = true, decorationLimit = 10)
        val phone = desktop.copy(isWaterAnimated = false)
        assertEquals(2, waterFrameFor(6, isAnimated = true, costs = desktop))
        assertEquals(0, waterFrameFor(6, isAnimated = false, costs = desktop))
        assertEquals(0, waterFrameFor(6, isAnimated = true, costs = phone))
    }

    @Test
    fun everyGroundAndWaterFrameHasAPatternInBothArtScales() {
        for (scale in EMBEDDED_PIXEL_ATLAS.manifest.scales.values) {
            for (ground in PixelGround.entries) {
                for (frame in 0 until WATER_FRAMES) assertTrue(groundPatternName(ground, frame) in scale.byName, groundPatternName(ground, frame))
            }
        }
    }
}
