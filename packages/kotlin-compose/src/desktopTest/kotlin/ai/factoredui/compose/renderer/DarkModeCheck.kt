package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
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

    private val cols = 5
    private val rows = 4

    private fun tilemap(themeValue: String) = SpecNode(
        id = "world",
        type = SpecNodeType.TILEMAP,
        props = mapOf(
            "theme" to SpecValue.StringValue(themeValue),
            "cols" to SpecValue.NumberValue(cols.toDouble()),
            "rows" to SpecValue.NumberValue(rows.toDouble()),
            "view" to SpecValue.StringValue("iso"),
            "uses" to SpecValue.StringValue("{uses}"),
            "cells" to SpecValue.StringValue("{cells}"),
            "selected_use" to SpecValue.StringValue("{brush}"),
        ),
    )

    private val uses = listOf(
        mapOf("id" to "tree", "label" to "Tree", "color" to "#1F5E2B", "sprite" to "tree"),
        mapOf("id" to "shed", "label" to "Shed", "color" to "#8B5A2B", "sprite" to "block"),
        mapOf("id" to "pond", "label" to "Pond", "color" to "#3B82C4", "sprite" to "water"),
    )

    private val cells = listOf(
        mapOf("col" to 2, "row" to 2, "use" to "tree"),
        mapOf("col" to 3, "row" to 1, "use" to "shed"),
        mapOf("col" to 1, "row" to 1, "use" to "pond"),
    )

    private fun darkWorld(check: SpecVisualCheck): PlacedTiles {
        check.render(tilemap("dark"), viewport = 500.dp)
        return PlacedTiles(check, TileShape.SQUARE, TileView.ISO, cols, rows)
    }

    private fun worldContext() = RenderContext(initialData = mapOf("uses" to uses, "cells" to cells, "brush" to "tree"))

    @Test
    fun theDarkGroundIsDarkAndTheGridStillReadsAgainstIt() = runComposeUiTest {
        val placed = darkWorld(SpecVisualCheck(this, worldContext()))
        val ground = luminance(placed.pixelAt(4, 3))
        assertTrue(ground < 0.3f, "an empty tile is dark: $ground")
    }

    @Test
    fun treesWaterAndBlocksStandOutFromTheDarkGround() = runComposeUiTest {
        val placed = darkWorld(SpecVisualCheck(this, worldContext()))
        val ground = luminance(placed.pixelAt(4, 3))
        val leaf = (-20..-6 step 2).maxOf { luminance(placed.pixelAt(2, 2, dy = it.toFloat())) }
        assertTrue(leaf > ground + 0.12f, "a tree's foliage is clearly lighter than the ground")
        assertTrue(luminance(placed.pixelAt(1, 1)) > ground + 0.12f, "water is clearly lighter than the ground")
        assertTrue(luminance(placed.pixelAt(3, 1, dy = -32f)) > ground + 0.15f, "a block roof is clearly lighter than the ground")
    }

    @Test
    fun aBlocksShadedSidesAreLighterThanTheGroundEdge() = runComposeUiTest {
        val placed = darkWorld(SpecVisualCheck(this, worldContext()))
        val ground = luminance(placed.pixelAt(4, 3))
        assertTrue(luminance(placed.pixelAt(3, 1, dx = -10f)) > ground + 0.08f, "the left wall stays lighter than the ground in dark: wall=${placed.pixelAt(3, 1, dx = -10f)} ground=$ground")
    }

    @Test
    fun theBrushPaletteLabelsAreLightInDark() = runComposeUiTest {
        val check = SpecVisualCheck(this, worldContext())
        darkWorld(check)
        assertTrue(check.pixelCount("world:brush:tree") { luminance(it) > 0.7f && it.alpha > 0.5f } > 10, "the chip label is light text")
    }

    @Test
    fun lightModeKeepsItsOriginalGround() = runComposeUiTest {
        val check = SpecVisualCheck(this, worldContext())
        check.render(tilemap("light"), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.ISO, cols, rows)
        assertTrue(placed.pixelAt(4, 3).isNear(Color(0xFFCFE0A8), 0.03f) || placed.pixelAt(4, 3).isNear(Color(0xFFC3D79B), 0.03f), "light ground is the original green")
    }
}
