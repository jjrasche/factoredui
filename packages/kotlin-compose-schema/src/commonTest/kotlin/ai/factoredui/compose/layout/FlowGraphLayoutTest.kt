package ai.factoredui.compose.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowGraphLayoutTest {

    private fun node(id: String, group: String? = null, rank: Int? = null) =
        FlowNodeSpec(id = id, width = 100f, height = 32f, group = group, rank = rank)

    private fun edge(from: String, to: String) = FlowEdgeSpec(from, to)

    private fun FlowLayout.centerY(id: String): Float = nodes.getValue(id).let { it.y + it.height / 2f }

    private fun FlowLayout.countCrossings(): Int {
        val routes = edges.filter { !it.isBackEdge }
        var crossings = 0
        for (i in routes.indices) for (j in i + 1 until routes.size) {
            val a = routes[i]
            val b = routes[j]
            if (a.from == b.from || a.to == b.to) continue
            val startOrder = centerY(a.from) - centerY(b.from)
            val endOrder = centerY(a.to) - centerY(b.to)
            val sameColumns = nodes.getValue(a.from).x == nodes.getValue(b.from).x &&
                nodes.getValue(a.to).x == nodes.getValue(b.to).x
            if (sameColumns && startOrder * endOrder < 0f) crossings++
        }
        return crossings
    }

    private fun FlowLayout.overlappingPairs(): List<Pair<String, String>> {
        val boxes = nodes.values.toList()
        return buildList {
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val a = boxes[i]
                val b = boxes[j]
                val apart = a.x + a.width <= b.x || b.x + b.width <= a.x || a.y + a.height <= b.y || b.y + b.height <= a.y
                if (!apart) add(a.id to b.id)
            }
        }
    }

    @Test
    fun aDependencyChainFlowsLeftToRight() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("rain"), node("soil_water"), node("growth")),
            edges = listOf(edge("rain", "soil_water"), edge("soil_water", "growth")),
        )
        val rain = layout.nodes.getValue("rain")
        val soil = layout.nodes.getValue("soil_water")
        val growth = layout.nodes.getValue("growth")
        assertTrue(rain.x + rain.width < soil.x, "a consumer sits right of its source")
        assertTrue(soil.x + soil.width < growth.x, "the chain advances one column per dependency")
    }

    @Test
    fun aNodeSitsInTheColumnOfItsLongestPathFromASource() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a"), node("b"), node("c"), node("d")),
            edges = listOf(edge("a", "b"), edge("b", "c"), edge("a", "d"), edge("c", "d")),
        )
        val d = layout.nodes.getValue("d")
        val c = layout.nodes.getValue("c")
        assertTrue(d.x > c.x, "d waits for the longer a-b-c-d route, not the short a-d edge")
    }

    @Test
    fun aRankHintPushesANodeRightOfWhereDependenciesAloneWouldPutIt() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a"), node("b"), node("late", rank = 4)),
            edges = listOf(edge("a", "b")),
        )
        assertTrue(layout.nodes.getValue("late").x > layout.nodes.getValue("b").x, "rank 4 sits right of dependency depth 1")
    }

    @Test
    fun aCycleDoesNotHangAndTheClosingEdgeIsMarkedBackward() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("leaf_mass"), node("growth"), node("soil_water")),
            edges = listOf(
                edge("leaf_mass", "growth"),
                edge("growth", "soil_water"),
                edge("soil_water", "leaf_mass"),
            ),
        )
        assertEquals(3, layout.edges.size, "every edge is still routed")
        assertEquals(1, layout.edges.count { it.isBackEdge }, "exactly the edge that closes the loop runs backward")
        assertTrue(layout.edges.single { it.isBackEdge }.let { it.from == "soil_water" && it.to == "leaf_mass" })
    }

    @Test
    fun aBackEdgeStillRunsFromItsDeclaredSourceToItsDeclaredTarget() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a"), node("b")),
            edges = listOf(edge("a", "b"), edge("b", "a")),
        )
        val back = layout.edges.single { it.isBackEdge }
        val b = layout.nodes.getValue("b")
        val a = layout.nodes.getValue("a")
        assertEquals("b", back.from)
        assertEquals("a", back.to)
        assertTrue(back.points.first().x >= b.x, "starts at the declared source")
        assertTrue(back.points.last().x <= a.x + a.width, "ends at the declared target")
    }

    @Test
    fun aSelfLoopAndAnEdgeToAnUnknownNodeAreDroppedNotFatal() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a"), node("b")),
            edges = listOf(edge("a", "a"), edge("a", "ghost"), edge("a", "b")),
        )
        assertEquals(listOf("a" to "b"), layout.edges.map { it.from to it.to })
    }

    @Test
    fun aDuplicateEdgeIsRoutedOnce() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a"), node("b")),
            edges = listOf(edge("a", "b"), edge("a", "b")),
        )
        assertEquals(1, layout.edges.size)
    }

    @Test
    fun eachGroupOwnsOneHorizontalBandAndNoBandsOverlap() {
        val layout = layoutFlowGraph(
            nodes = listOf(
                node("rain", "soil"), node("soil_water", "soil"),
                node("leaf", "vegetation"), node("stem", "vegetation"),
                node("intake", "livestock"),
            ),
            edges = listOf(edge("rain", "soil_water"), edge("soil_water", "leaf"), edge("leaf", "intake")),
        )
        assertEquals(listOf("soil", "vegetation", "livestock"), layout.lanes.map { it.group }, "lanes follow first appearance")
        layout.lanes.zipWithNext().forEach { (upper, lower) ->
            assertTrue(upper.top + upper.height <= lower.top, "${upper.group} band ends before ${lower.group} begins")
        }
        layout.lanes.forEach { lane ->
            layout.nodes.values.filter { it.id in groupMembers.getValue(lane.group) }.forEach { box ->
                assertTrue(box.y >= lane.top && box.y + box.height <= lane.top + lane.height, "${box.id} stays in ${lane.group}")
            }
        }
    }

    private val groupMembers: Map<String?, Set<String>> = mapOf(
        "soil" to setOf("rain", "soil_water"),
        "vegetation" to setOf("leaf", "stem"),
        "livestock" to setOf("intake"),
    )

    @Test
    fun groupOrderOverridesFirstAppearance() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a", "x"), node("b", "y")),
            edges = emptyList(),
            groupOrder = listOf("y", "x"),
        )
        assertEquals(listOf("y", "x"), layout.lanes.map { it.group })
    }

    @Test
    fun nodesWithNoGroupShareOneUnlabelledBand() {
        val layout = layoutFlowGraph(nodes = listOf(node("a"), node("b")), edges = listOf(edge("a", "b")))
        assertEquals(1, layout.lanes.size)
    }

    @Test
    fun crossingMinimisationUntanglesAFullyReversedBipartiteLayer() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a1"), node("a2"), node("a3"), node("b1"), node("b2"), node("b3")),
            edges = listOf(edge("a1", "b3"), edge("a2", "b2"), edge("a3", "b1")),
        )
        assertEquals(0, layout.countCrossings())
    }

    @Test
    fun aLongEdgeBendsThroughEveryColumnItSpans() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a"), node("b"), node("c"), node("d")),
            edges = listOf(edge("a", "b"), edge("b", "c"), edge("c", "d"), edge("a", "d")),
        )
        val long = layout.edges.single { it.from == "a" && it.to == "d" }
        assertTrue(long.points.size >= 4, "a three-column edge carries waypoints for the columns it passes over")
        assertTrue(long.points.zipWithNext().all { (p, q) -> q.x >= p.x }, "a forward edge never doubles back")
    }

    @Test
    fun aForwardEdgeLeavesTheRightSideOfItsSourceAndEntersTheLeftSideOfItsTarget() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a"), node("b")),
            edges = listOf(edge("a", "b")),
        )
        val a = layout.nodes.getValue("a")
        val b = layout.nodes.getValue("b")
        val route = layout.edges.single()
        assertEquals(a.x + a.width, route.points.first().x)
        assertEquals(b.x, route.points.last().x)
        assertEquals(a.y + a.height / 2f, route.points.first().y)
        assertEquals(b.y + b.height / 2f, route.points.last().y)
    }

    @Test
    fun noTwoNodeBoxesOverlapInADensePipeline() {
        val layout = layoutFlowGraph(syntheticPipeline().first, syntheticPipeline().second)
        assertEquals(emptyList(), layout.overlappingPairs())
    }

    @Test
    fun aDensePipelinePlacesEveryNodeAndRoutesEveryEdge() {
        val (nodes, edges) = syntheticPipeline()
        val layout = layoutFlowGraph(nodes, edges)
        assertEquals(nodes.size, layout.nodes.size)
        assertEquals(edges.map { it.from to it.to }.toSet().size, layout.edges.size)
        assertTrue(layout.width > 0f && layout.height > 0f)
        layout.nodes.values.forEach { box ->
            assertTrue(box.x >= 0f && box.y >= 0f && box.x + box.width <= layout.width && box.y + box.height <= layout.height, "${box.id} inside the extent")
        }
    }

    @Test
    fun aPinnedNodeKeepsItsExplicitPosition() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a"), FlowNodeSpec("b", 100f, 32f, pinnedX = 500f, pinnedY = 40f)),
            edges = listOf(edge("a", "b")),
        )
        val b = layout.nodes.getValue("b")
        assertEquals(500f, b.x)
        assertEquals(40f, b.y)
        assertEquals(b.x, layout.edges.single().points.last().x, "the edge follows the pinned node")
    }

    @Test
    fun aNodeWithOneNeighbourSitsLevelWithItWhenThereIsRoom() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a1"), node("a2"), node("a3"), node("b1")),
            edges = listOf(edge("a3", "b1")),
        )
        assertEquals(layout.centerY("a3"), layout.centerY("b1"), "the lone child lines up with its parent, not the middle of the stack")
    }

    @Test
    fun aSourceWithNoDependenciesSitsJustLeftOfItsEarliestConsumer() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a"), node("b"), node("c"), node("d"), node("late_input")),
            edges = listOf(edge("a", "b"), edge("b", "c"), edge("c", "d"), edge("late_input", "d")),
        )
        assertEquals(layout.nodes.getValue("c").x, layout.nodes.getValue("late_input").x, "the input waits beside c, one column before d")
    }

    @Test
    fun aSourceKeepsColumnZeroWhenCompactionIsOff() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a"), node("b"), node("c"), node("d"), node("late_input")),
            edges = listOf(edge("a", "b"), edge("b", "c"), edge("c", "d"), edge("late_input", "d")),
            compactSources = false,
        )
        assertEquals(layout.nodes.getValue("a").x, layout.nodes.getValue("late_input").x)
    }

    @Test
    fun edgesEnteringOneNodeSpreadAcrossItsLeftSideInTheOrderOfTheirSources() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("top"), node("middle"), node("bottom"), node("sink")),
            edges = listOf(edge("top", "sink"), edge("middle", "sink"), edge("bottom", "sink")),
        )
        val sink = layout.nodes.getValue("sink")
        val entryYs = listOf("top", "middle", "bottom").map { source -> layout.edges.single { it.from == source }.points.last().y }
        assertEquals(3, entryYs.toSet().size, "three edges enter at three different heights")
        assertEquals(entryYs.sorted(), entryYs, "higher sources enter higher, so the edges do not cross at the port")
        assertTrue(entryYs.all { it > sink.y && it < sink.y + sink.height }, "every entry stays on the node's left side")
    }

    @Test
    fun edgesLeavingOneNodeSpreadAcrossItsRightSide() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("source"), node("one"), node("two"), node("three")),
            edges = listOf(edge("source", "one"), edge("source", "two"), edge("source", "three")),
        )
        val exitYs = layout.edges.map { it.points.first().y }
        assertEquals(3, exitYs.toSet().size)
    }

    @Test
    fun unconnectedNodesSitInAStripBelowTheConnectedFlowOfTheirLane() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a", "v"), node("b", "v"), node("u1", "v"), node("u2", "v"), node("u3", "v")),
            edges = listOf(edge("a", "b")),
        )
        val flowBottom = maxOf(layout.nodes.getValue("a").let { it.y + it.height }, layout.nodes.getValue("b").let { it.y + it.height })
        listOf("u1", "u2", "u3").forEach { id ->
            assertTrue(layout.nodes.getValue(id).y >= flowBottom, "$id is parked below the connected nodes")
        }
        val lane = layout.lanes.single()
        listOf("u1", "u2", "u3").forEach { id ->
            val box = layout.nodes.getValue(id)
            assertTrue(box.y >= lane.top && box.y + box.height <= lane.top + lane.height, "$id stays in its lane band")
        }
    }

    @Test
    fun unconnectedNodesDoNotMoveTheConnectedFlow() {
        val connectedOnly = layoutFlowGraph(listOf(node("a", "v"), node("b", "v")), listOf(edge("a", "b")))
        val withParked = layoutFlowGraph(listOf(node("a", "v"), node("b", "v"), node("u1", "v"), node("u2", "v")), listOf(edge("a", "b")))
        assertEquals(connectedOnly.nodes.getValue("a"), withParked.nodes.getValue("a"))
        assertEquals(connectedOnly.nodes.getValue("b"), withParked.nodes.getValue("b"))
    }

    @Test
    fun manyUnconnectedNodesWrapIntoRowsInsteadOfOneTallStack() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a", "v"), node("b", "v"), node("c", "v")) + (1..12).map { node("lonely$it", "v") },
            edges = listOf(edge("a", "b"), edge("b", "c")),
        )
        val parkedXs = (1..12).map { layout.nodes.getValue("lonely$it").x }.toSet()
        assertTrue(parkedXs.size >= 2, "the strip uses more than one column")
        assertTrue(layout.height < 12 * 46f, "and is shorter than a single stack of twelve")
    }

    @Test
    fun aGraphOfOnlyUnconnectedNodesStillLaysOutWithoutOverlap() {
        val layout = layoutFlowGraph((1..9).map { node("u$it", "v") }, emptyList())
        assertEquals(9, layout.nodes.size)
        assertTrue(layout.width > 0f && layout.height > 0f)
        assertEquals(emptyList(), layout.overlappingPairs())
    }

    @Test
    fun aHubNodeKeepsItsLabelHeightSoSizeNeverReadsAsImportance() {
        val sources = (1..7).map { node("in$it") }
        val layout = layoutFlowGraph(
            nodes = sources + node("hub") + node("leaf"),
            edges = sources.map { edge(it.id, "hub") } + edge("hub", "leaf"),
        )
        assertEquals(32f, layout.nodes.getValue("hub").height)
        assertEquals(32f, layout.nodes.getValue("leaf").height)
    }

    @Test
    fun anUngroupedChainJoinsTheLaneOfTheGroupedNodeItFeeds() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("weather"), node("pet"), node("soil_water", "soil"), node("herd", "livestock")),
            edges = listOf(edge("weather", "pet"), edge("pet", "soil_water"), edge("herd", "soil_water")),
        )
        assertEquals(listOf("soil", "livestock"), layout.lanes.map { it.group }, "no unlabelled lane is created for nodes that can inherit one")
        val soilLane = layout.lanes.first { it.group == "soil" }
        listOf("weather", "pet", "soil_water").forEach { id ->
            val box = layout.nodes.getValue(id)
            assertTrue(box.y >= soilLane.top && box.y + box.height <= soilLane.top + soilLane.height, "$id sits in the soil lane")
        }
    }

    @Test
    fun anUngroupedNodePrefersTheLaneOfWhatItFeedsOverWhatFeedsIt() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("producer", "livestock"), node("middle"), node("consumer", "soil")),
            edges = listOf(edge("producer", "middle"), edge("middle", "consumer")),
        )
        val soilLane = layout.lanes.first { it.group == "soil" }
        val middle = layout.nodes.getValue("middle")
        assertTrue(middle.y >= soilLane.top && middle.y + middle.height <= soilLane.top + soilLane.height)
    }

    @Test
    fun anUngroupedNodeWithNoGroupedNeighbourKeepsAnUnlabelledLane() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a", "soil"), node("b", "soil"), node("loner")),
            edges = listOf(edge("a", "b")),
        )
        assertEquals(listOf("soil", null), layout.lanes.map { it.group })
    }

    @Test
    fun aLeftGutterKeepsEveryNodeClearOfTheLaneLabels() {
        val layout = layoutFlowGraph(
            nodes = listOf(node("a", "x"), node("b", "y")),
            edges = listOf(edge("a", "b")),
            gutter = 30f,
        )
        assertTrue(layout.nodes.values.all { it.x >= 30f }, "no node starts inside the label gutter")
    }

    @Test
    fun layoutIsDeterministic() {
        val (nodes, edges) = syntheticPipeline()
        assertEquals(layoutFlowGraph(nodes, edges), layoutFlowGraph(nodes, edges))
    }

    @Test
    fun anEmptyGraphLaysOutToNothing() {
        val layout = layoutFlowGraph(emptyList(), emptyList())
        assertTrue(layout.nodes.isEmpty() && layout.edges.isEmpty())
        assertFalse(layout.width < 0f)
    }

    // 41 nodes, 77 edges, 4 groups, with feedback loops: the shape of a state-variable pipeline.
    private fun syntheticPipeline(): Pair<List<FlowNodeSpec>, List<FlowEdgeSpec>> {
        val groups = listOf("soil", "vegetation", "livestock", "infrastructure")
        val nodes = (0 until 41).map { node("n$it", groups[it % 4]) }
        val edges = buildList {
            for (i in 1 until 41) add(edge("n${i - 1 - minOf(i - 1, i % 3)}", "n$i"))
            var seed = 17
            while (size < 76) {
                seed = (seed * 31 + 7) % 1009
                val from = seed % 41
                val to = (seed / 41 + from + 1) % 41
                if (from < to) add(edge("n$from", "n$to"))
            }
            add(edge("n20", "n3"))
        }
        return nodes to edges
    }
}
