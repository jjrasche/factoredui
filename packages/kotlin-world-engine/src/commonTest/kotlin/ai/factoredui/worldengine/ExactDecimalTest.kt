package ai.factoredui.worldengine

import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.units.ExactRatio
import ai.factoredui.worldengine.units.ceilingQuotient
import ai.factoredui.worldengine.units.exactDecimalOf
import ai.factoredui.worldengine.world.WorldLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExactDecimalTest {
    private fun decimal(literal: String): ExactRatio = exactDecimalOf(Json.parseToJsonElement(literal))

    private fun assertSameValue(expected: ExactRatio, actual: ExactRatio) = assertEquals(0, expected.compareTo(actual))

    private val tileOfTenFeetAndATrace = decimal("10.0000000000000000000000000001").times(ExactRatio.MM_PER_FT)

    @Test
    fun values_differing_only_past_the_thirtieth_digit_order_strictly() {
        assertTrue(decimal("7620") < decimal("7620.000000000000000000000000000001"))
        assertTrue(decimal("7620.000000000000000000000000000001") > decimal("7620"))
        assertTrue(decimal("-7620.000000000000000000000000000001") < decimal("-7620"))
    }

    @Test
    fun spellings_of_one_value_compare_equal() {
        assertSameValue(decimal("7620"), decimal("7620.000"))
        assertSameValue(decimal("7620"), decimal("7.62e3"))
        assertSameValue(decimal("7620"), decimal("762000E-2"))
        assertSameValue(decimal("0"), decimal("-0.0"))
    }

    @Test
    fun a_negative_value_is_below_every_positive_one() {
        assertTrue(decimal("-0.000000000000000000000000000001") < ExactRatio.ZERO)
        assertTrue(decimal("-123456789012345678901234567890") < decimal("0.000000000000000000000000000001"))
    }

    @Test
    fun times_multiplies_numerators_and_denominators_exactly() {
        assertSameValue(decimal("30.48"), decimal("0.1").times(ExactRatio.MM_PER_FT))
        assertSameValue(decimal("3048.00000000000000000000000003048"), tileOfTenFeetAndATrace)
        assertSameValue(decimal("0.25"), decimal("-0.5").times(decimal("-0.5")))
    }

    @Test
    fun times_whole_is_exact_at_any_size_and_sign() {
        assertSameValue(decimal("3048"), decimal("304.8").timesWhole(10))
        assertSameValue(decimal("246913578024691357803"), decimal("123456789012345678901.5").timesWhole(2))
        assertSameValue(decimal("-9223372036854775808"), decimal("1").timesWhole(Long.MIN_VALUE))
        assertSameValue(decimal("-3048.00000000000000000000000003048"), tileOfTenFeetAndATrace.timesWhole(-1))
    }

    @Test
    fun to_double_is_the_nearest_double_for_small_terms_and_close_for_long_ones() {
        assertEquals(-30.48, decimal("-30.48").toDouble())
        assertEquals(7620.0, decimal("7620.000000000000000000000000000001").toDouble(), 1e-9)
        assertEquals(1.5e300, decimal("15e299").toDouble(), 1e286)
        assertEquals(2.5e-300, decimal("25e-301").toDouble(), 1e-314)
    }

    @Test
    fun a_non_number_is_an_invalid_fraction_literal() {
        val refused = assertFailsWith<MalformedDataException> { exactDecimalOf(JsonPrimitive("12")) }
        assertEquals("invalid literal for Fraction: 12", refused.message)
    }

    @Test
    fun an_exact_multiple_of_a_thirty_digit_tile_spans_exactly_that_many_tiles() {
        assertEquals(3, ceilingQuotient(decimal("9144.00000000000000000000000009144"), tileOfTenFeetAndATrace))
        assertEquals(3, ceilingQuotient(decimal("9144.00000000000000000000000009143"), tileOfTenFeetAndATrace))
    }

    @Test
    fun one_unit_past_an_exact_multiple_spans_one_more_tile() {
        assertEquals(4, ceilingQuotient(decimal("9144.00000000000000000000000009145"), tileOfTenFeetAndATrace))
    }

    @Test
    fun a_quotient_with_no_usable_double_estimate_is_found_by_search() {
        assertEquals(3, ceilingQuotient(decimal("3e-400"), decimal("1e-400")))
        assertEquals(4, ceilingQuotient(decimal("3.000000000000000000000000000001e-400"), decimal("1e-400")))
        assertEquals(1_234_567, ceilingQuotient(decimal("1234566.5e-400"), decimal("1e-400")))
    }

    @Test
    fun a_quotient_is_capped_at_two_million_tiles() {
        assertEquals(2_000_000, ceilingQuotient(decimal("1e30"), decimal("3048")))
        assertEquals(2_000_000, ceilingQuotient(decimal("6096000000"), decimal("3048")))
    }

    @Test
    fun a_negative_quotient_rounds_toward_positive_infinity() {
        assertEquals(-3, ceilingQuotient(decimal("-7"), decimal("2")))
        assertEquals(-3, ceilingQuotient(decimal("7"), decimal("-2")))
    }

    @Test
    fun a_zero_unit_is_a_division_by_zero() {
        assertFailsWith<ArithmeticException> { ceilingQuotient(decimal("1"), decimal("0.0")) }
    }

    @Test
    fun a_twenty_one_digit_footprint_mm_derives_its_tiles() {
        val widened = REFERENCE_WORLD_FILES.getValue(LIDAR_FILE).replace("2400,", "7620.00000000000000001,")
        val world = WorldLoader.load(LIDAR_FILE, referenceLibrary(mapOf(LIDAR_FILE to widened)))
        assertEquals(listOf(2L, 1L), world.derivedFootprint("shed"))
    }
}
