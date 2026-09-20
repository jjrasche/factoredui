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
    val (_, readsByNode) = PropReads.record {
        renderScreen(spec, store, viewportWidthDp, viewportHeightDp, theme)
    }
    val unconsumed = mutableListOf<UnconsumedProp>()
    fun walk(node: SpecNode) {
        val read = readsByNode[node.id].orEmpty()
        node.props.keys.filterNot { it in read }.forEach { key ->
            unconsumed += UnconsumedProp(node.id, node.type, key)
        }
        node.children.forEach(::walk)
    }
    walk(spec.root)
    return unconsumed
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
