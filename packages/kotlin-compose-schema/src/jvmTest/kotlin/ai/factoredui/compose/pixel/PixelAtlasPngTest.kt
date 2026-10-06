package ai.factoredui.compose.pixel

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class PixelAtlasPngTest {

    private val atlasDirectory = File("../../art/pixel-style/atlas")

    private fun visible(argb: Int): Int = if ((argb ushr 24) == 0) 0 else argb

    @Test
    fun theEmbeddedSheetsAreTheCommittedPngsPixelForPixel() {
        val variants = EMBEDDED_PIXEL_ATLAS.manifest.scales.keys
        assertEquals(setOf("32-5ft", "64-5ft", "32-25ft"), variants)
        for (variant in variants) {
            EMBEDDED_PIXEL_ATLAS.sheets(variant).forEachIndexed { index, sheet ->
                val png = ImageIO.read(File(atlasDirectory, "pixel-atlas-$variant-$index.png"))
                assertEquals(png.width to png.height, sheet.width to sheet.height)
                val expected = png.getRGB(0, 0, png.width, png.height, null, 0, png.width).map(::visible)
                val mismatches = expected.indices.count { expected[it] != sheet.argb[it] }
                assertEquals(0, mismatches, "sheet $variant-$index")
            }
        }
    }

    @Test
    fun theEmbeddedManifestIsTheCommittedJson() {
        val committed = parsePixelManifest(File(atlasDirectory, "pixel-atlas.json").readText())
        assertEquals(committed, EMBEDDED_PIXEL_ATLAS.manifest)
    }
}
