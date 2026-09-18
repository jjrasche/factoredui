package ai.factoredui.compose.crawl

import ai.factoredui.compose.schema.Spec
import java.io.File
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class FlowCrawlCliTest {

    @Test
    fun the_gradle_task_flowCrawl_runs_the_crawler_on_a_spec_file_and_an_action_map_class_name() {
        val workDir = File.createTempFile("flow-crawl-cli", "").let { probe ->
            probe.delete()
            probe.also { it.mkdirs() }
        }
        val specFile = File(workDir, "spec.json")
        specFile.writeText(Json.encodeToString(Spec.serializer(), twoButtonSpec))
        val outDir = File(workDir, "out")

        main(arrayOf(specFile.absolutePath, TwoPageHost::class.java.name, outDir.absolutePath, "2"))

        assertTrue(File(outDir, "flow-crawl.json").isFile, "the crawl writes its json report")
        assertTrue(File(outDir, "flow-crawl.md").isFile, "the crawl writes its markdown report")
        assertTrue(File(outDir, "steps").listFiles().orEmpty().isNotEmpty(), "the crawl writes a screenshot per step")

        workDir.deleteRecursively()
    }

    @Test
    fun the_flowCrawl_gradle_task_points_at_the_crawler_main_and_takes_a_spec_and_an_action_map() {
        val buildFile = File("build.gradle.kts")
        assertTrue(buildFile.isFile, "the test runs from the kotlin-compose project directory")
        val build = buildFile.readText()

        assertTrue(build.contains("""tasks.register<JavaExec>("flowCrawl")"""), "the flowCrawl task is registered")
        assertTrue(
            build.contains("ai.factoredui.compose.crawl.FlowCrawlCliKt"),
            "flowCrawl points at the crawler CLI main the CLI test exercises",
        )
    }
}
