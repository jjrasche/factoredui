package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DarkModeCheck {

    private fun luminance(color: Color) = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private fun Color.isNear(other: Color, tolerance: Float = 0.02f) =
        abs(red - other.red) < tolerance && abs(green - other.green) < tolerance && abs(blue - other.blue) < tolerance

    private fun themedColumn(themeValue: SpecValue) = SpecNode(
        id = "scope",
        type = SpecNodeType.COLUMN,
        props = mapOf("theme" to themeValue, "background" to SpecValue.StringValue("ground"), "flex" to SpecValue.NumberValue(1.0), "padding" to SpecValue.NumberValue(8.0)),
        children = listOf(
            SpecNode("words", SpecNodeType.TEXT, props = mapOf("value" to SpecValue.StringValue("MMMMMMMM"), "variant" to SpecValue.StringValue("heading"))),
        ),
    )

    private fun SpecVisualCheck.pixelCount(nodeId: String, matches: (Color) -> Boolean): Int {
        val region = region(nodeId)
        val image = png().toPixelMap()
        var count = 0
        for (x in region.left.value.toInt() until region.right.value.toInt()) {
            for (y in region.top.value.toInt() until region.bottom.value.toInt()) if (matches(image[x, y])) count++
        }
        return count
    }

    @Test
    fun aDarkThemePropPaintsTheDarkGroundAndALightOneTheLightGround() = runComposeUiTest {
        val dark = SpecVisualCheck(this, RenderContext())
        dark.render(themedColumn(SpecValue.StringValue("dark")), viewport = 200.dp)
        assertTrue(dark.png().toPixelMap()[190, 190].isNear(SpecTheme.DARK.ground), "dark ground")
    }

    @Test
    fun aLightThemePropPaintsTheLightGround() = runComposeUiTest {
        val light = SpecVisualCheck(this, RenderContext())
        light.render(themedColumn(SpecValue.StringValue("light")), viewport = 200.dp)
        assertTrue(light.png().toPixelMap()[190, 190].isNear(SpecTheme.LIGHT.ground), "light ground")
    }

    @Test
    fun textIsLightOnDarkAndDarkOnLight() = runComposeUiTest {
        val dark = SpecVisualCheck(this, RenderContext())
        dark.render(themedColumn(SpecValue.StringValue("dark")), viewport = 200.dp)
        assertTrue(dark.pixelCount("words") { luminance(it) > 0.7f && it.alpha > 0.5f } > 20, "light glyphs on the dark ground")
        val light = SpecVisualCheck(this, RenderContext())
        light.render(themedColumn(SpecValue.StringValue("light")), viewport = 200.dp)
        assertTrue(light.pixelCount("words") { luminance(it) < 0.3f && it.alpha > 0.5f } > 20, "dark glyphs on the light ground")
    }

    @Test
    fun theHostFlipsTheThemeByWritingTheBinding() = runComposeUiTest {
        val context = RenderContext(initialData = mapOf("theme" to "light"))
        val check = SpecVisualCheck(this, context)
        check.render(themedColumn(SpecValue.StringValue("{theme}")), viewport = 200.dp)
        assertTrue(check.png().toPixelMap()[190, 190].isNear(SpecTheme.LIGHT.ground), "starts light")
        context.setBinding("theme", "dark")
        waitForIdle()
        assertTrue(check.png().toPixelMap()[190, 190].isNear(SpecTheme.DARK.ground), "flips to dark")
        context.setBinding("theme", "light")
        waitForIdle()
        assertTrue(check.png().toPixelMap()[190, 190].isNear(SpecTheme.LIGHT.ground), "and back")
    }

    @Test
    fun anAbsentOrUnknownThemeLeavesTheHostThemeAlone() = runComposeUiTest {
        val check = SpecVisualCheck(this, RenderContext(theme = SpecTheme.DARK))
        check.render(themedColumn(SpecValue.StringValue("sepia")), viewport = 200.dp)
        assertTrue(check.png().toPixelMap()[190, 190].isNear(SpecTheme.DARK.ground), "unknown theme names inherit the host's theme")
    }

    @Test
    fun theSurfaceTokenIsAShadeApartFromTheGroundInBothThemes() {
        listOf(SpecTheme.LIGHT, SpecTheme.DARK).forEach { theme ->
            val ground = theme.tokenColor("ground")!!
            val surface = theme.tokenColor("surface")!!
            assertTrue(abs(luminance(ground) - luminance(surface)) in 0.02f..0.25f, "surface is a subtle step from ground")
        }
        assertEquals(null, SpecTheme.DARK.tokenColor("#112233"), "a hex colour is not a token")
    }

    @Test
    fun aMaterialControlGetsLightTextInDarkAndDarkTextInLight() = runComposeUiTest {
        fun themed(name: String) = SpecNode(
            id = "scope",
            type = SpecNodeType.COLUMN,
            props = mapOf("theme" to SpecValue.StringValue(name), "background" to SpecValue.StringValue("ground"), "flex" to SpecValue.NumberValue(1.0), "padding" to SpecValue.NumberValue(8.0)),
            children = listOf(
                SpecNode(
                    "pick", SpecNodeType.SELECT,
                    props = mapOf(
                        "value" to SpecValue.StringValue("a"),
                        "options" to SpecValue.ArrayValue(listOf(SpecValue.ObjectValue(mapOf("label" to SpecValue.StringValue("MMMMMM"), "value" to SpecValue.StringValue("a"))))),
                    ),
                ),
            ),
        )
        val dark = SpecVisualCheck(this, RenderContext())
        dark.render(themed("dark"), viewport = 240.dp)
        assertTrue(dark.pixelCount("pick") { luminance(it) > 0.55f && it.alpha > 0.5f } > 15, "the control's label reads light on dark")
    }
}
