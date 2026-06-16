package ai.factoredui.compose.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RenderLayoutAlignTest {

    private fun textNode(id: String, value: String) = SpecNode(
        id = id,
        type = SpecNodeType.TEXT,
        props = mapOf("value" to SpecValue.StringValue(value)),
    )

    private fun column(id: String, align: String, child: SpecNode) = SpecNode(
        id = id,
        type = SpecNodeType.COLUMN,
        props = mapOf("align" to SpecValue.StringValue(align)),
        children = listOf(child),
    )

    private fun row(id: String, justify: String, child: SpecNode) = SpecNode(
        id = id,
        type = SpecNodeType.ROW,
        props = mapOf("justify" to SpecValue.StringValue(justify)),
        children = listOf(child),
    )

    @Test
    fun columnEndAlignPushesChildRightOfStartAlign() = runComposeUiTest {
        val root = SpecNode(
            id = "root",
            type = SpecNodeType.COLUMN,
            children = listOf(
                column("startCol", "start", textNode("s", "leftbubble")),
                column("endCol", "end", textNode("e", "rightbubble")),
            ),
        )
        setContent { RenderSpec(root = root, context = RenderContext()) }
        waitForIdle()

        val startLeft = onNodeWithText("leftbubble").getUnclippedBoundsInRoot().left
        val endLeft = onNodeWithText("rightbubble").getUnclippedBoundsInRoot().left
        assertTrue(endLeft > startLeft, "end-aligned child should sit right of start-aligned ($endLeft !> $startLeft)")
    }

    @Test
    fun rowEndJustifyPushesChildRightOfStartJustify() = runComposeUiTest {
        val root = SpecNode(
            id = "root",
            type = SpecNodeType.COLUMN,
            children = listOf(
                row("startRow", "start", textNode("s", "leftitem")),
                row("endRow", "end", textNode("e", "rightitem")),
            ),
        )
        setContent { RenderSpec(root = root, context = RenderContext()) }
        waitForIdle()

        val startLeft = onNodeWithText("leftitem").getUnclippedBoundsInRoot().left
        val endLeft = onNodeWithText("rightitem").getUnclippedBoundsInRoot().left
        assertTrue(endLeft > startLeft, "end-justified child should sit right of start-justified ($endLeft !> $startLeft)")
    }
}
