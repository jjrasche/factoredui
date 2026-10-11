package ai.factoredui.compose.scene

import ai.factoredui.compose.schema.BindingResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun reportsWith(rows: String, columns: String = """[{"id":"quantity","label":"Quantity","unit":"count"}]""") = """
    {"reports":[{"id":"groups","label":"Groups","columns":$columns,"rows":$rows}]}
""".trimIndent()

private fun refusal(text: String): String = assertFailsWith<ReportsFormatException> { readReports(text) }.message!!

class ReportsReaderTest {
    @Test
    fun a_report_reads_its_columns_and_rows_with_object_ids_and_values() {
        val reports = readReports(reportsWith("""[{"id":"blocks","label":"Blocks","object_ids":["e1","e2"],"values":{"quantity":2,"worth":92.37}}]"""))
        val report = reports.single()
        assertEquals("groups" to "Groups", report.id to report.label)
        assertEquals(listOf(ReportColumn("quantity", "Quantity", "count")), report.columns)
        assertEquals(ReportRow("blocks", "Blocks", listOf("e1", "e2"), mapOf("quantity" to 2.0, "worth" to 92.37)), report.rows.single())
    }

    @Test
    fun a_null_value_stays_null_and_is_never_read_as_zero() {
        val row = readReports(reportsWith("""[{"id":"total","label":"Total","object_ids":[],"values":{"worth":null,"total":6490.97}}]""")).single().rows.single()
        assertTrue("worth" in row.values)
        assertEquals(null, row.values["worth"])
        assertEquals(6490.97, row.values["total"])
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
            refusal(reportsWith("""[{"id":"blocks","label":"Blocks","values":{"quantity":"12"}}]""")),
        )
    }

    @Test
    fun an_object_id_that_is_not_text_is_refused() {
        assertEquals("reports[0].rows[0].object_ids[1] must be text, not 2", refusal(reportsWith("""[{"id":"blocks","label":"Blocks","object_ids":["e1",2]}]""")))
    }

    @Test
    fun rows_that_are_not_a_list_and_a_row_that_is_not_an_object_are_refused() {
        assertEquals("reports[0].rows must be an array, not {}", refusal(reportsWith("{}")))
        assertEquals("reports[0].rows[0] must be an object, not \"blocks\"", refusal(reportsWith("""["blocks"]""")))
    }

    @Test
    fun a_row_or_column_without_an_id_is_refused() {
        assertEquals("reports[0].rows[0] lacks a text id", refusal(reportsWith("""[{"label":"Blocks"}]""")))
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
        val twice = """{"reports":[{"id":"groups","label":"A","columns":[],"rows":[]},{"id":"groups","label":"B","columns":[],"rows":[]}]}"""
        assertEquals("two reports share the id 'groups'", refusal(twice))
    }

    @Test
    fun a_report_becomes_the_plain_rows_a_spec_list_binds_to() {
        val report = readReports(reportsWith("""[{"id":"total","label":"Total","object_ids":["e1"],"values":{"quantity":31,"worth":null}}]""")).single()
        assertEquals(
            listOf(mapOf("id" to "total", "label" to "Total", "object_ids" to listOf("e1"), "values" to mapOf("quantity" to 31.0, "worth" to null))),
            reportRowsState(report),
        )
    }

    @Test
    fun a_row_keeps_its_fractions_unclamped_and_its_flags_beside_its_values() {
        val row = readReports(reportsWith("""[{"id":"r","label":"R","values":{"a":12},"fractions":{"a":1.3,"b":null},"flags":{"a":"over"}}]""")).single().rows.single()
        assertEquals(mapOf("a" to 12.0), row.values)
        assertEquals(mapOf("a" to 1.3, "b" to null), row.fractions)
        assertEquals(mapOf("a" to "over"), row.flags)
    }

    @Test
    fun fractions_and_flags_bind_from_a_list_row_as_row_fractions_and_row_flags() {
        val state = reportsState(readReports(reportsWith("""[{"id":"r","label":"R","values":{"a":12},"fractions":{"a":1.3,"b":null},"flags":{"a":"over"}}]""")))
        val row = (state.getValue("groups")["rows"] as List<*>).single()
        val scope = mapOf("row" to row)
        assertEquals(1.3, BindingResolver.resolveBinding("{row.fractions.a}", scope))
        assertEquals(null, BindingResolver.resolveBinding("{row.fractions.b}", scope))
        assertEquals("over", BindingResolver.resolveBinding("{row.flags.a}", scope))
        assertEquals(12.0, BindingResolver.resolveBinding("{row.values.a}", scope))
    }

    @Test
    fun a_row_with_no_fractions_or_flags_has_neither_key_and_reads_as_before() {
        val report = readReports(reportsWith("""[{"id":"total","label":"Total","object_ids":["e1"],"values":{"quantity":31}}]""")).single()
        assertEquals(null, report.rows.single().fractions)
        assertEquals(null, report.rows.single().flags)
        assertEquals(setOf("id", "label", "object_ids", "values"), reportRowsState(report).single().keys)
    }

    @Test
    fun fractions_or_flags_that_are_not_an_object_and_entries_of_the_wrong_kind_are_refused_naming_where_they_sit() {
        assertEquals("reports[0].rows[0].fractions must be an object, not [1]", refusal(reportsWith("""[{"id":"r","fractions":[1]}]""")))
        assertEquals("reports[0].rows[0].fractions.a must be a number or null, not \"high\"", refusal(reportsWith("""[{"id":"r","fractions":{"a":"high"}}]""")))
        assertEquals("reports[0].rows[0].flags must be an object, not \"over\"", refusal(reportsWith("""[{"id":"r","flags":"over"}]""")))
        assertEquals("reports[0].rows[0].flags.a must be text or null, not 3", refusal(reportsWith("""[{"id":"r","flags":{"a":3}}]""")))
    }

    @Test
    fun an_engine_row_with_per_column_limits_flags_and_fractions_exposes_its_fractions_and_flags() {
        val engineRow = """
            [{"id":"e17","label":"Panel","object_ids":["e17"],"values":{"supports":2,"span_mm":2287.5,"use":0.5695250804362033},
              "limits":{"use":1},"flags":{"use":"within"},"fractions":{"use":0.5695250804362033}}]
        """.trimIndent()
        val columns = """[{"id":"use","label":"Use of the derived limit","unit":"1","kind":"ratio"}]"""
        val row = (reportsState(readReports(reportsWith(engineRow, columns))).getValue("groups")["rows"] as List<*>).single() as Map<*, *>
        assertEquals(mapOf("use" to 0.5695250804362033), row["fractions"])
        assertEquals(mapOf("use" to "within"), row["flags"])
    }

    @Test
    fun the_reports_become_a_state_map_keyed_by_report_id() {
        val state = reportsState(readReports(reportsWith("""[{"id":"blocks","label":"Blocks","object_ids":["e1"],"values":{"quantity":1}}]""")))
        val groups = state.getValue("groups")
        assertEquals("Groups", groups["label"])
        assertEquals(listOf(mapOf("id" to "quantity", "label" to "Quantity", "unit" to "count")), groups["columns"])
        assertEquals(listOf("e1"), ((groups["rows"] as List<*>).single() as Map<*, *>)["object_ids"])
    }
}
