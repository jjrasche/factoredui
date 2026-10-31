package ai.factoredui.compose.renderer

import ai.factoredui.compose.render.renderSpecToPng
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val PHONE_WIDTH_PX = 780
private const val PHONE_HEIGHT_PX = 1560
private const val PHONE_DENSITY = 2f

private val projectMilestones = mapOf(
    "digital-twin" to listOf("cell-sizing", "trusted-heat", "(no milestone)"),
    "class-patron" to listOf("(no milestone)"),
    "the-floor" to listOf("launch", "(no milestone)"),
)

private val statuses = listOf("draft", "started", "blocked", "stuck", "done", "confirmed", "unknown")

private fun fixtureJobs(): List<Map<String, Any?>> = (0 until 60).map { index ->
    val project = projectMilestones.keys.toList()[index % projectMilestones.size]
    val milestone = projectMilestones.getValue(project)[index % projectMilestones.getValue(project).size]
    mapOf(
        "name" to "job-$index",
        "why" to "Reason $index: what this job is for and why it was asked, in one or two lines so the row wraps once.",
        "status" to statuses[index % statuses.size],
        "project" to project,
        "milestone" to milestone,
        "performed_by" to if (index % 3 == 0) "jim" else "seat-${index % 4}",
        "needs_jim" to (index % 5 == 0),
    )
}

private fun fixtureFeed(): Map<String, Any?> = mapOf(
    "jobs" to fixtureJobs(),
    "projects" to projectMilestones.map { (name, milestones) -> mapOf("name" to name, "children" to milestones) },
    "seats" to listOf(
        mapOf("name" to "factored-ui", "state" to "busy", "up" to true, "retired" to false),
        mapOf("name" to "business", "state" to "idle", "up" to true, "retired" to false),
        mapOf("name" to "world-engine", "state" to "offline", "up" to false, "retired" to false),
        mapOf("name" to "old-seat", "state" to "offline", "up" to false, "retired" to true),
    ),
)

private fun fleetSpecJson(): String = FleetViewRenderTest::class.java.getResource("/specs/fleet-view.spec.json")!!.readText()

private fun fleetPng(view: Int, filterField: String, filterIn: List<String>): ByteArray = renderSpecToPng(
    specJson = fleetSpecJson(),
    width = PHONE_WIDTH_PX,
    height = PHONE_HEIGHT_PX,
    density = PHONE_DENSITY,
    data = mapOf("fleet" to fixtureFeed(), "ui" to mapOf("view" to view, "f" to mapOf("field" to filterField, "in" to filterIn))),
)

private fun printPicture(name: String, png: ByteArray) {
    println("FLEET-PNG $name ${Base64.getEncoder().encodeToString(png)}")
    File("build/fleet-view-$name.png").apply { parentFile.mkdirs() }.writeBytes(png)
}

class FleetViewRenderTest {

    @Test
    fun the_three_views_draw_and_differ_and_are_shown_as_pictures() {
        val jobs = fleetPng(0, "needs_jim", listOf("true"))
        val projects = fleetPng(1, "needs_jim", listOf("true"))
        val seats = fleetPng(2, "needs_jim", listOf("true"))
        printPicture("jobs", jobs)
        printPicture("projects", projects)
        printPicture("seats", seats)
        assertFalse(jobs.contentEquals(projects))
        assertFalse(projects.contentEquals(seats))
        assertFalse(jobs.contentEquals(seats))
    }

    @Test
    fun the_jobs_filter_changes_what_is_drawn() {
        val needsJim = fleetPng(0, "needs_jim", listOf("true"))
        val everything = fleetPng(0, "status", emptyList())
        assertFalse(needsJim.contentEquals(everything))
        assertTrue(needsJim.isNotEmpty())
    }
}
