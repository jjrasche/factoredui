package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.readSceneView
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.structure.BomLine
import ai.factoredui.compose.structure.LevelFocus
import ai.factoredui.compose.structure.bomLines
import ai.factoredui.compose.structure.bomTotals
import ai.factoredui.compose.structure.visibleParts
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

internal const val HOUSE_SCREEN_WIDTH = 900
internal const val HOUSE_SCREEN_HEIGHT = 1000

internal fun engineHouseSpec(): Spec = json.decodeFromString(
    Spec.serializer(),
    checkNotNull(EngineHouseSpecTest::class.java.getResource("/specs/engine-house.spec.json")) { "engine-house.spec.json is not on the test classpath" }.readText(),
)

internal fun engineHouseLines(scene: SceneView): List<BomLine> = bomLines(scene, visibleParts(scene, LevelFocus.AllLevels, showLower = true))

private fun rowOf(line: BomLine): Map<String, Any?> = mapOf(
    "type_id" to line.typeId,
    "label" to line.label,
    "quantity" to line.quantity,
    "ids" to line.ids,
    "unit_cost_usd" to line.unitCostUsd,
    "cost_usd" to line.costUsd,
    "weight_kg" to line.weightKg,
)

internal fun engineHouseStore(view: String, picked: List<String> = emptyList(), report: Map<String, Any?>? = null): Map<String, Any?> {
    val text = engineHouseText()
    val lines = engineHouseLines(readSceneView(text))
    val house = mapOf("scene" to text, "view" to view) + (report?.let { mapOf("report" to it) } ?: emptyMap())
    return mapOf(
        "house" to house,
        "bom" to mapOf("rows" to lines.map(::rowOf), "picked" to picked, "parts_total_usd" to bomTotals(lines).costUsd),
    )
}

private fun pickedIn(store: Map<String, Any?>): Any? = (store["bom"] as? Map<*, *>)?.get("picked")

class EngineHouseSpecTest {

    private val lines = engineHouseLines(readSceneView(engineHouseText()))
    private val piers = lines.first { it.typeId == "cast_pier" }
    private val edgeBeams = lines.first { it.typeId == "edge_beam" }

    private fun titleOf(line: BomLine) = "${line.quantity} × ${line.label}"

    @Test
    fun tapping_a_bom_row_highlights_exactly_that_lines_parts_and_tapping_another_moves_the_highlight() {
        driveScreen(engineHouseSpec(), engineHouseStore("plan"), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            assertEquals("drawn=35 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("house")))

            screen.tapText(titleOf(piers))
            assertEquals(piers.ids, pickedIn(screen.store()))
            assertEquals("drawn=35 highlighted=${piers.quantity} faded=${35 - piers.quantity} selected=", screen.stateDescriptionOf(partsTag("house")))

            screen.tapText(titleOf(edgeBeams))
            assertEquals(edgeBeams.ids, pickedIn(screen.store()))
            assertEquals("drawn=35 highlighted=${edgeBeams.quantity} faded=${35 - edgeBeams.quantity} selected=", screen.stateDescriptionOf(partsTag("house")))
        }
    }

    @Test
    fun each_bom_row_prints_its_unit_and_line_cost_in_dollars_and_says_when_its_weight_is_not_known() {
        driveScreen(engineHouseSpec(), engineHouseStore("plan"), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            val shown = screen.textsUnder("engine-house")
            assertTrue(listOf("12 × Cast pier in a form tube", "$92.37", "$1,108.44").all { it in shown }, "$shown")
            assertTrue(listOf("4 × Edge LVL beam segment", "$62", "$248").all { it in shown }, "$shown")
            assertEquals(lines.size, shown.count { it == "weight not known" })
            assertTrue("–" !in shown, "a row drew the bare dash: $shown")
        }
    }

    @Test
    fun the_parts_total_is_the_sum_of_the_part_types_and_the_named_report_rows_stay_hidden_without_a_report() {
        driveScreen(engineHouseSpec(), engineHouseStore("plan"), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            val shown = screen.textsUnder("engine-house")
            assertTrue("Parts total (sum of the part types)" in shown && "$5,630.49" in shown, "$shown")
            assertFalse("Hardware" in shown, "$shown")
            assertFalse("Engine total" in shown, "$shown")
        }
    }

    @Test
    fun hardware_and_the_engine_total_appear_once_the_report_carries_them() {
        val report = mapOf("hardware_usd" to 800.0, "bom_usd" to 6430.49)
        driveScreen(engineHouseSpec(), engineHouseStore("plan", report = report), HOUSE_SCREEN_WIDTH, HOUSE_SCREEN_HEIGHT).use { screen ->
            val shown = screen.textsUnder("engine-house")
            assertTrue(listOf("Hardware", "$800", "Engine total", "$6,430.49").all { it in shown }, "$shown")
        }
    }
}
