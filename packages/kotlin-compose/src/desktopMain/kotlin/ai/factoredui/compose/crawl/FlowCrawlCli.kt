package ai.factoredui.compose.crawl

import ai.factoredui.compose.schema.Spec
import java.io.File
import kotlinx.serialization.json.Json

private val specDecoder = Json { ignoreUnknownKeys = true }

private const val USAGE =
    "usage: flow-crawl-cli <spec.json> <action-map-class> [outDir=build/flow-crawl] " +
        "[maxDepth=4] [viewportWidthDp=400] [viewportHeightDp=800]"

/**
 * Crawl every action of a spec against a host action map named by class, grade every flow with
 * deterministic checks only, and write the report. Backs the `flowCrawl` gradle task.
 */
fun main(args: Array<String>) {
    if (args.size < 2) failWith(USAGE, 2)
    val specFile = File(args[0])
    if (!specFile.isFile) failWith("flow-crawl-cli: spec file not found: ${specFile.absolutePath}", 3)
    val host = loadCrawlHost(args[1])
    val outDir = File(args.getOrNull(2) ?: "build/flow-crawl")
    val options = CrawlOptions(
        maxDepth = args.getOrNull(3)?.toIntOrNull() ?: 4,
        viewportWidthDp = args.getOrNull(4)?.toIntOrNull() ?: 400,
        viewportHeightDp = args.getOrNull(5)?.toIntOrNull() ?: 800,
    )

    val spec = specDecoder.decodeFromString(Spec.serializer(), specFile.readText())
    val report = crawlFlows(spec, host, options)
    outDir.mkdirs()
    val files = writeCrawlReport(report, outDir)
    printSummary(report, files)
}

private fun loadCrawlHost(className: String): CrawlHost {
    val hostClass = runCatching { Class.forName(className) }
        .getOrElse { failWith("flow-crawl-cli: action map class not found: $className", 4) }
    val singleton = runCatching { hostClass.getDeclaredField("INSTANCE").get(null) }.getOrNull()
    val instance = singleton ?: hostClass.getDeclaredConstructor().newInstance()
    return instance as? CrawlHost
        ?: failWith("flow-crawl-cli: $className does not implement CrawlHost", 5)
}

private fun printSummary(report: CrawlReport, files: CrawlReportFiles) {
    println("flow-crawl: ${report.flows.size} flows · ${report.statesSeen} states · page score ${report.pageScore}")
    report.flows.take(5).forEach { flow ->
        println("  ${flow.id} ${flow.grade} ${flow.outcome} ${flow.steps.joinToString(" -> ") { it.action }}")
    }
    println("flow-crawl: wrote ${files.json.absolutePath} and ${files.markdown.absolutePath}")
}

private fun failWith(message: String, exitCode: Int): Nothing {
    System.err.println(message)
    kotlin.system.exitProcess(exitCode)
}
