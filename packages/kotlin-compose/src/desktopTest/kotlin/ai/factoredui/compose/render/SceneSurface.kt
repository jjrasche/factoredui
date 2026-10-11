package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.Spec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.awt.Point
import java.io.File
import kotlin.concurrent.thread

private const val FRAME_NANOS = 16_666_667L
private const val TICK_MS = 8L
private const val CLOSE_WAIT_MS = 2000L

/** A headless screen a control port can drive, one window unit to one pixel, drawing frames on its own like a live window. */
@OptIn(ExperimentalComposeUiApi::class)
class SceneSurface(spec: Spec, context: RenderContext, width: Int, height: Int, ticking: Boolean = true) : ControlSurface, AutoCloseable {
    private val lock = Any()
    private var clock = 0L
    @Volatile private var isOpen = true
    @Volatile private var isFrozen = false
    private val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            Box(Modifier.fillMaxSize()) { RenderSpec(spec = spec, context = context) }
        }
    }
    private val ticker = if (ticking) thread(isDaemon = true, name = "scene-surface-frames") { drawFrames() } else null

    init {
        synchronized(lock) { repeat(3) { drawFrame() } }
    }

    private fun drawFrame() {
        clock += FRAME_NANOS
        scene.render(clock).close()
    }

    private fun drawFrames() {
        while (isOpen) {
            if (!isFrozen) synchronized(lock) { if (isOpen) drawFrame() }
            Thread.sleep(TICK_MS)
        }
    }

    /** Stops drawing frames, as a minimised window does. */
    fun freeze() {
        isFrozen = true
    }

    override fun click(point: Point): String = onScreenThread {
        scene.sendPointerEvent(PointerEventType.Press, Offset(point.x.toFloat(), point.y.toFloat()))
        scene.sendPointerEvent(PointerEventType.Release, Offset(point.x.toFloat(), point.y.toFloat()))
        "clicked ${point.x},${point.y}"
    }

    override fun wheel(point: Point, notches: Int): String = onScreenThread {
        scene.sendPointerEvent(PointerEventType.Scroll, Offset(point.x.toFloat(), point.y.toFloat()), scrollDelta = Offset(0f, notches.toFloat()))
        "wheel $notches at ${point.x},${point.y}"
    }

    override fun shot(file: File): String = onScreenThread {
        clock += FRAME_NANOS
        val png = scene.render(clock).encodeToData(EncodedImageFormat.PNG) ?: error("no frame could be encoded")
        file.writeBytes(png.bytes)
        "saved ${file.path}"
    }

    override fun <T> onScreenThread(work: () -> T): T = synchronized(lock) {
        if (ticker == null) repeat(3) { drawFrame() }
        work()
    }

    override fun close() {
        isOpen = false
        ticker?.join(CLOSE_WAIT_MS)
        synchronized(lock) { scene.close() }
    }
}
