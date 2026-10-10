package ai.factoredui.compose.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun reportsWith(rows: String, columns: String = """[{"id":"quantity","label":"Quantity","unit":"count"}]""") = """
    {"reports":[{"id":"bom","label":"Bill of materials","columns":$columns,"rows":$rows}]}
""".trimIndent()

private fun refusal(text: String): String = assertFailsWith<ReportsFormatException> { readReports(text) }.message!!

class ReportsReaderTest {
    @Test
    fun a_report_reads_its_columns_and_rows_with_object_ids_and_values() {
        val reports = readReports(reportsWith("""[{"id":"pier","label":"Cast pier","object_ids":["e1","e2"],"values":{"quantity":2,"unit_cost_usd":92.37}}]"""))
        val report = reports.single()
        assertEquals("bom" to "Bill of materials", report.id to report.label)
        assertEquals(listOf(ReportColumn("quantity", "Quantity", "count")), report.columns)
        assertEquals(ReportRow("pier", "Cast pier", listOf("e1", "e2"), mapOf("quantity" to 2.0, "unit_cost_usd" to 92.37)), report.rows.single())
    }

    @Test
    fun a_null_value_stays_null_and_is_never_read_as_zero() {
        val row = readReports(reportsWith("""[{"id":"total","label":"Total","object_ids":[],"values":{"unit_cost_usd":null,"line_usd":6490.97}}]""")).single().rows.single()
        assertTrue("unit_cost_usd" in row.values)
        assertEquals(null, row.values["unit_cost_usd"])
        assertEquals(6490.97, row.values["line_usd"])
    }

    @Test
    fun a_row_with_no_object_ids_or_values_reads_as_empty_lists_not_a_failure() {
        val row = readReports(reportsWith("""[{"id":"hardware","label":"Hardware"}]""")).single().rows.single()
        assertEquals(emptyList(), row.objectIds)
        assertEquals(emptyMap(), row.values)
    }

    @Test
    fun a_value_given_as_text_is_refused_naming_where_it_sits() {
        assertEquals(
            "reports[0].rows[0].values.quantity must be a number or null, not \"12\"",
            refusal(reportsWith("""[{"id":"pier","label":"Cast pier","values":{"quantity":"12"}}]""")),
        )
    }

    @Test
    fun an_object_id_that_is_not_text_is_refused() {
        assertEquals("reports[0].rows[0].object_ids[1] must be text, not 2", refusal(reportsWith("""[{"id":"pier","label":"Cast pier","object_ids":["e1",2]}]""")))
    }

    @Test
    fun rows_that_are_not_a_list_and_a_row_that_is_not_an_object_are_refused() {
        assertEquals("reports[0].rows must be an array, not {}", refusal(reportsWith("{}")))
        assertEquals("reports[0].rows[0] must be an object, not \"pier\"", refusal(reportsWith("""["pier"]""")))
    }

    @Test
    fun a_row_or_column_without_an_id_is_refused() {
        assertEquals("reports[0].rows[0] lacks a text id", refusal(reportsWith("""[{"label":"Cast pier"}]""")))
        assertEquals("reports[0].columns[0] lacks a text id", refusal(reportsWith("[]", columns = """[{"label":"Quantity"}]""")))
    }

    @Test
    fun text_that_is_not_a_reports_object_is_refused() {
        assertEquals("the reports are not valid JSON", refusal("{not json"))
        assertEquals("the reports must be a JSON object", refusal("[]"))
        assertEquals("the reports object lacks a reports array", refusal("{}"))
    }

    @Test
    fun two_reports_with_one_id_are_refused_because_a_spec_binds_a_report_by_its_id() {
        val twice = """{"reports":[{"id":"bom","label":"A","columns":[],"rows":[]},{"id":"bom","label":"B","columns":[],"rows":[]}]}"""
        assertEquals("two reports share the id 'bom'", refusal(twice))
    }

    @Test
    fun a_report_becomes_the_plain_rows_a_spec_list_binds_to() {
        val report = readReports(reportsWith("""[{"id":"total","label":"Total","object_ids":["e1"],"values":{"quantity":31,"unit_cost_usd":null}}]""")).single()
        assertEquals(
            listOf(mapOf("id" to "total", "label" to "Total", "object_ids" to listOf("e1"), "values" to mapOf("quantity" to 31.0, "unit_cost_usd" to null))),
            reportRowsState(report),
        )
    }

    @Test
    fun the_reports_become_a_state_map_keyed_by_report_id() {
        val state = reportsState(readReports(reportsWith("""[{"id":"pier","label":"Cast pier","object_ids":["e1"],"values":{"quantity":1}}]""")))
        val bom = state.getValue("bom")
        assertEquals("Bill of materials", bom["label"])
        assertEquals(listOf(mapOf("id" to "quantity", "label" to "Quantity", "unit" to "count")), bom["columns"])
        assertEquals(listOf("e1"), ((bom["rows"] as List<*>).single() as Map<*, *>)["object_ids"])
    }
}
