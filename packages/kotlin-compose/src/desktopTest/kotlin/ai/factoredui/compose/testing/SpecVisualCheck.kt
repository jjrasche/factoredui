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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.renderer.BindingResolver
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import kotlin.test.assertTrue

// One rendered spec node: its id/type, its resolved props (bindings applied), and the exact on-screen
// region the renderer drew it into. The deterministic node<->pixels mapping a spec makes possible.
data class SpecShadowNode(
    val id: String,
    val type: SpecNodeType,
    val props: Map<String, Any?>,
    val bounds: DpRect?,
)

// The visual-test substrate floor: render any spec headless, get a PNG + the ShadowTree (every node's
// id/type/resolved-props/region), assert structure/order. Built on nodeTag + captureToImage + RenderSpec.
@OptIn(ExperimentalTestApi::class)
class SpecVisualCheck(private val scope: ComposeUiTest, private val context: RenderContext = RenderContext()) {

    private var renderedRoot: SpecNode? = null

    fun render(root: SpecNode, viewport: Dp = 300.dp) {
        renderedRoot = root
        scope.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(viewport)) { RenderSpec(root = root, context = context) }
            }
        }
        scope.waitForIdle()
    }

    fun shadowTree(): List<SpecShadowNode> {
        val root = renderedRoot ?: error("call render() before shadowTree()")
        val data = context.dataFlow.value
        val nodes = mutableListOf<SpecShadowNode>()
        fun walk(node: SpecNode) {
            val bounds = runCatching { scope.onNodeWithTag(node.id).getUnclippedBoundsInRoot() }.getOrNull()
            nodes += SpecShadowNode(node.id, node.type, BindingResolver.resolveProps(node.props, data), bounds)
            node.children.forEach(::walk)
        }
        walk(root)
        return nodes
    }

    fun node(nodeId: String): SpecShadowNode =
        shadowTree().firstOrNull { it.id == nodeId } ?: error("node '$nodeId' not in the rendered spec")

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

    fun tap(nodeId: String) {
        scope.onNodeWithTag(nodeId).performClick()
        scope.waitForIdle()
    }

    fun binding(path: String): Any? {
        var current: Any? = context.data
        for (segment in path.split(".")) {
            current = (current as? Map<*, *>)?.get(segment) ?: return null
        }
        return current
    }
}
