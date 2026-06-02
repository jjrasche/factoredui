package ai.factoredui.compose.scene3d

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import ai.factoredui.compose.forcegraph.math.Camera
import ai.factoredui.compose.forcegraph.math.ProjectedPoint
import ai.factoredui.compose.forcegraph.math.Vec3
import ai.factoredui.compose.forcegraph.math.project
import kotlin.math.abs
import kotlin.math.tan

private const val GRID_HALF_EXTENT = 5
private const val PERSON_HALF_WIDTH = 0.3f
private const val PERSON_HALF_DEPTH = 0.2f
private const val PERSON_HEIGHT = 1.8f
private const val SELECT_HIT_RADIUS_PX = 48f

/**
 * scene3d software rasterizer. Renders a flat-shaded mesh (or a box placeholder
 * while a mesh loads) per entity on a ground grid under an orbit camera. Tap to
 * select; drag a selected entity to move it on the ground plane; drag elsewhere
 * to orbit; scroll to zoom. Continuous gestures report via the callbacks; the
 * host debounces them into settled intents.
 */
@Composable
fun Scene3dView(
    world: Scene3dWorldState,
    camera: Camera,
    meshes: Map<String, PreparedMesh> = emptyMap(),
    modifier: Modifier = Modifier,
    onSelectEntity: (String) -> Unit = {},
    onCameraChange: () -> Unit = {},
    onMoveEntity: (String, List<Float>) -> Unit = { _, _ -> },
) {
    var cameraGeneration by remember { mutableStateOf(0) }
    val background = parseHexColor(world.background) ?: Color(0xFF2B2B30)
    val latestWorld by rememberUpdatedState(world)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var draggingEntityId: String? = null
                detectDragGestures(
                    onDragStart = { start ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val hit = nearestEntity(latestWorld.entities, camera, width, height, start)
                        draggingEntityId =
                            if (hit != null && latestWorld.entities.any { it.id == hit && it.selected }) hit else null
                    },
                    onDragEnd = { draggingEntityId = null },
                    onDragCancel = { draggingEntityId = null },
                    onDrag = { change, drag ->
                        val moving = draggingEntityId
                        if (moving == null) {
                            camera.drag(drag.x, drag.y)
                            cameraGeneration++
                            onCameraChange()
                        } else {
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            val baseY = latestWorld.entities.firstOrNull { it.id == moving }
                                ?.position?.toVec3()?.y ?: 0f
                            val hit = groundHit(camera, width, height, change.position, baseY)
                            if (hit != null) {
                                onMoveEntity(moving, listOf(hit.x, baseY, hit.z))
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val hit = nearestEntity(latestWorld.entities, camera, size.width.toFloat(), size.height.toFloat(), tap)
                    if (hit != null) onSelectEntity(hit)
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            camera.zoom(-scrollY)
                            cameraGeneration++
                            event.changes.forEach { it.consume() }
                            onCameraChange()
                        }
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            cameraGeneration

            val width = size.width
            val height = size.height
            drawRect(color = background, size = size)

            val view = camera.viewMatrix()
            val proj = camera.projectionMatrix(aspect = if (height > 0f) width / height else 1f)

            fun screen(point: Vec3): ProjectedPoint = point.project(view, proj, width, height)

            drawGroundGrid(::screen)

            val ordered = world.entities.sortedByDescending { entity ->
                screen(personCenter(entity)).depth
            }
            for (entity in ordered) {
                val mesh = meshes[entity.id]
                if (mesh != null) {
                    drawMesh(mesh, entity, ::screen)
                } else {
                    drawPersonBox(entity, ::screen)
                }
                if (entity.selected) {
                    drawSelectionRing(entity, ::screen)
                }
            }
            drawLights(world.lights, ::screen)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLights(
    lights: List<Scene3dLight>,
    screen: (Vec3) -> ProjectedPoint,
) {
    for (light in lights) {
        val projected = screen(light.position.toVec3())
        if (!projected.visible) continue
        val reach = 6f + 4f * light.intensity.coerceIn(0f, 2f)
        val color = if (light.type == "key") Color(0xFFFFE08A) else Color(0xFF8AB4FF)
        val center = Offset(projected.x, projected.y)
        drawLine(color, Offset(center.x - reach, center.y), Offset(center.x + reach, center.y), strokeWidth = 2f)
        drawLine(color, Offset(center.x, center.y - reach), Offset(center.x, center.y + reach), strokeWidth = 2f)
        drawCircle(color, radius = 2.5f, center = center)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMesh(
    mesh: PreparedMesh,
    entity: Scene3dEntity,
    screen: (Vec3) -> ProjectedPoint,
) {
    val base = entity.position.toVec3()
    val scale = entity.scale
    val projected = mesh.vertices.map { vertex ->
        screen(Vec3(base.x + vertex.x * scale, base.y + vertex.y * scale, base.z + vertex.z * scale))
    }
    val triangleCount = mesh.triangles.size / 3
    val order = (0 until triangleCount).sortedByDescending { triangle ->
        val a = mesh.triangles[triangle * 3]
        val b = mesh.triangles[triangle * 3 + 1]
        val c = mesh.triangles[triangle * 3 + 2]
        (projected[a].depth + projected[b].depth + projected[c].depth) / 3f
    }
    for (triangle in order) {
        val a = projected[mesh.triangles[triangle * 3]]
        val b = projected[mesh.triangles[triangle * 3 + 1]]
        val c = projected[mesh.triangles[triangle * 3 + 2]]
        if (!a.visible || !b.visible || !c.visible) continue
        val path = Path().apply {
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(c.x, c.y)
            close()
        }
        drawPath(path, color = mesh.colors.getOrElse(triangle) { Color(0xFF888888) })
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionRing(
    entity: Scene3dEntity,
    screen: (Vec3) -> ProjectedPoint,
) {
    val base = entity.position.toVec3()
    val radius = 0.5f * entity.scale
    val segments = 28
    val ringColor = Color(0xFFFFD24D)
    var previous = base + Vec3(radius, 0f, 0f)
    for (step in 1..segments) {
        val angle = (step.toFloat() / segments) * 2f * kotlin.math.PI.toFloat()
        val next = base + Vec3(radius * kotlin.math.cos(angle), 0f, radius * kotlin.math.sin(angle))
        drawWorldLine(screen, previous, next, ringColor, 2.5f)
        previous = next
    }
}

private fun personCenter(entity: Scene3dEntity): Vec3 {
    val base = entity.position.toVec3()
    return Vec3(base.x, base.y + PERSON_HEIGHT / 2f, base.z)
}

private fun nearestEntity(
    entities: List<Scene3dEntity>,
    camera: Camera,
    width: Float,
    height: Float,
    tap: Offset,
): String? {
    if (width <= 0f || height <= 0f) return null
    val view = camera.viewMatrix()
    val proj = camera.projectionMatrix(aspect = width / height)
    // Among entities whose centre is under the tap, pick the frontmost (smallest
    // depth) so overlapping characters disambiguate to the one nearest the camera,
    // ties broken by screen distance.
    return entities
        .mapNotNull { entity ->
            val projected = personCenter(entity).project(view, proj, width, height)
            if (!projected.visible) return@mapNotNull null
            val dx = projected.x - tap.x
            val dy = projected.y - tap.y
            Triple(entity.id, dx * dx + dy * dy, projected.depth)
        }
        .filter { (_, distanceSquared, _) -> distanceSquared <= SELECT_HIT_RADIUS_PX * SELECT_HIT_RADIUS_PX }
        .minWithOrNull(compareBy({ it.third }, { it.second }))
        ?.first
}

// Unproject a screen point to where its camera ray meets the y=planeY ground
// plane. Pinhole-ray form matching Matrix4.lookAt/perspective so the dragged
// entity tracks the cursor exactly. Null when the ray is parallel to / behind
// the plane.
private fun groundHit(
    camera: Camera,
    width: Float,
    height: Float,
    screen: Offset,
    planeY: Float,
): Vec3? {
    if (width <= 0f || height <= 0f) return null
    val eye = camera.eyePosition()
    val zAxis = (eye - camera.target).normalize()
    val xAxis = Vec3(0f, 1f, 0f).cross(zAxis).normalize()
    val yAxis = zAxis.cross(xAxis)
    val forward = -zAxis
    val tanHalf = tan(camera.fovYRadians / 2f)
    val ndcX = 2f * screen.x / width - 1f
    val ndcY = 1f - 2f * screen.y / height
    val direction = (forward + xAxis * (ndcX * tanHalf * (width / height)) + yAxis * (ndcY * tanHalf)).normalize()
    if (abs(direction.y) < 1e-5f) return null
    val t = (planeY - eye.y) / direction.y
    if (t <= 0f) return null
    return eye + direction * t
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGroundGrid(
    screen: (Vec3) -> ProjectedPoint,
) {
    val gridColor = Color(0x33FFFFFF)
    val span = GRID_HALF_EXTENT
    for (i in -span..span) {
        drawWorldLine(screen, Vec3(i.toFloat(), 0f, -span.toFloat()), Vec3(i.toFloat(), 0f, span.toFloat()), gridColor)
        drawWorldLine(screen, Vec3(-span.toFloat(), 0f, i.toFloat()), Vec3(span.toFloat(), 0f, i.toFloat()), gridColor)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPersonBox(
    entity: Scene3dEntity,
    screen: (Vec3) -> ProjectedPoint,
) {
    val base = entity.position.toVec3()
    val hw = PERSON_HALF_WIDTH * entity.scale
    val hd = PERSON_HALF_DEPTH * entity.scale
    val top = base.y + PERSON_HEIGHT * entity.scale
    val corners = listOf(
        Vec3(base.x - hw, base.y, base.z - hd),
        Vec3(base.x + hw, base.y, base.z - hd),
        Vec3(base.x + hw, base.y, base.z + hd),
        Vec3(base.x - hw, base.y, base.z + hd),
        Vec3(base.x - hw, top, base.z - hd),
        Vec3(base.x + hw, top, base.z - hd),
        Vec3(base.x + hw, top, base.z + hd),
        Vec3(base.x - hw, top, base.z + hd),
    )
    val edges = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 0,
        4 to 5, 5 to 6, 6 to 7, 7 to 4,
        0 to 4, 1 to 5, 2 to 6, 3 to 7,
    )
    val tint = entityTint(entity.id)
    val color = if (entity.selected) Color(0xFFFFD24D) else tint
    val stroke = if (entity.selected) 3f else 1.6f
    for ((from, to) in edges) {
        drawWorldLine(screen, corners[from], corners[to], color, stroke)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWorldLine(
    screen: (Vec3) -> ProjectedPoint,
    from: Vec3,
    to: Vec3,
    color: Color,
    strokeWidth: Float = 1f,
) {
    val a = screen(from)
    val b = screen(to)
    if (!a.visible || !b.visible) return
    drawLine(color = color, start = Offset(a.x, a.y), end = Offset(b.x, b.y), strokeWidth = strokeWidth)
}

private fun entityTint(id: String): Color {
    val hue = (abs(id.hashCode()) % 360).toFloat()
    return hsvToColor(hue, 0.45f, 0.85f)
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val c = value * saturation
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = value - c
    val (r, g, b) = when ((hue / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

private fun parseHexColor(name: String?): Color? {
    if (name == null) return null
    val hex = name.removePrefix("#")
    if (hex.length != 6) return null
    return runCatching {
        Color(
            red = hex.substring(0, 2).toInt(16) / 255f,
            green = hex.substring(2, 4).toInt(16) / 255f,
            blue = hex.substring(4, 6).toInt(16) / 255f,
        )
    }.getOrNull()
}
