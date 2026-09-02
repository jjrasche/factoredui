package ai.factoredui.compose.crawl

import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import kotlinx.serialization.json.Json

/** The screen as the crawler holds it: the spec on show and the store it binds against. */
data class CrawlState(val spec: Spec, val store: Map<String, Any?>)

/**
 * One host action as a pure transition. Purity is load-bearing: the crawler replays paths and
 * compares states, so a handler that reaches outside its arguments makes the crawl unrepeatable.
 */
typealias CrawlHandler = (state: CrawlState, params: Map<String, Any?>) -> CrawlState

/** The action map a spec is crawled against. Named on the command line and loaded by class name. */
interface CrawlHost {
    val handlers: Map<String, CrawlHandler>
    val initialStore: Map<String, Any?> get() = emptyMap()
}

data class CrawlOptions(
    val maxDepth: Int = 4,
    val viewportWidthDp: Int = 400,
    val viewportHeightDp: Int = 800,
)

enum class FlowOutcome { NEW_STATE, CYCLE, NO_OP, DEAD_ACTION }

class FlowStep(val nodeId: String, val action: String, val png: ByteArray)

data class CrawledFlow(
    val id: String,
    val steps: List<FlowStep>,
    val outcome: FlowOutcome,
    val findings: List<FlowFinding>,
    val grade: Int,
)

data class CrawlReport(
    val flows: List<CrawledFlow>,
    val pageScore: Int,
    val meanGrade: Double,
    val statesSeen: Int,
)

/** Every node carrying an action, in spec order. The crawl's frontier at any one state. */
fun actionableNodes(root: SpecNode): List<SpecNode> {
    val actionable = mutableListOf<SpecNode>()
    fun walk(node: SpecNode) {
        if (node.action != null) actionable += node
        node.children.forEach(::walk)
    }
    walk(root)
    return actionable
}

fun crawlFlows(startSpec: Spec, host: CrawlHost, options: CrawlOptions = CrawlOptions()): CrawlReport {
    val crawl = FlowCrawl(host, options)
    crawl.explore(CrawlState(startSpec, host.initialStore))
    return crawl.report()
}

private class OpenFlow(
    val id: String,
    val steps: List<FlowStep>,
    val outcome: FlowOutcome,
    val findings: MutableList<FlowFinding>,
)

private class Frontier(
    val state: CrawlState,
    val path: List<FlowStep>,
    val depth: Int,
    val arrivedBy: OpenFlow?,
)

private class FlowCrawl(private val host: CrawlHost, private val options: CrawlOptions) {

    private val seen = mutableSetOf<String>()
    private val screens = mutableMapOf<String, RenderedScreen>()
    private val flows = mutableListOf<OpenFlow>()

    fun explore(start: CrawlState) {
        seen += fingerprintState(start)
        val queue = ArrayDeque<Frontier>()
        queue += Frontier(start, emptyList(), 0, null)
        while (queue.isNotEmpty()) expandFrontier(queue.removeFirst(), queue)
    }

    fun report(): CrawlReport {
        val graded = flows.map { finishFlow(it) }.sortedWith(compareBy({ it.grade }, { it.id }))
        return CrawlReport(
            flows = graded,
            pageScore = graded.minOfOrNull { it.grade } ?: PERFECT_FLOW_GRADE,
            meanGrade = if (graded.isEmpty()) PERFECT_FLOW_GRADE.toDouble() else graded.map { it.grade }.average(),
            statesSeen = seen.size,
        )
    }

    private fun expandFrontier(frontier: Frontier, queue: ArrayDeque<Frontier>) {
        val outcomes = actionableNodes(frontier.state.spec.root).map { node -> walkAction(frontier, node, queue) }
        markDeadEnd(frontier, outcomes)
    }

    private fun walkAction(frontier: Frontier, node: SpecNode, queue: ArrayDeque<Frontier>): FlowOutcome {
        val actionRef = requireNotNull(node.action) { "actionableNodes only yields nodes carrying an action" }
        val handler = host.handlers[actionRef.action]
        val params = BindingResolver.resolveProps(actionRef.params, frontier.state.store)
        val next = handler?.invoke(frontier.state, params) ?: frontier.state
        val outcome = classifyTransition(handler, frontier.state, next)
        val flow = openFlow(frontier, node, actionRef.action, next, outcome)
        if (outcome != FlowOutcome.NEW_STATE) return outcome
        seen += fingerprintState(next)
        if (frontier.depth + 1 < options.maxDepth) {
            queue += Frontier(next, flow.steps, frontier.depth + 1, flow)
        }
        return outcome
    }

    private fun classifyTransition(handler: CrawlHandler?, from: CrawlState, to: CrawlState): FlowOutcome = when {
        handler == null -> FlowOutcome.DEAD_ACTION
        to == from -> FlowOutcome.NO_OP
        fingerprintState(to) in seen -> FlowOutcome.CYCLE
        else -> FlowOutcome.NEW_STATE
    }

    private fun openFlow(
        frontier: Frontier,
        node: SpecNode,
        action: String,
        landing: CrawlState,
        outcome: FlowOutcome,
    ): OpenFlow {
        val screen = screenOf(landing)
        val findings = mutableListOf<FlowFinding>()
        outcomeFinding(outcome, node.id)?.let { findings += it }
        findings += structuralFindings(screen, landing.spec.root)
        val flow = OpenFlow(
            id = "flow-%03d".format(flows.size + 1),
            steps = frontier.path + FlowStep(node.id, action, screen.png),
            outcome = outcome,
            findings = findings,
        )
        flows += flow
        return flow
    }

    private fun markDeadEnd(frontier: Frontier, outcomes: List<FlowOutcome>) {
        val arrivedBy = frontier.arrivedBy ?: return
        if (outcomes.any { it == FlowOutcome.NEW_STATE }) return
        arrivedBy.findings += FlowFinding(CrawlCheck.DEAD_END, listOf(), FlowDeductions.DEAD_END)
    }

    private fun screenOf(state: CrawlState): RenderedScreen = screens.getOrPut(fingerprintState(state)) {
        renderScreen(state.spec, state.store, options.viewportWidthDp, options.viewportHeightDp)
    }

    private fun finishFlow(flow: OpenFlow): CrawledFlow = CrawledFlow(
        id = flow.id,
        steps = flow.steps,
        outcome = flow.outcome,
        findings = flow.findings.toList(),
        grade = gradeFlow(flow.findings),
    )
}

private fun outcomeFinding(outcome: FlowOutcome, nodeId: String): FlowFinding? = when (outcome) {
    FlowOutcome.NEW_STATE -> null
    FlowOutcome.CYCLE -> FlowFinding(CrawlCheck.CYCLE, listOf(nodeId), FlowDeductions.CYCLE)
    FlowOutcome.NO_OP -> FlowFinding(CrawlCheck.NO_OP, listOf(nodeId), FlowDeductions.NO_OP)
    FlowOutcome.DEAD_ACTION -> FlowFinding(CrawlCheck.DEAD_ACTION, listOf(nodeId), FlowDeductions.DEAD_ACTION)
}

private val fingerprintJson = Json { encodeDefaults = true }

fun fingerprintState(state: CrawlState): String =
    fingerprintJson.encodeToString(Spec.serializer(), state.spec) + "|" + fingerprintStore(state.store)

private fun fingerprintStore(store: Map<String, Any?>): String =
    store.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${fingerprintValue(it.value)}" }

private fun fingerprintValue(value: Any?): String = when (value) {
    is Map<*, *> -> value.entries
        .sortedBy { it.key.toString() }
        .joinToString(",", "{", "}") { "${it.key}=${fingerprintValue(it.value)}" }
    is List<*> -> value.joinToString(",", "[", "]") { fingerprintValue(it) }
    else -> value.toString()
}
