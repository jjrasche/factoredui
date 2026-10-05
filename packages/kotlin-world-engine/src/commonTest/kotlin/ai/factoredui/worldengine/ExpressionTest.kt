package ai.factoredui.worldengine

import ai.factoredui.worldengine.expression.Evaluation
import ai.factoredui.worldengine.expression.ExpressionException
import ai.factoredui.worldengine.expression.Scope
import ai.factoredui.worldengine.expression.ScopeSite
import ai.factoredui.worldengine.expression.Value
import ai.factoredui.worldengine.expression.ValueType
import ai.factoredui.worldengine.expression.checkExpression
import ai.factoredui.worldengine.expression.compensatedSum
import ai.factoredui.worldengine.expression.parseExpression
import ai.factoredui.worldengine.log.EventLog
import ai.factoredui.worldengine.units.BaseDimension
import ai.factoredui.worldengine.units.Dimension
import ai.factoredui.worldengine.world.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExpressionTest {
    private val parcel: World = parcelWorld()

    private fun typeOf(text: String, site: ScopeSite = ScopeSite.EQUATION, agentType: String? = null): ValueType =
        checkExpression(parseExpression(text), Scope(parcel, site, agentType))

    private fun refusal(text: String, site: ScopeSite = ScopeSite.EQUATION): ExpressionException =
        assertFailsWith { typeOf(text, site) }

    private fun valueOf(text: String, world: World = parcel, log: EventLog = EventLog(world), tile: Pair<Int, Int>? = null): Value {
        val state = log.stateOf()
        val instance = tile?.let { state.instanceAt(it.first, it.second) }
        return Evaluation(world, state, tileInstance = instance).valueOf(parseExpression(text))
    }

    @Test
    fun multiplication_combines_dimensions_and_division_cancels_them() {
        assertEquals(ValueType.Num(Dimension.of(BaseDimension.FT to 2)), typeOf("count('paddock') * tile_area"))
        assertEquals(ValueType.Num(Dimension.NONE), typeOf("1 [year] / 1 [day]"))
    }

    @Test
    fun a_comparison_needs_equal_dimensions_and_strings_compare_only_for_equality() {
        assertEquals(ValueType.Bool, typeOf("'a' == 'b'"))
        assertEquals("'<' cannot compare str with str", refusal("'a' < 'b'").message)
        assertEquals("'>=' compares tile with dimensionless", refusal("count('path') >= 1").message)
    }

    @Test
    fun tile_and_self_exist_only_in_their_own_sites() {
        assertEquals("'tile' exists only inside a rule", refusal("edge(tile)").message)
        assertEquals(ValueType.Num(Dimension.of(BaseDimension.FT to 1)), typeOf("edge(tile)", ScopeSite.RULE))
        assertEquals("'self' exists only inside an agent's weight or utility", refusal("self.distance_ft").message)
        assertEquals(ValueType.Num(Dimension.of(BaseDimension.FT to 1)), typeOf("self.distance_ft", ScopeSite.AGENT, "neighbor"))
    }

    @Test
    fun a_score_is_visible_to_scores_but_not_to_equations() {
        assertEquals("name 'area_path' is not a built-in, equation, stock or score here", refusal("area_path").message)
        assertEquals(ValueType.Num(Dimension.of(BaseDimension.FT to 2)), typeOf("area_path", ScopeSite.SCORING))
    }

    @Test
    fun function_arguments_are_checked_for_arity_quoting_and_vocabulary() {
        assertEquals("count takes 1 arguments, found 2", refusal("count('path', 'pond')").message)
        assertEquals("count: use 'driveway' is not an object type, #tag or 'any'", refusal("count('driveway')").message)
        assertEquals(ValueType.Num(Dimension.of(BaseDimension.TILE to 1)), typeOf("count('#open') + count('any')"))
        assertEquals("side direction must be one of ('north', 'south', 'east', 'west')", refusal("side(tile, 'up', 'path')", ScopeSite.RULE).message)
        assertEquals("neighbors radius must be in tile, found dimensionless", refusal("neighbors(tile, 1, 'path')", ScopeSite.RULE).message)
        assertEquals("projected_support takes a quoted agent type the world declares", refusal("projected_support('cat')").message)
        assertEquals("if branches differ: ft and dimensionless", refusal("if(true, 1 [ft], 1)").message)
    }

    @Test
    fun a_text_property_is_a_string_and_a_numeric_one_keeps_its_unit() {
        assertEquals(ValueType.Num(Dimension.of(BaseDimension.FT to 1)), typeOf("parcel.structure_setback"))
        assertEquals(ValueType.Str, typeOf("parcel.zoning"))
    }

    @Test
    fun evaluation_follows_precedence_and_short_circuits() {
        assertEquals(Value.Num(-5.0), valueOf("-2 * 3 + 1"))
        assertEquals(Value.Bool(true), valueOf("not (1 [ft] > 2 [ft]) and false or true"))
        assertEquals(Value.Bool(false), valueOf("false and 1 / 0 > 0"))
        assertEquals(Value.Num(2.0), valueOf("min(3 [ft], 2 [ft])"))
        assertEquals(Value.Num(43560.0), valueOf("max(1 [acre], 40000 [sq_ft])"))
    }

    @Test
    fun division_by_zero_is_an_error_the_world_must_guard() {
        val refused = assertFailsWith<ExpressionException> { valueOf("1 / (2 - 2)") }
        assertEquals("divide_by_zero", refused.kind)
    }

    @Test
    fun built_in_names_read_the_world_and_its_clock() {
        assertEquals(Value.Num(625.0), valueOf("tile_area"))
        assertEquals(Value.Num(24.0), valueOf("tick_length"))
        assertEquals(Value.Num(50.0), valueOf("parcel.structure_setback"))
        assertEquals(Value.Num(0.0), valueOf("projected_support('neighbor')"))
    }

    @Test
    fun grid_functions_count_tiles_by_footprint_side_ring_edge_and_distance() {
        val log = EventLog(parcel)
        log.layPath(6 to 3, 6 to 2)
        log.place("van_pad", 7, 3)
        log.place("hoop_house", 3, 12)
        assertEquals(Value.Num(1.0), valueOf("neighbors(tile, 1 [tile], 'path')", log = log, tile = 7 to 3))
        assertEquals(Value.Num(2.0), valueOf("neighbors(tile, 2 [tile], 'path')", log = log, tile = 7 to 3))
        assertEquals(Value.Num(1.0), valueOf("side(tile, 'west', 'path')", log = log, tile = 7 to 3))
        assertEquals(Value.Num(75.0), valueOf("edge(tile)", log = log, tile = 7 to 3))
        assertEquals(Value.Num(75.0), valueOf("edge(tile)", log = log, tile = 4 to 12))
        assertEquals(Value.Num(25.0), valueOf("distance(tile, 'path')", log = log, tile = 7 to 3))
        assertEquals(Value.Num(Double.POSITIVE_INFINITY), valueOf("distance('pond', 'path')", log = log))
        assertEquals(Value.Num(5.0), valueOf("count('#open') + count('van_pad') + count('#structure')", log = log))
    }

    @Test
    fun sum_adds_a_property_over_placed_instances_with_compensation() {
        assertEquals(1.0, compensatedSum(List(10) { 0.1 }))
        assertEquals(0.30000000000000004, 0.1 + 0.2)
        val log = EventLog(dungeonWorld())
        log.place("floor", 1, 1)
        log.place("monster", 2, 1)
        log.place("treasure", 2, 2)
        log.place("treasure", 6, 6)
        assertEquals(Value.Num(1.0), valueOf("sum('guarded')", world = log.world, log = log))
    }

    @Test
    fun a_treasure_placed_beside_a_monster_is_guarded_by_the_effect_rule() {
        val log = EventLog(dungeonWorld())
        log.place("floor", 1, 1)
        log.place("monster", 2, 1)
        log.place("treasure", 2, 2)
        val treasure = log.stateOf().instanceAt(2, 2)!!
        assertEquals(Value.Num(1.0), treasure.props["guarded"])
        assertTrue(log.stateOf().instanceAt(1, 1)!!.props.isEmpty())
    }
}
