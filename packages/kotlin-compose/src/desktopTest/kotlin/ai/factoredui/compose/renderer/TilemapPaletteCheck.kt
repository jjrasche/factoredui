package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TilemapPaletteCheck {

    private fun tilemap(palette: String? = null) = SpecNode(
        id = "world",
        type = SpecNodeType.TILEMAP,
        props = buildMap {
            put("cols", SpecValue.NumberValue(5.0))
            put("rows", SpecValue.NumberValue(4.0))
            put("uses", SpecValue.StringValue("{uses}"))
            put("selected_use", SpecValue.StringValue("{brush}"))
            put("brush_label", SpecValue.StringValue("{brush_label}"))
            if (palette != null) put("palette", SpecValue.StringValue(palette))
        },
    )

    private val uses = listOf(
        mapOf("id" to "path", "label" to "Path", "color" to "#D2B48C", "sprite" to "flat"),
        mapOf("id" to "pond", "label" to "Pond", "color" to "#3B82C4", "sprite" to "water"),
    )

    private fun contextOf() = RenderContext(initialData = mapOf("uses" to uses, "brush" to "path", "brush_label" to ""))

    @Test
    fun theSelectedBrushIsNamedByItsLabelNotItsId() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf())
        check.render(tilemap(), viewport = 400.dp)
        assertEquals("Path", check.binding("brush_label"))
    }

    @Test
    fun pickingAnotherBrushRenamesIt() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf())
        check.render(tilemap(), viewport = 400.dp)
        check.tap("world:brush:pond")
        assertEquals("Pond", check.binding("brush_label"))
        check.tap("world:brush:erase")
        assertEquals("erase", check.binding("brush_label"))
    }

    @Test
    fun theTopPaletteSitsAboveTheMapAndTheDefaultPaletteBelowIt() = runComposeUiTest {
        val top = SpecVisualCheck(this, contextOf())
        top.render(tilemap(palette = "top"), viewport = 400.dp)
        assertTrue(top.region("world:brush:path").top < top.region("world:map").top, "palette above the map")
        val bottom = SpecVisualCheck(this, contextOf())
        bottom.render(tilemap(), viewport = 400.dp)
        assertTrue(bottom.region("world:brush:path").top > bottom.region("world:map").top, "palette below the map by default")
    }
}
