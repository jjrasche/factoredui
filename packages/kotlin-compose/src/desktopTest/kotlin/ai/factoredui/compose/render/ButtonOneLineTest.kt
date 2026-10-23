package ai.factoredui.compose.render

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val SPEC = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "root": { "id": "root", "type": "column", "props": {}, "children": [
      { "id": "chip", "type": "button", "props": { "label": "wall@2934,3771,1219 and a name that is much too long for the width", "variant": "outline", "size": "sm", "width": 168 } } ] } }
    """,
)

class ButtonOneLineTest {
    @Test
    fun a_button_label_too_long_for_its_width_stays_on_one_line() {
        driveScreen(SPEC, widthDp = 400, heightDp = 300).use { screen ->
            val chip = assertNotNull(screen.boundsOf("chip"), "the button is not drawn")
            assertTrue(chip.bottom.value - chip.top.value < 64f, "the button is ${chip.bottom.value - chip.top.value} dp tall")
            assertTrue(chip.right.value - chip.left.value <= 170f, "the button is ${chip.right.value - chip.left.value} dp wide")
        }
    }
}