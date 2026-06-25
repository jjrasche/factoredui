package ai.factoredui.compose.render

import ai.factoredui.compose.scene3d.inlineBodyFramesOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineBodyFrameRenderTest {

    private val goldenBodyFrame = """
        {"frames":[{"root_x":0.5,"root_y":0.0,"root_z":0.0,"joints":[
          [0.0,0.0,0.95],[0.1,0.0,0.9],[0.1,0.0,0.5],[0.1,0.0,0.08],[0.1,-0.12,0.04],
          [-0.1,0.0,0.9],[-0.1,0.0,0.5],[-0.1,0.0,0.08],[-0.1,-0.12,0.04],
          [0.0,0.0,1.1],[0.0,0.0,1.25],[0.0,0.0,1.4],[0.0,0.0,1.5],[0.0,0.0,1.65],
          [0.05,0.0,1.42],[0.18,0.0,1.42],[0.3,0.0,1.2],[0.32,0.0,0.98],
          [-0.05,0.0,1.42],[-0.18,0.0,1.42],[-0.3,0.0,1.2],[-0.32,0.0,0.98],
          [0.33,0.0,0.9],[-0.33,0.0,0.9]]}],"playhead":0}
    """.trimIndent()

    private fun inlineBodySpec(bodyFrame: String): String = """
        {"spec_version":1,"renderer_min":1,"root":{"id":"scene","type":"scene3d","props":{
          "body_frame": ${kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(bodyFrame))}
        }}}
    """.trimIndent()

    @Test
    fun inlineBodyFramesParseFromTheGoldenJson() {
        val response = inlineBodyFramesOrNull(goldenBodyFrame)
        assertTrue(response != null, "the golden body_frame must parse to a BodyFramesResponse")
        assertEquals(1, response.frames.size)
        assertEquals(24, response.frames.first().joints.size)
        assertEquals(0.5f, response.frames.first().rootX)
    }

    @Test
    fun inlineBodyFramesReturnsNullForGarbageNotAnException() {
        assertEquals(null, inlineBodyFramesOrNull("not json"))
        assertEquals(null, inlineBodyFramesOrNull(""))
    }

    @Test
    fun aSpecWithInlineBodyFrameRendersHermeticallyToPng() {
        val png = renderSpecToPng(inlineBodySpec(goldenBodyFrame), width = 400, height = 600)
        assertTrue(png.size > 256, "inline-body scene3d must render to a real PNG with no network (got ${png.size} bytes)")
    }
}
