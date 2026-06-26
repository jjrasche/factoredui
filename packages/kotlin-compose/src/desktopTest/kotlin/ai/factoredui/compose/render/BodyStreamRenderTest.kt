package ai.factoredui.compose.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodyStreamRenderTest {

    private val liveBodyScene = """
        {"spec_version":1,"renderer_min":1,"root":{"id":"scene","type":"scene3d","props":{
          "body":"{runtime.bodyState}","chrome":false
        }}}
    """.trimIndent()

    private fun frameAt(dx: Float): String {
        val joints = (0 until 24).joinToString(",") { i ->
            val x = dx + (if (i % 2 == 0) 0.1f else -0.1f)
            val y = 0.05f * i
            "[$x,$y,0.0]"
        }
        return """{"joints":[$joints]}"""
    }

    private fun streamJson(vararg dxs: Float): String =
        "[" + dxs.joinToString(",") { frameAt(it) } + "]"

    @Test
    fun aBodyStreamRendersAsAMovingSkeleton() {
        val stream = streamJson(0.0f, 0.4f, 0.8f)
        val frames = renderBodyStream(liveBodyScene, "runtime.bodyState", stream, width = 400, height = 600)

        assertEquals(3, frames.size, "a 3-frame stream must produce 3 rendered PNGs")
        frames.forEachIndexed { i, png ->
            assertTrue(png.size > 256, "frame $i must be a real PNG (got ${png.size} bytes)")
        }
        assertFalse(frames[0].contentEquals(frames[1]), "frame 0→1 must DIFFER — the skeleton moved (headless eye-loop sees motion)")
        assertFalse(frames[1].contentEquals(frames[2]), "frame 1→2 must DIFFER — the stream keeps moving")
    }

    @Test
    fun aStaticStreamRendersIdenticalFrames() {
        val stream = streamJson(0.5f, 0.5f)
        val frames = renderBodyStream(liveBodyScene, "runtime.bodyState", stream, width = 400, height = 600)
        assertEquals(2, frames.size)
        assertTrue(frames[0].contentEquals(frames[1]), "identical body state must render identical pixels — the leaf is deterministic, motion comes only from the stream")
    }

    private fun assertFalse(condition: Boolean, message: String) = assertTrue(!condition, message)
}
