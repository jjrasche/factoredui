package ai.factoredui.compose.crawl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowGradeTest {

    @Test
    fun a_tap_target_smaller_than_48dp_costs_the_flow_points() {
        val cramped = specOf(columnNode("root", listOf(buttonNode("tiny_button", "x", "go_a"))))
        val roomy = specOf(columnNode("root", listOf(roomyCardNode("roomy_card", "go_a"))))

        val crampedFlow = crawlFlows(cramped, TwoPageHost(), CrawlOptions(maxDepth = 1)).flows.single()
        val roomyFlow = crawlFlows(roomy, TwoPageHost(), CrawlOptions(maxDepth = 1)).flows.single()

        val cramping = crampedFlow.findings.filter { it.check == CrawlCheck.SMALL_TAP_TARGET }
        assertEquals(listOf("tiny_button"), cramping.flatMap { it.nodeIds })
        assertEquals(FlowDeductions.SMALL_TAP_TARGET, cramping.single().deduction)
        assertTrue(
            roomyFlow.findings.none { it.check == CrawlCheck.SMALL_TAP_TARGET },
            "a target at or above ${MINIMUM_TAP_TARGET_DP}dp costs nothing",
        )
    }

    @Test
    fun overlapping_visible_nodes_cost_the_flow_points() {
        val stacked = specOf(
            columnNode(
                "root",
                listOf(
                    stackNode(
                        "overlap_stack",
                        listOf(roomyCardNode("under_card", null), roomyCardNode("over_card", "go_a")),
                    ),
                ),
            ),
        )
        val separated = specOf(
            columnNode("root", listOf(roomyCardNode("first_card", null), roomyCardNode("second_card", "go_a"))),
        )

        val stackedFlow = crawlFlows(stacked, TwoPageHost(), CrawlOptions(maxDepth = 1)).flows.single()
        val separatedFlow = crawlFlows(separated, TwoPageHost(), CrawlOptions(maxDepth = 1)).flows.single()

        val overlaps = stackedFlow.findings.filter { it.check == CrawlCheck.OVERLAPPING_NODES }
        assertTrue(
            overlaps.any { it.nodeIds.containsAll(listOf("under_card", "over_card")) },
            "the two stacked cards must be reported as an overlapping pair",
        )
        assertTrue(
            separatedFlow.findings.none { it.check == CrawlCheck.OVERLAPPING_NODES },
            "siblings laid out in a column never overlap",
        )
    }

    @Test
    fun a_node_rendered_outside_the_viewport_costs_the_flow_points() {
        val tall = specOf(
            scrollNode(
                "root",
                listOf(
                    roomyCardNode("first_card", "go_a"),
                    roomyCardNode("second_card", null),
                    roomyCardNode("third_card", null),
                ),
            ),
        )
        val pinhole = CrawlOptions(maxDepth = 1, viewportWidthDp = 200, viewportHeightDp = 120)
        val roomy = CrawlOptions(maxDepth = 1, viewportWidthDp = 200, viewportHeightDp = 1200)

        val pinholeFlow = crawlFlows(tall, TwoPageHost(), pinhole).flows.single()
        val roomyFlow = crawlFlows(tall, TwoPageHost(), roomy).flows.single()

        val offscreen = pinholeFlow.findings.filter { it.check == CrawlCheck.OFFSCREEN_NODE }
        assertTrue(offscreen.isNotEmpty(), "a 120dp viewport cannot hold three roomy cards")
        assertTrue(offscreen.flatMap { it.nodeIds }.contains("third_card"))
        assertTrue(
            roomyFlow.findings.none { it.check == CrawlCheck.OFFSCREEN_NODE },
            "the same spec in a tall viewport has nothing offscreen",
        )
    }

    @Test
    fun a_flow_grade_is_one_hundred_less_every_deduction_and_never_negative() {
        val findings = listOf(
            FlowFinding(CrawlCheck.DEAD_ACTION, listOf("a"), FlowDeductions.DEAD_ACTION),
            FlowFinding(CrawlCheck.SMALL_TAP_TARGET, listOf("a"), FlowDeductions.SMALL_TAP_TARGET),
        )

        assertEquals(
            PERFECT_FLOW_GRADE - FlowDeductions.DEAD_ACTION - FlowDeductions.SMALL_TAP_TARGET,
            gradeFlow(findings),
        )
        assertEquals(0, gradeFlow(List(20) { findings.first() }))
    }
}
