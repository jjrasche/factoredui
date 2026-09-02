package ai.factoredui.compose.crawl

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowReportTest {

    @Test
    fun the_report_orders_flows_worst_first_and_carries_a_png_per_step() {
        val mixed = specOf(
            columnNode(
                "root",
                listOf(
                    buttonNode("go_a_button", "A", "go_a"),
                    buttonNode("orphan_button", "orphan", "no_such_action"),
                ),
            ),
        )
        val outDir = File.createTempFile("flow-crawl-report", "").let { probe ->
            probe.delete()
            probe.also { it.mkdirs() }
        }

        val report = crawlFlows(mixed, TwoPageHost(), CrawlOptions(maxDepth = 2))
        val files = writeCrawlReport(report, outDir)

        val grades = report.flows.map { it.grade }
        assertEquals(grades.sorted(), grades, "flows are ordered worst-first")
        assertTrue(report.flows.first().outcome == FlowOutcome.DEAD_ACTION, "the dead action is the worst flow")

        val stepCount = report.flows.sumOf { it.steps.size }
        assertEquals(stepCount, files.screenshots.size, "one png per step of every flow")
        assertTrue(files.screenshots.all { it.isFile && it.length() > 0 }, "every screenshot is a real png on disk")

        val json = files.json.readText()
        assertTrue(json.contains("\"pageScore\""), "the json carries the page score")
        assertTrue(json.contains(".png"), "the json points at the screenshots")
        val markdown = files.markdown.readText()
        assertTrue(markdown.contains("Flow crawl"), "the markdown is a readable report")
        assertTrue(markdown.contains(report.flows.first().id), "the worst flow is listed")

        outDir.deleteRecursively()
    }

    @Test
    fun the_page_score_is_the_worst_flow_and_the_mean_rides_beside_it() {
        val report = crawlFlows(twoButtonSpec, UnregisteredActionHost(), CrawlOptions(maxDepth = 1))

        assertEquals(report.flows.minOf { it.grade }, report.pageScore)
        assertEquals(report.flows.map { it.grade }.average(), report.meanGrade)
    }
}
