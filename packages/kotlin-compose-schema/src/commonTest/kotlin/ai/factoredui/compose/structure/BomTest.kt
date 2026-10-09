package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.SceneType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val PART_TYPES = listOf(
    SceneType("joist", "Joist: treated 2x10", unitCostUsd = 18.5, unitWeightKg = 10.0),
    SceneType("pier", "Concrete pier", unitCostUsd = 42.0, unitWeightKg = null, unitWeightNullReason = "no weight is banked for a pier"),
    SceneType("beam", "Beam: three-ply 2x12"),
)

private val DECK = houseScene(types = PART_TYPES)

private val PARTS = listOf(
    shown(part("pier-0", "pier")),
    shown(part("joist-0", "joist", level = 1)),
    shown(part("joist-1", "joist", level = 1)),
    shown(part("beam-0", "beam", level = 1)),
    shown(part("joist-2", "joist", level = 1)),
)

private fun lineOf(typeId: String) = bomLines(DECK, PARTS).first { it.typeId == typeId }

class BomTest {
    @Test
    fun parts_of_one_type_become_one_line_with_the_quantity_and_every_id() {
        val joist = lineOf("joist")
        assertEquals(3, joist.quantity)
        assertEquals(listOf("joist-0", "joist-1", "joist-2"), joist.ids)
        assertEquals("Joist: treated 2x10", joist.label)
    }

    @Test
    fun a_lines_cost_and_weight_are_the_unit_figure_times_the_quantity() {
        val joist = lineOf("joist")
        assertEquals(18.5, joist.unitCostUsd)
        assertEquals(55.5, joist.costUsd)
        assertEquals(10.0, joist.unitWeightKg)
        assertEquals(30.0, joist.weightKg)
    }

    @Test
    fun a_type_with_no_price_in_the_file_has_a_null_cost_not_zero() {
        val beam = lineOf("beam")
        assertNull(beam.unitCostUsd)
        assertNull(beam.costUsd)
        assertNull(beam.unitWeightKg)
        assertNull(beam.weightKg)
    }

    @Test
    fun a_type_priced_but_not_weighed_keeps_its_price_and_nulls_its_weight() {
        val pier = lineOf("pier")
        assertEquals(42.0, pier.costUsd)
        assertNull(pier.weightKg)
    }

    @Test
    fun a_part_whose_type_the_file_does_not_declare_still_gets_a_line_labelled_by_its_type_id() {
        val stray = bomLines(DECK, listOf(shown(part("x-0", "mystery"))))
        assertEquals(1, stray.size)
        assertEquals("mystery", stray[0].label)
        assertNull(stray[0].costUsd)
    }

    @Test
    fun lines_come_in_the_order_each_type_first_appears_which_is_lowest_level_first() =
        assertEquals(listOf("pier", "joist", "beam"), bomLines(DECK, PARTS).map { it.typeId })

    @Test
    fun a_zero_price_is_a_price() {
        val free = bomLines(houseScene(types = listOf(SceneType("a", "A", unitCostUsd = 0.0))), listOf(shown(part("a-0", "a"))))
        assertEquals(0.0, free[0].costUsd)
    }

    @Test
    fun a_price_that_is_not_finite_is_no_price() {
        val broken = bomLines(houseScene(types = listOf(SceneType("a", "A", unitCostUsd = Double.NaN))), listOf(shown(part("a-0", "a"))))
        assertNull(broken[0].costUsd)
    }

    @Test
    fun the_totals_add_the_lines_that_have_a_figure_and_count_the_lines_that_do_not() {
        val totals = bomTotals(bomLines(DECK, PARTS))
        assertEquals(5, totals.quantity)
        assertEquals(97.5, totals.costUsd)
        assertEquals(1, totals.unpricedLines)
        assertEquals(30.0, totals.weightKg)
        assertEquals(2, totals.unweighedLines)
    }

    @Test
    fun totals_with_no_figure_at_all_are_null_not_zero() {
        val totals = bomTotals(bomLines(houseScene(types = listOf(SceneType("a", "A"))), listOf(shown(part("a-0", "a")))))
        assertNull(totals.costUsd)
        assertNull(totals.weightKg)
    }

    @Test
    fun dollars_show_a_dollar_sign_thousands_commas_and_cents_only_when_there_are_cents() {
        assertEquals("$18.50", dollarsText(18.5))
        assertEquals("$1,500", dollarsText(1500.0))
        assertEquals("$0", dollarsText(0.0))
        assertEquals("$1,234,567.89", dollarsText(1234567.885))
        assertEquals("–", dollarsText(null))
    }

    @Test
    fun weights_are_shown_in_pounds_to_the_nearest_tenth_under_100_and_whole_above() {
        assertEquals("22.0 lb", poundsText(10.0))
        assertEquals("220 lb", poundsText(100.0))
        assertEquals("1.1 lb", poundsText(0.5))
        assertEquals("2,205 lb", poundsText(1000.0))
        assertEquals("–", poundsText(null))
    }

    @Test
    fun a_lines_card_text_gives_the_unit_and_line_figures() =
        assertEquals(BomLineText("3 × Joist: treated 2x10", "$18.50 each · $55.50 for 3", "22.0 lb each · 66.1 lb for 3"), bomLineText(lineOf("joist")))

    @Test
    fun a_line_with_no_price_says_the_file_has_none() = assertEquals("no price in the scene file", bomLineText(lineOf("beam")).priceLine)

    @Test
    fun a_line_with_no_weight_says_the_weight_is_not_known_and_gives_the_files_reason() =
        assertEquals("weight not known: no weight is banked for a pier", bomLineText(lineOf("pier")).weightLine)

    @Test
    fun a_line_with_no_weight_and_no_reason_says_only_that_the_weight_is_not_known() =
        assertEquals("weight not known", bomLineText(lineOf("beam")).weightLine)

    @Test
    fun the_totals_text_names_how_many_lines_the_sum_leaves_out() =
        assertEquals(BomTotalsText("$97.50 (1 unpriced)", "66.1 lb (2 unweighed)"), bomTotalsText(bomTotals(bomLines(DECK, PARTS))))

    @Test
    fun totals_with_no_figure_say_no_prices_and_weight_not_known() =
        assertEquals(BomTotalsText("no prices in the scene file", "weight not known"), bomTotalsText(BomTotals(3, null, 3, null, 3)))
}
