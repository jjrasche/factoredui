package ai.factoredui.worldengine.expression

import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.json.missingKey
import ai.factoredui.worldengine.state.AgentRecord
import ai.factoredui.worldengine.json.pythonFloat
import ai.factoredui.worldengine.ground.tileMeanMm
import ai.factoredui.worldengine.ground.tileSlopePct
import ai.factoredui.worldengine.state.TileFootprint
import ai.factoredui.worldengine.state.InstanceRecord
import ai.factoredui.worldengine.state.State
import ai.factoredui.worldengine.state.Tile
import ai.factoredui.worldengine.world.World
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

private const val MM_PER_FT = 304.8
private val TRUE: Value = Value.Bool(true)
private val FALSE: Value = Value.Bool(false)

class Evaluation(
    val world: World,
    val state: State,
    val tileInstance: TileFootprint? = null,
    val agent: AgentRecord? = null,
    val actedInstance: InstanceRecord? = null,
) {
    private val memo: MutableMap<String, Value> = mutableMapOf()

    fun valueOf(node: ExprNode): Value = when (node) {
        is ExprNode.NumberLiteral -> Value.Num(node.value)
        is ExprNode.StringLiteral -> Value.Text(node.value)
        is ExprNode.BooleanLiteral -> Value.Bool(node.value)
        is ExprNode.Name -> valueOfName(node.name)
        is ExprNode.Negate -> valueOf(node.operand).let { operand -> if (operand == Value.Null) Value.Null else Value.Num(-numericOf(operand, "negation")) }
        is ExprNode.Not -> valueOf(node.operand).let { operand -> if (operand == Value.Null) Value.Null else Value.Bool(!isTruthyValue(operand)) }
        is ExprNode.And -> conjunction(node)
        is ExprNode.Or -> disjunction(node)
        is ExprNode.Arithmetic -> arithmetic(node.operator, valueOf(node.left), valueOf(node.right))
        is ExprNode.Comparison -> compareValues(node.operator, valueOf(node.left), valueOf(node.right))?.let { Value.Bool(it) } ?: Value.Null
        is ExprNode.Call -> call(node.function, node.arguments)
    }

    fun numberOf(node: ExprNode): Double = numericOf(valueOf(node), "a world figure")

    fun measuredNumberOf(node: ExprNode): Double? = valueOf(node).takeIf { it != Value.Null }?.let { numericOf(it, "a world figure") }

    private fun conjunction(node: ExprNode.And): Value {
        val left = valueOf(node.left)
        if (left == FALSE) return FALSE
        val right = valueOf(node.right)
        if (right == FALSE) return FALSE
        return if (left == Value.Null || right == Value.Null) Value.Null else TRUE
    }

    private fun disjunction(node: ExprNode.Or): Value {
        val left = valueOf(node.left)
        if (left == TRUE) return TRUE
        val right = valueOf(node.right)
        if (right == TRUE) return TRUE
        return if (left == Value.Null || right == Value.Null) Value.Null else FALSE
    }

    private fun valueOfName(name: String): Value {
        if (name == "tile") return Value.TileRef(tileInstance)
        if (name == "instance") return Value.InstanceRef(actedInstance)
        if (name == "tile_area") return Value.Num(world.tileArea)
        if (name == "now") return Value.Num(state.ticks * world.tickHours)
        if (name == "tick_length") return Value.Num(world.tickHours)
        if ('.' in name) return valueOfDotted(name)
        state.stocks[name]?.let { return Value.Num(it) }
        return memo.getOrPut(name) { valueOfNamedExpression(name) }
    }

    private fun valueOfNamedExpression(name: String): Value {
        world.equations[name]?.let { return valueOf(world.ast(it.expr)) }
        world.scoring[name]?.let { return valueOf(world.ast(it.expr)) }
        throw ExpressionException("unknown_word", "name '$name'")
    }

    private fun valueOfDotted(name: String): Value {
        val head = name.substringBefore('.')
        val member = name.substringAfter('.')
        if (head == "self") return Value.Num(agent?.attributes?.get(member) ?: throw missingKey(member))
        val linked = world.linkedInstance
        if (world.linkAlias != null && head == world.linkAlias && linked != null) return linked.props[member] ?: throw missingKey(member)
        return world.defaultProperties(head)[member] ?: throw missingKey(member)
    }

    private fun call(function: String, arguments: List<ExprNode>): Value = when (function) {
        "if" -> choose(arguments)
        "min" -> pickExtreme(arguments, isMinimum = true)
        "max" -> pickExtreme(arguments, isMinimum = false)
        "count_instances" -> Value.Num(instancesOf(arguments[0]).size.toDouble())
        "min_distance_mm" -> nearestInstanceGapFt(instancesOf(arguments[0]), instancesOf(arguments[1]))?.let { Value.Num(it) } ?: Value.Null
        "count" -> Value.Num(state.cells.values.count { matchesUse(it, quotedUse(arguments[0])) }.toDouble())
        "sum" -> Value.Num(sumProperty(quotedUse(arguments[0])))
        "neighbors" -> Value.Num(countNeighbors(arguments))
        "side" -> Value.Num(countSide(arguments))
        "edge" -> Value.Num(edgeClearance())
        "distance" -> Value.Num(nearestDistance(tilesOf(arguments[0]), tilesOf(arguments[1])))
        "projected_support" -> Value.Num(projectedSupport(quotedUse(arguments[0])))
        in GROUND_FUNCTIONS -> groundValue(function)
        else -> throw ExpressionException("unknown_word", "function '$function'")
    }

    private fun groundValue(function: String): Value {
        val surface = state.ground ?: throw MalformedDataException("'NoneType' object is not iterable")
        return when (function) {
            "min_ground_mm" -> Value.Num(surface.lowestMm() / MM_PER_FT)
            "max_ground_mm" -> Value.Num(surface.highestMm() / MM_PER_FT)
            else -> tileInstance?.let { Value.Num(boundGroundValue(function, surface.heightsMm, it.tiles)) } ?: Value.Null
        }
    }

    private fun boundGroundValue(function: String, heights: List<Double>, tiles: List<Tile>): Double {
        if (function == "ground_mm") return compensatedSum(tiles.map { tileMeanMm(heights, world.cols, it) }) / tiles.size / MM_PER_FT
        val sideMm = world.tileMm().toDouble()
        return tiles.maxOf { tileSlopePct(heights, world.cols, it, sideMm) }
    }

    private fun choose(arguments: List<ExprNode>): Value {
        val condition = valueOf(arguments[0])
        if (condition == Value.Null) return Value.Null
        return if (isTruthyValue(condition)) valueOf(arguments[1]) else valueOf(arguments[2])
    }

    private fun pickExtreme(arguments: List<ExprNode>, isMinimum: Boolean): Value {
        val values = arguments.map { valueOf(it) }
        if (Value.Null in values) return Value.Null
        return values.drop(1).fold(values.first()) { kept, candidate ->
            val isBetter = if (isMinimum) compareValues("<", candidate, kept) else compareValues(">", candidate, kept)
            if (isBetter == true) candidate else kept
        }
    }

    private fun instancesOf(argument: ExprNode): List<InstanceRecord> {
        if (argument is ExprNode.StringLiteral) return state.sortedInstanceRecords().filter { typeMatchesUse(it.type, argument.value) }
        val referenced = valueOf(argument) as? Value.InstanceRef ?: throw MalformedDataException("min_distance_mm takes a use or 'instance'")
        return listOf(referenced.record ?: throw MalformedDataException("'NoneType' object is not subscriptable"))
    }

    private fun typeMatchesUse(typeId: String, use: String): Boolean {
        if (use == "any") return true
        if (use.startsWith("#")) return use.substring(1) in (world.types[typeId]?.tags ?: throw missingKey(typeId))
        return typeId == use
    }

    private fun quotedUse(argument: ExprNode): String =
        (argument as? ExprNode.StringLiteral)?.value ?: throw MalformedDataException("a use argument must be a quoted string")

    private fun sumProperty(property: String): Double {
        val values = state.instances.values.mapNotNull { instance -> instance.props[property] ?: Value.Num(0.0) }
            .filter { isNumeric(it) }
            .map { numericOf(it, "sum") }
        return compensatedSum(values)
    }

    private fun tileFootprint(): Set<Tile> {
        val instance = tileInstance ?: throw MalformedDataException("'NoneType' object is not subscriptable")
        return instance.tiles.toSet()
    }

    private fun countNeighbors(arguments: List<ExprNode>): Double {
        val footprint = tileFootprint()
        val radius = truncateRadius(numericOf(valueOf(arguments[1]), "neighbors radius"))
        val use = if (arguments.size == 3) quotedUse(arguments[2]) else "any"
        var found = 0
        for (col in 0 until world.cols) {
            for (row in 0 until world.rows) {
                if (isInRing(Tile(col, row), footprint, radius) && matchesUse(state.cells[Tile(col, row)], use)) found++
            }
        }
        return found.toDouble()
    }

    private fun isInRing(tile: Tile, footprint: Set<Tile>, radius: Long): Boolean =
        tile !in footprint && footprint.minOf { abs(tile.col - it.col) + abs(tile.row - it.row) } <= radius

    private fun countSide(arguments: List<ExprNode>): Double {
        val footprint = tileFootprint()
        val step = DIRECTIONS[quotedUse(arguments[1])] ?: throw missingKey(quotedUse(arguments[1]))
        val beside = footprint.map { Tile(it.col + step.first, it.row + step.second) }.toSet() - footprint
        val use = quotedUse(arguments[2])
        return beside.count { isOnGrid(it) && matchesUse(state.cells[it], use) }.toDouble()
    }

    private fun isOnGrid(tile: Tile): Boolean = tile.col in 0 until world.cols && tile.row in 0 until world.rows

    private fun edgeClearance(): Double {
        val tiles = tileInstance?.tiles ?: throw MalformedDataException("'NoneType' object is not subscriptable")
        val clearance = tiles.minOf { minOf(it.col, it.row, world.cols - 1 - it.col, world.rows - 1 - it.row) }
        return clearance * world.tileFt
    }

    private fun tilesOf(argument: ExprNode): Set<Tile> {
        if (argument is ExprNode.StringLiteral) return state.cells.filter { (_, instanceId) -> matchesUse(instanceId, argument.value) }.keys
        val referenced = valueOf(argument) as? Value.TileRef ?: throw MalformedDataException("distance takes a use or 'tile'")
        return referenced.instance?.tiles?.toSet() ?: throw MalformedDataException("'NoneType' object is not subscriptable")
    }

    private fun nearestDistance(first: Set<Tile>, second: Set<Tile>): Double {
        if (first.isEmpty() || second.isEmpty()) return Double.POSITIVE_INFINITY
        return first.minOf { a -> second.minOf { b -> tileDistance(a, b) } } * world.tileFt
    }

    private fun tileDistance(a: Tile, b: Tile): Double {
        val dx = (a.col - b.col).toLong()
        val dy = (a.row - b.row).toLong()
        return sqrt((dx * dx + dy * dy).toDouble())
    }

    private fun matchesUse(instanceId: String?, use: String): Boolean {
        if (instanceId == null) return false
        val typeId = state.instances[instanceId]?.type ?: throw missingKey(instanceId)
        if (use == "any") return true
        if (use.startsWith("#")) return use.substring(1) in (world.types[typeId]?.tags ?: throw missingKey(typeId))
        return typeId == use
    }

    private fun projectedSupport(agentType: String): Double {
        val spec = world.agents[agentType] ?: throw missingKey(agentType)
        val weightTree = world.ast(spec.weight)
        val utilityTree = world.ast(spec.utility)
        var total = 0.0
        var approving = 0.0
        state.agents.values.sortedBy { it.id }.filter { it.type == agentType }.forEach { neighbour ->
            val perAgent = Evaluation(world, state, agent = neighbour)
            val weight = perAgent.numberOf(weightTree)
            total += weight
            if (compareValues(">", perAgent.valueOf(utilityTree), Value.Num(0.0)) == true) approving += weight
        }
        return if (total > 0) approving / total else 0.0
    }
}

fun nearestInstanceGapFt(first: List<InstanceRecord>, second: List<InstanceRecord>): Double? {
    val gaps = first.flatMap { a -> second.filter { b -> a.id != b.id }.map { b -> planeGapMm(a, b) } }
    return gaps.minOrNull()?.let { it / MM_PER_FT }
}

private fun planeGapMm(a: InstanceRecord, b: InstanceRecord): Double =
    hypot(pythonFloat(a.xMm) - pythonFloat(b.xMm), pythonFloat(a.yMm) - pythonFloat(b.yMm))

fun arithmetic(operator: String, left: Value, right: Value): Value {
    if (left == Value.Null || right == Value.Null) return Value.Null
    return Value.Num(arithmeticOfNumbers(operator, left, right))
}

private fun arithmeticOfNumbers(operator: String, left: Value, right: Value): Double {
    val leftNumber = numericOf(left, operator)
    val rightNumber = numericOf(right, operator)
    return when (operator) {
        "+" -> leftNumber + rightNumber
        "-" -> leftNumber - rightNumber
        "*" -> leftNumber * rightNumber
        else -> divide(leftNumber, rightNumber)
    }
}

private fun divide(numerator: Double, denominator: Double): Double {
    if (denominator == 0.0) throw ExpressionException("divide_by_zero", "division by zero; guard it with if()")
    return numerator / denominator
}

fun compareValues(operator: String, left: Value, right: Value): Boolean? {
    if (left == Value.Null || right == Value.Null) return null
    if (operator == "==") return areEqualValues(left, right)
    if (operator == "!=") return !areEqualValues(left, right)
    val order = orderOf(left, right)
    return when (operator) {
        "<" -> order.isLess
        "<=" -> order.isLess || order.isEqual
        ">" -> order.isGreater
        else -> order.isGreater || order.isEqual
    }
}

private class Ordering(val isLess: Boolean, val isEqual: Boolean, val isGreater: Boolean)

private fun orderOf(left: Value, right: Value): Ordering {
    if (isNumeric(left) && isNumeric(right)) {
        val a = numericOf(left, "comparison")
        val b = numericOf(right, "comparison")
        return Ordering(a < b, a == b, a > b)
    }
    if (left is Value.Text && right is Value.Text) {
        val difference = left.value.compareTo(right.value)
        return Ordering(difference < 0, difference == 0, difference > 0)
    }
    throw MalformedDataException("'<' not supported between ${describeValue(left)} and ${describeValue(right)}")
}

private fun areEqualValues(left: Value, right: Value): Boolean = when {
    isNumeric(left) && isNumeric(right) -> numericOf(left, "comparison") == numericOf(right, "comparison")
    left is Value.TileRef && right is Value.TileRef -> left.instance === right.instance
    else -> left == right
}

private fun truncateRadius(value: Double): Long {
    if (value.isNaN()) throw MalformedDataException("cannot convert float NaN to integer")
    if (value.isInfinite()) throw MalformedDataException("cannot convert float infinity to integer")
    return value.toLong()
}

fun compensatedSum(values: List<Double>): Double {
    var total = 0.0
    var compensation = 0.0
    values.forEach { item ->
        val next = total + item
        compensation += if (abs(total) >= abs(item)) (total - next) + item else (item - next) + total
        total = next
    }
    return if (compensation != 0.0 && compensation.isFinite()) total + compensation else total
}
