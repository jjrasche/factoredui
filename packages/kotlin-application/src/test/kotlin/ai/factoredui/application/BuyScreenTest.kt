package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val VIEW = """{ "title": "What to buy", "world": "w" }"""

private const val ANSWER = """{ "objects": [], "bill_of_materials": { "nesting": "none", "total_usd": 130.0, "lines": [
  { "stock_item": "fir-2x10-12ft", "count": 6, "unit_price_usd": 16.5, "total_usd": 99.0, "price_source": "tp.clip_2x10_fir" },
  { "stock_item": "pvc-sch40-4in", "count": 2, "unit_price_usd": 15.5, "total_usd": 31.0, "price_source": "aq/diy/price-pvc-4in" } ] } }"""

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + (this["children"]?.jsonArray ?: emptyList()).flatMap { it.jsonObject.nodes() }

private fun specOf(answer: String = ANSWER) = buySpec(readBuyScreen(VIEW), Json.parseToJsonElement(answer).jsonObject)

private fun viewObject(): JsonObject = specOf()["state"]!!.jsonObject["bom.view"]!!.jsonObject

class BuyScreenTest {
    @Test
    fun the_engines_total_is_shown_on_top() {
        val total = specOf()["root"]!!.jsonObject.nodes().first { it["id"]!!.jsonPrimitive.content == "buy.total" }["props"]!!.jsonObject
        assertEquals(130.0, total["value"]!!.jsonPrimitive.double)
        assertEquals("usd", total["format"]!!.jsonPrimitive.content)
    }

    @Test
    fun one_table_row_per_line_of_the_bill_with_the_engines_figures() {
        val rows = viewObject()["rows"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("fir-2x10-12ft", "pvc-sch40-4in"), rows.map { it["label"]!!.jsonPrimitive.content })
        val first = rows.first()["values"]!!.jsonObject
        assertEquals(6.0, first["count"]!!.jsonPrimitive.double)
        assertEquals(16.5, first["unit_price"]!!.jsonPrimitive.double)
        assertEquals(99.0, first["total"]!!.jsonPrimitive.double)
        assertEquals("tp.clip_2x10_fir", first["source"]!!.jsonPrimitive.content)
    }

    @Test
    fun the_columns_are_count_each_total_and_source_and_the_table_sorts_by_total_first() {
        assertEquals(listOf("count", "unit_price", "total", "source"), viewObject()["columns"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })
        val table = specOf()["root"]!!.jsonObject.nodes().first { it["type"]!!.jsonPrimitive.content == "table" }["props"]!!.jsonObject
        assertEquals("{bom.sort_by}", table["sort_by"]!!.jsonPrimitive.content)
        assertEquals("total", specOf()["state"]!!.jsonObject["bom.sort_by"]!!.jsonPrimitive.content)
    }

    @Test
    fun an_answer_with_no_bill_is_refused() {
        assertFailsWith<BuyFormatException> { specOf("""{ "objects": [] }""") }
    }

    @Test
    fun the_page_scrolls() {
        assertEquals("scrollview", specOf()["root"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(specOf().toString().contains("What to buy"))
    }
}
