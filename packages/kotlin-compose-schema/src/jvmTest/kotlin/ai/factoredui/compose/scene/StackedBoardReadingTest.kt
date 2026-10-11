package ai.factoredui.compose.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun resourceText(name: String): String = StackedBoardReadingTest::class.java.getResource(name)!!.readText()

private fun board(): SceneView = readSceneView(resourceText("/stacked-board.scene.json"))

private fun boardReports(): List<Report> = readReports(resourceText("/stacked-board.reports.json"))

private fun groups(): Report = boardReports().single { it.id == "groups" }

class StackedBoardReadingTest {
    @Test
    fun the_board_reads_with_its_twelve_parts_on_a_20_by_16_grid() {
        val scene = board()
        assertEquals(20 to 16, scene.cols to scene.rows)
        assertEquals(listOf("b1", "b2", "b3", "b4", "b5", "b6", "r1", "r2", "p1", "p2", "p3", "p4"), scene.footprints!!.items.map { it.id })
    }

    @Test
    fun the_board_is_three_stack_levels_each_resting_on_the_one_below() {
        val scene = board()
        assertEquals(listOf(StackLevel(0, 0, null), StackLevel(1, 400, null), StackLevel(2, 700, null)), scene.stackLevels)
        val levelOf = scene.footprints!!.items.groupBy({ it.type }, { Triple(it.stackLevel, it.zMm, it.heightMm) }).mapValues { it.value.toSet() }
        assertEquals(setOf(Triple(0, 0L, 400L)), levelOf["block"])
        assertEquals(setOf(Triple(1, 400L, null)), levelOf["bar"])
        assertEquals(setOf(Triple(2, 700L, null)), levelOf["plate"])
    }

    @Test
    fun a_type_that_states_no_height_leaves_each_part_to_carry_its_own() {
        val scene = board()
        assertNull(scene.types.first { it.id == "block" }.heightMm)
        assertTrue(scene.footprints!!.items.filter { it.type == "block" }.all { it.heightMm == 400L })
    }

    @Test
    fun the_board_written_and_read_back_is_the_scene_first_read() {
        val scene = board()
        assertEquals(scene, readSceneView(sceneViewJson(scene)))
    }

    @Test
    fun the_report_rows_keep_their_order_object_ids_and_values() {
        val report = groups()
        assertEquals(listOf("blocks", "bars", "plates", "loose", "everything"), report.rows.map { it.id })
        assertEquals(listOf("count", "worth"), report.columns.map { it.id })
        assertEquals(mapOf("count" to 6.0, "worth" to 12.5), report.rows.first().values)
    }

    @Test
    fun a_value_the_report_sends_as_null_stays_null_never_zero() {
        val plates = groups().rows.first { it.id == "plates" }
        assertTrue("worth" in plates.values)
        assertNull(plates.values["worth"])
    }

    @Test
    fun a_row_may_name_no_part_of_the_scene() = assertEquals(emptyList(), groups().rows.first { it.id == "loose" }.objectIds)

    @Test
    fun every_object_id_a_report_names_is_a_part_of_the_scene() {
        val sceneIds = board().footprints!!.items.map { it.id }.toSet()
        assertTrue(boardReports().flatMap { report -> report.rows.flatMap { it.objectIds } }.all { it in sceneIds })
    }

    @Test
    fun the_state_a_spec_binds_carries_every_row_with_its_object_ids_and_values() {
        val rows = reportsState(boardReports()).getValue("groups")["rows"] as List<*>
        val bars = rows[1] as Map<*, *>
        assertEquals("Bars", bars["label"])
        assertEquals(listOf("r1", "r2"), bars["object_ids"])
        assertEquals(mapOf("count" to 2.0, "worth" to 40.0), bars["values"])
        assertEquals(5, rows.size)
    }
}
