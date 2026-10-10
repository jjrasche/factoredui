package ai.factoredui.compose.scene

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun resourceText(name: String): String = EngineHouseReportsTest::class.java.getResource(name)!!.readText()

private fun engineReports(): List<Report> = readReports(resourceText("/house-floor.reports.json"))

private fun List<Report>.named(id: String): Report = first { it.id == id }

class EngineHouseReportsTest {
    @Test
    fun the_engine_sends_a_bill_of_materials_and_a_capacity_report_for_beams_and_for_panels() {
        assertEquals(listOf("bom", "beam_capacity", "panel_capacity"), engineReports().map { it.id })
    }

    @Test
    fun the_bill_of_materials_has_seven_rows_ending_in_the_engine_total_of_6490_dollars_97() {
        val bom = engineReports().named("bom")
        assertEquals(listOf("pier", "edge_beam", "interior_beam", "panel", "post_sticks", "hardware", "total"), bom.rows.map { it.id })
        assertEquals(listOf("quantity", "unit_cost_usd", "line_usd"), bom.columns.map { it.id })
        val total = bom.rows.last()
        assertTrue(abs(total.values.getValue("line_usd")!! - 6490.97) < 1e-6, "${total.values}")
        assertEquals(31.0, total.values["quantity"])
        assertEquals(31, total.objectIds.size)
    }

    @Test
    fun the_total_row_has_no_unit_cost_and_the_reader_keeps_it_null() {
        val total = engineReports().named("bom").rows.first { it.id == "total" }
        assertTrue("unit_cost_usd" in total.values)
        assertEquals(null, total.values["unit_cost_usd"])
    }

    @Test
    fun no_bom_row_states_a_weight() {
        assertTrue(engineReports().named("bom").rows.none { "weight_kg" in it.values })
    }

    @Test
    fun the_post_sticks_and_hardware_rows_name_no_part_of_the_scene() {
        val rows = engineReports().named("bom").rows.associateBy { it.id }
        assertEquals(emptyList(), rows.getValue("post_sticks").objectIds)
        assertEquals(emptyList(), rows.getValue("hardware").objectIds)
        assertEquals(800.0, rows.getValue("hardware").values["line_usd"])
    }

    @Test
    fun each_capacity_report_has_one_row_per_part_keyed_by_the_part_id() {
        val beams = engineReports().named("beam_capacity")
        val panels = engineReports().named("panel_capacity")
        assertEquals(listOf("e13", "e14", "e15", "e16"), beams.rows.map { it.id })
        assertEquals((17..31).map { "e$it" }, panels.rows.map { it.id })
        assertTrue((beams.rows + panels.rows).all { it.objectIds == listOf(it.id) })
        assertEquals(listOf("supports", "span_mm", "bending_use", "shear_use", "live_deflection_use", "total_deflection_use"), beams.columns.map { it.id })
        assertEquals(mapOf("supports" to 2.0, "span_mm" to 2440.0, "use" to 0.7576350143203), panels.rows.first { it.id == "e22" }.values)
    }

    @Test
    fun the_bom_state_a_spec_binds_carries_every_row_with_its_object_ids_and_values() {
        val rows = reportsState(engineReports()).getValue("bom")["rows"] as List<*>
        val pier = rows.first() as Map<*, *>
        assertEquals("Cast pier", pier["label"])
        assertEquals((1..12).map { "e$it" }, pier["object_ids"])
        assertEquals(mapOf("quantity" to 12.0, "unit_cost_usd" to 92.37, "line_usd" to 1108.44), pier["values"])
        assertEquals(7, rows.size)
    }
}
