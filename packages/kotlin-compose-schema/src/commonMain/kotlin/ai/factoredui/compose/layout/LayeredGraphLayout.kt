package ai.factoredui.compose.layout

data class LayoutPosition(val x: Float, val y: Float)

fun layeredGraphLayout(
    nodes: List<String>,
    edges: List<Pair<String, String>>,
    width: Float,
    height: Float,
): Map<String, LayoutPosition> {
    val layerOf = assignLayers(nodes, edges)
    return spreadPositions(layerOf, width, height)
}

private fun assignLayers(nodes: List<String>, edges: List<Pair<String, String>>): Map<String, Int> {
    val children = nodes.associateWith { mutableListOf<String>() }
    val indegree = nodes.associateWith { 0 }.toMutableMap()
    for ((from, to) in edges) {
        if (from !in children || to !in indegree) continue
        children.getValue(from).add(to)
        indegree[to] = indegree.getValue(to) + 1
    }
    val layer = nodes.associateWith { 0 }.toMutableMap()
    val ready = ArrayDeque(nodes.filter { indegree.getValue(it) == 0 })
    var settled = 0
    while (ready.isNotEmpty()) {
        val parent = ready.removeFirst()
        settled++
        for (child in children.getValue(parent)) {
            layer[child] = maxOf(layer.getValue(child), layer.getValue(parent) + 1)
            indegree[child] = indegree.getValue(child) - 1
            if (indegree.getValue(child) == 0) ready.addLast(child)
        }
    }
    if (settled < nodes.size) {
        val deepest = layer.values.maxOrNull() ?: 0
        nodes.filter { indegree.getValue(it) > 0 }.forEach { layer[it] = deepest + 1 }
    }
    return layer
}

private fun spreadPositions(layerOf: Map<String, Int>, width: Float, height: Float): Map<String, LayoutPosition> {
    val byLayer = layerOf.entries.groupBy({ it.value }, { it.key })
    val layerCount = (byLayer.keys.maxOrNull() ?: 0) + 1
    return byLayer.flatMap { (layerIndex, idsInLayer) ->
        val y = rowCenter(layerIndex, layerCount, height)
        idsInLayer.sorted().mapIndexed { column, id ->
            id to LayoutPosition(x = rowCenter(column, idsInLayer.size, width), y = y)
        }
    }.toMap()
}

private fun rowCenter(index: Int, count: Int, extent: Float): Float =
    extent * (index + 1) / (count + 1)
