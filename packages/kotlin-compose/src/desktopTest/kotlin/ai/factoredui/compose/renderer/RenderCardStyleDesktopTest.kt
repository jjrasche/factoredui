package ai.factoredui.compose.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RenderCardStyleDesktopTest {

    @Test
    fun cardPaintsBoundBackgroundColor() = runComposeUiTest {
        val card = SpecNode(
            id = "bubble",
            type = SpecNodeType.CARD,
            props = mapOf(
                "background" to SpecValue.StringValue("#6C5CE7"),
                "cornerRadius" to SpecValue.NumberValue(16.0),
                "padding" to SpecValue.NumberValue(12.0),
            ),
            children = listOf(
                SpecNode(
                    id = "t",
                    type = SpecNodeType.TEXT,
                    props = mapOf("value" to SpecValue.StringValue("hi")),
                ),
            ),
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(200.dp)) {
                    RenderSpec(root = card, context = RenderContext())
                }
            }
        }
        waitForIdle()

        val pixels = onRoot().captureToImage().toPixelMap()
        val sample = pixels[150, 20]
        val expectedR = 0x6C / 255f
        val expectedG = 0x5C / 255f
        val expectedB = 0xE7 / 255f
        val off = abs(sample.red - expectedR) + abs(sample.green - expectedG) + abs(sample.blue - expectedB)
        assertTrue(off < 0.05f, "card background pixel should match #6C5CE7, got r=${sample.red} g=${sample.green} b=${sample.blue}")
    }

    @Test
    fun cardCapsWidthAtMaxWidthFractionOfParent() = runComposeUiTest {
        val card = SpecNode(
            id = "bubble",
            type = SpecNodeType.CARD,
            props = mapOf("maxWidthFraction" to SpecValue.NumberValue(0.5)),
            children = listOf(
                SpecNode(
                    id = "t",
                    type = SpecNodeType.TEXT,
                    props = mapOf("value" to SpecValue.StringValue("a chat message long enough to overflow half the parent width easily")),
                ),
            ),
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(200.dp)) {
                    RenderSpec(root = card, context = RenderContext())
                }
            }
        }
        waitForIdle()

        val bounds = onNodeWithTag("bubble").getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue(width <= 104.dp, "card should cap at ~50% of 200dp parent, got $width")
        assertTrue(width > 60.dp, "long content should fill the bubble up toward the cap, got $width")
    }
}
