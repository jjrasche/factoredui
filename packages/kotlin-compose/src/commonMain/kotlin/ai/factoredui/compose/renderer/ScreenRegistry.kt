package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.SpecNode
import androidx.compose.runtime.Composable
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
    var partTap: ((String) -> Boolean)? = null
}

class ScreenRegistry {
    private val records = LinkedHashMap<String, NodeRecord>()
    var root: SpecNode? = null

    fun recordOf(path: String): NodeRecord? = records[path]

    fun paths(): List<String> = records.keys.toList()

    fun record(path: String, nodeId: String, type: String, update: NodeRecord.() -> Unit) {
        records.getOrPut(path) { NodeRecord(path, nodeId, type) }.update()
    }

    fun forget(path: String) {
        records.remove(path)
    }

    fun update(path: String, change: NodeRecord.() -> Unit) {
        records[path]?.change()
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

@Composable
internal fun RecordDrawn(change: NodeRecord.() -> Unit) {
    val registry = LocalScreenRegistry.current ?: return
    val identity = LocalNodeIdentity.current ?: return
    SideEffect { registry.update(identity.path, change) }
}
