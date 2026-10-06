package ai.factoredui.worldengine.world

import ai.factoredui.worldengine.expression.ExpressionException
import ai.factoredui.worldengine.expression.INSTANCE_VERBS
import ai.factoredui.worldengine.expression.ruleSiteFor
import ai.factoredui.worldengine.expression.Scope
import ai.factoredui.worldengine.expression.ScopeSite
import ai.factoredui.worldengine.expression.ValueType
import ai.factoredui.worldengine.expression.checkExpression
import ai.factoredui.worldengine.expression.describeType
import ai.factoredui.worldengine.expression.numberOfUnit
import ai.factoredui.worldengine.expression.projectedAgentTypes
import ai.factoredui.worldengine.expression.referencedNames
import ai.factoredui.worldengine.ground.findGroundProblems
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

sealed interface Expectation {
    data object Anything : Expectation
    data class OfType(val type: ValueType) : Expectation
    data class OfUnit(val unit: String?) : Expectation
    data class MissingTarget(val message: String) : Expectation
}

data class ExpressionSite(
    val where: String,
    val text: String?,
    val scope: Scope,
    val expectation: Expectation,
    val isBinding: Boolean = false,
)

data class SiteProblem(val kind: String, val message: String)

data class Finding(val where: String, val kind: String, val message: String)

fun expressionSites(world: World): List<ExpressionSite> = ruleSites(world) + equationSites(world) + stockSites(world) + scoreSites(world) + agentSites(world)

private fun ruleSites(world: World): List<ExpressionSite> = (world.ownRules + world.inheritedRules).flatMap { rule ->
    val where = if (rule.isInherited) "inherited.${rule.id}" else "rules.${rule.id}"
    val site = ruleSiteFor(rule.on)
    val sites = mutableListOf<ExpressionSite>()
    if (rule.hasRequire) sites += ExpressionSite("$where.require", rule.require, Scope(world, site), Expectation.OfType(ValueType.Bool))
    if (rule.hasEffect) sites += ExpressionSite("$where.effect", rule.effect?.to ?: "", Scope(world, site), effectExpectation(world, rule))
    sites
}

private fun equationSites(world: World): List<ExpressionSite> = world.equations.values.map {
    ExpressionSite("equations.${it.id}", it.expr, Scope(world, ScopeSite.EQUATION), Expectation.OfUnit(it.requiredUnit()))
}

private fun stockSites(world: World): List<ExpressionSite> = world.stocks.values.map {
    ExpressionSite("stocks.${it.id}", it.next, Scope(world, ScopeSite.STOCK), Expectation.OfUnit(it.requiredUnit()))
}

private fun scoreSites(world: World): List<ExpressionSite> = world.scoring.values.map {
    ExpressionSite("scoring.${it.id}", it.expr, Scope(world, ScopeSite.SCORING), Expectation.OfUnit(it.requiredUnit()), it.isBinding)
}

private fun agentSites(world: World): List<ExpressionSite> = world.agents.values.flatMap { agent ->
    listOf("weight" to agent.weight, "utility" to agent.utility).map { (field, text) ->
        ExpressionSite("agents.${agent.type}.$field", text, Scope(world, ScopeSite.AGENT, agent.type), Expectation.OfUnit("1"))
    }
}

private fun effectExpectation(world: World, rule: RuleSpec): Expectation {
    val target = rule.effect?.set
    if (rule.on in INSTANCE_VERBS) return Expectation.MissingTarget("an instance carries no properties, so an ${rule.on} rule cannot set '${target ?: "None"}'")
    val typeId = rule.appliesTo.orEmpty().firstOrNull { it in world.types } ?: return Expectation.Anything
    val spec = target?.let { world.propertySpec(typeId, it) } ?: return Expectation.MissingTarget("type $typeId has no property '${target ?: "None"}'")
    return Expectation.OfType(if (spec.isText) ValueType.Str else numberOfUnit(spec.unit))
}

fun checkSite(site: ExpressionSite): SiteProblem? = try {
    val produced = checkExpression(site.scope.world.ast(site.text), site.scope)
    val wanted = wantedType(site.expectation)
    when {
        site.expectation is Expectation.MissingTarget -> SiteProblem("unknown_word", site.expectation.message)
        wanted != null && produced != wanted -> SiteProblem("unit_mismatch", "produces ${describeType(produced)}, declared ${describeType(wanted)}")
        else -> null
    }
} catch (problem: ExpressionException) {
    SiteProblem(problem.kind, problem.message)
}

private fun wantedType(expectation: Expectation): ValueType? = when (expectation) {
    Expectation.Anything -> null
    is Expectation.OfType -> expectation.type
    is Expectation.OfUnit -> numberOfUnit(expectation.unit)
    is Expectation.MissingTarget -> null
}

fun nameGraph(world: World): Map<String, Set<String>> {
    val named = LinkedHashMap<String, String?>()
    world.equations.values.forEach { named["equation:${it.id}"] = it.expr }
    world.scoring.values.forEach { named["scoring:${it.id}"] = it.expr }
    world.agents.values.forEach { named["agent:${it.type}"] = "(${pythonFieldText(it.weightRaw)}) + (${pythonFieldText(it.utilityRaw)})" }
    return named.mapValues { (_, text) -> edgesOf(world, text) }
}

private fun pythonFieldText(element: JsonElement?): String = when {
    element == null -> "None"
    element is JsonPrimitive && element.isString -> element.content
    else -> ai.factoredui.worldengine.text.pythonStr(element)
}

private fun edgesOf(world: World, text: String?): Set<String> {
    val tree = try {
        world.ast(text)
    } catch (_: ExpressionException) {
        return emptySet()
    }
    val nameEdges = referencedNames(tree).mapNotNull { name ->
        when (name) {
            in world.equations -> "equation:$name"
            in world.scoring -> "scoring:$name"
            else -> null
        }
    }
    return (nameEdges + projectedAgentTypes(tree).map { "agent:$it" }).toSet()
}

fun findCycle(world: World): List<String> = CycleSearch(nameGraph(world)).find()

private class CycleSearch(private val graph: Map<String, Set<String>>) {
    private val colour: MutableMap<String, String> = graph.keys.associateWithTo(mutableMapOf()) { "white" }
    private val trail: MutableList<String> = mutableListOf()

    fun find(): List<String> {
        for (node in graph.keys.sorted()) {
            if (colour[node] == "white") visit(node).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return emptyList()
    }

    private fun visit(node: String): List<String> {
        colour[node] = "grey"
        trail += node
        for (target in graph[node].orEmpty().sorted()) {
            if (colour[target] == "grey") return trail.subList(trail.indexOf(target), trail.size).toList() + target
            if (colour[target] == "white") visit(target).takeIf { it.isNotEmpty() }?.let { return it }
        }
        trail.removeAt(trail.lastIndex)
        colour[node] = "black"
        return emptyList()
    }
}

fun diagnose(world: World): List<Finding> {
    val findings = world.linkProblems.map { Finding("links", "link", it) }.toMutableList()
    findings += findGroundProblems(world.ground, world.cols, world.rows).map { Finding("ground", "ground", it) }
    val cycle = findCycle(world)
    if (cycle.isNotEmpty()) findings += Finding("names", "cycle", cycle.joinToString(" -> "))
    expressionSites(world).forEach { site -> checkSite(site)?.let { findings += Finding(site.where, it.kind, it.message) } }
    return findings
}
