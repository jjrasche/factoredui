package ai.factoredui.worldengine.expression

import ai.factoredui.worldengine.text.isPythonSpace
import ai.factoredui.worldengine.text.pythonStrRepr

class ExpressionException(val kind: String, override val message: String) : Exception(message)

enum class TokenKind { NUMBER, UNIT, STRING, NAME, OP }

data class Token(val kind: TokenKind, val text: String)

private val TWO_CHARACTER_OPERATORS = listOf("<=", ">=", "==", "!=")
private const val ONE_CHARACTER_OPERATORS = "-+*/<>(),"

fun tokenizeExpression(text: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var position = 0
    while (position < text.length) {
        if (text.substring(position).all { isPythonSpace(it) }) break
        val start = skipSpace(text, position)
        val token = matchToken(text, start)
            ?: throw ExpressionException("syntax", "unexpected character at $position: ${pythonStrRepr(text.substring(position, minOf(text.length, position + 12)))}")
        tokens += token
        position = start + token.text.length
    }
    return tokens
}

private fun skipSpace(text: String, from: Int): Int {
    var at = from
    while (at < text.length && isPythonSpace(text[at])) at++
    return at
}

private fun matchToken(text: String, start: Int): Token? =
    matchNumber(text, start) ?: matchUnit(text, start) ?: matchString(text, start) ?: matchName(text, start) ?: matchOperator(text, start)

private fun digitRunEnd(text: String, from: Int): Int {
    var at = from
    while (at < text.length && text[at].isDigit()) at++
    return at
}

private fun matchNumber(text: String, start: Int): Token? {
    val integerEnd = digitRunEnd(text, start)
    if (integerEnd == start) return null
    val fractionEnd = if (text.getOrNull(integerEnd) == '.') digitRunEnd(text, integerEnd + 1) else integerEnd
    val end = if (fractionEnd > integerEnd + 1) fractionEnd else integerEnd
    return Token(TokenKind.NUMBER, text.substring(start, end))
}

private fun matchUnit(text: String, start: Int): Token? {
    if (text.getOrNull(start) != '[') return null
    var at = start + 1
    while (at < text.length && text[at] != ']' && text[at] != '[') at++
    if (text.getOrNull(at) != ']') return null
    return Token(TokenKind.UNIT, text.substring(start, at + 1))
}

private fun matchString(text: String, start: Int): Token? {
    if (text.getOrNull(start) != '\'') return null
    val close = text.indexOf('\'', start + 1)
    if (close < 0) return null
    return Token(TokenKind.STRING, text.substring(start, close + 1))
}

private fun isNameStart(character: Char?): Boolean = character != null && (character in 'A'..'Z' || character in 'a'..'z' || character == '_')

private fun isNamePart(character: Char): Boolean = isNameStart(character) || character in '0'..'9'

private fun identifierEnd(text: String, from: Int): Int {
    var at = from + 1
    while (at < text.length && isNamePart(text[at])) at++
    return at
}

private fun matchName(text: String, start: Int): Token? {
    if (!isNameStart(text.getOrNull(start))) return null
    val headEnd = identifierEnd(text, start)
    val hasMember = text.getOrNull(headEnd) == '.' && isNameStart(text.getOrNull(headEnd + 1))
    val end = if (hasMember) identifierEnd(text, headEnd + 1) else headEnd
    return Token(TokenKind.NAME, text.substring(start, end))
}

private fun matchOperator(text: String, start: Int): Token? {
    val pair = TWO_CHARACTER_OPERATORS.firstOrNull { text.startsWith(it, start) }
    if (pair != null) return Token(TokenKind.OP, pair)
    val single = text.getOrNull(start)?.takeIf { it in ONE_CHARACTER_OPERATORS } ?: return null
    return Token(TokenKind.OP, single.toString())
}
