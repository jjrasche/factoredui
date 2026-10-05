package ai.factoredui.worldengine

import ai.factoredui.worldengine.expression.ExpressionException
import ai.factoredui.worldengine.expression.Token
import ai.factoredui.worldengine.expression.TokenKind
import ai.factoredui.worldengine.expression.tokenizeExpression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TokenizerTest {
    @Test
    fun a_rule_tokenizes_into_names_numbers_units_strings_and_operators() {
        val tokens = tokenizeExpression("neighbors(tile, 1 [tile], 'path') >= 1.5 [tile]")
        assertEquals(
            listOf(
                Token(TokenKind.NAME, "neighbors"), Token(TokenKind.OP, "("), Token(TokenKind.NAME, "tile"),
                Token(TokenKind.OP, ","), Token(TokenKind.NUMBER, "1"), Token(TokenKind.UNIT, "[tile]"),
                Token(TokenKind.OP, ","), Token(TokenKind.STRING, "'path'"), Token(TokenKind.OP, ")"),
                Token(TokenKind.OP, ">="), Token(TokenKind.NUMBER, "1.5"), Token(TokenKind.UNIT, "[tile]"),
            ),
            tokens,
        )
    }

    @Test
    fun a_dotted_name_is_one_token_and_a_second_dot_is_refused() {
        assertEquals(listOf(Token(TokenKind.NAME, "paddock.annual_forage_yield")), tokenizeExpression("paddock.annual_forage_yield"))
        val refused = assertFailsWith<ExpressionException> { tokenizeExpression("a.b.c") }
        assertEquals("syntax", refused.kind)
        assertEquals("unexpected character at 3: '.c'", refused.message)
    }

    @Test
    fun an_unknown_character_reports_its_position_before_whitespace_and_a_twelve_character_window() {
        val refused = assertFailsWith<ExpressionException> { tokenizeExpression("count('path'); count('path')") }
        assertEquals("unexpected character at 13: \"; count('pat\"", refused.message)
    }

    @Test
    fun trailing_whitespace_ends_the_token_stream() {
        assertEquals(listOf(Token(TokenKind.NUMBER, "1")), tokenizeExpression("  1 \t\n"))
    }

    @Test
    fun a_number_with_a_trailing_dot_leaves_the_dot_unmatched() {
        val refused = assertFailsWith<ExpressionException> { tokenizeExpression("1.") }
        assertEquals("unexpected character at 1: '.'", refused.message)
    }
}
