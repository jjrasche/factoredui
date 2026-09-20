package ai.factoredui.compose.crawl

import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.testing.SpecShadowNode
import androidx.compose.ui.unit.DpRect

fun undrawnNodes(
    spec: Spec,
    store: Map<String, Any?>,
    viewportWidthDp: Int,
    viewportHeightDp: Int,
): List<SpecShadowNode> {
    val screen = renderScreen(spec, store, viewportWidthDp, viewportHeightDp)
    val asked = nodesTheSpecAsksFor(spec.root, store)
    return screen.nodes
        .filter { it.id in asked }
        .filterNot { it.bounds.paintsPixelsInside(screen.viewport) }
}

fun assertEverySpecNodeDrew(
    spec: Spec,
    store: Map<String, Any?> = emptyMap(),
    viewportWidthDp: Int = 400,
    viewportHeightDp: Int = 800,
    expectedUndrawn: Set<String> = emptySet(),
) {
    val undrawn = undrawnNodes(spec, store, viewportWidthDp, viewportHeightDp)
        .filterNot { it.id in expectedUndrawn }
    if (undrawn.isEmpty()) return
    val named = undrawn.joinToString("\n") { node -> "  ${node.id} (${node.type}) drew ${node.bounds.describe()}" }
    throw AssertionError(
        "${undrawn.size} node(s) the spec declares painted no pixels in " +
            "${viewportWidthDp}x${viewportHeightDp}dp:\n$named",
    )
}

private fun nodesTheSpecAsksFor(root: SpecNode, store: Map<String, Any?>): Set<String> {
    val asked = mutableSetOf<String>()
    fun walk(node: SpecNode) {
        if (!BindingResolver.isVisible(node.visible, store)) return
        asked += node.id
        node.children.forEach(::walk)
    }
    walk(root)
    return asked
}

private fun DpRect?.paintsPixelsInside(viewport: DpRect): Boolean {
    val bounds = this ?: return false
    if (bounds.right <= bounds.left || bounds.bottom <= bounds.top) return false
    return bounds.left < viewport.right && bounds.right > viewport.left &&
        bounds.top < viewport.bottom && bounds.bottom > viewport.top
}

private fun DpRect?.describe(): String = when {
    this == null -> "no region at all"
    right <= left || bottom <= top -> "an empty region at (${left.value}, ${top.value})"
    else -> "entirely outside the viewport at (${left.value}, ${top.value})"
}
