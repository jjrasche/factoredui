package ai.factoredui.worldengine.world

import ai.factoredui.worldengine.events.AppliedEvent
import ai.factoredui.worldengine.events.RefusalException
import ai.factoredui.worldengine.events.applyEvent
import ai.factoredui.worldengine.events.seatMeasuredInstance
import ai.factoredui.worldengine.expression.ExprNode
import ai.factoredui.worldengine.expression.ExpressionException
import ai.factoredui.worldengine.expression.parseExpression
import ai.factoredui.worldengine.expression.storedProperty
import ai.factoredui.worldengine.expression.Value
import ai.factoredui.worldengine.ground.GroundSpec
import ai.factoredui.worldengine.ground.GroundState
import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.json.objects
import ai.factoredui.worldengine.json.optionalList
import ai.factoredui.worldengine.json.pythonFloat
import ai.factoredui.worldengine.json.pythonInt
import ai.factoredui.worldengine.json.required
import ai.factoredui.worldengine.json.requiredObject
import ai.factoredui.worldengine.json.requiredText
import ai.factoredui.worldengine.json.optionalText
import ai.factoredui.worldengine.state.Instance
import ai.factoredui.worldengine.state.State
import ai.factoredui.worldengine.units.ExactRatio
import ai.factoredui.worldengine.units.MAX_FOOTPRINT_TILES
import ai.factoredui.worldengine.units.ceilingQuotient
import ai.factoredui.worldengine.units.exactDecimalOf
import ai.factoredui.worldengine.units.parseUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class World private constructor(
    val path: String,
    val doc: JsonObject,
    private val library: WorldLibrary,
    chain: List<String>,
) {
    val id: String = doc.requiredText("id")
    val grid: JsonObject = doc.requiredObject("grid")
    val cols: Int = pythonInt(grid.required("cols")).toInt()
    val rows: Int = pythonInt(grid.required("rows")).toInt()
    val tileFt: Double = pythonFloat(grid.required("tile_ft"))
    val frame: JsonElement? = doc["frame"]?.takeIf { it !is JsonNull }
    val ground: GroundSpec? = doc["ground"]?.takeIf { it !is JsonNull }?.let { GroundSpec(it as? JsonObject ?: throw MalformedDataException("'ground' is not an object")) }
    val types: Map<String, ObjectType> = indexBy(doc.optionalList("object_types").objects().map { ObjectType.from(it) }) { it.id }
    val tags: Set<String> = types.values.flatMap { it.tags }.toSet()
    val equations: Map<String, EquationSpec> = indexBy(doc.optionalList("equations").objects().map { EquationSpec.from(it) }) { it.id }
    val stocks: Map<String, StockSpec> = indexBy(doc.optionalList("stocks").objects().map { StockSpec.from(it) }) { it.id }
    val scoring: Map<String, ScoreSpec> = indexBy(doc.optionalList("scoring").objects().map { ScoreSpec.from(it) }) { it.id }
    val actions: Map<String, ActionSpec> = indexBy(doc.optionalList("actions").objects().map { ActionSpec.from(it) }) { it.verb }
    val agents: Map<String, AgentSpec> = indexBy(doc.optionalList("agents").objects().map { AgentSpec.from(it) }) { it.type }
    val tickHours: Double = readTickHours()
    val link: LinkSpec? = doc.optionalList("links").firstOrNull()?.let { LinkSpec(it as? JsonObject ?: JsonObject(emptyMap())) }?.takeIf { it.isPresent }
    val linkAlias: String? = link?.alias
    val linkProblems: MutableList<String> = mutableListOf()
    var parent: World? = null
        private set
    var linkedInstance: Instance? = null
        private set
    var linkedType: ObjectType? = null
        private set
    val inheritedRules: MutableList<RuleSpec> = mutableListOf()
    private val astCache: MutableMap<String, ExprNode> = mutableMapOf()
    private var seedCache: State? = null

    init {
        if (link != null) resolveLink(chain + path)
    }

    val ownRules: List<RuleSpec> = doc.optionalList("rules").objects().map { RuleSpec.from(it) }

    val rules: List<RuleSpec> = ownRules.filter { it.scope == "self" } + inheritedRules

    private fun readTickHours(): Double {
        val clock = doc["clock"] as? JsonObject ?: return 1.0 * parseUnit("day").factor
        return pythonFloat(clock.required("tick_length")) * parseUnit(clock.requiredText("tick_unit")).factor
    }

    private fun resolveLink(chain: List<String>) {
        val declared = link ?: return
        val parentPath = resolveSiblingPath(path, declared.parent)
        if (parentPath in chain) {
            linkProblems += "link cycle through ${fileNameOf(parentPath)}"
            return
        }
        val parentText = library.readText(parentPath)
        if (parentText == null) {
            linkProblems += "parent world ${declared.parent} does not exist"
            return
        }
        val parentState = openParent(parentPath, parentText, chain) ?: return
        attachLinkedParcel(declared, parentState)
        inheritChildRules(declared)
    }

    private fun openParent(parentPath: String, parentText: String, chain: List<String>): State? = try {
        val opened = World(parentPath, parseBoundedJson(parentText).jsonObject, library, chain)
        parent = opened
        opened.seedState()
    } catch (problem: Exception) {
        parent = null
        linkProblems += "parent seed does not replay: ${describeLoadProblem(problem)}"
        null
    }

    private fun attachLinkedParcel(declared: LinkSpec, parentState: State) {
        val world = parent ?: return
        linkedInstance = parentState.instances[declared.parcel]
        val instance = linkedInstance
        if (instance == null) {
            linkProblems += "parcel ${declared.parcel} is not placed in ${declared.parent}"
            return
        }
        linkedType = world.types[instance.type]
    }

    private fun inheritChildRules(declared: LinkSpec) {
        val world = parent ?: return
        val exported = world.ownRules.filter { it.raw.optionalText("scope") == "child" }.associateBy { it.id }
        declared.inherit.forEach { ruleId ->
            val rule = exported[ruleId]
            if (rule != null) inheritedRules += rule.inherited() else linkProblems += "inherited rule $ruleId is not a child-scope rule of ${world.id}"
        }
    }

    fun ast(text: String?): ExprNode {
        if (text == null) return parseExpression(null)
        return astCache.getOrPut(text) { parseExpression(text) }
    }

    val tileArea: Double get() = tileFt * tileFt

    fun extentSqFt(): Double = cols * rows * tileArea

    fun tileMm(): ExactRatio = exactDecimalOf(grid.required("tile_ft")).times(ExactRatio.MM_PER_FT)

    fun extentMm(): Pair<ExactRatio, ExactRatio> = tileMm().let { it.timesWhole(cols.toLong()) to it.timesWhole(rows.toLong()) }

    fun derivedFootprint(typeId: String): List<Long> {
        val objectType = types[typeId] ?: throw ai.factoredui.worldengine.json.missingKey(typeId)
        return objectType.footprintMm().map { ceilingQuotient(exactDecimalOf(it), tileMm()) }
    }

    fun declaredFootprints(typeId: String): List<Pair<Long, Long>> {
        val objectType = types[typeId] ?: throw ai.factoredui.worldengine.json.missingKey(typeId)
        val declared = if (objectType.hasTileFootprint) objectType.tileFootprint().let { listOf(it.first.toLong() to it.second.toLong()) } else emptyList()
        val derived = if ("footprint_mm" in objectType.raw) derivedFootprint(typeId).let { listOf(it[0] to it[1]) } else emptyList()
        return declared + derived
    }

    fun isFootprintTooLarge(typeId: String): Boolean = declaredFootprints(typeId).any { (width, height) -> width * height > MAX_FOOTPRINT_TILES }

    fun footprintOf(typeId: String): Pair<Int, Int> {
        val objectType = types[typeId] ?: throw ai.factoredui.worldengine.json.missingKey(typeId)
        if (objectType.hasTileFootprint) return objectType.tileFootprint()
        val derived = derivedFootprint(typeId)
        if (derived.size != 2) throw MalformedDataException("footprint_mm must hold exactly two numbers")
        return derived[0].toInt() to derived[1].toInt()
    }

    fun linkedExtentSqFt(): Double? {
        val instance = linkedInstance ?: return null
        val world = parent ?: return null
        return instance.tiles.size * world.tileArea
    }

    fun propertySpec(typeId: String, name: String): PropertySpec? = types[typeId]?.property(name)

    fun defaultProperties(typeId: String): MutableMap<String, Value> {
        val objectType = types[typeId] ?: throw ai.factoredui.worldengine.json.missingKey(typeId)
        return objectType.properties.associateTo(LinkedHashMap()) { it.name to storedProperty(it, it.default) }
    }

    fun seedState(): State {
        val cached = seedCache ?: buildSeedState().also { seedCache = it }
        return cached.copy()
    }

    private fun buildSeedState(): State {
        var state = State()
        state.ground = ground?.let { GroundState(it.seedHeightsMm(), 0) }
        stocks.values.forEach { stock -> state.stocks[stock.id] = pythonFloat(stock.initial) * parseUnit(stock.requiredUnit()).factor }
        doc.optionalList("seed").objects().forEach { entry ->
            if (entry.requiredText("action") == "place_instance") {
                seatMeasuredInstance(this, state, entry)
                return@forEach
            }
            val event = AppliedEvent(
                id = entry.requiredText("id"),
                actor = entry.optionalText("actor") ?: "world:$id",
                action = entry.requiredText("action"),
                parameters = entry["parameters"]?.let { it as? JsonObject ?: throw MalformedDataException("seed parameters are not an object") } ?: JsonObject(emptyMap()),
            )
            state = applyEvent(this, state, event) { null }
        }
        return state
    }

    companion object {
        fun open(path: String, library: WorldLibrary): World {
            val text = library.readText(path) ?: throw WorldLoadException("world file $path does not exist")
            return World(normalizeWorldPath(path), parseBoundedJson(text).jsonObject, library, emptyList())
        }

        fun fromDocument(path: String, document: JsonObject, library: WorldLibrary): World =
            World(normalizeWorldPath(path), document, library, emptyList())
    }
}

fun describeLoadProblem(problem: Exception): String = when (problem) {
    is RefusalException -> "${problem.refusal.rule}: ${problem.refusal.message}"
    is ExpressionException -> problem.message
    is MalformedDataException -> problem.message
    is SerializationException -> problem.message ?: "not JSON"
    is IllegalArgumentException -> problem.message ?: "malformed"
    else -> throw problem
}

private fun <T> indexBy(items: List<T>, key: (T) -> String): Map<String, T> {
    val indexed = LinkedHashMap<String, T>()
    items.forEach { indexed[key(it)] = it }
    return indexed
}
