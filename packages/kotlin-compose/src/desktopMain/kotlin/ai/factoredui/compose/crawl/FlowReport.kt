package ai.factoredui.compose.crawl

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FlowFindingRecord(val check: String, val nodeIds: List<String>, val deduction: Int)

@Serializable
data class FlowStepRecord(val nodeId: String, val action: String, val screenshot: String)

@Serializable
data class FlowRecord(
    val id: String,
    val grade: Int,
    val outcome: String,
    val steps: List<FlowStepRecord>,
    val findings: List<FlowFindingRecord>,
)

@Serializable
data class CrawlReportRecord(
    val pageScore: Int,
    val meanGrade: Double,
    val statesSeen: Int,
    val flows: List<FlowRecord>,
)

class CrawlReportFiles(val json: File, val markdown: File, val screenshots: List<File>)

private val reportJson = Json { prettyPrint = true }

/** Write the crawl to disk: one PNG per step, the machine report, and the one a human reads. */
fun writeCrawlReport(report: CrawlReport, outDir: File): CrawlReportFiles {
    val screenshots = writeStepScreenshots(report, File(outDir, "steps"))
    val record = toRecord(report)
    val json = File(outDir, "flow-crawl.json")
    json.writeText(reportJson.encodeToString(CrawlReportRecord.serializer(), record))
    val markdown = File(outDir, "flow-crawl.md")
    markdown.writeText(renderMarkdown(record))
    return CrawlReportFiles(json, markdown, screenshots)
}

private fun writeStepScreenshots(report: CrawlReport, stepsDir: File): List<File> {
    stepsDir.mkdirs()
    return report.flows.flatMap { flow ->
        flow.steps.mapIndexed { index, step ->
            File(stepsDir, "${flow.id}-${index + 1}-${step.nodeId}.png").apply { writeBytes(step.png) }
        }
    }
}

private fun toRecord(report: CrawlReport): CrawlReportRecord = CrawlReportRecord(
    pageScore = report.pageScore,
    meanGrade = report.meanGrade,
    statesSeen = report.statesSeen,
    flows = report.flows.map { flow -> toFlowRecord(flow) },
)

private fun toFlowRecord(flow: CrawledFlow): FlowRecord = FlowRecord(
    id = flow.id,
    grade = flow.grade,
    outcome = flow.outcome.name,
    steps = flow.steps.mapIndexed { index, step ->
        FlowStepRecord(step.nodeId, step.action, "steps/${flow.id}-${index + 1}-${step.nodeId}.png")
    },
    findings = flow.findings.map { FlowFindingRecord(it.check, it.nodeIds, it.deduction) },
)

private fun renderMarkdown(record: CrawlReportRecord): String {
    val header = listOf(
        "# Flow crawl",
        "",
        "Page score **${record.pageScore}** (worst of ${record.flows.size} flows) · " +
            "mean ${"%.1f".format(record.meanGrade)} · ${record.statesSeen} states seen",
        "",
        "| flow | grade | outcome | path | findings |",
        "| --- | --- | --- | --- | --- |",
    )
    return (header + record.flows.map { renderFlowRow(it) }).joinToString("\n") + "\n"
}

private fun renderFlowRow(flow: FlowRecord): String {
    val path = flow.steps.joinToString(" → ") { "${it.nodeId}:${it.action}" }
    val findings = flow.findings.joinToString("; ") { finding ->
        "${finding.check}(-${finding.deduction})${renderNodeIds(finding.nodeIds)}"
    }
    return "| ${flow.id} | ${flow.grade} | ${flow.outcome} | $path | ${findings.ifEmpty { "clean" }} |"
}

private fun renderNodeIds(nodeIds: List<String>): String =
    if (nodeIds.isEmpty()) "" else " " + nodeIds.joinToString("+")
