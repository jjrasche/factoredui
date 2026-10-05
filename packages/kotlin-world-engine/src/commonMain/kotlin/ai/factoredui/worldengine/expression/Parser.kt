package ai.factoredui.worldengine.expression

import ai.factoredui.worldengine.text.pythonStrRepr
import ai.factoredui.worldengine.text.pythonStrip
import ai.factoredui.worldengine.text.pythonTupleRepr
import ai.factoredui.worldengine.units.parseUnit

const val MAX_NODES = 256
const val MAX_DEPTH = 24

val FUNCTIONS: List<String> = listOf("count", "sum", "neighbors", "side", "edge", "distance", "if", "min", "max", "projected_support", "count_instances", "min_distance_mm")
val KEYWORDS: List<String> = listOf("and", "or", "not", "true", "false")
val COMPARATORS: List<String> = listOf("<", "<=", ">", ">=", "==", "!=")

fun parseExpression(text: String?): ExprNode {
    if (text == null || pythonStrip(text).isEmpty()) throw ExpressionException("syntax", "expression is empty")
    return ExpressionParser(tokenizeExpression(text)).parse()
}

private class ExpressionParser(private val tokens: List<Token>) {
    private var at = 0
    private var depth = 0
    private var nodes = 0

    fun parse(): ExprNode {
        val node = parseOr()
        if (at < tokens.size) throw ExpressionException("syntax", "unexpected ${pythonStrRepr(tokens[at].text)} after a complete expression")
        return node
    }

    private fun peek(): String? = tokens.getOrNull(at)?.text

    private fun take(): Token {
        val token = tokens.getOrNull(at) ?: throw ExpressionException("syntax", "expression ends early")
        at++
        return token
    }

    private fun expect(value: String) {
        val text = take().text
        if (text != value) throw ExpressionException("syntax", "expected ${pythonStrRepr(value)}, found ${pythonStrRepr(text)}")
    }

    private fun make(node: ExprNode): ExprNode {
        nodes++
        if (nodes > MAX_NODES) throw ExpressionException("bound", "expression exceeds $MAX_NODES nodes")
        return node
    }

    private fun descend() {
        depth++
        if (depth > MAX_DEPTH) throw ExpressionException("bound", "expression nests deeper than $MAX_DEPTH")
    }

    private fun parseOr(): ExprNode {
        descend()
        var node = parseAnd()
        while (peek() == "or") {
            take()
            node = make(ExprNode.Or(node, parseAnd()))
        }
        depth--
        return node
    }

    private fun parseAnd(): ExprNode {
        var node = parseNot()
        while (peek() == "and") {
            take()
            node = make(ExprNode.And(node, parseNot()))
        }
        return node
    }

    private fun parseNot(): ExprNode {
        if (peek() != "not") return parseCompare()
        take()
        descend()
        val operand = parseNot()
        depth--
        return make(ExprNode.Not(operand))
    }

    private fun parseCompare(): ExprNode {
        val node = parseSum()
        if (peek() !in COMPARATORS) return node
        val operator = take().text
        return make(ExprNode.Comparison(operator, node, parseSum()))
    }

    private fun parseSum(): ExprNode {
        var node = parseTerm()
        while (peek() == "+" || peek() == "-") {
            val operator = take().text
            node = make(ExprNode.Arithmetic(operator, node, parseTerm()))
        }
        return node
    }

    private fun parseTerm(): ExprNode {
        var node = parseUnary()
        while (peek() == "*" || peek() == "/") {
            val operator = take().text
            node = make(ExprNode.Arithmetic(operator, node, parseUnary()))
        }
        return node
    }

    private fun parseUnary(): ExprNode {
        if (peek() != "-") return parseAtom()
        take()
        descend()
        val operand = parseUnary()
        depth--
        return make(ExprNode.Negate(operand))
    }

    private fun parseAtom(): ExprNode {
        val token = take()
        return when {
            token.kind == TokenKind.NUMBER -> parseNumber(token.text)
            token.kind == TokenKind.STRING -> make(ExprNode.StringLiteral(token.text.substring(1, token.text.length - 1)))
            token.kind == TokenKind.NAME -> parseNameAtom(token.text)
            token.text == "(" -> parseParenthesised()
            else -> throw ExpressionException("syntax", "unexpected ${pythonStrRepr(token.text)}")
        }
    }

    private fun parseNumber(text: String): ExprNode {
        val unitText = if (tokens.getOrNull(at)?.kind == TokenKind.UNIT) take().text.let { it.substring(1, it.length - 1) } else ""
        val unit = parseUnit(unitText)
        return make(ExprNode.NumberLiteral(asciiDigits(text).toDouble() * unit.factor, unit.dimension))
    }

    private fun parseNameAtom(text: String): ExprNode {
        if (text == "true" || text == "false") return make(ExprNode.BooleanLiteral(text == "true"))
        if (text in KEYWORDS) throw ExpressionException("syntax", "${pythonStrRepr(text)} cannot start an operand")
        if (peek() == "(") return parseCall(text)
        return make(ExprNode.Name(text))
    }

    private fun parseParenthesised(): ExprNode {
        val node = parseOr()
        expect(")")
        return node
    }

    private fun parseCall(function: String): ExprNode {
        if (function !in FUNCTIONS) {
            throw ExpressionException("unknown_word", "function '$function' is not in the closed vocabulary ${pythonTupleRepr(FUNCTIONS)}")
        }
        expect("(")
        val arguments = mutableListOf(parseOr())
        while (peek() == ",") {
            take()
            arguments += parseOr()
        }
        expect(")")
        return make(ExprNode.Call(function, arguments))
    }
}

private fun asciiDigits(text: String): String = text.map { if (it.isDigit()) ('0' + it.digitToInt()) else it }.joinToString("")
