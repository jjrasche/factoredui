package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import javax.swing.JFrame
import javax.swing.SwingUtilities

/** What a control port drives: pointer input, a picture of the screen, and the thread the screen is drawn on. */
interface ControlSurface {
    fun click(point: Point): String
    fun wheel(point: Point, notches: Int): String
    fun shot(file: File): String
    fun <T> onScreenThread(work: () -> T): T
}

// Pointer input goes through the window's own AWT listeners, so it reaches the widgets on a locked screen.
class AwtWindowSurface : ControlSurface {
    @Volatile var window: JFrame? = null

    override fun click(point: Point): String = onScreenThread {
        val (target, local) = listenerAt(point) { it.mouseListeners.isNotEmpty() }
        val modifiers = MouseEvent.BUTTON1_DOWN_MASK
        target.dispatchEvent(mouseEvent(target, MouseEvent.MOUSE_MOVED, local, 0, 0))
        target.dispatchEvent(mouseEvent(target, MouseEvent.MOUSE_PRESSED, local, modifiers, 1))
        target.dispatchEvent(mouseEvent(target, MouseEvent.MOUSE_RELEASED, local, 0, 1))
        target.dispatchEvent(mouseEvent(target, MouseEvent.MOUSE_CLICKED, local, 0, 1))
        "clicked ${point.x},${point.y} on ${target.javaClass.name}"
    }

    override fun wheel(point: Point, notches: Int): String = onScreenThread {
        val (target, local) = listenerAt(point) { it.mouseWheelListeners.isNotEmpty() }
        val event = MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, local.x, local.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, notches)
        target.dispatchEvent(event)
        "wheel $notches at ${point.x},${point.y}"
    }

    override fun shot(file: File): String = onScreenThread {
        val layer = skiaLayerIn(checkNotNull(window) { "the window is not open yet" }) ?: error("the window holds no SkiaLayer")
        val bitmap = layer.screenshot() ?: error("the window has drawn no frame yet")
        val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG) ?: error("the window's pixels could not be encoded")
        file.writeBytes(png.bytes)
        "saved ${file.path} ${bitmap.width}x${bitmap.height}"
    }

    override fun <T> onScreenThread(work: () -> T): T {
        var outcome: Result<T>? = null
        SwingUtilities.invokeAndWait { outcome = runCatching(work) }
        return checkNotNull(outcome).getOrThrow()
    }

    private fun listenerAt(point: Point, isListener: (Component) -> Boolean): Pair<Component, Point> {
        val content = checkNotNull(window) { "the window is not open yet" }.contentPane
        val deepest = SwingUtilities.getDeepestComponentAt(content, point.x, point.y) ?: error("nothing at ${point.x},${point.y}")
        val target = generateSequence(deepest) { it.parent }.firstOrNull(isListener) ?: error("nothing at ${point.x},${point.y} listens")
        return target to SwingUtilities.convertPoint(content, point, target)
    }
}

/** A handler reads only the parameters its route declares, so the declared set is the route's real interface. */
class RouteQuery(private val route: String, private val parameters: Set<String>, private val raw: Map<String, String>) {
    fun optional(name: String): String? {
        check(name in parameters) { "route /$route does not declare the parameter '$name'" }
        return raw[name]
    }

    fun required(name: String): String = requireNotNull(optional(name)) { "route /$route needs the parameter '$name'" }
}

class ControlRoute(val name: String, val parameters: Set<String>, val handle: (RouteQuery) -> String)

class WindowControl(private val context: RenderContext, private val surface: ControlSurface = AwtWindowSurface()) {

    val routes: List<ControlRoute> = listOf(
        ControlRoute("tree", setOf("id", "text", "visible")) { query -> surface.onScreenThread { treeText(query) } },
        ControlRoute("get", setOf("path")) { query -> readBinding(query.required("path")) },
        ControlRoute("set", setOf("path", "value", SETTLE_MS)) { query -> settled(query) { setBinding(query.required("path"), query.required("value")) } },
        ControlRoute("click", setOf("id", "x", "y", SETTLE_MS)) { query -> settled(query, clickExpectation(query)) { click(query) } },
        ControlRoute("wheel", setOf("x", "y", "notches", SETTLE_MS)) { query -> settled(query) { surface.wheel(pointOf(query), query.required("notches").toInt()) } },
        ControlRoute("shot", setOf("file")) { query -> surface.shot(File(query.required("file"))) },
    )

    fun start(port: Int): HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0).apply {
        routes.forEach { route -> createContext("/${route.name}") { exchange -> answer(exchange) { raw -> route.handle(RouteQuery(route.name, route.parameters, raw)) } } }
        start()
    }

    private val writes = Any()

    /**
     * A write returns once the screen has drawn it: a frame after it, then a frame that changed nothing in the tree, or the bound.
     * The waiter count is changed on the screen's thread: the frame counter's coroutine resumes on whichever thread changes it.
     */
    private fun settled(query: RouteQuery, expected: () -> Boolean = { true }, write: () -> String): String {
        val boundMs = query.optional(SETTLE_MS)?.let { requireNotNull(it.toLongOrNull()?.takeIf { ms -> ms >= 0 }) { "$SETTLE_MS must be a whole number of milliseconds, not '$it'" } } ?: DEFAULT_SETTLE_MS
        synchronized(writes) {
            if (boundMs == 0L) return write()
            val screen = context.screen
            surface.onScreenThread { screen.waiters.update { it + 1 } }
            try {
                val startFrame = screen.frame
                val result = write()
                return "$result; ${quietAfter(startFrame, boundMs, expected)}"
            } finally {
                surface.onScreenThread { screen.waiters.update { it - 1 } }
            }
        }
    }

    /** What a click by id is known to produce, read on the screen's thread: a closed select opens, an option closes its menu. */
    private fun clickExpectation(query: RouteQuery): () -> Boolean {
        val id = query.optional("id") ?: return { true }
        val expectation = surface.onScreenThread { clickOutcomeOf(context.screen, id) }
        return { surface.onScreenThread { expectation(context.screen) } }
    }

    private fun isScreenWhole(): Boolean = surface.onScreenThread { openMenusArePlaced(context.screen) }

    private fun quietAfter(startFrame: Long, boundMs: Long, expected: () -> Boolean): String {
        val screen = context.screen
        val deadline = System.nanoTime() + boundMs * NANOS_PER_MS
        var seenFrame = screen.frame
        var seenChanges = screen.changes
        var quietFrames = 0
        while (screen.isCountingFrames && System.nanoTime() < deadline) {
            Thread.sleep(SETTLE_POLL_MS)
            val frame = screen.frame
            if (frame == seenFrame) continue
            val changes = screen.changes
            quietFrames = if (frame >= startFrame + 2 && changes == seenChanges) quietFrames + 1 else 0
            if (quietFrames >= QUIET_FRAMES && expected() && isScreenWhole()) return "settled frame=$frame"
            seenFrame = frame
            seenChanges = changes
        }
        val waited = if (screen.isCountingFrames) boundMs else 0L
        return "unsettled after $waited ms frame=${screen.frame}"
    }

    private fun setBinding(path: String, value: String): String {
        context.setBinding(path, jsonToAny(Json.parseToJsonElement(value)))
        return "set $path"
    }

    private fun readBinding(path: String): String {
        var current: Any? = context.data
        path.split(".").forEach { segment ->
            val parent = current as? Map<*, *>
            require(parent != null && parent.containsKey(segment)) { "$path names no key" }
            current = parent[segment]
        }
        return anyToJson(current).toString()
    }

    private fun click(query: RouteQuery): String {
        val id = query.optional("id") ?: return surface.click(pointOf(query))
        return when (val target = surface.onScreenThread { clickTargetFor(context.screen, id) }) {
            is ClickTarget.AtPoint -> surface.click(target.point)
            is ClickTarget.ScenePart -> surface.onScreenThread { tapScenePart(context.screen, target) }
        }
    }

    private fun treeText(query: RouteQuery): String {
        val drawn = screenNodes(context.screen) ?: error("nothing has been drawn yet")
        val shown = if (query.optional("visible") == "1") visibleOnly(drawn) ?: error("the root is not visible") else drawn
        query.optional("text")?.let { substring -> return JsonArray(idsShowing(shown, substring).map(::JsonPrimitive)).toString() }
        val subtree = query.optional("id")?.let { id -> findNode(shown, id) ?: error("no drawn node has id '$id'") } ?: shown
        return JsonObject(mapOf("frame" to JsonPrimitive(context.screen.frame)) + screenJson(subtree)).toString()
    }
}

private const val SETTLE_MS = "settle_ms"
private const val DEFAULT_SETTLE_MS = 2000L
private const val SETTLE_POLL_MS = 2L
private const val QUIET_FRAMES = 2
private const val NANOS_PER_MS = 1_000_000L

private fun mouseEvent(target: Component, id: Int, at: Point, modifiers: Int, clicks: Int) =
    MouseEvent(target, id, System.currentTimeMillis(), modifiers, at.x, at.y, clicks, false, if (id == MouseEvent.MOUSE_MOVED) MouseEvent.NOBUTTON else MouseEvent.BUTTON1)

private fun skiaLayerIn(component: Component): SkiaLayer? {
    if (component is SkiaLayer) return component
    val children = (component as? Container)?.components.orEmpty()
    return children.firstNotNullOfOrNull { skiaLayerIn(it) }
}

private fun pointOf(query: RouteQuery) = Point(query.required("x").toInt(), query.required("y").toInt())

private fun queryOf(exchange: HttpExchange): Map<String, String> =
    exchange.requestURI.rawQuery.orEmpty().split("&").filter { it.contains("=") }.associate { pair ->
        URLDecoder.decode(pair.substringBefore("="), Charsets.UTF_8) to URLDecoder.decode(pair.substringAfter("="), Charsets.UTF_8)
    }

private fun answer(exchange: HttpExchange, handle: (Map<String, String>) -> String) {
    val outcome = runCatching { handle(queryOf(exchange)) }
    val body = outcome.getOrElse { failure -> "refused: ${failure.message ?: failure::class.simpleName}" }.toByteArray()
    exchange.sendResponseHeaders(if (outcome.isSuccess) 200 else 400, body.size.toLong())
    exchange.responseBody.use { it.write(body) }
}
