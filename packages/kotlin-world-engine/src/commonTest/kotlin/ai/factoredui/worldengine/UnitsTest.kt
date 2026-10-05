package ai.factoredui.worldengine

import ai.factoredui.worldengine.expression.ExpressionException
import ai.factoredui.worldengine.units.BaseDimension
import ai.factoredui.worldengine.units.Dimension
import ai.factoredui.worldengine.units.describeDimension
import ai.factoredui.worldengine.units.parseUnit
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UnitsTest {
    @Test
    fun blank_and_one_are_dimensionless_with_factor_one() {
        assertEquals(1.0, parseUnit("").factor)
        assertEquals(Dimension.NONE, parseUnit(null).dimension)
        assertEquals(Dimension.NONE, parseUnit(" 1 ").dimension)
    }

    @Test
    fun an_acre_is_43560_square_feet() {
        val acre = parseUnit("acre")
        assertEquals(43560.0, acre.factor)
        assertEquals(Dimension.of(BaseDimension.FT to 2), acre.dimension)
    }

    @Test
    fun a_compound_unit_multiplies_factors_and_adds_exponents_in_order() {
        val yieldUnit = parseUnit("ton / acre/year")
        assertEquals(2000.0 * 43560.0.pow(-1) * 8760.0.pow(-1), yieldUnit.factor)
        assertEquals(Dimension.of(BaseDimension.LB to 1, BaseDimension.FT to -2, BaseDimension.HOUR to -1), yieldUnit.dimension)
    }

    @Test
    fun a_power_raises_the_unit() {
        assertEquals(Dimension.of(BaseDimension.FT to 2), parseUnit("ft^2").dimension)
        assertEquals(Dimension.NONE, parseUnit("ft^0").dimension)
    }

    @Test
    fun a_malformed_unit_is_a_syntax_error_before_any_unknown_name() {
        val refused = assertFailsWith<ExpressionException> { parseUnit("furlong * *") }
        assertEquals("syntax", refused.kind)
        assertEquals("unit 'furlong * *' is not name(^n) joined by * or /", refused.message)
    }

    @Test
    fun an_unlisted_unit_is_an_unknown_word() {
        val refused = assertFailsWith<ExpressionException> { parseUnit("ft/furlong") }
        assertEquals("unknown_word", refused.kind)
        assertEquals("unit 'furlong' is not in the unit table", refused.message)
    }

    @Test
    fun a_dimension_describes_itself_in_base_order() {
        assertEquals("dimensionless", describeDimension(Dimension.NONE))
        assertEquals("ft^2 tile^-1", describeDimension(parseUnit("sq_ft/tile").dimension))
        assertEquals("lb hour^-1", describeDimension(parseUnit("ton/year").dimension))
    }
}
