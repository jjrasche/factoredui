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
        for (width in listOf(32, 64)) {
            EMBEDDED_PIXEL_ATLAS.sheets(width).forEachIndexed { index, sheet ->
                val png = ImageIO.read(File(atlasDirectory, "pixel-atlas-$width-$index.png"))
                assertEquals(png.width to png.height, sheet.width to sheet.height)
                val expected = png.getRGB(0, 0, png.width, png.height, null, 0, png.width).map(::visible)
                val mismatches = expected.indices.count { expected[it] != sheet.argb[it] }
                assertEquals(0, mismatches, "sheet $width-$index")
            }
        }
    }

    @Test
    fun theEmbeddedManifestIsTheCommittedJson() {
        val committed = parsePixelManifest(File(atlasDirectory, "pixel-atlas.json").readText())
        assertEquals(committed, EMBEDDED_PIXEL_ATLAS.manifest)
    }
}
