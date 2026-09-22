package ai.factoredui.compose.crawl

import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.testing.SpecShadowNode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat

data class DrivenIntent(val nodeId: String, val action: String, val params: Map<String, Any?>)

private const val FRAME_NANOS = 16_666_667L

@OptIn(ExperimentalComposeUiApi::class)
class DrivenScreen internal constructor(
    private val spec: Spec,
    store: Map<String, Any?>,
    private val widthDp: Int,
    private val heightDp: Int,
) : AutoCloseable {

    private val recorded = mutableListOf<DrivenIntent>()
    private val context = RenderContext(initialData = store, observability = IntentRecorder(recorded))
    private var clock = 0L

    private val scene = ImageComposeScene(width = widthDp, height = heightDp, density = Density(1f)) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            Box(Modifier.fillMaxSize()) { RenderSpec(spec = spec, context = context) }
        }
    }

    init {
        settle()
    }

    fun intents(): List<DrivenIntent> = recorded.toList()

    fun frame(): RenderedScreen {
        settle()
        val png = scene.render(clock).encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
        return RenderedScreen(nodes = shadowNodes(), png = png, viewport = DpRect(0.dp, 0.dp, widthDp.dp, heightDp.dp))
    }

    fun tap(nodeId: String): List<DrivenIntent> {
        val before = recorded.size
        val target = regionOf(nodeId)
            ?: throw AssertionError("cannot tap '$nodeId': it drew no region in ${widthDp}x${heightDp}dp")
        val centre = Offset(
            (target.left.value + target.right.value) / 2f,
            (target.top.value + target.bottom.value) / 2f,
        )
        scene.sendPointerEvent(PointerEventType.Press, centre)
        scene.sendPointerEvent(PointerEventType.Release, centre)
        settle()
        return recorded.drop(before)
    }

    fun tapAt(xDp: Float, yDp: Float): List<DrivenIntent> {
        val before = recorded.size
        scene.sendPointerEvent(PointerEventType.Press, Offset(xDp, yDp))
        scene.sendPointerEvent(PointerEventType.Release, Offset(xDp, yDp))
        settle()
        return recorded.drop(before)
    }

    fun store(): Map<String, Any?> = context.data

    fun type(path: String, value: Any?) {
        context.setBinding(path, value)
        settle()
    }

    override fun close() = scene.close()

    private fun settle() {
        repeat(3) {
            clock += FRAME_NANOS
            scene.render(clock).close()
        }
    }

    private fun regionOf(nodeId: String): DpRect? = regionsNow()[nodeId]

    private fun shadowNodes(): List<SpecShadowNode> {
        val regions = regionsNow()
        val nodes = mutableListOf<SpecShadowNode>()
        fun walk(node: ai.factoredui.compose.schema.SpecNode) {
            nodes += SpecShadowNode(
                id = node.id,
                type = node.type,
                props = ai.factoredui.compose.schema.BindingResolver.resolveProps(node.props, context.data),
                bounds = regions[node.id],
            )
            node.children.forEach(::walk)
        }
        walk(spec.root)
        return nodes
    }

    private fun regionsNow(): Map<String, DpRect> {
        val regions = mutableMapOf<String, DpRect>()
        scene.semanticsOwners.forEach { owner ->
            fun walk(node: androidx.compose.ui.semantics.SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
                    if (tag !in regions) {
                        val origin = node.positionInRoot
                        regions[tag] = DpRect(
                            left = origin.x.dp,
                            top = origin.y.dp,
                            right = (origin.x + node.size.width).dp,
                            bottom = (origin.y + node.size.height).dp,
                        )
                    }
                }
                node.children.forEach(::walk)
            }
            walk(owner.unmergedRootSemanticsNode)
        }
        return regions
    }
}

fun driveScreen(
    spec: Spec,
    store: Map<String, Any?> = emptyMap(),
    widthDp: Int = 400,
    heightDp: Int = 800,
): DrivenScreen = DrivenScreen(spec, store, widthDp, heightDp)

private class IntentRecorder(private val into: MutableList<DrivenIntent>) : Observability {
    override fun onRender(nodeId: String) = Unit
    override fun onInteraction(nodeId: String, action: ActionRef, resolvedParams: Map<String, Any?>) {
        into += DrivenIntent(nodeId = nodeId, action = action.action, params = resolvedParams)
    }
}
