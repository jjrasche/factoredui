package ai.factoredui.worldengine

import ai.factoredui.worldengine.expression.ExprNode
import ai.factoredui.worldengine.expression.ExpressionException
import ai.factoredui.worldengine.expression.MAX_DEPTH
import ai.factoredui.worldengine.expression.MAX_NODES
import ai.factoredui.worldengine.expression.parseExpression
import ai.factoredui.worldengine.expression.projectedAgentTypes
import ai.factoredui.worldengine.expression.referencedNames
import ai.factoredui.worldengine.units.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ParserTest {
    private fun refusalKind(text: String): String = assertFailsWith<ExpressionException> { parseExpression(text) }.kind

    @Test
    fun precedence_binds_multiplication_tighter_than_addition_and_comparison_loosest() {
        val tree = parseExpression("1 + 2 * 3 > 4 and not false")
        val conjunction = assertIs<ExprNode.And>(tree)
        val comparison = assertIs<ExprNode.Comparison>(conjunction.left)
        val sum = assertIs<ExprNode.Arithmetic>(comparison.left)
        assertEquals("+", sum.operator)
        assertIs<ExprNode.Arithmetic>(sum.right)
        assertIs<ExprNode.Not>(conjunction.right)
    }

    @Test
    fun a_number_with_a_unit_is_stored_in_base_units() {
        val acre = assertIs<ExprNode.NumberLiteral>(parseExpression("2 [acre]"))
        assertEquals(87120.0, acre.value)
        assertEquals(Dimension.of(ai.factoredui.worldengine.units.BaseDimension.FT to 2), acre.dimension)
    }

    @Test
    fun an_empty_expression_is_a_syntax_error() {
        assertEquals("syntax", refusalKind("   "))
        assertEquals("expression is empty", assertFailsWith<ExpressionException> { parseExpression(null) }.message)
    }

    @Test
    fun a_function_outside_the_closed_vocabulary_is_an_unknown_word() {
        assertEquals("unknown_word", refusalKind("while(true)"))
        assertEquals("unknown_word", refusalKind("import('os')"))
    }

    @Test
    fun a_keyword_cannot_start_an_operand() {
        val refused = assertFailsWith<ExpressionException> { parseExpression("1 and or") }
        assertEquals("'or' cannot start an operand", refused.message)
    }

    @Test
    fun leftover_tokens_after_a_complete_expression_are_refused() {
        val refused = assertFailsWith<ExpressionException> { parseExpression("1 2") }
        assertEquals("unexpected '2' after a complete expression", refused.message)
    }

    @Test
    fun an_expression_that_ends_early_is_refused() {
        assertEquals("expression ends early", assertFailsWith<ExpressionException> { parseExpression("1 +") }.message)
    }

    @Test
    fun exactly_the_node_bound_parses_and_one_more_is_refused() {
        parseExpression(List(128) { "1" }.joinToString(" + "))
        assertEquals("bound", refusalKind(List(MAX_NODES + 1) { "1" }.joinToString(" + ")))
    }

    @Test
    fun nesting_past_the_depth_bound_is_refused() {
        assertEquals("bound", refusalKind("(".repeat(MAX_DEPTH + 1) + "1" + ")".repeat(MAX_DEPTH + 1)))
        parseExpression("(".repeat(MAX_DEPTH - 1) + "1" + ")".repeat(MAX_DEPTH - 1))
        assertEquals("bound", refusalKind("(".repeat(MAX_DEPTH) + "1" + ")".repeat(MAX_DEPTH)))
    }

    @Test
    fun chained_negation_counts_toward_the_depth_bound() {
        assertEquals("bound", refusalKind("-".repeat(MAX_DEPTH) + "1"))
        parseExpression("-".repeat(MAX_DEPTH - 1) + "1")
    }

    @Test
    fun names_and_projected_agent_types_are_collected_from_the_tree() {
        val tree = parseExpression("pasture_yield * tick_length + projected_support('neighbor')")
        assertEquals(setOf("pasture_yield", "tick_length"), referencedNames(tree))
        assertEquals(setOf("neighbor"), projectedAgentTypes(tree))
    }
}
