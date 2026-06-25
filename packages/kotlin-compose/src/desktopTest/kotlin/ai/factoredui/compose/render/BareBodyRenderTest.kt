package ai.factoredui.compose.render

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class BareBodyRenderTest {

    private val goldenBodyFrame = """
        {"frames":[{"root_x":0.0,"root_y":0.0,"root_z":0.0,"joints":[
          [0.0,0.0,0.95],[0.1,0.0,0.9],[0.1,0.0,0.5],[0.1,0.0,0.08],[0.1,-0.12,0.04],
          [-0.1,0.0,0.9],[-0.1,0.0,0.5],[-0.1,0.0,0.08],[-0.1,-0.12,0.04],
          [0.0,0.0,1.1],[0.0,0.0,1.25],[0.0,0.0,1.4],[0.0,0.0,1.5],[0.0,0.0,1.65],
          [0.05,0.0,1.42],[0.18,0.0,1.42],[0.3,0.0,1.2],[0.32,0.0,0.98],
          [-0.05,0.0,1.42],[-0.18,0.0,1.42],[-0.3,0.0,1.2],[-0.32,0.0,0.98],
          [0.33,0.0,0.9],[-0.33,0.0,0.9]]}],"playhead":0}
    """.trimIndent()

    private fun bareBodySpec(): String {
        val frame = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonPrimitive.serializer(),
            kotlinx.serialization.json.JsonPrimitive(goldenBodyFrame),
        )
        return """
            {"spec_version":1,"renderer_min":1,"root":{"id":"scene","type":"scene3d","props":{
              "body_frame": $frame, "chrome": false
            }}}
        """.trimIndent()
    }

    @Test
    fun bareBodyRendersOnTransparentBackgroundSoAlphaIsTheSilhouette() {
        val png = renderSpecToPng(bareBodySpec(), width = 400, height = 600, transparent = true)
        val image = ImageIO.read(ByteArrayInputStream(png))
        val cornerAlpha = (image.getRGB(2, 2) ushr 24) and 0xFF
        assertTrue(cornerAlpha == 0, "bare-body transparent render must leave the background fully transparent (corner alpha=$cornerAlpha)")

        var opaquePixels = 0
        for (x in 0 until image.width step 4) {
            for (y in 0 until image.height step 4) {
                if (((image.getRGB(x, y) ushr 24) and 0xFF) > 0) opaquePixels++
            }
        }
        assertTrue(opaquePixels in 1..4000, "the body silhouette must be a SMALL fraction of the frame, not the whole thing (opaque samples=$opaquePixels) — proves no chrome/grid filling the frame")
    }
}
