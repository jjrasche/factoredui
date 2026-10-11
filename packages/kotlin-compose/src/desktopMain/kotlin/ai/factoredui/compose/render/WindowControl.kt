package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
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

// Pointer input goes through the window's own AWT listeners, so it reaches the widgets on a locked screen.
class WindowControl(private val context: RenderContext) {
    @Volatile var window: JFrame? = null

    fun start(port: Int): HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0).apply {
        createContext("/set") { exchange -> answer(exchange) { query -> setBinding(query) } }
        createContext("/get") { exchange -> answer(exchange) { query -> readBinding(query) } }
        createContext("/click") { exchange -> answer(exchange) { query -> onEdt { clickAt(pointOf(query)) } } }
        createContext("/wheel") { exchange -> answer(exchange) { query -> onEdt { wheelAt(pointOf(query), query.getValue("notches").toInt()) } } }
        createContext("/shot") { exchange -> answer(exchange) { query -> onEdt { saveShot(File(query.getValue("file"))) } } }
        start()
    }

    private fun setBinding(query: Map<String, String>): String {
        val path = query.getValue("path")
        context.setBinding(path, jsonToAny(Json.parseToJsonElement(query.getValue("value"))))
        return "set $path"
    }

    private fun readBinding(query: Map<String, String>): String {
        val path = query.getValue("path")
        var current: Any? = context.data
        path.split(".").forEach { segment ->
            val parent = current as? Map<*, *>
            require(parent != null && parent.containsKey(segment)) { "$path names no key" }
            current = parent[segment]
        }
        return anyToJson(current).toString()
    }

    private fun clickAt(point: Point): String {
        val (target, local) = listenerAt(point) { it.mouseListeners.isNotEmpty() }
        val modifiers = MouseEvent.BUTTON1_DOWN_MASK
        target.dispatchEvent(mouseEvent(target, MouseEvent.MOUSE_MOVED, local, 0, 0))
        target.dispatchEvent(mouseEvent(target, MouseEvent.MOUSE_PRESSED, local, modifiers, 1))
        target.dispatchEvent(mouseEvent(target, MouseEvent.MOUSE_RELEASED, local, 0, 1))
        target.dispatchEvent(mouseEvent(target, MouseEvent.MOUSE_CLICKED, local, 0, 1))
        return "clicked ${point.x},${point.y} on ${target.javaClass.name}"
    }

    private fun wheelAt(point: Point, notches: Int): String {
        val (target, local) = listenerAt(point) { it.mouseWheelListeners.isNotEmpty() }
        val event = MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, local.x, local.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, notches)
        target.dispatchEvent(event)
        return "wheel $notches at ${point.x},${point.y}"
    }

    private fun saveShot(file: File): String {
        val layer = skiaLayerIn(checkNotNull(window) { "the window is not open yet" }) ?: error("the window holds no SkiaLayer")
        val bitmap = layer.screenshot() ?: error("the window has drawn no frame yet")
        val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG) ?: error("the window's pixels could not be encoded")
        file.writeBytes(png.bytes)
        return "saved ${file.path} ${bitmap.width}x${bitmap.height}"
    }

    private fun listenerAt(point: Point, isListener: (Component) -> Boolean): Pair<Component, Point> {
        val content = checkNotNull(window) { "the window is not open yet" }.contentPane
        val deepest = SwingUtilities.getDeepestComponentAt(content, point.x, point.y) ?: error("nothing at ${point.x},${point.y}")
        val target = generateSequence(deepest) { it.parent }.firstOrNull(isListener) ?: error("nothing at ${point.x},${point.y} listens")
        return target to SwingUtilities.convertPoint(content, point, target)
    }
}

private fun mouseEvent(target: Component, id: Int, at: Point, modifiers: Int, clicks: Int) =
    MouseEvent(target, id, System.currentTimeMillis(), modifiers, at.x, at.y, clicks, false, if (id == MouseEvent.MOUSE_MOVED) MouseEvent.NOBUTTON else MouseEvent.BUTTON1)

private fun skiaLayerIn(component: Component): SkiaLayer? {
    if (component is SkiaLayer) return component
    val children = (component as? Container)?.components.orEmpty()
    return children.firstNotNullOfOrNull { skiaLayerIn(it) }
}

private fun pointOf(query: Map<String, String>) = Point(query.getValue("x").toInt(), query.getValue("y").toInt())

private fun <T> onEdt(work: () -> T): T {
    var outcome: Result<T>? = null
    SwingUtilities.invokeAndWait { outcome = runCatching(work) }
    return checkNotNull(outcome).getOrThrow()
}

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
