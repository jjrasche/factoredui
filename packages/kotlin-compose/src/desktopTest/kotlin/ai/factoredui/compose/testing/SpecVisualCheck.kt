package ai.factoredui.compose.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.SpecNode
import kotlin.test.assertTrue

// The visual-test substrate floor: render any spec headless, get a PNG + a node->region map, assert
// structure/order deterministically. Built on nodeTag (node->bounds) + captureToImage + RenderSpec.
@OptIn(ExperimentalTestApi::class)
class SpecVisualCheck(private val scope: ComposeUiTest, private val context: RenderContext = RenderContext()) {

    fun render(root: SpecNode, viewport: Dp = 300.dp) {
        scope.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(viewport)) { RenderSpec(root = root, context = context) }
            }
        }
        scope.waitForIdle()
    }

    fun png(): ImageBitmap = scope.onRoot().captureToImage()

    fun region(nodeId: String): DpRect = scope.onNodeWithTag(nodeId).getUnclippedBoundsInRoot()

    fun assertRenderedToPixels() {
        val image = png()
        assertTrue(image.width > 0 && image.height > 0, "spec must render to a non-empty raster")
    }

    fun assertPresent(nodeId: String) {
        scope.onNodeWithTag(nodeId).assertExists()
    }

    fun assertOccupiesRegion(nodeId: String) {
        val bounds = region(nodeId)
        assertTrue((bounds.right - bounds.left) > 0.dp && (bounds.bottom - bounds.top) > 0.dp,
            "node '$nodeId' must occupy a real rendered region")
    }

    fun assertAbove(upperId: String, lowerId: String) {
        assertTrue(region(upperId).top < region(lowerId).top,
            "node '$upperId' must render above '$lowerId'")
    }

    fun assertLeftOf(leftId: String, rightId: String) {
        assertTrue(region(leftId).left < region(rightId).left,
            "node '$leftId' must render left of '$rightId'")
    }
}
