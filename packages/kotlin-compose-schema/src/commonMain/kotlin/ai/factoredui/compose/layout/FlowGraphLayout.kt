package ai.factoredui.compose.layout

data class FlowNodeSpec(
    val id: String,
    val width: Float,
    val height: Float,
    val group: String? = null,
    val rank: Int? = null,
    val pinnedX: Float? = null,
    val pinnedY: Float? = null,
)

data class FlowEdgeSpec(val from: String, val to: String)

data class FlowPoint(val x: Float, val y: Float)

data class FlowNodeBox(val id: String, val x: Float, val y: Float, val width: Float, val height: Float)

data class FlowEdgeRoute(val from: String, val to: String, val points: List<FlowPoint>, val isBackEdge: Boolean)

data class FlowLane(val group: String?, val top: Float, val height: Float)

data class FlowLayout(
    val nodes: Map<String, FlowNodeBox>,
    val edges: List<FlowEdgeRoute>,
    val lanes: List<FlowLane>,
    val width: Float,
    val height: Float,
)

private const val COLUMN_GAP = 72f
private const val ROW_GAP = 14f
private const val LANE_PADDING = 12f
private const val LANE_GAP = 8f
private const val WAYPOINT_HEIGHT = 6f
private const val EXTENT_MARGIN = 16f
private const val ORDERING_SWEEPS = 12
private const val RELAXATION_SWEEPS = 8
private const val PORT_INSET = 4f
private const val CONSUMER_VOTE = 2
private const val PRODUCER_VOTE = 1
private const val STRIP_GAP = 12f
private const val STRIP_SEPARATION = 18f
private const val MIN_STRIP_WIDTH = 360f

private class OrientedEdge(val from: String, val to: String, val isBackEdge: Boolean)

private class ChainItem(val id: String, val lane: Int, val height: Float, val width: Float, val isWaypoint: Boolean)

private class EdgeChain(val edge: OrientedEdge, val waypointIds: List<String>)

private class Adjacency(val up: Map<String, List<String>>, val down: Map<String, List<String>>)

private class Ordering(val layers: List<List<String>>, val adjacency: Adjacency)

fun layoutFlowGraph(
    nodes: List<FlowNodeSpec>,
    edges: List<FlowEdgeSpec>,
    groupOrder: List<String> = emptyList(),
    gutter: Float = 0f,
    compactSources: Boolean = true,
): FlowLayout {
    val specs = nodes.distinctBy { it.id }
    if (specs.isEmpty()) return FlowLayout(emptyMap(), emptyList(), emptyList(), 0f, 0f)
    val routable = routableEdges(specs, edges)
    val (parked, flowing) = partitionParked(specs, routable)
    val oriented = orientAcyclic(flowing.map { it.id }, routable)
    val rawLayers = assignLayers(flowing, oriented)
    val layerOf = compactLayers(if (compactSources) pullSourcesRight(flowing, oriented, rawLayers) else rawLayers)
    val groupOf = inheritGroups(specs, routable, groupOrder)
    val groups = orderGroups(specs.map { groupOf.getValue(it.id) }, groupOrder)
    val laneOf = specs.associate { it.id to groups.indexOf(groupOf.getValue(it.id)) }
    val items = HashMap<String, ChainItem>()
    specs.forEach { items[it.id] = ChainItem(it.id, laneOf.getValue(it.id), it.height, it.width, isWaypoint = false) }
    val chains = oriented.mapIndexed { index, edge -> buildChain(index, edge, layerOf, laneOf, items) }
    val ordering = orderWithinLayers(flowing, chains, items, layerOf)
    val placement = placeItems(ordering, items, groups.size, gutter, parked.map { items.getValue(it.id) })
    val boxes = pinNodes(specs, placement.boxes)
    return FlowLayout(
        nodes = boxes,
        edges = spreadPorts(chains.map { routeChain(it, boxes, placement.waypoints) }, boxes),
        lanes = placement.lanes.mapIndexed { index, lane -> FlowLane(groups[index], lane.first, lane.second) },
        width = placement.width,
        height = placement.height,
    )
}

private fun partitionParked(specs: List<FlowNodeSpec>, edges: List<FlowEdgeSpec>): Pair<List<FlowNodeSpec>, List<FlowNodeSpec>> {
    val connected = edges.flatMap { listOf(it.from, it.to) }.toSet()
    return specs.partition { it.id !in connected && it.rank == null }
}

private fun inheritGroups(specs: List<FlowNodeSpec>, edges: List<FlowEdgeSpec>, groupOrder: List<String>): Map<String, String?> {
    val groupOf = specs.associate { it.id to it.group }.toMutableMap()
    val tieOrder = orderGroups(specs.mapNotNull { it.group }, groupOrder)
    do {
        val adopted = groupOf.filterValues { it == null }.keys.mapNotNull { id ->
            neighbourGroupVotes(id, edges, groupOf).maxWithOrNull(compareBy({ it.value }, { -tieOrder.indexOf(it.key) }))?.let { id to it.key }
        }
        adopted.forEach { (id, group) -> groupOf[id] = group }
    } while (adopted.isNotEmpty())
    return groupOf
}

private fun neighbourGroupVotes(id: String, edges: List<FlowEdgeSpec>, groupOf: Map<String, String?>): Map<String, Int> {
    val votes = HashMap<String, Int>()
    edges.forEach { edge ->
        if (edge.from == id) groupOf[edge.to]?.let { votes[it] = (votes[it] ?: 0) + CONSUMER_VOTE }
        if (edge.to == id) groupOf[edge.from]?.let { votes[it] = (votes[it] ?: 0) + PRODUCER_VOTE }
    }
    return votes
}

private fun routableEdges(specs: List<FlowNodeSpec>, edges: List<FlowEdgeSpec>): List<FlowEdgeSpec> {
    val known = specs.map { it.id }.toSet()
    return edges.filter { it.from != it.to && it.from in known && it.to in known }.distinct()
}

private fun orientAcyclic(nodeIds: List<String>, edges: List<FlowEdgeSpec>): List<OrientedEdge> {
    val outgoing = nodeIds.associateWith { mutableListOf<FlowEdgeSpec>() }
    edges.forEach { outgoing.getValue(it.from).add(it) }
    val backEdges = findBackEdges(nodeIds, outgoing)
    return edges.map { edge ->
        if (edge in backEdges) OrientedEdge(edge.to, edge.from, isBackEdge = true) else OrientedEdge(edge.from, edge.to, isBackEdge = false)
    }
}

private fun findBackEdges(nodeIds: List<String>, outgoing: Map<String, List<FlowEdgeSpec>>): Set<FlowEdgeSpec> {
    val onStack = HashSet<String>()
    val finished = HashSet<String>()
    val backEdges = HashSet<FlowEdgeSpec>()
    for (root in nodeIds) {
        if (root in finished) continue
        val stack = ArrayDeque<Pair<String, Int>>()
        stack.addLast(root to 0)
        onStack.add(root)
        while (stack.isNotEmpty()) {
            val (node, nextIndex) = stack.removeLast()
            val edgesOut = outgoing.getValue(node)
            if (nextIndex >= edgesOut.size) {
                onStack.remove(node)
                finished.add(node)
                continue
            }
            stack.addLast(node to nextIndex + 1)
            val edge = edgesOut[nextIndex]
            when {
                edge.to in onStack -> backEdges.add(edge)
                edge.to !in finished -> {
                    onStack.add(edge.to)
                    stack.addLast(edge.to to 0)
                }
            }
        }
    }
    return backEdges
}

private fun assignLayers(specs: List<FlowNodeSpec>, edges: List<OrientedEdge>): Map<String, Int> {
    val outgoing = specs.associate { it.id to mutableListOf<String>() }
    val unmetDependencies = specs.associate { it.id to 0 }.toMutableMap()
    edges.forEach {
        outgoing.getValue(it.from).add(it.to)
        unmetDependencies[it.to] = unmetDependencies.getValue(it.to) + 1
    }
    val layer = specs.associate { it.id to (it.rank ?: 0) }.toMutableMap()
    val ready = ArrayDeque(specs.map { it.id }.filter { unmetDependencies.getValue(it) == 0 })
    while (ready.isNotEmpty()) {
        val node = ready.removeFirst()
        for (next in outgoing.getValue(node)) {
            layer[next] = maxOf(layer.getValue(next), layer.getValue(node) + 1)
            unmetDependencies[next] = unmetDependencies.getValue(next) - 1
            if (unmetDependencies.getValue(next) == 0) ready.addLast(next)
        }
    }
    return layer
}

private fun pullSourcesRight(specs: List<FlowNodeSpec>, edges: List<OrientedEdge>, layerOf: Map<String, Int>): Map<String, Int> {
    val hasDependency = edges.map { it.to }.toSet()
    val consumers = edges.groupBy({ it.from }, { it.to })
    val hinted = specs.filter { it.rank != null }.map { it.id }.toSet()
    return layerOf.mapValues { (id, layer) ->
        val nearest = consumers[id].orEmpty().minOfOrNull { layerOf.getValue(it) }
        if (id in hasDependency || id in hinted || nearest == null) layer else maxOf(layer, nearest - 1)
    }
}

private fun compactLayers(layerOf: Map<String, Int>): Map<String, Int> {
    val dense = layerOf.values.distinct().sorted().withIndex().associate { it.value to it.index }
    return layerOf.mapValues { dense.getValue(it.value) }
}

private fun orderGroups(nodeGroups: List<String?>, groupOrder: List<String>): List<String?> {
    val present = nodeGroups.distinct()
    val declared = groupOrder.filter { it in present }
    return declared + present.filter { it !in declared }
}

private fun buildChain(
    index: Int,
    edge: OrientedEdge,
    layerOf: Map<String, Int>,
    laneOf: Map<String, Int>,
    items: MutableMap<String, ChainItem>,
): EdgeChain {
    val fromLayer = layerOf.getValue(edge.from)
    val toLayer = layerOf.getValue(edge.to)
    val waypointIds = (fromLayer + 1 until toLayer).map { layer ->
        val id = "~edge$index@$layer"
        val nearerSource = layer - fromLayer <= toLayer - layer
        val lane = laneOf.getValue(if (nearerSource) edge.from else edge.to)
        items[id] = ChainItem(id, lane, WAYPOINT_HEIGHT, 0f, isWaypoint = true)
        id
    }
    return EdgeChain(edge, waypointIds)
}

private fun orderWithinLayers(
    specs: List<FlowNodeSpec>,
    chains: List<EdgeChain>,
    items: Map<String, ChainItem>,
    layerOf: Map<String, Int>,
): Ordering {
    val layerCount = (layerOf.values.maxOrNull() ?: 0) + 1
    val layerOfItem = HashMap(layerOf)
    chains.forEach { chain -> chain.waypointIds.forEachIndexed { step, id -> layerOfItem[id] = layerOf.getValue(chain.edge.from) + 1 + step } }
    val up = HashMap<String, MutableList<String>>()
    val down = HashMap<String, MutableList<String>>()
    chains.forEach { chain ->
        val path = listOf(chain.edge.from) + chain.waypointIds + chain.edge.to
        path.zipWithNext().forEach { (upper, lower) ->
            down.getOrPut(upper) { mutableListOf() }.add(lower)
            up.getOrPut(lower) { mutableListOf() }.add(upper)
        }
    }
    val creationIndex = (specs.map { it.id } + chains.flatMap { it.waypointIds }).withIndex().associate { it.value to it.index }
    var layers: List<List<String>> = (0 until layerCount).map { layer ->
        creationIndex.keys.filter { layerOfItem.getValue(it) == layer }
            .sortedWith(compareBy({ items.getValue(it).lane }, { creationIndex.getValue(it) }))
    }
    repeat(ORDERING_SWEEPS) {
        layers = sweepLayers(layers, up, items, downward = true)
        layers = sweepLayers(layers, down, items, downward = false)
    }
    return Ordering(layers, Adjacency(up, down))
}

private fun sweepLayers(
    layers: List<List<String>>,
    neighbors: Map<String, List<String>>,
    items: Map<String, ChainItem>,
    downward: Boolean,
): List<List<String>> {
    val ordered = layers.toMutableList()
    val sequence = if (downward) 1 until ordered.size else (ordered.size - 2) downTo 0
    for (layer in sequence) {
        val referenceLayer = if (downward) layer - 1 else layer + 1
        val position = ordered[referenceLayer].withIndex().associate { it.value to it.index.toDouble() }
        val current = ordered[layer].withIndex().associate { it.value to it.index }
        ordered[layer] = ordered[layer].sortedWith(
            compareBy<String>({ items.getValue(it).lane }, { barycenter(it, neighbors, position, current) }, { current.getValue(it) }),
        )
    }
    return ordered
}

private fun barycenter(id: String, neighbors: Map<String, List<String>>, position: Map<String, Double>, current: Map<String, Int>): Double {
    val reachable = neighbors[id].orEmpty().mapNotNull { position[it] }
    return if (reachable.isEmpty()) current.getValue(id).toDouble() else reachable.average()
}

private class Placement(
    val boxes: Map<String, FlowNodeBox>,
    val waypoints: Map<String, FlowPoint>,
    val lanes: List<Pair<Float, Float>>,
    val width: Float,
    val height: Float,
)

private class Strip(val offsets: List<Triple<String, Float, Float>>, val width: Float, val height: Float)

private fun packStrip(parked: List<ChainItem>, availableWidth: Float): Strip {
    if (parked.isEmpty()) return Strip(emptyList(), 0f, 0f)
    val offsets = ArrayList<Triple<String, Float, Float>>()
    var x = 0f
    var y = 0f
    var rowHeight = 0f
    var widest = 0f
    for (item in parked) {
        if (x > 0f && x + item.width > availableWidth) {
            y += rowHeight + ROW_GAP
            x = 0f
            rowHeight = 0f
        }
        offsets.add(Triple(item.id, x, y))
        widest = maxOf(widest, x + item.width)
        x += item.width + STRIP_GAP
        rowHeight = maxOf(rowHeight, item.height)
    }
    return Strip(offsets, widest, y + rowHeight)
}

private fun placeItems(ordering: Ordering, items: Map<String, ChainItem>, laneCount: Int, gutter: Float, parked: List<ChainItem>): Placement {
    val layers = ordering.layers
    val columnWidth = layers.map { layer -> layer.maxOfOrNull { items.getValue(it).width } ?: 0f }
    val columnLeft = columnLefts(columnWidth, gutter)
    val flowLeft = columnLeft.first()
    val flowRight = columnLeft.last() + columnWidth.last()
    val flowContent = laneContentHeights(layers, items, laneCount)
    val strips = (0 until laneCount).map { lane -> packStrip(parked.filter { it.lane == lane }, maxOf(flowRight - flowLeft, MIN_STRIP_WIDTH)) }
    val stripOffsetY = flowContent.map { if (it > 0f) it + STRIP_SEPARATION else 0f }
    val laneContent = flowContent.indices.map { lane ->
        if (strips[lane].offsets.isEmpty()) flowContent[lane] else stripOffsetY[lane] + strips[lane].height
    }
    val laneTops = laneTops(laneContent)
    val topOf = relaxVertically(ordering, items, laneCount, laneTops, flowContent)
    val boxes = HashMap<String, FlowNodeBox>()
    val waypoints = HashMap<String, FlowPoint>()
    layers.forEachIndexed { layerIndex, layer ->
        val centerX = columnLeft[layerIndex] + columnWidth[layerIndex] / 2f
        for (id in layer) {
            val item = items.getValue(id)
            val top = topOf.getValue(id)
            if (item.isWaypoint) {
                waypoints[id] = FlowPoint(centerX, top + item.height / 2f)
            } else {
                boxes[id] = FlowNodeBox(id, centerX - item.width / 2f, top, item.width, item.height)
            }
        }
    }
    strips.forEachIndexed { lane, strip ->
        val baseY = laneTops[lane] + LANE_PADDING + stripOffsetY[lane]
        for ((id, dx, dy) in strip.offsets) {
            val item = items.getValue(id)
            boxes[id] = FlowNodeBox(id, flowLeft + dx, baseY + dy, item.width, item.height)
        }
    }
    val right = maxOf(flowRight, flowLeft + (strips.maxOfOrNull { it.width } ?: 0f))
    val lastBottom = laneTops.last() + laneContent.last() + 2 * LANE_PADDING
    return Placement(
        boxes = boxes,
        waypoints = waypoints,
        lanes = laneTops.indices.map { laneTops[it] to laneContent[it] + 2 * LANE_PADDING },
        width = right + EXTENT_MARGIN,
        height = lastBottom + EXTENT_MARGIN,
    )
}

private fun relaxVertically(
    ordering: Ordering,
    items: Map<String, ChainItem>,
    laneCount: Int,
    laneTops: List<Float>,
    laneContent: List<Float>,
): Map<String, Float> {
    val topOf = HashMap<String, Float>()
    ordering.layers.forEach { layer ->
        for (lane in 0 until laneCount) {
            val inLane = layer.filter { items.getValue(it).lane == lane }
            var y = laneTops[lane] + LANE_PADDING + (laneContent[lane] - stackHeight(inLane, items)) / 2f
            for (id in inLane) {
                topOf[id] = y
                y += items.getValue(id).height + ROW_GAP
            }
        }
    }
    repeat(RELAXATION_SWEEPS) {
        for (layer in ordering.layers + ordering.layers.reversed()) {
            for (lane in 0 until laneCount) {
                val inLane = layer.filter { items.getValue(it).lane == lane }
                if (inLane.isEmpty()) continue
                val desired = inLane.map { id -> desiredTop(id, ordering.adjacency, items, topOf) }
                val bounds = laneTops[lane] + LANE_PADDING to laneTops[lane] + LANE_PADDING + laneContent[lane]
                packWithin(inLane, desired, items, bounds).forEach { (id, top) -> topOf[id] = top }
            }
        }
    }
    return topOf
}

private fun desiredTop(id: String, neighbors: Adjacency, items: Map<String, ChainItem>, topOf: Map<String, Float>): Float {
    val connected = neighbors.up[id].orEmpty() + neighbors.down[id].orEmpty()
    if (connected.isEmpty()) return topOf.getValue(id)
    val centers = connected.map { topOf.getValue(it) + items.getValue(it).height / 2f }
    return centers.average().toFloat() - items.getValue(id).height / 2f
}

private fun packWithin(
    ids: List<String>,
    desired: List<Float>,
    items: Map<String, ChainItem>,
    bounds: Pair<Float, Float>,
): List<Pair<String, Float>> {
    val heights = ids.map { items.getValue(it).height }
    val tops = desired.toFloatArray()
    for (index in 1 until tops.size) tops[index] = maxOf(tops[index], tops[index - 1] + heights[index - 1] + ROW_GAP)
    tops[tops.lastIndex] = minOf(tops.last(), bounds.second - heights.last())
    for (index in tops.lastIndex - 1 downTo 0) tops[index] = minOf(tops[index], tops[index + 1] - heights[index] - ROW_GAP)
    tops[0] = maxOf(tops[0], bounds.first)
    for (index in 1 until tops.size) tops[index] = maxOf(tops[index], tops[index - 1] + heights[index - 1] + ROW_GAP)
    return ids.zip(tops.toList())
}

private fun columnLefts(columnWidth: List<Float>, gutter: Float): List<Float> {
    val lefts = ArrayList<Float>()
    var x = EXTENT_MARGIN + gutter
    for (width in columnWidth) {
        lefts.add(x)
        x += width + COLUMN_GAP
    }
    return lefts
}

private fun stackHeight(ids: List<String>, items: Map<String, ChainItem>): Float =
    if (ids.isEmpty()) 0f else ids.sumOf { items.getValue(it).height.toDouble() }.toFloat() + ROW_GAP * (ids.size - 1)

private fun laneContentHeights(layers: List<List<String>>, items: Map<String, ChainItem>, laneCount: Int): List<Float> =
    (0 until laneCount).map { lane ->
        layers.maxOfOrNull { layer -> stackHeight(layer.filter { items.getValue(it).lane == lane }, items) } ?: 0f
    }

private fun laneTops(laneContent: List<Float>): List<Float> {
    val tops = ArrayList<Float>()
    var y = EXTENT_MARGIN
    for (content in laneContent) {
        tops.add(y)
        y += content + 2 * LANE_PADDING + LANE_GAP
    }
    return tops
}

private fun pinNodes(specs: List<FlowNodeSpec>, boxes: Map<String, FlowNodeBox>): Map<String, FlowNodeBox> =
    specs.associate { spec ->
        val box = boxes.getValue(spec.id)
        spec.id to box.copy(x = spec.pinnedX ?: box.x, y = spec.pinnedY ?: box.y)
    }

private fun routeChain(chain: EdgeChain, boxes: Map<String, FlowNodeBox>, waypoints: Map<String, FlowPoint>): FlowEdgeRoute {
    val source = boxes.getValue(chain.edge.from)
    val target = boxes.getValue(chain.edge.to)
    val points = buildList {
        add(FlowPoint(source.x + source.width, source.y + source.height / 2f))
        chain.waypointIds.forEach { add(waypoints.getValue(it)) }
        add(FlowPoint(target.x, target.y + target.height / 2f))
    }
    return if (chain.edge.isBackEdge) {
        FlowEdgeRoute(from = chain.edge.to, to = chain.edge.from, points = points.reversed(), isBackEdge = true)
    } else {
        FlowEdgeRoute(from = chain.edge.from, to = chain.edge.to, points = points, isBackEdge = false)
    }
}

private enum class PortSide { LEFT, RIGHT }

private class PortUse(val routeIndex: Int, val isStart: Boolean, val neighborY: Float)

private fun spreadPorts(routes: List<FlowEdgeRoute>, boxes: Map<String, FlowNodeBox>): List<FlowEdgeRoute> {
    val uses = HashMap<Pair<String, PortSide>, MutableList<PortUse>>()
    routes.forEachIndexed { index, route ->
        val startBox = boxes.getValue(route.from)
        val endBox = boxes.getValue(route.to)
        uses.getOrPut(route.from to sideOf(route.points.first(), startBox)) { mutableListOf() }.add(PortUse(index, true, route.points[1].y))
        uses.getOrPut(route.to to sideOf(route.points.last(), endBox)) { mutableListOf() }.add(PortUse(index, false, route.points[route.points.size - 2].y))
    }
    val adjusted = routes.map { it.points.toMutableList() }
    for ((key, group) in uses) {
        if (group.size < 2) continue
        val box = boxes.getValue(key.first)
        val span = box.height - 2 * PORT_INSET
        group.sortedBy { it.neighborY }.forEachIndexed { slot, use ->
            val y = box.y + PORT_INSET + span * (slot + 1) / (group.size + 1)
            val pointIndex = if (use.isStart) 0 else adjusted[use.routeIndex].lastIndex
            adjusted[use.routeIndex][pointIndex] = adjusted[use.routeIndex][pointIndex].copy(y = y)
        }
    }
    return routes.mapIndexed { index, route -> route.copy(points = adjusted[index]) }
}

private fun sideOf(point: FlowPoint, box: FlowNodeBox): PortSide =
    if (point.x >= box.x + box.width / 2f) PortSide.RIGHT else PortSide.LEFT
