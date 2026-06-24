package ai.factoredui.compose.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderSpecToPngTest {

    private val specJson = """
        {"spec_version":1,"renderer_min":1,"root":{"id":"root","type":"column","children":[
          {"id":"greeting","type":"text","props":{"value":"hello"}},
          {"id":"cta","type":"button","props":{"label":"Go"}}
        ]}}
    """.trimIndent()

    @Test
    fun rendersSpecJsonToNonEmptyPngBytes() {
        val png = renderSpecToPng(specJson, width = 300, height = 300)
        assertTrue(png.size > 64, "render must produce real PNG bytes, got ${png.size}")
    }

    @Test
    fun outputCarriesThePngSignature() {
        val png = renderSpecToPng(specJson, width = 200, height = 200)
        assertEquals(0x89.toByte(), png[0])
        assertEquals(0x50.toByte(), png[1])
        assertEquals(0x4E.toByte(), png[2])
        assertEquals(0x47.toByte(), png[3])
    }
}
