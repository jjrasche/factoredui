package ai.factoredui.compose.crawl

import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.testing.SpecShadowNode
import androidx.compose.ui.unit.DpRect

/** Stable identifiers for every deterministic check the crawler applies. No model is consulted. */
object CrawlCheck {
    const val DEAD_ACTION = "dead_action"
    const val NO_OP = "no_op"
    const val CYCLE = "cycle"
    const val DEAD_END = "dead_end"
    const val SMALL_TAP_TARGET = "small_tap_target"
    const val OVERLAPPING_NODES = "overlapping_nodes"
    const val OFFSCREEN_NODE = "offscreen_node"
}

/** Points a flow loses per finding. One line each, the check it answers beside it. */
object FlowDeductions {
    const val DEAD_ACTION = 40 // an_action_with_no_registered_handler_grades_as_a_dead_action
    const val NO_OP = 15 // an_action_that_leaves_spec_and_store_unchanged_grades_as_a_no_op
    const val CYCLE = 5 // a_flow_that_returns_to_a_seen_state_is_cut_and_marked_as_a_cycle
    const val DEAD_END = 10 // a_state_with_no_action_leading_anywhere_new_is_a_dead_end
    const val SMALL_TAP_TARGET = 8 // a_tap_target_smaller_than_48dp_costs_the_flow_points
    const val OVERLAPPING_NODES = 6 // overlapping_visible_nodes_cost_the_flow_points
    const val OFFSCREEN_NODE = 12 // a_node_rendered_outside_the_viewport_costs_the_flow_points
}

const val PERFECT_FLOW_GRADE = 100

/** WCAG 2.2 SC 2.5.8 Target Size (Minimum) rounded to the Material touch-target floor. */
const val MINIMUM_TAP_TARGET_DP = 48f

data class FlowFinding(val check: String, val nodeIds: List<String>, val deduction: Int)

fun gradeFlow(findings: List<FlowFinding>): Int =
    (PERFECT_FLOW_GRADE - findings.sumOf { it.deduction }).coerceAtLeast(0)

fun structuralFindings(screen: RenderedScreen, root: SpecNode): List<FlowFinding> =
    findSmallTapTargets(screen, actionableNodes(root).map { it.id }.toSet()) +
        findOverlappingNodes(screen, ancestorsById(root)) +
        findOffscreenNodes(screen)

fun findSmallTapTargets(screen: RenderedScreen, actionableIds: Set<String>): List<FlowFinding> =
    screen.visibleNodes()
        .filter { it.id in actionableIds && isBelowTapFloor(it.bounds!!) }
        .map { FlowFinding(CrawlCheck.SMALL_TAP_TARGET, listOf(it.id), FlowDeductions.SMALL_TAP_TARGET) }

fun findOverlappingNodes(screen: RenderedScreen, ancestors: Map<String, Set<String>>): List<FlowFinding> {
    val drawn = screen.visibleNodes().filter { hasArea(it.bounds!!) }
    val findings = mutableListOf<FlowFinding>()
    for (left in drawn.indices) {
        for (right in left + 1 until drawn.size) {
            val pair = listOf(drawn[left], drawn[right])
            if (isKinPair(pair, ancestors)) continue
            if (!overlaps(pair[0].bounds!!, pair[1].bounds!!)) continue
            findings += FlowFinding(
                CrawlCheck.OVERLAPPING_NODES,
                pair.map { it.id },
                FlowDeductions.OVERLAPPING_NODES,
            )
        }
    }
    return findings
}

fun findOffscreenNodes(screen: RenderedScreen): List<FlowFinding> =
    screen.visibleNodes()
        .filter { hasArea(it.bounds!!) && !contains(screen.viewport, it.bounds) }
        .map { FlowFinding(CrawlCheck.OFFSCREEN_NODE, listOf(it.id), FlowDeductions.OFFSCREEN_NODE) }

/** Every node id mapped to the ids of its spec ancestors — a container always covers its children. */
fun ancestorsById(root: SpecNode): Map<String, Set<String>> {
    val ancestors = mutableMapOf<String, Set<String>>()
    fun walk(node: SpecNode, above: Set<String>) {
        ancestors[node.id] = above
        node.children.forEach { child -> walk(child, above + node.id) }
    }
    walk(root, emptySet())
    return ancestors
}

private fun isKinPair(pair: List<SpecShadowNode>, ancestors: Map<String, Set<String>>): Boolean =
    pair[0].id in ancestors[pair[1].id].orEmpty() || pair[1].id in ancestors[pair[0].id].orEmpty()

private fun isBelowTapFloor(bounds: DpRect): Boolean =
    (bounds.right - bounds.left).value < MINIMUM_TAP_TARGET_DP ||
        (bounds.bottom - bounds.top).value < MINIMUM_TAP_TARGET_DP

private fun hasArea(bounds: DpRect): Boolean = bounds.right > bounds.left && bounds.bottom > bounds.top

private fun overlaps(first: DpRect, second: DpRect): Boolean =
    first.left < second.right && second.left < first.right &&
        first.top < second.bottom && second.top < first.bottom

private fun contains(outer: DpRect, inner: DpRect): Boolean =
    inner.left >= outer.left && inner.top >= outer.top &&
        inner.right <= outer.right && inner.bottom <= outer.bottom
