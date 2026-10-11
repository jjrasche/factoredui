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

private const val FRAME_NANOS = 16_666_667L

/** A headless screen a control port can drive, one window unit to one pixel, settled before and after every act. */
@OptIn(ExperimentalComposeUiApi::class)
class SceneSurface(spec: Spec, context: RenderContext, width: Int, height: Int) : ControlSurface, AutoCloseable {
    private val lock = Any()
    private var clock = 0L
    private val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            Box(Modifier.fillMaxSize()) { RenderSpec(spec = spec, context = context) }
        }
    }

    init {
        settle()
    }

    private fun settle() {
        repeat(3) {
            clock += FRAME_NANOS
            scene.render(clock).close()
        }
    }

    override fun click(point: Point): String = onScreenThread {
        scene.sendPointerEvent(PointerEventType.Press, Offset(point.x.toFloat(), point.y.toFloat()))
        scene.sendPointerEvent(PointerEventType.Release, Offset(point.x.toFloat(), point.y.toFloat()))
        "clicked ${point.x},${point.y}"
    }

    override fun wheel(point: Point, notches: Int): String = error("this surface does not scroll")

    override fun shot(file: File): String = onScreenThread {
        clock += FRAME_NANOS
        val png = scene.render(clock).encodeToData(EncodedImageFormat.PNG) ?: error("no frame could be encoded")
        file.writeBytes(png.bytes)
        "saved ${file.path}"
    }

    override fun <T> onScreenThread(work: () -> T): T = synchronized(lock) {
        settle()
        work().also { settle() }
    }

    override fun close() = scene.close()
}
