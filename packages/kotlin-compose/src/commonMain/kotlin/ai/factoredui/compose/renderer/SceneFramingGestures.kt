package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.stackedscene.SceneCamera
import ai.factoredui.compose.stackedscene.aspectAfterDrag
import ai.factoredui.compose.stackedscene.pannedBy
import ai.factoredui.compose.stackedscene.zoomedAbout
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput

internal data class SceneFraming(val quarterTurns: Int = 0, val camera: SceneCamera = SceneCamera())

internal class FramingPaths(val quarterTurns: String?, val zoom: String?, val panX: String?, val panY: String?) {
    val isGestureEnabled: Boolean get() = quarterTurns != null || zoom != null
}

internal fun framingPathsOf(node: SpecNode) = FramingPaths(
    quarterTurns = node.props["quarter_turns"]?.bindingPath(),
    zoom = node.props["zoom"]?.bindingPath(),
    panX = node.props["pan_x"]?.bindingPath(),
    panY = node.props["pan_y"]?.bindingPath(),
)

private fun <T> boundOrHeld(path: String?, stated: T, held: T): T = if (path != null) stated else held

internal fun shownFraming(stated: SceneFraming, held: SceneFraming?, paths: FramingPaths): SceneFraming {
    if (held == null) return stated
    return SceneFraming(
        quarterTurns = boundOrHeld(paths.quarterTurns, stated.quarterTurns, held.quarterTurns),
        camera = SceneCamera(
            zoom = boundOrHeld(paths.zoom, stated.camera.zoom, held.camera.zoom),
            panX = boundOrHeld(paths.panX, stated.camera.panX, held.camera.panX),
            panY = boundOrHeld(paths.panY, stated.camera.panY, held.camera.panY),
        ),
    )
}

private fun writeChanged(context: RenderContext, path: String?, before: Any, after: Any) {
    if (path != null && before != after) context.setBinding(path, after)
}

internal fun writeFraming(context: RenderContext, paths: FramingPaths, before: SceneFraming, after: SceneFraming, hold: (SceneFraming) -> Unit) {
    writeChanged(context, paths.quarterTurns, before.quarterTurns, after.quarterTurns)
    writeChanged(context, paths.zoom, before.camera.zoom, after.camera.zoom)
    writeChanged(context, paths.panX, before.camera.panX, after.camera.panX)
    writeChanged(context, paths.panY, before.camera.panY, after.camera.panY)
    hold(after)
}

/** The framing a gesture builds on: what was last drawn, or what an earlier event in the same frame already moved it to. */
internal class FramingInHand {
    var drawn: SceneFraming = SceneFraming()
    var moved: SceneFraming? = null

    fun current(): SceneFraming = moved ?: drawn
}

private fun AwaitPointerEventScope.draggedFraming(start: SceneFraming, travel: Offset, isPan: Boolean, isTurnable: Boolean): SceneFraming? {
    if (isPan) return start.copy(camera = pannedBy(start.camera, travel.x.toDouble(), travel.y.toDouble(), size.width.toDouble(), size.height.toDouble()))
    if (!isTurnable) return null
    return aspectAfterDrag(start.quarterTurns, travel.x.toDp().value.toDouble())?.let { start.copy(quarterTurns = it) }
}

internal fun Modifier.framingGestures(isTurnable: Boolean, inHand: FramingInHand, move: (SceneFraming) -> Unit, tap: (Offset) -> Unit): Modifier =
    pointerInput(isTurnable) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val isPan = currentEvent.keyboardModifiers.isCtrlPressed
            val start = inHand.current()
            var isDragging = false
            var hasMoved = false
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp()) {
                    if (!isDragging) tap(change.position)
                    break
                }
                val travel = change.position - down.position
                if (!isDragging && travel.getDistance() > viewConfiguration.touchSlop) isDragging = true
                if (!isDragging) continue
                change.consume()
                if (hasMoved && !isPan) continue
                draggedFraming(start, travel, isPan, isTurnable)?.let { next ->
                    move(next)
                    hasMoved = true
                }
            }
        }
    }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.first()
                val now = inHand.current()
                move(now.copy(camera = zoomedAbout(now.camera, change.scrollDelta.y.toDouble(), change.position.x.toDouble(), change.position.y.toDouble(), size.width.toDouble(), size.height.toDouble())))
                change.consume()
            }
        }
    }
