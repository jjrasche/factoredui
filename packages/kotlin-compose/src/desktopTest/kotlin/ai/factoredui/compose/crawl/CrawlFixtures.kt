package ai.factoredui.compose.crawl

import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue

fun specOf(root: SpecNode): Spec = Spec(specVersion = 1, rendererMin = 1, root = root)

fun buttonNode(id: String, label: String, action: String?): SpecNode = SpecNode(
    id = id,
    type = SpecNodeType.BUTTON,
    props = mapOf("label" to SpecValue.StringValue(label)),
    action = action?.let { ActionRef(action = it) },
)

fun textNode(id: String, text: String): SpecNode = SpecNode(
    id = id,
    type = SpecNodeType.TEXT,
    props = mapOf("text" to SpecValue.StringValue(text)),
)

fun columnNode(id: String, children: List<SpecNode>): SpecNode =
    SpecNode(id = id, type = SpecNodeType.COLUMN, children = children)

fun scrollNode(id: String, children: List<SpecNode>): SpecNode =
    SpecNode(id = id, type = SpecNodeType.SCROLLVIEW, children = children)

fun stackNode(id: String, children: List<SpecNode>): SpecNode =
    SpecNode(id = id, type = SpecNodeType.STACK, children = children)

fun roomyCardNode(id: String, action: String?): SpecNode = SpecNode(
    id = id,
    type = SpecNodeType.CARD,
    props = mapOf("padding" to SpecValue.NumberValue(32.0)),
    children = listOf(textNode("$id.label", "a comfortably large target")),
    action = action?.let { ActionRef(action = it) },
)

val twoButtonSpec: Spec = specOf(
    columnNode(
        "root",
        listOf(
            buttonNode("go_a_button", "A", "go_a"),
            buttonNode("go_b_button", "B", "go_b"),
        ),
    ),
)

private fun pageStore(page: String): Map<String, Any?> = mapOf("page" to page)

class TwoPageHost : CrawlHost {
    override val handlers: Map<String, CrawlHandler> = mapOf(
        "go_a" to { state, _ -> state.copy(store = pageStore("a")) },
        "go_b" to { state, _ -> state.copy(store = pageStore("b")) },
    )
}

class UnregisteredActionHost : CrawlHost {
    override val handlers: Map<String, CrawlHandler> = emptyMap()
}

class StandingStillHost : CrawlHost {
    override val handlers: Map<String, CrawlHandler> = mapOf(
        "go_a" to { state, _ -> state },
        "go_b" to { state, _ -> state },
    )
}

class TwoPagePingPongHost : CrawlHost {
    override val handlers: Map<String, CrawlHandler> = mapOf(
        "go_there" to { state, _ -> state.copy(store = pageStore("there")) },
        "go_back" to { state, _ -> state.copy(store = pageStore("here")) },
    )
}

val pingPongSpec: Spec = specOf(
    columnNode(
        "root",
        listOf(
            buttonNode("there_button", "there", "go_there"),
            buttonNode("back_button", "back", "go_back"),
        ),
    ),
)

/**
 * The action map for samples/genesis-phone-screen.json — genesis's own two actions as pure
 * transitions, so the crawler can walk the phone screen without booting the engine.
 */
class GenesisPhoneHost : CrawlHost {
    override val initialStore: Map<String, Any?> = mapOf(
        "composing" to mapOf("intent" to "build the flow crawler"),
        "engine" to mapOf("url" to "http://10.0.0.5:8099"),
        "intents" to listOf("build the flow crawler"),
    )

    override val handlers: Map<String, CrawlHandler> = mapOf(
        "submit_intent" to { state, _ -> state.copy(store = appendIntent(state.store)) },
        "set_engine_address" to { state, _ -> state.copy(store = state.store) },
    )
}

private fun appendIntent(store: Map<String, Any?>): Map<String, Any?> {
    val composed = (store["composing"] as? Map<*, *>)?.get("intent") as? String ?: return store
    val intents = (store["intents"] as? List<*>).orEmpty() + composed
    return store + mapOf("intents" to intents, "composing" to mapOf("intent" to ""))
}
