package ai.factoredui.worldengine.validate

import ai.factoredui.worldengine.expression.ExprNode
import ai.factoredui.worldengine.expression.ExpressionException
import ai.factoredui.worldengine.expression.projectedAgentTypes
import ai.factoredui.worldengine.expression.referencedNames
import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.json.isTruthy
import ai.factoredui.worldengine.json.objects
import ai.factoredui.worldengine.json.optionalList
import ai.factoredui.worldengine.json.optionalText
import ai.factoredui.worldengine.json.pythonEquals
import ai.factoredui.worldengine.json.requiredObject
import ai.factoredui.worldengine.json.requiredText
import ai.factoredui.worldengine.schema.VALIDATION_RULES_JSON
import ai.factoredui.worldengine.schema.schemaErrors
import ai.factoredui.worldengine.text.pythonStr
import ai.factoredui.worldengine.units.parseUnit
import ai.factoredui.worldengine.world.MapWorldLibrary
import ai.factoredui.worldengine.world.World
import ai.factoredui.worldengine.world.WorldLoader
import ai.factoredui.worldengine.world.checkSite
import ai.factoredui.worldengine.world.describeLoadProblem
import ai.factoredui.worldengine.world.expressionSites
import ai.factoredui.worldengine.world.findCycle
import ai.factoredui.worldengine.world.nameGraph
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

val SCANS: List<String> = listOf("worlds", "expressions", "names", "seeds", "rules", "actions", "links", "projections", "figures", "object_types", "footprints", "instances")
val ERROR_FIELDS: List<String> = listOf("position_mm", "height_mm", "crown_radius_mm")
val SOURCED_KINDS: List<String> = listOf("price", "labor", "yield", "regulation", "demographic")
val GENERIC_SPRITES: List<String> = listOf("flat", "block", "tree", "arch", "water", "fence")

data class BlindFinding(val rule: String, val message: String)

data class FiredFinding(val rule: String, val world: String, val message: String)

data class ValidationReport(val blind: List<BlindFinding>, val fired: List<FiredFinding>, val valid: List<String>, val invalid: List<String>)

object WorldValidator {
    val rules: List<JsonObject> by lazy { Json.parseToJsonElement(VALIDATION_RULES_JSON).jsonObject.getValue("rules").jsonArray.map { it.jsonObject } }

    fun validate(files: Map<String, String>): ValidationReport = judge(deriveFacts(files), rules)

    fun deriveFacts(files: Map<String, String>): Map<String, List<JsonObject>> {
        val facts = SCANS.associateWith { mutableListOf<JsonObject>() }
        val library = MapWorldLibrary(files)
        files.keys.filter { it.endsWith(".world.json") && '/' !in it }.sorted().forEach { name -> deriveWorldFacts(name, files.getValue(name), library, facts) }
        return facts
    }

    private fun deriveWorldFacts(name: String, text: String, library: MapWorldLibrary, facts: Map<String, MutableList<JsonObject>>) {
        val document = try {
            Json.parseToJsonElement(text)
        } catch (problem: IllegalArgumentException) {
            facts.getValue("worlds") += fact("world" to name, "parse_ok" to false, "parse_error" to (problem.message ?: "not JSON"), "schema_errors" to emptyList<String>())
            return
        }
        val schemaProblems = schemaErrors(document, WorldLoader.worldSchema)
        facts.getValue("worlds") += fact("world" to name, "parse_ok" to true, "parse_error" to "", "schema_errors" to schemaProblems)
        if (schemaProblems.isNotEmpty()) return
        val world = World.fromDocument(name, document.jsonObject, library)
        facts.getValue("expressions") += expressionFacts(name, world)
        facts.getValue("names") += fact("world" to name, "cycle" to findCycle(world))
        facts.getValue("seeds") += fact("world" to name, "seed_error" to findSeedError(world))
        facts.getValue("rules") += ruleFacts(name, world)
        facts.getValue("actions") += world.actions.values.map { fact("world" to name, "verb" to it.verb, "emits" to (it.emits ?: JsonNull)) }
        facts.getValue("links") += linkFacts(name, world)
        facts.getValue("projections") += projectionFacts(name, world)
        facts.getValue("figures") += figureFacts(name, world)
        facts.getValue("object_types") += typeFacts(name, world)
        facts.getValue("footprints") += footprintFacts(name, world)
        facts.getValue("instances") += instanceFacts(name, world)
    }

    private fun classifySiteProblem(kind: String?, message: String?): List<Pair<String, Any?>> {
        val bucket = when (kind) {
            null -> null
            "syntax" -> "syntax_errors"
            "unknown_word" -> "unknown_words"
            "unit_mismatch" -> "unit_errors"
            "bound" -> "bound_errors"
            else -> "syntax_errors"
        }
        val buckets = listOf("syntax_errors", "unknown_words", "unit_errors", "bound_errors").map { it to if (it == bucket) listOf(message) else emptyList() }
        return buckets + ("over_bound" to (bucket == "bound_errors"))
    }

    private fun expressionFacts(name: String, world: World): List<JsonObject> {
        val siteFacts = expressionSites(world).map { site ->
            val problem = checkSite(site)
            fact(listOf("world" to name, "where" to site.where) + classifySiteProblem(problem?.kind, problem?.message))
        }
        val declarationFacts = declarationSites(world).map { (where, unit) ->
            val problem = try {
                parseUnit(unit)
                null
            } catch (refused: ExpressionException) {
                refused
            }
            fact(listOf("world" to name, "where" to where) + classifySiteProblem(problem?.kind, problem?.message))
        }
        return siteFacts + declarationFacts
    }

    private fun declarationSites(world: World): List<Pair<String, String?>> {
        val propertyUnits = world.types.values.flatMap { type ->
            type.properties.filter { !it.isText }.map { "object_types.${type.id}.properties.${it.name}.unit" to (it.unit ?: "1") }
        }
        val attributeUnits = world.agents.values.flatMap { agent -> agent.attributes.map { "agents.${agent.type}.attributes.${it.name}.unit" to it.unit } }
        return propertyUnits + attributeUnits + ("clock.tick_unit" to world.doc.requiredObject("clock").requiredText("tick_unit"))
    }

    private fun findSeedError(world: World): String = try {
        world.seedState()
        ""
    } catch (problem: Exception) {
        describeLoadProblem(problem)
    }

    private fun ruleFacts(name: String, world: World): List<JsonObject> = (world.ownRules + world.inheritedRules).map { rule ->
        val unknown = mutableListOf<String>()
        if (rule.scope == "self" || rule.isInherited) {
            unknown += rule.appliesTo.orEmpty().filter { it !in world.types }
            if ("applies_to_tag" in rule.raw && rule.appliesToTag !in world.tags) unknown += "#${rule.appliesToTag}"
        }
        fact("world" to name, "id" to rule.id, "kind" to if (rule.hasRequire) "require" else "effect", "message" to (rule.message ?: ""), "unknown_targets" to unknown)
    }

    private fun linkFacts(name: String, world: World): List<JsonObject> {
        val link = world.link ?: return emptyList()
        val parentExtent = world.linkedExtentSqFt()
        return listOf(
            fact(
                "world" to name,
                "parent" to link.parent,
                "problems" to world.linkProblems.toList(),
                "extent_sq_ft" to world.extentSqFt(),
                "parent_extent_sq_ft" to parentExtent,
                "fits" to (parentExtent == null || world.extentSqFt() <= parentExtent),
            ),
        )
    }

    private fun namedNodes(world: World, tree: ExprNode): List<String> = referencedNames(tree).mapNotNull { referenced ->
        when (referenced) {
            in world.equations -> "equation:$referenced"
            in world.scoring -> "scoring:$referenced"
            else -> null
        }
    }

    private fun findProjectedAgents(world: World, graph: Map<String, Set<String>>, tree: ExprNode): Set<String> {
        val projected = projectedAgentTypes(tree).toMutableSet()
        val pending = namedNodes(world, tree).toMutableList()
        val seen = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val node = pending.removeAt(pending.lastIndex)
            if (!seen.add(node)) continue
            if (node.startsWith("agent:")) projected += node.removePrefix("agent:")
            pending += graph[node].orEmpty()
        }
        return projected
    }

    private fun projectionFacts(name: String, world: World): List<JsonObject> {
        val graph = nameGraph(world)
        return expressionSites(world).mapNotNull { site ->
            val tree = try {
                world.ast(site.text)
            } catch (_: ExpressionException) {
                return@mapNotNull null
            }
            if (findProjectedAgents(world, graph, tree).isEmpty()) return@mapNotNull null
            val isBinding = site.where.startsWith("rules.") || site.where.startsWith("inherited.") || site.isBinding
            fact("world" to name, "where" to site.where, "binding" to isBinding)
        }
    }

    private fun figureFacts(name: String, world: World): List<JsonObject> = world.types.values.flatMap { type ->
        type.properties.filter { it.kind in SOURCED_KINDS }.map {
            fact("world" to name, "type" to type.id, "property" to it.name, "kind" to it.kind, "has_source" to it.hasSource)
        }
    }

    private fun typeFacts(name: String, world: World): List<JsonObject> {
        val extensions = (world.doc.requiredObject("sprites")["extensions"] as? JsonArray ?: JsonArray(emptyList())).objects().map { it.requiredText("id") }
        val known = GENERIC_SPRITES.toSet() + extensions
        return world.types.values.map { fact("world" to name, "type" to it.id, "sprite" to it.sprite, "sprite_known" to (it.sprite in known)) }
    }

    private fun footprintFacts(name: String, world: World): List<JsonObject> =
        world.types.values.filter { it.hasTileFootprint && "footprint_mm" in it.raw }.map { type ->
            val derived = JsonArray(world.derivedFootprint(type.id).map { JsonPrimitive(it) })
            val declared = type.raw.getValue("footprint")
            fact("world" to name, "type" to type.id, "footprint" to declared, "derived" to derived, "agrees" to pythonEquals(declared, derived))
        }

    private fun instanceFacts(name: String, world: World): List<JsonObject> {
        val entries = world.doc.optionalList("seed").objects().filter { it.requiredText("action") == "place_instance" }
        val idCounts = entries.groupingBy { it.requiredText("id") }.eachCount()
        return entries.map { entry ->
            val parameters = entry.requiredObject("parameters")
            val error = parameters["error"] as? JsonObject ?: JsonObject(emptyMap())
            fact(
                "world" to name,
                "id" to entry.requiredText("id"),
                "is_unique" to (idCounts[entry.requiredText("id")] == 1),
                "has_source" to ("source" in parameters),
                "missing_error_fields" to ERROR_FIELDS.filter { it !in error },
                "unreasoned" to ERROR_FIELDS.filter { it in error && !isFigureReasoned(error.getValue(it)) },
            )
        }
    }

    private fun isFigureReasoned(figure: JsonElement): Boolean {
        val fields = figure as? JsonObject ?: JsonObject(emptyMap())
        val value = fields["value"]
        if (value == null || value is JsonNull) return isTruthy(fields["null_reason"])
        return "source" in fields
    }

    fun judge(facts: Map<String, List<JsonObject>>, rules: List<JsonObject>): ValidationReport {
        val blind = mutableListOf<BlindFinding>()
        val fired = mutableListOf<FiredFinding>()
        rules.forEach { rule ->
            val records = facts[rule.requiredText("scans")] ?: throw MalformedDataException("rules.json scans an unknown set")
            if (rule.optionalText("kind") == "blind") {
                val minimum = (rule["min"] as JsonPrimitive).content.toInt()
                if (records.size < minimum) blind += BlindFinding(rule.requiredText("id"), rule.requiredText("message"))
                return@forEach
            }
            records.filterNot { holds(rule.requiredObject("condition"), it) }.forEach { record ->
                fired += FiredFinding(rule.requiredText("id"), pythonStr(record.getValue("world")), formatMessage(rule.requiredText("message"), record))
            }
        }
        val worlds = facts.getValue("worlds").map { pythonStr(it.getValue("world")) }
        val invalid = fired.map { it.world }.toSet().sorted()
        return ValidationReport(blind, fired, worlds.filter { it !in invalid }, invalid)
    }

    fun holds(condition: JsonObject, record: JsonObject): Boolean {
        val value = condition.optionalText("field")?.let { record[it] } ?: JsonNull
        return when (val operation = condition.requiredText("op")) {
            "empty" -> !isTruthy(value)
            "nonempty" -> isTruthy(value)
            "is_true" -> value is JsonPrimitive && !value.isString && value.content == "true"
            "is_false" -> value is JsonPrimitive && !value.isString && value.content == "false"
            "eq_value" -> pythonEquals(value, condition["value"])
            "when" -> !holds(condition.requiredObject("if"), record) || holds(condition.requiredObject("then"), record)
            "all" -> (condition["of"] as JsonArray).all { holds(it.jsonObject, record) }
            else -> throw MalformedDataException("rules.json uses unknown op $operation")
        }
    }

    private fun formatMessage(template: String, record: JsonObject): String =
        Regex("\\{([A-Za-z_][A-Za-z0-9_]*)\\}").replace(template) { match ->
            record[match.groupValues[1]]?.let { pythonStr(it) } ?: match.value
        }
}

private fun fact(vararg fields: Pair<String, Any?>): JsonObject = fact(fields.toList())

private fun fact(fields: List<Pair<String, Any?>>): JsonObject = JsonObject(fields.associateTo(LinkedHashMap()) { (key, value) -> key to jsonOf(value) })

private fun jsonOf(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map { jsonOf(it) })
    else -> JsonPrimitive(value.toString())
}
