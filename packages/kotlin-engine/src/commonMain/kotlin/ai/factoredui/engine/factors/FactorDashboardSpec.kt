package ai.factoredui.engine.factors

import ai.factoredui.compose.schema.RENDERER_VERSION
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue

/**
 * Builds an SDUI spec for a factor dashboard.
 *
 * Ported from `packages/core/src/factors/dashboard-spec.ts`. The spec binds a
 * `list` to the `factors` data source; each factor renders as an outlined card
 * with a tier chip and Avg / P95 / Users stats. Per-row bindings use the
 * legacy `{item.x}` scope key (the renderer also exposes `{row.x}`).
 *
 * Pairs with the reactive `data_source` LIST binding: a host can point the list
 * at a live `ComponentFactorAggregate` query and the dashboard updates as
 * factors recompute.
 */
fun buildFactorDashboardSpec(componentPath: String): Spec = Spec(
    specVersion = 1,
    rendererMin = RENDERER_VERSION,
    root = buildRoot(componentPath),
)

private fun buildRoot(componentPath: String): SpecNode = node(
    id = "root",
    type = SpecNodeType.COLUMN,
    props = mapOf("gap" to num(16), "padding" to num(16)),
    children = listOf(buildHeader(componentPath), buildFactorList()),
)

private fun buildHeader(componentPath: String): SpecNode = node(
    id = "header",
    type = SpecNodeType.COLUMN,
    props = mapOf("gap" to num(4)),
    children = listOf(
        node(
            id = "title",
            type = SpecNodeType.TEXT,
            props = mapOf("value" to str("Factor Dashboard"), "variant" to str("heading")),
        ),
        node(
            id = "subtitle",
            type = SpecNodeType.TEXT,
            props = mapOf("value" to str(componentPath), "variant" to str("caption"), "color" to str("#666")),
        ),
    ),
)

private fun buildFactorList(): SpecNode = node(
    id = "factor-list",
    type = SpecNodeType.LIST,
    props = mapOf(
        "data" to str("{sources.factors}"),
        "emptyText" to str("No factors computed yet"),
        "itemTemplate" to SpecValue.NodeValue(buildFactorCard()),
    ),
)

private fun buildFactorCard(): SpecNode = node(
    id = "factor-card",
    type = SpecNodeType.CARD,
    props = mapOf("variant" to str("outlined")),
    children = listOf(
        node(
            id = "card-content",
            type = SpecNodeType.COLUMN,
            props = mapOf("gap" to num(8), "padding" to num(12)),
            children = listOf(buildCardHeader(), buildCardStats()),
        ),
    ),
)

private fun buildCardHeader(): SpecNode = node(
    id = "card-header",
    type = SpecNodeType.ROW,
    props = mapOf("justify" to str("between"), "align" to str("center")),
    children = listOf(
        node(
            id = "factor-name",
            type = SpecNodeType.TEXT,
            props = mapOf("value" to str("{item.factor_name}"), "variant" to str("subheading"), "bold" to bool(true)),
        ),
        node(
            id = "tier-badge",
            type = SpecNodeType.CHIP,
            props = mapOf("label" to str("{item.factor_tier}"), "variant" to str("filled")),
        ),
    ),
)

private fun buildCardStats(): SpecNode = node(
    id = "card-stats",
    type = SpecNodeType.ROW,
    props = mapOf("gap" to num(16)),
    children = listOf(
        buildStatColumn("stat-avg", "Avg", "{item.avg_value}"),
        buildStatColumn("stat-p95", "P95", "{item.p95_value}"),
        buildStatColumn("stat-users", "Users", "{item.user_count}"),
    ),
)

private fun buildStatColumn(id: String, label: String, valueRef: String): SpecNode = node(
    id = id,
    type = SpecNodeType.COLUMN,
    props = mapOf("align" to str("center"), "gap" to num(2)),
    children = listOf(
        node(
            id = "$id-label",
            type = SpecNodeType.TEXT,
            props = mapOf("value" to str(label), "variant" to str("caption"), "color" to str("#888")),
        ),
        node(
            id = "$id-value",
            type = SpecNodeType.TEXT,
            props = mapOf("value" to str(valueRef), "variant" to str("body"), "bold" to bool(true)),
        ),
    ),
)

// --- SpecValue / SpecNode construction helpers ---

private fun str(value: String): SpecValue = SpecValue.StringValue(value)
private fun num(value: Int): SpecValue = SpecValue.NumberValue(value.toDouble())
private fun bool(value: Boolean): SpecValue = SpecValue.BooleanValue(value)

private fun node(
    id: String,
    type: SpecNodeType,
    props: Map<String, SpecValue> = emptyMap(),
    children: List<SpecNode> = emptyList(),
): SpecNode = SpecNode(id = id, type = type, props = props, children = children)
