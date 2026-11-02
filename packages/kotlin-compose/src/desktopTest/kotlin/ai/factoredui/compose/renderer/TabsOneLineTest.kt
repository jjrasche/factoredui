package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PHONE_WIDTH_DP = 390
private const val PHONE_HEIGHT_DP = 844
private const val LONGEST_LINE_DP = 30f

private fun tabsSpec(items: String, selected: Int): Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "state": { "tab": { "index": $selected } },
      "root": { "id": "root", "type": "column", "props": {}, "children": [
        { "id": "tabs", "type": "tabs", "props": { "items": $items, "selectedIndex": "{tab.index}" }, "children": [
          { "id": "c0", "type": "text", "props": { "value": "zero" } }, { "id": "c1", "type": "text", "props": { "value": "one" } },
          { "id": "c2", "type": "text", "props": { "value": "two" } }, { "id": "c3", "type": "text", "props": { "value": "three" } },
          { "id": "c4", "type": "text", "props": { "value": "four" } } ] } ] } }
    """,
)

private val FIVE = """["Serpentine", "Parallel", "Down-up", "Network", "Floor vents"]"""

class TabsOneLineTest {
    @Test
    fun five_long_labels_on_a_phone_each_stay_on_one_line() {
        driveScreen(tabsSpec(FIVE, 0), widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP).use { screen ->
            listOf("Serpentine", "Parallel", "Down-up", "Network").forEach { label ->
                val bounds = assertNotNull(screen.boundsOfText(label), "$label is not drawn whole")
                assertTrue(bounds.bottom.value - bounds.top.value < LONGEST_LINE_DP, "$label is ${bounds.bottom.value - bounds.top.value} dp tall: it wrapped")
            }
        }
    }

    @Test
    fun the_selected_tab_is_scrolled_into_view() {
        driveScreen(tabsSpec(FIVE, 4), widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP).use { screen ->
            val bounds = assertNotNull(screen.boundsOfText("Floor vents"))
            assertTrue(bounds.left.value >= 0f && bounds.right.value <= PHONE_WIDTH_DP, "Floor vents spans ${bounds.left.value}..${bounds.right.value} of $PHONE_WIDTH_DP")
        }
    }

    @Test
    fun three_short_labels_still_share_the_width_equally() {
        driveScreen(tabsSpec("""["A", "B", "C"]""", 0), widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP).use { screen ->
            val first = assertNotNull(screen.boundsOfText("A"))
            val last = assertNotNull(screen.boundsOfText("C"))
            assertTrue(last.left.value - first.left.value > PHONE_WIDTH_DP / 2f, "the tabs are bunched at one side: ${first.left.value}, ${last.left.value}")
        }
    }
}
