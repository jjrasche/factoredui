package ai.factoredui.measure

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.w3c.dom.HTMLElement

private fun str(value: String) = SpecValue.StringValue(value)

private val minimalSpec = SpecNode(
    id = "root",
    type = SpecNodeType.COLUMN,
    children = listOf(
        SpecNode(
            id = "headline",
            type = SpecNodeType.TEXT,
            props = mapOf("value" to str("{headline}"), "variant" to str("heading")),
        ),
        SpecNode(
            id = "tap",
            type = SpecNodeType.BUTTON,
            props = mapOf("label" to str("Tap me")),
            action = ActionRef(action = "tapped"),
        ),
    ),
)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    lateinit var context: RenderContext
    context = RenderContext(
        actions = mapOf("tapped" to { _: Map<String, Any?> ->
            context.setBinding("headline", "tapped")
            reportTapped()
        }),
        initialData = mapOf("headline" to "ready"),
    )
    ComposeViewport(documentBody()) {
        RenderSpec(root = minimalSpec, context = context)
    }
    reportRendered()
}

private fun documentBody(): HTMLElement = js("document.body")

private fun reportRendered(): Unit = js("{ window.__FUI_RENDERED = true; document.title = 'fui-rendered'; }")

private fun reportTapped(): Unit = js("{ window.__FUI_TAPPED = true; document.title = 'fui-tapped'; }")
