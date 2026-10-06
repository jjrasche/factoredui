package ai.factoredui.worldengine.expression

import ai.factoredui.worldengine.text.pythonTupleRepr
import ai.factoredui.worldengine.units.Dimension
import ai.factoredui.worldengine.units.describeDimension
import ai.factoredui.worldengine.units.parseUnit
import ai.factoredui.worldengine.world.ObjectType
import ai.factoredui.worldengine.world.World

sealed interface ValueType {
    data class Num(val dimension: Dimension) : ValueType
    data object Bool : ValueType
    data object Str : ValueType
    data object TileType : ValueType
    data object InstanceType : ValueType
}

fun describeType(type: ValueType): String = when (type) {
    is ValueType.Num -> describeDimension(type.dimension)
    ValueType.Bool -> "bool"
    ValueType.Str -> "str"
    ValueType.TileType -> "tile"
    ValueType.InstanceType -> "instance"
}

val DIRECTIONS: Map<String, Pair<Int, Int>> = linkedMapOf("north" to (0 to -1), "south" to (0 to 1), "east" to (1 to 0), "west" to (-1 to 0))

val INSTANCE_VERBS: List<String> = listOf("place_instance", "remove_instance")

enum class ScopeSite { RULE, INSTANCE_RULE, EQUATION, STOCK, SCORING, AGENT }

fun ruleSiteFor(on: String?): ScopeSite = if (on in INSTANCE_VERBS) ScopeSite.INSTANCE_RULE else ScopeSite.RULE

private val TILE_DIMENSION: Dimension = parseUnit("tile").dimension
private val FT_DIMENSION: Dimension = parseUnit("ft").dimension

fun numberOfUnit(unit: String?): ValueType.Num = ValueType.Num(parseUnit(unit).dimension)

class Scope(val world: World, val site: ScopeSite, val agentType: String? = null) {
    fun typeOfName(name: String): ValueType {
        if (name == "tile") {
            if (site == ScopeSite.RULE) return ValueType.TileType
            throw ExpressionException("unknown_word", "'tile' exists only inside a rule")
        }
        if (name == "instance") {
            if (site == ScopeSite.INSTANCE_RULE) return ValueType.InstanceType
            throw ExpressionException("unknown_word", "'instance' exists only inside a place_instance or remove_instance rule")
        }
        if (name == "tile_area") return numberOfUnit("sq_ft/tile")
        if (name == "now" || name == "tick_length") return numberOfUnit("hour")
        if ('.' in name) return typeOfDotted(name)
        world.equations[name]?.let { return numberOfUnit(it.requiredUnit()) }
        world.stocks[name]?.let { return numberOfUnit(it.requiredUnit()) }
        val score = world.scoring[name]
        if (score != null && (site == ScopeSite.SCORING || site == ScopeSite.AGENT)) return numberOfUnit(score.requiredUnit())
        throw ExpressionException("unknown_word", "name '$name' is not a built-in, equation, stock or score here")
    }

    private fun typeOfDotted(name: String): ValueType {
        val head = name.substringBefore('.')
        val member = name.substringAfter('.')
        if (head == "self") return typeOfAgentAttribute(member)
        val linkedType = world.linkedType
        if (world.linkAlias != null && head == world.linkAlias && linkedType != null) return propertyType(linkedType, member, name)
        val objectType = world.types[head] ?: throw ExpressionException("unknown_word", "'$head' in '$name' is not an object type or a link alias")
        return propertyType(objectType, member, name)
    }

    private fun typeOfAgentAttribute(member: String): ValueType {
        if (site != ScopeSite.AGENT) throw ExpressionException("unknown_word", "'self' exists only inside an agent's weight or utility")
        val attribute = world.agents[agentType]?.attributes?.lastOrNull { it.name == member }
            ?: throw ExpressionException("unknown_word", "agent $agentType has no attribute '$member'")
        return numberOfUnit(attribute.unit)
    }

    fun isKnownUse(use: String): Boolean = when {
        use == "any" -> true
        use.startsWith("#") -> use.substring(1) in world.tags
        else -> use in world.types
    }

    fun propertyDimension(property: String): Dimension {
        val dimensions = world.types.values.flatMap { it.properties }
            .filter { it.name == property && !it.isText }
            .map { parseUnit(it.unit).dimension }
            .toSet()
        if (dimensions.isEmpty()) throw ExpressionException("unknown_word", "no object type has a numeric property '$property'")
        if (dimensions.size > 1) throw ExpressionException("unit_mismatch", "property '$property' is declared in different units across types")
        return dimensions.single()
    }
}

fun propertyType(objectType: ObjectType, member: String, name: String): ValueType {
    val spec = objectType.property(member)
        ?: throw ExpressionException("unknown_word", "'$name': type ${objectType.id} has no property '$member'")
    return if (spec.isText) ValueType.Str else numberOfUnit(spec.unit)
}

fun checkExpression(node: ExprNode, scope: Scope): ValueType = when (node) {
    is ExprNode.NumberLiteral -> ValueType.Num(node.dimension)
    is ExprNode.StringLiteral -> ValueType.Str
    is ExprNode.BooleanLiteral -> ValueType.Bool
    is ExprNode.Name -> scope.typeOfName(node.name)
    is ExprNode.Negate -> checkExpression(node.operand, scope).also { requireNumber(it, "negation") }
    is ExprNode.Not -> ValueType.Bool.also { requireBoolean(checkExpression(node.operand, scope), "not") }
    is ExprNode.And -> checkLogical(node.left, node.right, "and", scope)
    is ExprNode.Or -> checkLogical(node.left, node.right, "or", scope)
    is ExprNode.Arithmetic -> checkArithmetic(node, scope)
    is ExprNode.Comparison -> checkComparison(node, scope)
    is ExprNode.Call -> checkCall(node.function, node.arguments, scope)
}

private fun checkLogical(left: ExprNode, right: ExprNode, operator: String, scope: Scope): ValueType {
    requireBoolean(checkExpression(left, scope), operator)
    requireBoolean(checkExpression(right, scope), operator)
    return ValueType.Bool
}

private fun requireNumber(type: ValueType, where: String) {
    if (type !is ValueType.Num) throw ExpressionException("unit_mismatch", "$where needs a number, found ${describeType(type)}")
}

private fun requireBoolean(type: ValueType, where: String) {
    if (type != ValueType.Bool) throw ExpressionException("unit_mismatch", "$where needs true or false, found ${describeType(type)}")
}

private fun checkArithmetic(node: ExprNode.Arithmetic, scope: Scope): ValueType {
    val left = checkExpression(node.left, scope)
    val right = checkExpression(node.right, scope)
    requireNumber(left, node.operator)
    requireNumber(right, node.operator)
    val leftDimension = (left as ValueType.Num).dimension
    val rightDimension = (right as ValueType.Num).dimension
    if (node.operator == "+" || node.operator == "-") {
        if (leftDimension != rightDimension) {
            throw ExpressionException("unit_mismatch", "'${node.operator}' joins ${describeDimension(leftDimension)} and ${describeDimension(rightDimension)}")
        }
        return left
    }
    return ValueType.Num(leftDimension.combine(rightDimension, if (node.operator == "*") 1 else -1))
}

private fun checkComparison(node: ExprNode.Comparison, scope: Scope): ValueType {
    val left = checkExpression(node.left, scope)
    val right = checkExpression(node.right, scope)
    if (left is ValueType.Num && right is ValueType.Num) {
        if (left.dimension != right.dimension) {
            throw ExpressionException("unit_mismatch", "'${node.operator}' compares ${describeDimension(left.dimension)} with ${describeDimension(right.dimension)}")
        }
        return ValueType.Bool
    }
    val isEqualityOfLikes = left == right && (left == ValueType.Str || left == ValueType.Bool) && (node.operator == "==" || node.operator == "!=")
    if (isEqualityOfLikes) return ValueType.Bool
    throw ExpressionException("unit_mismatch", "'${node.operator}' cannot compare ${describeType(left)} with ${describeType(right)}")
}

private fun literalUse(argument: ExprNode, scope: Scope, function: String): String {
    val literal = argument as? ExprNode.StringLiteral ?: throw ExpressionException("syntax", "$function takes a quoted use, not a computed value")
    if (!scope.isKnownUse(literal.value)) {
        throw ExpressionException("unknown_word", "$function: use '${literal.value}' is not an object type, #tag or 'any'")
    }
    return literal.value
}

private fun requireTile(argument: ExprNode, scope: Scope, function: String) {
    if (checkExpression(argument, scope) != ValueType.TileType) throw ExpressionException("unit_mismatch", "$function takes 'tile' as its first argument")
}

private fun requireArity(function: String, arguments: List<ExprNode>, allowed: List<Int>) {
    if (arguments.size !in allowed) {
        throw ExpressionException("syntax", "$function takes ${allowed.joinToString(" or ")} arguments, found ${arguments.size}")
    }
}

private fun checkCall(function: String, arguments: List<ExprNode>, scope: Scope): ValueType = when (function) {
    "count" -> checkCount(arguments, scope)
    "sum" -> checkSum(arguments, scope)
    "neighbors" -> checkNeighbors(arguments, scope)
    "side" -> checkSide(arguments, scope)
    "edge" -> checkEdge(arguments, scope)
    "distance" -> checkDistance(arguments, scope)
    "if" -> checkIf(arguments, scope)
    "min", "max" -> checkExtreme(function, arguments, scope)
    "projected_support" -> checkProjectedSupport(arguments, scope)
    "count_instances" -> checkCountInstances(arguments, scope)
    "min_distance_mm" -> checkMinDistance(arguments, scope)
    in GROUND_FUNCTIONS -> checkGroundCall(function, arguments, scope)
    else -> throw ExpressionException("unknown_word", "function '$function' is not in the closed vocabulary")
}

private fun checkCount(arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity("count", arguments, listOf(1))
    literalUse(arguments[0], scope, "count")
    return ValueType.Num(TILE_DIMENSION)
}

private fun checkSum(arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity("sum", arguments, listOf(1))
    val property = arguments[0] as? ExprNode.StringLiteral ?: throw ExpressionException("syntax", "sum takes a quoted property name")
    return ValueType.Num(scope.propertyDimension(property.value))
}

private fun checkNeighbors(arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity("neighbors", arguments, listOf(2, 3))
    requireTile(arguments[0], scope, "neighbors")
    val radius = checkExpression(arguments[1], scope)
    if (radius != ValueType.Num(TILE_DIMENSION)) {
        throw ExpressionException("unit_mismatch", "neighbors radius must be in tile, found ${describeType(radius)}")
    }
    if (arguments.size == 3) literalUse(arguments[2], scope, "neighbors")
    return ValueType.Num(TILE_DIMENSION)
}

private fun checkSide(arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity("side", arguments, listOf(3))
    requireTile(arguments[0], scope, "side")
    val direction = arguments[1] as? ExprNode.StringLiteral
    if (direction == null || direction.value !in DIRECTIONS) {
        throw ExpressionException("unknown_word", "side direction must be one of ${pythonTupleRepr(DIRECTIONS.keys.toList())}")
    }
    literalUse(arguments[2], scope, "side")
    return ValueType.Num(TILE_DIMENSION)
}

private fun checkEdge(arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity("edge", arguments, listOf(1))
    requireTile(arguments[0], scope, "edge")
    return ValueType.Num(FT_DIMENSION)
}

private fun checkDistance(arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity("distance", arguments, listOf(2))
    arguments.forEach { argument -> if (argument is ExprNode.StringLiteral) literalUse(argument, scope, "distance") else requireTile(argument, scope, "distance") }
    return ValueType.Num(FT_DIMENSION)
}

private fun checkIf(arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity("if", arguments, listOf(3))
    requireBoolean(checkExpression(arguments[0], scope), "if condition")
    val chosen = checkExpression(arguments[1], scope)
    val otherwise = checkExpression(arguments[2], scope)
    if (chosen != otherwise) throw ExpressionException("unit_mismatch", "if branches differ: ${describeType(chosen)} and ${describeType(otherwise)}")
    return chosen
}

private fun checkExtreme(function: String, arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity(function, arguments, listOf(2))
    val left = checkExpression(arguments[0], scope)
    val right = checkExpression(arguments[1], scope)
    requireNumber(left, function)
    if (left != right) throw ExpressionException("unit_mismatch", "$function joins ${describeType(left)} and ${describeType(right)}")
    return left
}

private fun requireInstanceSet(argument: ExprNode, scope: Scope, function: String) {
    if (argument is ExprNode.StringLiteral) {
        literalUse(argument, scope, function)
        return
    }
    if (checkExpression(argument, scope) != ValueType.InstanceType) throw ExpressionException("unit_mismatch", "$function takes a quoted use or 'instance'")
}

private fun checkCountInstances(arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity("count_instances", arguments, listOf(1))
    literalUse(arguments[0], scope, "count_instances")
    return ValueType.Num(Dimension.NONE)
}

private fun checkMinDistance(arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity("min_distance_mm", arguments, listOf(2))
    arguments.forEach { requireInstanceSet(it, scope, "min_distance_mm") }
    return ValueType.Num(FT_DIMENSION)
}

private fun checkGroundCall(function: String, arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity(function, arguments, listOf(0))
    if (scope.world.ground == null) throw ExpressionException("unknown_word", "$function(): world ${scope.world.id} declares no ground")
    if (function in TILE_GROUND_FUNCTIONS && scope.site != ScopeSite.RULE) {
        throw ExpressionException("unknown_word", "$function() reads the tile a rule is bound to; it exists only inside a rule")
    }
    return if (function == "slope_pct") ValueType.Num(Dimension.NONE) else ValueType.Num(FT_DIMENSION)
}

private fun checkProjectedSupport(arguments: List<ExprNode>, scope: Scope): ValueType {
    requireArity("projected_support", arguments, listOf(1))
    val agentType = arguments[0] as? ExprNode.StringLiteral
    if (agentType == null || agentType.value !in scope.world.agents) {
        throw ExpressionException("unknown_word", "projected_support takes a quoted agent type the world declares")
    }
    return ValueType.Num(Dimension.NONE)
}
