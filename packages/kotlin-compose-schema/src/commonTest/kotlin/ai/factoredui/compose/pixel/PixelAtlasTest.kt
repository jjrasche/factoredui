package ai.factoredui.compose.pixel

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PixelAtlasTest {

    private val tinyManifest = """
        {"version":1,
         "scales":{"32":{"art_tile_width":32,"art_tile_feet":5.0,"sheets":[{"width":40,"height":20}],
           "sprites":[{"name":"shed/10x8/SE","kind":"model","class":"shed","size":"10x8","facing":"SE","frame":0,"sheet":0,
             "x":3,"y":4,"width":20,"height":12,"anchor_x":9,"anchor_y":10,"art_tile_width":32,"footprint_ft":[10.0,8.0],"height_ft":9.0,"swap":""}]}},
         "swaps":{"tractor":{"base":"Red","ramps":{"Red":["#FF6E1D1A","#FF9B2A25"],"Blue":["#FF1B3F7A","#FF2859A8"]}}}}
    """.trimIndent()

    @Test
    fun theReaderKeepsEverySpriteFieldIncludingTheGroundContactAnchor() {
        val manifest = parsePixelManifest(tinyManifest)
        val shed = manifest.scales.getValue(32).byName.getValue("shed/10x8/SE")
        assertEquals(PixelSpriteKind.MODEL, shed.kind)
        assertEquals(listOf(3, 4, 20, 12), listOf(shed.x, shed.y, shed.width, shed.height))
        assertEquals(9 to 10, shed.anchorX to shed.anchorY)
        assertEquals(10.0 to 8.0, shed.footprintFt)
        assertEquals(listOf(SheetSize(40, 20)), manifest.scales.getValue(32).sheets)
    }

    @Test
    fun swapRampsReadAsArgbIntegers() {
        val swap = parsePixelManifest(tinyManifest).swaps.getValue("tractor")
        assertEquals("Red", swap.base)
        assertEquals(listOf(0xFF6E1D1A.toInt(), 0xFF9B2A25.toInt()), swap.ramps.getValue("Red"))
    }

    @Test
    fun unpackingExpandsLiteralsAndRepeats() {
        val packed = byteArrayOf(2, 7, 8, 9, (257 - 5).toByte(), 4)
        assertContentEquals(byteArrayOf(7, 8, 9, 4, 4, 4, 4, 4), unpackBits(packed, 8))
    }

    @Test
    fun unpackingRefusesAStreamOfTheWrongLength() {
        assertFailsWith<IllegalStateException> { unpackBits(byteArrayOf(0, 1), 2) }
    }

    @Test
    fun aPaletteSwapReplacesOnlyTheBaseRampColours() {
        val red = 0xFF6E1D1A.toInt()
        val glass = 0xFF78A9C9.toInt()
        val sheet = ArgbSheet(2, 1, intArrayOf(red, glass))
        val swapped = swapPalette(sheet, listOf(red), listOf(0xFF1B3F7A.toInt()))
        assertContentEquals(intArrayOf(0xFF1B3F7A.toInt(), glass), swapped.argb)
    }

    @Test
    fun cropCopiesTheRectangleRowByRow() {
        val sheet = ArgbSheet(3, 2, intArrayOf(1, 2, 3, 4, 5, 6))
        assertContentEquals(intArrayOf(2, 3, 5, 6), sheet.crop(1, 0, 2, 2).argb)
    }

    @Test
    fun theEmbeddedAtlasCarriesBothArtScalesWithMatchingSheetsAndTheSameSprites() {
        val manifest = EMBEDDED_PIXEL_ATLAS.manifest
        assertEquals(setOf(32, 64), manifest.scales.keys)
        assertEquals(manifest.scales.getValue(32).byName.keys, manifest.scales.getValue(64).byName.keys)
        for ((width, scale) in manifest.scales) {
            val sheets = EMBEDDED_PIXEL_ATLAS.sheets(width)
            assertEquals(scale.sheets, sheets.map { SheetSize(it.width, it.height) })
            for (sprite in scale.sprites) {
                val bounds = scale.sheets[sprite.sheet]
                assertTrue(sprite.x + sprite.width <= bounds.width && sprite.y + sprite.height <= bounds.height, sprite.name)
            }
        }
    }

    @Test
    fun aGroundPatternDecodesFullyOpaqueAndATractorSwapsToBlue() {
        val scale = EMBEDDED_PIXEL_ATLAS.manifest.scales.getValue(32)
        val grass = scale.byName.getValue("ground-grass/0")
        val pixels = EMBEDDED_PIXEL_ATLAS.sheets(32)[grass.sheet].crop(grass.x, grass.y, grass.width, grass.height)
        assertTrue(pixels.argb.all { (it ushr 24) == 0xFF })
        val tractor = scale.byName.getValue("tractor/idle/SE")
        val red = swappedSprite(EMBEDDED_PIXEL_ATLAS, tractor, "Red")
        val blue = swappedSprite(EMBEDDED_PIXEL_ATLAS, tractor, "Blue")
        assertContentEquals(red.argb.map { it ushr 24 }, blue.argb.map { it ushr 24 }, "the silhouette is unchanged")
        val blueRamp = EMBEDDED_PIXEL_ATLAS.manifest.swaps.getValue("tractor").ramps.getValue("Blue").toSet()
        assertTrue(blue.argb.count { it in blueRamp } > 20, "the body is drawn in the blue ramp")
        assertTrue(red.argb.none { it in blueRamp })
    }
}
