package ai.factoredui.compose.render

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
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

    @Test
    fun seededDataChangesWhatABoundNodeDraws() {
        val bound = """
            {"spec_version":1,"renderer_min":1,"root":{"id":"root","type":"column","children":[
              {"id":"greeting","type":"text","props":{"value":"{word}","variant":"heading"}}
            ]}}
        """.trimIndent()
        val hello = renderSpecToPng(bound, width = 300, height = 120, data = mapOf("word" to "hello"))
        val goodbye = renderSpecToPng(bound, width = 300, height = 120, data = mapOf("word" to "goodbye world"))
        assertTrue(!hello.contentEquals(goodbye), "different data must draw different pixels")
    }

    @Test
    fun aDarkThemeRendersADarkGroundAndTheLightDefaultIsUnchanged() {
        val empty = """{"spec_version":1,"renderer_min":1,"root":{"id":"root","type":"column","props":{"flex":1},"children":[]}}"""
        fun cornerLuminance(png: ByteArray): Float {
            val pixel = org.jetbrains.skia.Image.makeFromEncoded(png).toComposeImageBitmap().toPixelMap()[2, 2]
            return 0.2126f * pixel.red + 0.7152f * pixel.green + 0.0722f * pixel.blue
        }
        val dark = renderSpecToPng(empty, width = 64, height = 64, density = 1f, theme = ai.factoredui.compose.renderer.SpecTheme.DARK)
        val light = renderSpecToPng(empty, width = 64, height = 64, density = 1f)
        assertTrue(cornerLuminance(dark) < 0.2f, "dark ground: ${cornerLuminance(dark)}")
        assertTrue(cornerLuminance(light) > 0.9f, "light stays the default: ${cornerLuminance(light)}")
    }
}
