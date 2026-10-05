package ai.factoredui.worldengine.expression

import ai.factoredui.worldengine.units.Dimension

sealed interface ExprNode {
    data class NumberLiteral(val value: Double, val dimension: Dimension) : ExprNode
    data class StringLiteral(val value: String) : ExprNode
    data class BooleanLiteral(val value: Boolean) : ExprNode
    data class Name(val name: String) : ExprNode
    data class Negate(val operand: ExprNode) : ExprNode
    data class Not(val operand: ExprNode) : ExprNode
    data class And(val left: ExprNode, val right: ExprNode) : ExprNode
    data class Or(val left: ExprNode, val right: ExprNode) : ExprNode
    data class Arithmetic(val operator: String, val left: ExprNode, val right: ExprNode) : ExprNode
    data class Comparison(val operator: String, val left: ExprNode, val right: ExprNode) : ExprNode
    data class Call(val function: String, val arguments: List<ExprNode>) : ExprNode
}

fun childrenOf(node: ExprNode): List<ExprNode> = when (node) {
    is ExprNode.Arithmetic -> listOf(node.left, node.right)
    is ExprNode.Comparison -> listOf(node.left, node.right)
    is ExprNode.And -> listOf(node.left, node.right)
    is ExprNode.Or -> listOf(node.left, node.right)
    is ExprNode.Not -> listOf(node.operand)
    is ExprNode.Negate -> listOf(node.operand)
    is ExprNode.Call -> node.arguments
    else -> emptyList()
}

fun walkNodes(root: ExprNode): List<ExprNode> {
    val visited = mutableListOf<ExprNode>()
    val pending = ArrayDeque(listOf(root))
    while (pending.isNotEmpty()) {
        val node = pending.removeFirst()
        visited += node
        pending.addAll(0, childrenOf(node))
    }
    return visited
}

fun referencedNames(root: ExprNode): Set<String> = walkNodes(root).filterIsInstance<ExprNode.Name>().map { it.name }.toSet()

fun projectedAgentTypes(root: ExprNode): Set<String> =
    walkNodes(root).filterIsInstance<ExprNode.Call>().mapNotNull { quotedProjection(it) }.toSet()

private fun quotedProjection(call: ExprNode.Call): String? {
    if (call.function != "projected_support") return null
    return (call.arguments.firstOrNull() as? ExprNode.StringLiteral)?.value
}
