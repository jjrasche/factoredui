package ai.factoredui.compose.crawl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowCrawlerTest {

    @Test
    fun actionable_nodes_lists_exactly_the_nodes_whose_action_is_not_null() {
        val root = columnNode(
            "root",
            listOf(
                textNode("headline", "no action here"),
                buttonNode("go_a_button", "A", "go_a"),
                stackNode("inner", listOf(buttonNode("go_b_button", "B", "go_b"), textNode("caption", "quiet"))),
                buttonNode("inert_button", "inert", null),
            ),
        )

        val actionable = actionableNodes(root).map { it.id }

        assertEquals(listOf("go_a_button", "go_b_button"), actionable)
    }

    @Test
    fun firing_every_action_from_a_two_button_spec_yields_two_flows_of_depth_one() {
        val report = crawlFlows(twoButtonSpec, TwoPageHost(), CrawlOptions(maxDepth = 1))

        val depthOne = report.flows.filter { it.steps.size == 1 }
        assertEquals(2, depthOne.size, "one flow per actionable node")
        assertEquals(setOf("go_a_button", "go_b_button"), depthOne.map { it.steps.last().nodeId }.toSet())
        assertEquals(2, report.flows.size, "a depth limit of one opens nothing deeper")
    }

    @Test
    fun a_flow_that_returns_to_a_seen_state_is_cut_and_marked_as_a_cycle() {
        val report = crawlFlows(pingPongSpec, TwoPagePingPongHost(), CrawlOptions(maxDepth = 4))

        val cycles = report.flows.filter { it.outcome == FlowOutcome.CYCLE }
        assertTrue(cycles.isNotEmpty(), "ping-pong handlers must produce at least one cycle")
        assertTrue(
            cycles.all { it.findings.any { finding -> finding.check == CrawlCheck.CYCLE } },
            "a cycle flow carries the cycle finding",
        )
        val statesReachable = 3
        assertEquals(statesReachable, report.statesSeen, "the cut stops the crawl re-expanding a seen state")
    }

    @Test
    fun an_action_with_no_registered_handler_grades_as_a_dead_action() {
        val report = crawlFlows(twoButtonSpec, UnregisteredActionHost(), CrawlOptions(maxDepth = 3))

        assertEquals(2, report.flows.size)
        assertTrue(report.flows.all { it.outcome == FlowOutcome.DEAD_ACTION })
        val deduction = report.flows.first().findings.first { it.check == CrawlCheck.DEAD_ACTION }.deduction
        assertEquals(FlowDeductions.DEAD_ACTION, deduction)
        assertTrue(report.flows.all { it.grade < PERFECT_FLOW_GRADE }, "a dead action must cost the flow points")
    }

    @Test
    fun an_action_that_leaves_spec_and_store_unchanged_grades_as_a_no_op() {
        val report = crawlFlows(twoButtonSpec, StandingStillHost(), CrawlOptions(maxDepth = 3))

        assertEquals(2, report.flows.size)
        assertTrue(report.flows.all { it.outcome == FlowOutcome.NO_OP })
        assertTrue(report.flows.all { flow -> flow.findings.any { it.check == CrawlCheck.NO_OP } })
    }

    @Test
    fun a_state_with_no_action_leading_anywhere_new_is_a_dead_end_unless_it_is_the_root() {
        val report = crawlFlows(twoButtonSpec, TwoPageHost(), CrawlOptions(maxDepth = 4))

        val deadEnds = report.flows.filter { flow -> flow.findings.any { it.check == CrawlCheck.DEAD_END } }
        assertEquals(2, deadEnds.size, "both reachable pages are dead ends; the root is exempt")
        assertEquals(setOf("go_a_button", "go_b_button"), deadEnds.map { it.steps.last().nodeId }.toSet())
        assertTrue(deadEnds.all { it.steps.size == 1 }, "the finding lands on the flow that arrived at the dead end")
    }
}
