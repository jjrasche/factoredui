package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun column(id: String, label: String, unit: String? = null, kind: String? = null, nullDisplay: String? = null) =
    mapOf("id" to id, "label" to label, "unit" to unit, "kind" to kind, "null_display" to nullDisplay).filterValues { it != null }

private fun view(vararg columns: Map<String, Any?>) = mapOf("id" to "parts", "label" to "Parts", "columns" to columns.toList(), "rows" to emptyList<Any>(), "version" to 1)

private val PROFILE = mapOf(
    "units" to mapOf(
        "usd" to "usd",
        "mm" to "feet_inches",
        "kg*mm/second^2" to mapOf("scale" to 0.000224809, "suffix" to " lbf", "decimals" to 0),
        "kg/mm/second^2" to mapOf("format" to mapOf("scale" to 145.0, "suffix" to " psi"), "label" to "psf"),
    ),
    "kinds" to mapOf("ratio" to "percent", "length" to "feet_inches"),
)

private fun layoutOf(view: Map<String, Any?>, overrides: Any? = null, showUnit: Boolean = true, missing: String = "–"): TableLayout =
    tableLayout(readTableView(view)!!, readDisplayProfile(PROFILE), readColumnOverrides(overrides), showUnit, missing)

class TableLayoutTest {

    @Test
    fun a_columns_format_comes_from_its_override_then_its_unit_then_its_kind() {
        val both = view(column("cost", "Cost", unit = "usd", kind = "length"))
        assertEquals("usd", layoutOf(both).columns.single().format)
        assertEquals("percent", layoutOf(both, mapOf("cost" to mapOf("format" to "percent"))).columns.single().format)
        assertEquals("feet_inches", layoutOf(view(column("span", "Span", unit = "furlong", kind = "length"))).columns.single().format)
        assertEquals("percent", layoutOf(view(column("use", "Use", kind = "ratio"))).columns.single().format)
    }

    @Test
    fun a_column_with_no_unit_or_kind_is_drawn_plain_with_no_notice() {
        val layout = layoutOf(view(column("note", "Note")))
        assertNull(layout.columns.single().format)
        assertNull(layout.notice)
    }

    @Test
    fun units_with_no_entry_draw_plain_and_the_table_says_so_once_naming_them() {
        val layout = layoutOf(view(column("f", "Force", unit = "kg*mm/second"), column("e", "Energy", unit = "joule"), column("e2", "Energy again", unit = "joule")))
        assertEquals(listOf(null, null, null), layout.columns.map { it.format })
        assertEquals("table 'parts': no display entry for unit(s) 'kg*mm/second', 'joule'; drawn as plain figures", layout.notice)
    }

    @Test
    fun a_kind_with_no_entry_is_named_only_when_its_column_has_no_unit_entry() {
        assertEquals("table 'parts': no display entry for kind(s) 'mass'; drawn as plain figures", layoutOf(view(column("w", "Weight", kind = "mass"))).notice)
        assertNull(layoutOf(view(column("c", "Cost", unit = "usd", kind = "mass"))).notice)
    }

    @Test
    fun the_header_shows_the_unit_label_from_the_entry_its_object_suffix_or_nothing_for_a_named_format() {
        val layout = layoutOf(view(column("p", "Pressure", unit = "kg/mm/second^2"), column("f", "Force", unit = "kg*mm/second^2"), column("c", "Cost", unit = "usd")))
        assertEquals(listOf("Pressure (psf)", "Force (lbf)", "Cost"), layout.columns.map { it.header })
    }

    @Test
    fun an_override_label_is_shown_as_written_and_show_unit_false_hides_unit_labels() {
        val pressure = view(column("p", "Pressure", unit = "kg/mm/second^2"))
        assertEquals("Load", layoutOf(pressure, mapOf("p" to mapOf("label" to "Load"))).columns.single().header)
        assertEquals("Pressure", layoutOf(pressure, showUnit = false).columns.single().header)
    }

    @Test
    fun a_columns_null_display_beats_the_tables_missing_text() {
        val layout = layoutOf(view(column("a", "A", unit = "usd", nullDisplay = "not priced"), column("b", "B", unit = "usd")), missing = "none yet")
        assertEquals(listOf("not priced", "none yet"), layout.columns.map { it.missing })
    }

    @Test
    fun a_hidden_column_is_left_out_and_numeric_columns_align_right() {
        val layout = layoutOf(view(column("a", "A", unit = "usd"), column("b", "B"), column("c", "C", unit = "usd")), mapOf("c" to mapOf("hidden" to true)))
        assertEquals(listOf("a" to "right", "b" to "left"), layout.columns.map { it.id to it.align })
    }
}
