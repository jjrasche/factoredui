package ai.factoredui.compose.crawl

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.testing.SpecShadowNode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat

/**
 * One headless render of a spec: the shadow tree (every spec node with the region the renderer
 * drew it into) plus the PNG of that same frame. Same node-to-pixels mapping SpecVisualCheck
 * offers inside ComposeUiTest, available to a plain `main` so the crawler is not a test.
 */
class RenderedScreen(
    val nodes: List<SpecShadowNode>,
    val png: ByteArray,
    val viewport: DpRect,
) {
    fun visibleNodes(): List<SpecShadowNode> = nodes.filter { it.bounds != null }
}

/**
 * Render [spec] against [store] at a density of 1, so one dp is one pixel and every measured
 * region is directly comparable to the dp-stated layout floors the grader applies.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun renderScreen(spec: Spec, store: Map<String, Any?>, viewportWidthDp: Int, viewportHeightDp: Int): RenderedScreen {
    val context = RenderContext(initialData = store)
    val scene = ImageComposeScene(width = viewportWidthDp, height = viewportHeightDp, density = Density(1f)) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            Box(Modifier.fillMaxSize()) { RenderSpec(spec = spec, context = context) }
        }
    }
    try {
        val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
        val regions = collectTaggedRegions(scene)
        return RenderedScreen(
            nodes = shadowNodes(spec.root, store, regions),
            png = png,
            viewport = DpRect(0.dp, 0.dp, viewportWidthDp.dp, viewportHeightDp.dp),
        )
    } finally {
        scene.close()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun collectTaggedRegions(scene: ImageComposeScene): Map<String, DpRect> {
    val regions = mutableMapOf<String, DpRect>()
    scene.semanticsOwners.forEach { owner -> gatherTaggedRegions(owner.unmergedRootSemanticsNode, regions) }
    return regions
}

private fun gatherTaggedRegions(node: SemanticsNode, into: MutableMap<String, DpRect>) {
    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
    if (tag != null && tag !in into) into[tag] = node.unclippedBoundsInRoot()
    node.children.forEach { child -> gatherTaggedRegions(child, into) }
}

private fun shadowNodes(
    root: SpecNode,
    store: Map<String, Any?>,
    regions: Map<String, DpRect>,
): List<SpecShadowNode> {
    val nodes = mutableListOf<SpecShadowNode>()
    fun walk(node: SpecNode) {
        nodes += SpecShadowNode(node.id, node.type, BindingResolver.resolveProps(node.props, store), regions[node.id])
        node.children.forEach(::walk)
    }
    walk(root)
    return nodes
}

/**
 * The region the node was laid out into, NOT clipped to its ancestors — `boundsInRoot` clips, which
 * would silently hide the very case the offscreen check exists to catch.
 */
private fun SemanticsNode.unclippedBoundsInRoot(): DpRect {
    val origin = positionInRoot
    return DpRect(
        left = origin.x.dp,
        top = origin.y.dp,
        right = (origin.x + size.width).dp,
        bottom = (origin.y + size.height).dp,
    )
}
