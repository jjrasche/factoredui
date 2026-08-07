package ai.factoredui.compose.renderer

import ai.factoredui.compose.render.renderSpecToPng
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Real-data proof: 68 Jolly Rd parcels (common-ground) through the geomap primitive, headless.
class JollyRdParcelsReceiptTest {

    private fun fixtureSpecJson(): String =
        checkNotNull(javaClass.getResourceAsStream("/geomap/jolly_parcels_spec.json")) {
            "fixture /geomap/jolly_parcels_spec.json missing from desktopTest resources"
        }.bufferedReader().readText()

    @Test
    fun theParcelFixtureRendersToAPngWithScoreRampFills() {
        val png = renderSpecToPng(fixtureSpecJson(), width = 800, height = 800, density = 1f)
        assertEquals(0x89.toByte(), png[0])
        assertEquals(0x50.toByte(), png[1])

        val image = ImageIO.read(ByteArrayInputStream(png))
        val distinctColors = HashSet<Int>()
        var paintedPixels = 0
        for (y in 0 until image.height step 4) {
            for (x in 0 until image.width step 4) {
                val rgb = image.getRGB(x, y) and 0xFFFFFF
                distinctColors.add(rgb)
                if (rgb != 0xFFFFFF && rgb != 0x000000) paintedPixels++
            }
        }
        assertTrue(distinctColors.size >= 8, "score-ramp fills must yield many colors, got ${distinctColors.size}")
        val sampledPixels = (image.height / 4) * (image.width / 4)
        assertTrue(
            paintedPixels > sampledPixels / 20,
            "parcels must cover a visible share of the canvas: $paintedPixels of $sampledPixels sampled",
        )

        val receipt = File("build/geomap-receipts/jolly_parcels_receipt.png")
        receipt.parentFile.mkdirs()
        receipt.writeBytes(png)
    }
}
