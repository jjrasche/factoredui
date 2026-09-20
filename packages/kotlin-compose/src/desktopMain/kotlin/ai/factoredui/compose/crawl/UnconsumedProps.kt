package ai.factoredui.compose.crawl

import ai.factoredui.compose.renderer.PropReads
import ai.factoredui.compose.renderer.SpecTheme
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType

data class UnconsumedProp(val nodeId: String, val type: SpecNodeType, val key: String)

fun unconsumedProps(
    spec: Spec,
    store: Map<String, Any?> = emptyMap(),
    viewportWidthDp: Int = 400,
    viewportHeightDp: Int = 800,
    theme: SpecTheme = SpecTheme.LIGHT,
): List<UnconsumedProp> {
    val (_, log) = PropReads.record {
        renderScreen(spec, store, viewportWidthDp, viewportHeightDp, theme)
    }
    val unconsumed = mutableListOf<UnconsumedProp>()
    fun walk(node: SpecNode) {
        if (node.id in log.visited) {
            val read = log.reads[node.id].orEmpty()
            node.props.keys.filterNot { it in read }.forEach { key ->
                unconsumed += UnconsumedProp(node.id, node.type, key)
            }
        }
        node.children.forEach(::walk)
    }
    walk(spec.root)
    return unconsumed
}

data class UnreachedNode(val id: String, val type: SpecNodeType)

fun unreachedNodes(
    spec: Spec,
    store: Map<String, Any?> = emptyMap(),
    viewportWidthDp: Int = 400,
    viewportHeightDp: Int = 800,
    theme: SpecTheme = SpecTheme.LIGHT,
): List<UnreachedNode> {
    val (_, log) = PropReads.record {
        renderScreen(spec, store, viewportWidthDp, viewportHeightDp, theme)
    }
    val unreached = mutableListOf<UnreachedNode>()
    fun walk(node: SpecNode) {
        if (node.id !in log.visited) unreached += UnreachedNode(node.id, node.type)
        node.children.forEach(::walk)
    }
    walk(spec.root)
    return unreached
}

fun assertEveryPropWasRead(
    spec: Spec,
    store: Map<String, Any?> = emptyMap(),
    viewportWidthDp: Int = 400,
    viewportHeightDp: Int = 800,
    theme: SpecTheme = SpecTheme.LIGHT,
    expectedUnread: Set<String> = emptySet(),
) {
    val ignored = unconsumedProps(spec, store, viewportWidthDp, viewportHeightDp, theme)
        .filterNot { "${it.nodeId}.${it.key}" in expectedUnread }
    if (ignored.isEmpty()) return
    val named = ignored.joinToString("\n") { "  ${it.nodeId} (${it.type}) declared '${it.key}' and the renderer never read it" }
    throw AssertionError("${ignored.size} prop(s) the spec declares reach nothing:\n$named")
}
