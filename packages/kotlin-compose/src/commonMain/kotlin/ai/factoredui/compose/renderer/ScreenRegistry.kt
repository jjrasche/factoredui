package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.SpecNode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.concurrent.Volatile
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

data class ScreenBounds(val x: Int, val y: Int, val w: Int, val h: Int) {
    val hasSize: Boolean get() = w > 0 && h > 0
    val centreX: Int get() = x + w / 2
    val centreY: Int get() = y + h / 2
}

data class ScenePartRecord(
    val id: String,
    val typeId: String,
    val level: Int,
    val fill: Long,
    val outline: String,
    val value: Double?,
    val canvasLeft: Float,
    val canvasTop: Float,
    val canvasRight: Float,
    val canvasBottom: Float,
    val canvasCentreX: Float,
    val canvasCentreY: Float,
    val coveredBy: List<String> = emptyList(),
)

data class MenuItemRecord(val value: String, val label: String, val selected: Boolean)

data class SceneLegendRecord(val text: String, val colour: Long)

data class SceneRecord(
    val view: String,
    val focus: String,
    val hideTop: Int,
    val showLower: Boolean,
    val yAxis: String,
    val north: String?,
    val activeScheme: String?,
    val legend: List<SceneLegendRecord>,
    val notices: List<String>,
    val parts: List<ScenePartRecord>,
    val popup: String?,
)

/** Where a scene's canvas sits in the window, in the units the window's pointer uses. */
data class CanvasPlacement(val originX: Float, val originY: Float, val pixelsPerUnit: Float)

class NodeRecord(
    val path: String,
    val nodeId: String,
    val type: String,
) {
    var visible: Boolean = true
    var boundProps: Map<String, Any?> = emptyMap()
    var enabled: Boolean = true
    var action: String? = null
    var text: String? = null
    var bounds: ScreenBounds? = null
    var rowCount: Int? = null
    var notices: List<String> = emptyList()
    var scene: SceneRecord? = null
    var canvas: CanvasPlacement? = null
    var fill: Long? = null
    var ink: Long? = null
    var shownProps: Map<String, Any?> = emptyMap()
    var menu: List<MenuItemRecord>? = null
    val menuBounds: MutableMap<String, ScreenBounds> = mutableMapOf()
    val menuComposed: MutableSet<String> = mutableSetOf()
    var partTap: ((String) -> Boolean)? = null
}

private fun NodeRecord.fingerprint(): List<Any?> =
    listOf(visible, boundProps, enabled, action, text, bounds, rowCount, notices, scene, canvas, fill, ink, shownProps, menu, menuBounds.toMap(), menuComposed.toSet())

/** Written on the screen's thread as it draws; [frame] and [changes] are read by a driver waiting on another thread for the screen to settle. */
class ScreenRegistry {
    private val records = LinkedHashMap<String, NodeRecord>()
    var root: SpecNode? = null

    @Volatile var frame: Long = 0L
        private set

    @Volatile var changes: Long = 0L
        private set

    @Volatile var isCountingFrames: Boolean = false

    val waiters = MutableStateFlow(0)

    fun countFrame() {
        frame += 1
    }

    fun recordOf(path: String): NodeRecord? = records[path]

    fun paths(): List<String> = records.keys.toList()

    private fun changeOf(record: NodeRecord, change: NodeRecord.() -> Unit) {
        val before = record.fingerprint()
        record.change()
        if (record.fingerprint() != before) changes += 1
    }

    fun record(path: String, nodeId: String, type: String, update: NodeRecord.() -> Unit) {
        val existing = records[path]
        if (existing != null) return changeOf(existing, update)
        records[path] = NodeRecord(path, nodeId, type).apply(update)
        changes += 1
    }

    fun forget(path: String) {
        if (records.remove(path) != null) changes += 1
    }

    fun update(path: String, change: NodeRecord.() -> Unit) {
        records[path]?.let { changeOf(it, change) }
    }
}

/** Counts drawn frames on the screen's own frame clock, but only while a driver waits, so an idle screen is never redrawn for it. */
@Composable
internal fun ScreenFrameCounter(registry: ScreenRegistry) {
    LaunchedEffect(registry) {
        registry.isCountingFrames = true
        try {
            registry.waiters.collectLatest { count ->
                while (count > 0) withFrameNanos { registry.countFrame() }
            }
        } finally {
            registry.isCountingFrames = false
        }
    }
}

data class NodeIdentity(val id: String, val path: String)

val LocalScreenRegistry = staticCompositionLocalOf<ScreenRegistry?> { null }

internal val LocalPathPrefix = compositionLocalOf { "" }

internal val LocalNodeIdentity = compositionLocalOf<NodeIdentity?> { null }

private fun windowUnits(coordinates: LayoutCoordinates, density: Density): ScreenBounds {
    val rect = coordinates.boundsInWindow()
    return ScreenBounds(
        (rect.left / density.density).roundToInt(),
        (rect.top / density.density).roundToInt(),
        (rect.width / density.density).roundToInt(),
        (rect.height / density.density).roundToInt(),
    )
}

internal fun Modifier.placementOf(id: String): Modifier = composed {
    val registry = LocalScreenRegistry.current
    val identity = LocalNodeIdentity.current
    val density = LocalDensity.current
    if (registry == null || identity == null || identity.id != id) {
        Modifier
    } else {
        Modifier.onGloballyPositioned { coordinates -> registry.update(identity.path) { bounds = windowUnits(coordinates, density) } }
    }
}

internal fun Modifier.menuItemPlacement(value: String): Modifier = composed {
    val registry = LocalScreenRegistry.current
    val identity = LocalNodeIdentity.current
    val density = LocalDensity.current
    if (registry == null || identity == null) {
        Modifier
    } else {
        Modifier.onGloballyPositioned { coordinates -> registry.update(identity.path) { menuBounds[value] = windowUnits(coordinates, density) } }
    }
}

internal fun Modifier.canvasPlacement(): Modifier = composed {
    val registry = LocalScreenRegistry.current
    val identity = LocalNodeIdentity.current
    val density = LocalDensity.current
    if (registry == null || identity == null) {
        Modifier
    } else {
        Modifier.onGloballyPositioned { coordinates ->
            val origin = coordinates.positionInWindow()
            registry.update(identity.path) { canvas = CanvasPlacement(origin.x / density.density, origin.y / density.density, density.density) }
        }
    }
}

/** A menu item is on screen from its composition to its disposal, which outlasts the menu's closing while it animates out. */
@Composable
internal fun RecordMenuItemLifetime(value: String) {
    val registry = LocalScreenRegistry.current ?: return
    val identity = LocalNodeIdentity.current ?: return
    DisposableEffect(registry, identity.path, value) {
        registry.update(identity.path) { menuComposed += value }
        onDispose {
            registry.update(identity.path) {
                menuComposed -= value
                menuBounds -= value
            }
        }
    }
}

@Composable
internal fun RecordDrawn(change: NodeRecord.() -> Unit) {
    val registry = LocalScreenRegistry.current ?: return
    val identity = LocalNodeIdentity.current ?: return
    SideEffect { registry.update(identity.path, change) }
}
