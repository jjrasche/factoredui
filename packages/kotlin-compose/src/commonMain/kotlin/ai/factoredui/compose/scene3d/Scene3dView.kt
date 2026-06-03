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
import ai.factoredui.compose.forcegraph.math.Matrix4
import ai.factoredui.compose.forcegraph.math.ProjectedPoint
import ai.factoredui.compose.forcegraph.math.Vec3
import ai.factoredui.compose.forcegraph.math.project
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

private const val GRID_HALF_EXTENT = 5
private const val PERSON_HALF_WIDTH = 0.3f
private const val PERSON_HALF_DEPTH = 0.2f
private const val PERSON_HEIGHT = 1.8f
private const val SELECT_HIT_RADIUS_PX = 48f
private const val JOINT_HIT_RADIUS_PX = 20f
private const val JOINT_GRAB_RADIUS_PX = 30f

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
    poses: Map<String, Array<Matrix4>> = emptyMap(),
    selectedJoint: Pair<String, Int>? = null,
    modifier: Modifier = Modifier,
    onSelectEntity: (String) -> Unit = {},
    onSelectJoint: (String, Int) -> Unit = { _, _ -> },
    onCameraChange: () -> Unit = {},
    onMoveEntity: (String, List<Float>) -> Unit = { _, _ -> },
    onPoseChange: (String, Array<Matrix4>) -> Unit = { _, _ -> },
    onJointReleased: (String) -> Unit = {},
) {
    var cameraGeneration by remember { mutableStateOf(0) }
    val background = parseHexColor(world.background) ?: Color(0xFF2B2B30)
    val latestWorld by rememberUpdatedState(world)
    val latestMeshes by rememberUpdatedState(meshes)
    val latestPoses by rememberUpdatedState(poses)
    val latestSelectedJoint by rememberUpdatedState(selectedJoint)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var mode = DragKind.ORBIT
                var dragEntity: String? = null
                var dragJoint = -1
                detectDragGestures(
                    onDragStart = { start ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val grab = jointUnderCursor(latestSelectedJoint, latestWorld, latestMeshes, latestPoses, camera, width, height, start)
                        when {
                            grab != null -> {
                                mode = DragKind.JOINT; dragEntity = grab.first; dragJoint = grab.second
                            }
                            else -> {
                                val hit = nearestEntity(latestWorld.entities, camera, width, height, start)
                                if (hit != null && latestWorld.entities.any { it.id == hit && it.selected }) {
                                    mode = DragKind.MOVE; dragEntity = hit
                                } else {
                                    mode = DragKind.ORBIT; dragEntity = null
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        if (mode == DragKind.JOINT) dragEntity?.let { onJointReleased(it) }
                        mode = DragKind.ORBIT; dragEntity = null; dragJoint = -1
                    },
                    onDragCancel = { mode = DragKind.ORBIT; dragEntity = null; dragJoint = -1 },
                    onDrag = { change, drag ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        when (mode) {
                            DragKind.JOINT -> {
                                val id = dragEntity ?: return@detectDragGestures
                                val newPose = solveJointAim(latestWorld, latestMeshes, latestPoses, id, dragJoint, camera, width, height, change.position)
                                if (newPose != null) onPoseChange(id, newPose)
                            }
                            DragKind.MOVE -> {
                                val id = dragEntity ?: return@detectDragGestures
                                val baseY = latestWorld.entities.firstOrNull { it.id == id }?.position?.toVec3()?.y ?: 0f
                                val hit = groundHit(camera, width, height, change.position, baseY)
                                if (hit != null) onMoveEntity(id, listOf(hit.x, baseY, hit.z))
                            }
                            DragKind.ORBIT -> {
                                camera.drag(drag.x, drag.y)
                                cameraGeneration++
                                onCameraChange()
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val jointHit = jointTap(latestWorld, latestMeshes, latestPoses, camera, width, height, tap)
                    if (jointHit != null) {
                        onSelectJoint(jointHit.first, jointHit.second)
                    } else {
                        val hit = nearestEntity(latestWorld.entities, camera, width, height, tap)
                        if (hit != null) onSelectEntity(hit)
                    }
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
                    val rig = mesh.rig
                    val pose = poses[entity.id]
                    val displayVerts = if (rig != null && pose != null) rig.posedVertices(pose) else mesh.vertices
                    drawMesh(mesh, displayVerts, entity, ::screen)
                    if (entity.selected && rig != null) {
                        val world3d = rig.worldJointTransforms(pose ?: rig.identityPose())
                        val selected = selectedJoint?.takeIf { it.first == entity.id }?.second ?: -1
                        drawJointDots(rig, world3d, entity, selected, ::screen)
                    }
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

private enum class DragKind { ORBIT, MOVE, JOINT }

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
    displayVertices: List<Vec3>,
    entity: Scene3dEntity,
    screen: (Vec3) -> ProjectedPoint,
) {
    val base = entity.position.toVec3()
    val scale = entity.scale
    val projected = displayVertices.map { vertex ->
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

// Pinhole ray (origin, direction) through a screen pixel, matching
// Matrix4.lookAt/perspective so unprojection tracks the cursor exactly.
private fun cameraRay(camera: Camera, width: Float, height: Float, screen: Offset): Pair<Vec3, Vec3> {
    val eye = camera.eyePosition()
    val zAxis = (eye - camera.target).normalize()
    val xAxis = Vec3(0f, 1f, 0f).cross(zAxis).normalize()
    val yAxis = zAxis.cross(xAxis)
    val forward = -zAxis
    val tanHalf = tan(camera.fovYRadians / 2f)
    val ndcX = 2f * screen.x / width - 1f
    val ndcY = 1f - 2f * screen.y / height
    val direction = (forward + xAxis * (ndcX * tanHalf * (width / height)) + yAxis * (ndcY * tanHalf)).normalize()
    return eye to direction
}

private fun groundHit(camera: Camera, width: Float, height: Float, screen: Offset, planeY: Float): Vec3? {
    if (width <= 0f || height <= 0f) return null
    val (eye, direction) = cameraRay(camera, width, height, screen)
    if (abs(direction.y) < 1e-5f) return null
    val t = (planeY - eye.y) / direction.y
    if (t <= 0f) return null
    return eye + direction * t
}

// Where the cursor ray meets the sphere of radius around center (the fixed bone
// length around the pivot). Off-sphere cursors clamp to the nearest surface
// point so the joint still tracks — the rigid bone resolves the depth.
private fun sphereHit(camera: Camera, width: Float, height: Float, screen: Offset, center: Vec3, radius: Float): Vec3 {
    val (eye, direction) = cameraRay(camera, width, height, screen)
    val toCenter = eye - center
    val b = toCenter.dot(direction)
    val c = toCenter.dot(toCenter) - radius * radius
    val discriminant = b * b - c
    if (discriminant < 0f) {
        val closest = eye + direction * (-b)
        return center + (closest - center).normalize() * radius
    }
    val root = sqrt(discriminant)
    val t = if (-b - root >= 0f) -b - root else -b + root
    return eye + direction * t
}

private fun jointScenePositions(world: Array<Matrix4>, entity: Scene3dEntity): List<Vec3> {
    val base = entity.position.toVec3()
    val scale = entity.scale
    return jointOrigins(world).map { Vec3(base.x + it.x * scale, base.y + it.y * scale, base.z + it.z * scale) }
}

private fun nearestJoint(
    positions: List<Vec3>,
    camera: Camera,
    width: Float,
    height: Float,
    cursor: Offset,
    radiusPx: Float,
): Int? {
    val view = camera.viewMatrix()
    val proj = camera.projectionMatrix(width / height)
    var best = -1
    var bestDistance = radiusPx * radiusPx
    for (joint in positions.indices) {
        val projected = positions[joint].project(view, proj, width, height)
        if (!projected.visible) continue
        val dx = projected.x - cursor.x
        val dy = projected.y - cursor.y
        val distance = dx * dx + dy * dy
        if (distance <= bestDistance) {
            bestDistance = distance
            best = joint
        }
    }
    return if (best >= 0) best else null
}

private fun jointTap(
    world: Scene3dWorldState,
    meshes: Map<String, PreparedMesh>,
    poses: Map<String, Array<Matrix4>>,
    camera: Camera,
    width: Float,
    height: Float,
    cursor: Offset,
): Pair<String, Int>? {
    for (entity in world.entities) {
        if (!entity.selected) continue
        val rig = meshes[entity.id]?.rig ?: continue
        val transforms = rig.worldJointTransforms(poses[entity.id] ?: rig.identityPose())
        val joint = nearestJoint(jointScenePositions(transforms, entity), camera, width, height, cursor, JOINT_HIT_RADIUS_PX)
        if (joint != null) return entity.id to joint
    }
    return null
}

private fun jointUnderCursor(
    selectedJoint: Pair<String, Int>?,
    world: Scene3dWorldState,
    meshes: Map<String, PreparedMesh>,
    poses: Map<String, Array<Matrix4>>,
    camera: Camera,
    width: Float,
    height: Float,
    cursor: Offset,
): Pair<String, Int>? {
    val selected = selectedJoint ?: return null
    val entity = world.entities.firstOrNull { it.id == selected.first } ?: return null
    val rig = meshes[selected.first]?.rig ?: return null
    val transforms = rig.worldJointTransforms(poses[selected.first] ?: rig.identityPose())
    val positions = jointScenePositions(transforms, entity)
    if (selected.second !in positions.indices) return null
    return if (nearestJoint(listOf(positions[selected.second]), camera, width, height, cursor, JOINT_GRAB_RADIUS_PX) != null) {
        selected
    } else {
        null
    }
}

private fun solveJointAim(
    world: Scene3dWorldState,
    meshes: Map<String, PreparedMesh>,
    poses: Map<String, Array<Matrix4>>,
    entityId: String,
    jointIndex: Int,
    camera: Camera,
    width: Float,
    height: Float,
    cursor: Offset,
): Array<Matrix4>? {
    val entity = world.entities.firstOrNull { it.id == entityId } ?: return null
    val rig = meshes[entityId]?.rig ?: return null
    val pose = poses[entityId] ?: rig.identityPose()
    val parent = rig.parents.getOrElse(jointIndex) { -1 }
    if (parent < 0) return null
    val transforms = rig.worldJointTransforms(pose)
    val positions = jointScenePositions(transforms, entity)
    val pivot = positions[parent]
    val handle = positions[jointIndex]
    val boneLength = (handle - pivot).length()
    if (boneLength < 1e-5f) return null
    val target = sphereHit(camera, width, height, cursor, pivot, boneLength)
    val delta = rotationFromTo((handle - pivot).normalize(), (target - pivot).normalize())
    val grandparent = rig.parents.getOrElse(parent) { -1 }
    val grandRotation = if (grandparent < 0) Matrix4.identity() else transforms[grandparent].rotationPart()
    val newLocal = grandRotation.transposeRotation() * delta * transforms[parent].rotationPart()
    val newPose = pose.copyOf()
    newPose[parent] = newLocal
    return newPose
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawJointDots(
    rig: Scene3dRig,
    world: Array<Matrix4>,
    entity: Scene3dEntity,
    selectedJoint: Int,
    screen: (Vec3) -> ProjectedPoint,
) {
    val positions = jointScenePositions(world, entity)
    positions.forEachIndexed { joint, position ->
        val projected = screen(position)
        if (!projected.visible) return@forEachIndexed
        val center = Offset(projected.x, projected.y)
        if (joint == selectedJoint) {
            drawCircle(Color(0x55FFC83D), radius = 11f, center = center)
            drawCircle(Color(0xFFFFC83D), radius = 6f, center = center)
        } else {
            drawCircle(Color(0xCCEFEFF5), radius = 3.5f, center = center)
        }
    }
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
