package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals

private fun job(name: String, status: String, needsJim: Boolean) = mapOf("name" to name, "status" to status, "needs_jim" to needsJim)

private val jobs = listOf(job("a", "done", false), job("b", "started", false), job("c", "blocked", true), job("d", "stuck", false), job("e", "draft", true))

private fun names(rows: List<Any?>) = rows.map { (it as Map<*, *>)["name"] }

class ListSelectionTest {

    @Test
    fun rows_keep_only_those_whose_field_is_one_of_the_allowed_values() {
        val where = mapOf("field" to "status", "in" to listOf("blocked", "stuck"))
        assertEquals(listOf("c", "d"), names(selectListRows(jobs, where, null)))
    }

    @Test
    fun a_boolean_field_matches_the_text_true() {
        val where = mapOf("field" to "needs_jim", "in" to listOf("true"))
        assertEquals(listOf("c", "e"), names(selectListRows(jobs, where, null)))
    }

    @Test
    fun an_empty_or_missing_allowed_list_keeps_every_row() {
        assertEquals(5, selectListRows(jobs, mapOf("field" to "status", "in" to emptyList<String>()), null).size)
        assertEquals(5, selectListRows(jobs, mapOf("field" to "status"), null).size)
        assertEquals(5, selectListRows(jobs, null, null).size)
    }

    @Test
    fun rows_are_ordered_by_each_key_in_turn_with_unranked_values_last_and_ties_kept_in_order() {
        val order = listOf(
            mapOf("field" to "needs_jim", "rank" to listOf("true")),
            mapOf("field" to "status", "rank" to listOf("blocked", "stuck", "started")),
        )
        assertEquals(listOf("c", "e", "d", "b", "a"), names(selectListRows(jobs, null, order)))
    }

    @Test
    fun a_filter_and_an_order_work_together() {
        val where = mapOf("field" to "status", "in" to listOf("started", "stuck", "blocked"))
        val order = listOf(mapOf("field" to "status", "rank" to listOf("blocked", "stuck", "started")))
        assertEquals(listOf("c", "d", "b"), names(selectListRows(jobs, where, order)))
    }

    @Test
    fun a_single_text_allowed_value_is_taken_as_a_list_of_one() {
        assertEquals(listOf("b"), names(selectListRows(jobs, mapOf("field" to "status", "in" to "started"), null)))
    }
}
