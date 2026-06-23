package ai.factoredui.compose.scene3d

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ai.factoredui.compose.forcegraph.math.Camera
import ai.factoredui.compose.forcegraph.math.Matrix4
import ai.factoredui.compose.forcegraph.startSseSubscription
import ai.factoredui.compose.observability.NoOpObservability
import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.Scene3dProps
import ai.factoredui.compose.testing.DomShadow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val CAMERA_SETTLE_MS = 150L
private const val SMPLH_POSE_LEN = 156
private const val SMPLH_BODY_JOINTS = 22
private const val GRAVITY = -9.8f
private const val GROUND_FRICTION = 0.7f
private const val MOVE_FRAME_DT = 1f / 60f

private val SMPL24_PARENTS = intArrayOf(
    -1, 0, 1, 2, 3, 0, 5, 6, 7, 0, 9, 10, 11, 12, 11, 14, 15, 16, 11, 18, 19, 20, 17, 21,
)

private fun smpl24GraphHops(from: Int, n: Int): IntArray {
    val adjacency = Array(n) { mutableListOf<Int>() }
    SMPL24_PARENTS.forEachIndexed { child, parent ->
        if (parent in 0 until n && child < n) {
            adjacency[child].add(parent)
            adjacency[parent].add(child)
        }
    }
    val hops = IntArray(n) { Int.MAX_VALUE }
    if (from !in 0 until n) return hops
    hops[from] = 0
    val queue = ArrayDeque<Int>()
    queue.add(from)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (neighbor in adjacency[current]) {
            if (hops[neighbor] == Int.MAX_VALUE) {
                hops[neighbor] = hops[current] + 1
                queue.add(neighbor)
            }
        }
    }
    return hops
}

// The spared leg reads as the limp: over the clip, the foot that plants far less than the other is
// the one being protected. Heat its leg chain (Hip/Knee/Ankle/Toe) so the injured body's hurt side
// glows and the healthy body — which uses both legs — stays cold. Ankles are index 3 (L) / 7 (R),
// height is the stream's z (index 2). Returns null when both legs share the load (no limp to mark).
private fun sparedLegHeat(source: MotionClip): List<Float>? {
    val frames = source.frames
    if (frames.size < 2) return null
    val rootX = frames.map { it.joints.getOrNull(0)?.getOrNull(0) ?: 0f }
    if ((rootX.max() - rootX.min()) < 0.5f) return null
    var leftPlantedFrames = 0
    for (frame in frames) {
        val leftHeight = frame.joints.getOrNull(3)?.getOrNull(2) ?: 0f
        val rightHeight = frame.joints.getOrNull(7)?.getOrNull(2) ?: 0f
        if (leftHeight < rightHeight) leftPlantedFrames++
    }
    val total = frames.size
    val rightPlantedFrames = total - leftPlantedFrames
    val sparedRightLeg = leftPlantedFrames >= 0.6f * total && rightPlantedFrames <= 0.3f * total
    val sparedLeftLeg = rightPlantedFrames >= 0.6f * total && leftPlantedFrames <= 0.3f * total
    val sparedJoints = when {
        sparedRightLeg -> listOf(5, 6, 7, 8)
        sparedLeftLeg -> listOf(1, 2, 3, 4)
        else -> return null
    }
    return (0 until 24).map { if (it in sparedJoints) 0.9f else 0f }
}

internal fun jointFrameDigest(joints: List<List<Float>>): String =
    joints.joinToString(";") { joint ->
        joint.joinToString(",") { axis -> (axis * 1000f).roundToInt().toString() }
    }

// Bake the root channel into each joint per frame so the body translates with its root (no foot-skate),
// then the existing clip pipeline (autoplay/world-build/timeline/DomShadow) draws it unchanged. Joints
// stay in the stream's Z-up space; bodyFrom applies the Z-up→Y-up swap downstream.
internal fun bodyFramesToMotionClip(response: BodyFramesResponse): MotionClip =
    MotionClip(
        name = "body-frames",
        frames = response.frames.map { frame ->
            MotionClipFrame(
                joints = frame.joints.map { joint ->
                    listOf(
                        joint.getOrElse(0) { 0f } + frame.rootX,
                        joint.getOrElse(1) { 0f } + frame.rootY,
                        joint.getOrElse(2) { 0f } + frame.rootZ,
                    )
                },
            )
        },
    )

// PREVIEW field (swaps for il-injury's geodesic pain_at when exposed): the dragged
// impact ball picks the nearest body joint; pain falls off along the bone graph from
// it, scaled by impulse. Geodesic-ish — leaves the far side dark, unlike euclidean.
private fun applyImpactPain(state: Scene3dWorldState, impulse: Float): Scene3dWorldState {
    val ball = state.entities.firstOrNull { it.kind == "ball" } ?: return state
    val body = state.entities.firstOrNull { it.id == "injured" }
        ?: state.entities.firstOrNull { (it.jointFrame?.size ?: 0) >= 24 && it.kind != "ghost" }
        ?: return state
    val joints = body.jointFrame ?: return state
    val bx = ball.position.getOrElse(0) { 0f }
    val by = ball.position.getOrElse(1) { 0f }
    val bz = ball.position.getOrElse(2) { 0f }
    var nearest = 0
    var best = Float.MAX_VALUE
    joints.forEachIndexed { index, joint ->
        val dx = joint.getOrElse(0) { 0f } - bx
        val dy = joint.getOrElse(1) { 0f } - by
        val dz = joint.getOrElse(2) { 0f } - bz
        val distance = dx * dx + dy * dy + dz * dz
        if (distance < best) { best = distance; nearest = index }
    }
    val hops = smpl24GraphHops(nearest, joints.size)
    val strength = impulse.coerceIn(0f, 1f)
    val pain = joints.indices.map { index ->
        val hop = hops[index]
        if (hop == Int.MAX_VALUE) 0f else (strength * kotlin.math.exp(-0.55f * hop)).coerceIn(0f, 1f)
    }
    return state.copy(entities = state.entities.map { if (it.id == body.id) it.copy(pain = pain) else it })
}

@Composable
fun RenderScene3d(
    props: Scene3dProps,
    nodeId: String = "scene3d",
    observability: Observability = NoOpObservability,
    playheadBinding: Int? = null,
    playingBinding: Boolean? = null,
    onPlayheadChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val httpClient = remember { HttpClient() }
    val scope = rememberCoroutineScope()
    DisposableEffect(httpClient) { onDispose { httpClient.close() } }

    var world by remember { mutableStateOf(Scene3dWorldState()) }
    var meshes by remember { mutableStateOf<Map<String, PreparedMesh>>(emptyMap()) }
    val loadedMeshUrls = remember { mutableMapOf<String, String>() }
    var loadError by remember { mutableStateOf<String?>(null) }
    var cameraInitialized by remember { mutableStateOf(false) }
    val camera = remember { Camera() }
    var shotCamera by remember { mutableStateOf<Scene3dCameraState?>(null) }
    var cameraVersion by remember { mutableStateOf(0) }
    var previewMode by remember { mutableStateOf(false) }
    var previewStatus by remember { mutableStateOf("") }
    var engineStatus by remember { mutableStateOf("") }
    var solveTarget by remember { mutableStateOf<List<Float>?>(null) }
    var naturalness by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }
    var playFrame by remember { mutableStateOf(0) }
    // scene3d bakes NO timeline chrome — playback is declarative (`autoplay` prop) or host-driven
    // (`playing` binding wins). `playing` gates who owns the playhead: playing → scene3d advances it and
    // writes it back so a host slider follows; paused → the host's scrub drives it through playheadBinding.
    val effectivePlaying = playingBinding ?: props.clipAutoplay
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var localPositions by remember { mutableStateOf<Map<String, List<Float>>>(emptyMap()) }
    var moveSettleJob by remember { mutableStateOf<Job?>(null) }
    var clientPoses by remember { mutableStateOf<Map<String, Array<Matrix4>>>(emptyMap()) }
    var selectedJoint by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var poseMode by remember { mutableStateOf(PoseMode.MOVE) }
    var movePrevPos by remember { mutableStateOf<List<Float>?>(null) }
    var releaseVelocity by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var promptText by remember { mutableStateOf("") }
    var promptStatus by remember { mutableStateOf("") }
    var promptTotalMs by remember { mutableStateOf(0f) }
    var promptOk by remember { mutableStateOf(true) }
    var appliedPoseRefs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var motionClip by remember { mutableStateOf<MotionClip?>(null) }
    var motionClipB by remember { mutableStateOf<MotionClip?>(null) }

    LaunchedEffect(props.clipUrl) {
        val url = props.clipUrl
        if (url == null) {
            motionClip = null
            return@LaunchedEffect
        }
        runCatching {
            json.decodeFromString(MotionClip.serializer(), httpClient.get(url).bodyAsText())
        }.fold(
            onSuccess = { motionClip = it },
            onFailure = { loadError = "scene3d clip: ${it.message ?: it::class.simpleName}" },
        )
    }

    LaunchedEffect(props.clipUrlB) {
        val url = props.clipUrlB
        if (url == null) {
            motionClipB = null
            return@LaunchedEffect
        }
        runCatching {
            json.decodeFromString(MotionClip.serializer(), httpClient.get(url).bodyAsText())
        }.fold(
            onSuccess = { motionClipB = it },
            onFailure = { loadError = "scene3d clipB: ${it.message ?: it::class.simpleName}" },
        )
    }

    LaunchedEffect(props.engineUrl, props.engine, props.clipSeverity, props.clipEffector, props.clipImpulse, props.board, solveTarget) {
        val url = props.engineUrl ?: return@LaunchedEffect
        if (props.simId != null) return@LaunchedEffect
        delay(300)
        engineStatus = "${props.engine} · solving…"
        val requestBody = buildJsonObject {
            put("engine", props.engine)
            put("severity", props.clipSeverity)
            put("effector", props.clipEffector)
            put("impulse", props.clipImpulse)
            props.board?.let { put("board", it) }
            solveTarget?.let { put("target", anyToJson(it)) }
        }.toString()
        runCatching {
            val text = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.bodyAsText()
            val parsed = json.parseToJsonElement(text).jsonObject
            val mode = parsed["mode"]?.jsonPrimitive?.contentOrNull
            val natObj = parsed["naturalness"]?.jsonObject
            val nat = natObj?.takeIf { it["error"] == null }?.let {
                Triple(
                    it["score"]?.jsonPrimitive?.floatOrNull ?: 0f,
                    it["smoothness"]?.jsonPrimitive?.floatOrNull ?: 0f,
                    it["continuity"]?.jsonPrimitive?.floatOrNull ?: 0f,
                )
            }
            Triple(json.decodeFromString(MotionClip.serializer(), text), mode, nat)
        }.fold(
            onSuccess = { (clip, mode, nat) ->
                motionClip = clip
                naturalness = nat
                engineStatus = "${props.engine} · ${clip.frames.size}f${mode?.let { " · $it" } ?: ""}"
            },
            onFailure = { engineStatus = "${props.engine} · offline (${it.message ?: "no endpoint"})" },
        )
    }

    // Live body-sim path: POST {simId} to /embodiment/body-frames, draw the returned ring via the clip
    // pipeline. Read-once + scrub-locally per the pinned seam; the believable-future fields ride null.
    LaunchedEffect(props.simId, props.engineUrl) {
        val simId = props.simId ?: return@LaunchedEffect
        val url = props.engineUrl ?: return@LaunchedEffect
        runCatching {
            val text = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("simId", simId) }.toString())
            }.bodyAsText()
            json.decodeFromString(BodyFramesResponse.serializer(), text)
        }.fold(
            onSuccess = { response ->
                motionClip = bodyFramesToMotionClip(response)
                playFrame = response.playhead.coerceAtLeast(0)
                engineStatus = "body-frames · ${response.frames.size}f"
            },
            onFailure = { engineStatus = "body-frames · offline (${it.message ?: "no endpoint"})" },
        )
    }

    val heatSingle = remember(motionClip, props.board) {
        motionClip?.takeIf { props.board == "hopscotch" }?.let { sparedLegHeat(it) }
    }
    val heatB = remember(motionClipB) { motionClipB?.let { sparedLegHeat(it) } }

    LaunchedEffect(motionClip, effectivePlaying) {
        val clip = motionClip ?: return@LaunchedEffect
        if (clip.frames.size < 2 || !effectivePlaying) return@LaunchedEffect
        val periodMs = (1000f / clip.fps.coerceAtLeast(1f)).toLong().coerceAtLeast(16L)
        while (true) {
            delay(periodMs)
            playFrame = (playFrame + 1) % clip.frames.size
            onPlayheadChange(playFrame)
        }
    }

    // Host-driven scrub: while paused, the inbound playhead binding (the host's slider) drives the
    // frame. During playback scene3d owns the playhead, so inbound is ignored.
    LaunchedEffect(playheadBinding, effectivePlaying, motionClip) {
        if (!effectivePlaying) {
            val clip = motionClip ?: return@LaunchedEffect
            playheadBinding?.let { playFrame = it.coerceIn(0, (clip.frames.size - 1).coerceAtLeast(0)) }
        }
    }

    LaunchedEffect(playFrame, motionClip, motionClipB) {
        val clip = motionClip ?: return@LaunchedEffect
        if (clip.frames.isEmpty() || clip.frames.first().joints.size < 24) return@LaunchedEffect
        val clipB = motionClipB?.takeIf { it.frames.isNotEmpty() && it.frames.first().joints.size >= 24 }
        val index = playFrame.coerceIn(0, clip.frames.size - 1)
        val goalDefault = clip.goal.takeIf { it.size >= 3 }?.let { listOf(it[0], it[2], -it[1]) } ?: listOf(0.4f, 1.0f, 0.6f)
        fun bodyFrom(source: MotionClip, idx: Int, id: String, lateral: Float, heat: List<Float>?, centerX: Float = 0f): Scene3dEntity {
            val f = source.frames[idx.coerceIn(0, source.frames.size - 1)]
            return Scene3dEntity(
                id = id,
                jointFrame = f.joints.map { j -> listOf(j.getOrElse(0) { 0f } - centerX, j.getOrElse(2) { 0f }, -j.getOrElse(1) { 0f } + lateral) },
                pain = heat ?: f.pain.takeIf { it.isNotEmpty() },
            )
        }
        if (props.simId != null) {
            world = Scene3dWorldState(entities = listOf(bodyFrom(clip, index, "body", 0f, null)))
            if (!cameraInitialized) {
                applyCameraState(camera, Scene3dCameraState(position = listOf(3.0f, 1.9f, 3.0f), target = listOf(0f, 0.95f, 0f)))
                cameraInitialized = true
            }
        } else if (clipB != null) {
            world = Scene3dWorldState(entities = listOf(bodyFrom(clip, index, "healthy", -0.9f, null), bodyFrom(clipB, index, "injured", 0.9f, heatB)))
            if (!cameraInitialized) {
                applyCameraState(camera, Scene3dCameraState(position = listOf(2.8f, 1.5f, 3.3f), target = listOf(0f, 0.85f, 0f)))
                cameraInitialized = true
            }
        } else if (props.board == "hopscotch") {
            val pelvisX = clip.frames[index].joints.getOrNull(0)?.getOrNull(0) ?: 0f
            val body = bodyFrom(clip, index, "injured", 0f, heatSingle, pelvisX)
            val ball = Scene3dEntity(id = "impact", kind = "ball", selected = true, position = listOf(0.3f, 1.0f, 0.4f))
            world = Scene3dWorldState(entities = listOf(body, ball))
            if (!cameraInitialized) {
                applyCameraState(camera, Scene3dCameraState(position = listOf(0.95f, 0.75f, 1.25f), target = listOf(0f, 0.7f, 0f)))
                cameraInitialized = true
            }
        } else {
            val body = bodyFrom(clip, index, "injured", 0f, null)
            val goal = Scene3dEntity(id = "goal", kind = "goal", selected = true, position = goalDefault)
            val ball = Scene3dEntity(id = "impact", kind = "ball", selected = true, position = listOf(0.25f, 1.0f, 0.5f))
            world = Scene3dWorldState(entities = listOfNotNull(body, goal, ball))
            if (!cameraInitialized) {
                applyCameraState(camera, Scene3dCameraState(position = listOf(2.0f, 1.3f, 2.0f), target = listOf(0f, 0.7f, 0f)))
                cameraInitialized = true
            }
        }
    }

    motionClip?.let { clip ->
        val drawnFrame = playFrame.coerceIn(0, (clip.frames.size - 1).coerceAtLeast(0))
        val drawnJoints = clip.frames.getOrNull(drawnFrame)?.joints ?: emptyList()
        DisposableEffect(clip, drawnFrame) {
            DomShadow.emit(
                id = "$nodeId:frame",
                role = "scene3d-frame",
                attrs = mapOf(
                    "playhead" to drawnFrame.toString(),
                    "frame-count" to clip.frames.size.toString(),
                    "joints-digest" to jointFrameDigest(drawnJoints),
                    "source" to clip.name.ifEmpty { null },
                ),
            )
            onDispose { DomShadow.remove("$nodeId:frame") }
        }
    }

    LaunchedEffect(props.board) {
        if (props.board != "hopscotch" || props.engineUrl != null) return@LaunchedEffect
        val pattern = listOf("single", "single", "double", "single", "double", "single", "double", "single")
        val tiles = mutableListOf<Scene3dEntity>()
        var z = -3f
        var n = 1
        for (foot in pattern) {
            if (foot == "single") {
                tiles.add(Scene3dEntity(id = "tile_$n", kind = "tile", position = listOf(0f, 0f, z)))
            } else {
                tiles.add(Scene3dEntity(id = "tile_${n}a", kind = "tile", position = listOf(-0.5f, 0f, z)))
                tiles.add(Scene3dEntity(id = "tile_${n}b", kind = "tile", position = listOf(0.5f, 0f, z)))
            }
            z += 1.1f
            n++
        }
        world = Scene3dWorldState(entities = tiles)
        if (!cameraInitialized) {
            applyCameraState(camera, Scene3dCameraState(position = listOf(4.2f, 3.2f, 4.5f), target = listOf(0f, 0f, 1f)))
            cameraInitialized = true
        }
    }

    LaunchedEffect(props.worldStateUrl) {
        if (props.worldStateUrl.isEmpty()) {
            if (props.clipUrl == null && props.board == null && props.simId == null) loadError = "scene3d: missing world_state_url"
            return@LaunchedEffect
        }
        runCatching {
            val response: HttpResponse = httpClient.get(props.worldStateUrl)
            json.decodeFromString(Scene3dWorldState.serializer(), response.bodyAsText())
        }.fold(
            onSuccess = { state ->
                world = state
                if (!cameraInitialized) {
                    state.camera?.let { applyCameraState(camera, it) }
                    shotCamera = state.camera ?: cameraSnapshot(camera)
                    cameraInitialized = true
                }
            },
            onFailure = { loadError = "scene3d: fetch failed — ${it.message ?: it::class.simpleName}" },
        )
    }

    DisposableEffect(props.worldStreamUrl) {
        val url = props.worldStreamUrl
        if (url.isNullOrEmpty()) return@DisposableEffect onDispose { }
        val sub = startSseSubscription(
            url = url,
            onMessage = { frame ->
                runCatching { json.decodeFromString(Scene3dWorldState.serializer(), frame) }
                    .getOrNull()?.let { world = it }
            },
            onError = { },
        )
        onDispose { sub.close() }
    }

    LaunchedEffect(world) {
        for (entity in world.entities) {
            val meshUrl = entity.meshUrl ?: continue
            if (loadedMeshUrls[entity.id] == meshUrl) continue
            val prepared = runCatching {
                val body = httpClient.get(resolveAssetUrl(props.worldStateUrl, meshUrl)).bodyAsText()
                json.decodeFromString(Scene3dMesh.serializer(), body).prepare()
            }.getOrNull()
            if (prepared != null) {
                loadedMeshUrls[entity.id] = meshUrl
                meshes = meshes + (entity.id to prepared)
                val rig = prepared.rig
                if (rig != null && clientPoses[entity.id] == null) {
                    clientPoses = clientPoses + (entity.id to rig.identityPose())
                }
            }
        }
    }

    // One path for every settled intent: capture it as a factored-ui interaction
    // event AND POST it to the server. Continuous gestures (camera) coalesce via
    // emitCameraSettled; discrete intents (select, move) fire immediately.
    fun emitIntent(action: String, params: Map<String, Any?>) {
        observability.onInteraction(nodeId, ActionRef(action = action), params)
        val url = props.actionUrl ?: return
        val body = buildJsonObject {
            put("action", action)
            put("params", anyToJson(params))
        }.toString()
        scope.launch {
            runCatching {
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        }
    }

    // Explicit "snap to plausible" (NOT on every drag-release — see Fork B). The drag loop conveys
    // exactly what the user dragged; T2/Wan is the plausibility finisher. This is the opt-in button
    // for when the user WANTS VPoser to clean a pose up: it hands the current joint positions to the
    // server prior and re-skins the nearest plausible full-body pose.
    fun snapToPlausible(entityId: String) {
        val url = props.actionUrl ?: return
        val rig = meshes[entityId]?.rig ?: return
        val pose = clientPoses[entityId] ?: rig.identityPose()
        val targetJoints = jointOrigins(rig.worldJointTransforms(pose)).map { listOf(it.x, it.y, it.z) }
        val body = buildJsonObject {
            put("action", "refine-pose")
            put("params", anyToJson(mapOf("entity_id" to entityId, "target_joints" to targetJoints)))
        }.toString()
        scope.launch {
            val text = runCatching {
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.bodyAsText()
            }.getOrNull() ?: return@launch
            val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@launch
            if (parsed["ok"]?.jsonPrimitive?.booleanOrNull != true) return@launch
            val rotations = parsed["local_rotations"]?.jsonArray ?: return@launch
            val snapped = Array(rig.jointCount) { Matrix4.identity() }
            for (joint in 0 until minOf(rig.jointCount, rotations.size)) {
                snapped[joint] = rowMajorRotationToMatrix4(rotations[joint].jsonArray.map { it.jsonPrimitive.float })
            }
            clientPoses = clientPoses + (entityId to snapped)
        }
    }

    // Re-skin an entity from a server pose_ref via the client LBS — shared by the Sit button + SSE.
    fun applyPoseFromRef(entityId: String, poseRef: String) {
        val rig = meshes[entityId]?.rig ?: return
        val poseFile = poseRef.substringAfterLast('/').substringAfterLast('\\')
        val poseUrl = resolveAssetUrl(props.worldStateUrl, "/assets/poses/$poseFile")
        scope.launch {
            val poseText = runCatching { httpClient.get(poseUrl).bodyAsText() }.getOrNull() ?: return@launch
            val poseDoc = runCatching { json.parseToJsonElement(poseText).jsonObject }.getOrNull() ?: return@launch
            val axisAngles = poseDoc["smpl_pose_body"]?.jsonArray?.map { it.jsonPrimitive.float } ?: return@launch
            val posed = Array(rig.jointCount) { Matrix4.identity() }
            for (joint in 1 until minOf(rig.jointCount, axisAngles.size / 3)) {
                val base = joint * 3
                posed[joint] = axisAngleToMatrix4(axisAngles[base], axisAngles[base + 1], axisAngles[base + 2])
            }
            clientPoses = clientPoses + (entityId to posed)
        }
    }

    // Interactive sit: move the chair under the character + ask the settle solver to seat them.
    fun sitInChair(entityId: String) {
        val actionUrl = props.actionUrl ?: return
        world.entities.firstOrNull { it.id == entityId }?.position?.let { seat ->
            emitIntent("move-entity", mapOf("entity_id" to "chair_1", "position" to listOf(seat[0], 0f, seat[2])))
        }
        val body = buildJsonObject {
            put("action", "sit")
            put("params", anyToJson(mapOf("entity_id" to entityId, "prop_id" to "chair_1")))
        }.toString()
        scope.launch {
            val text = runCatching {
                httpClient.post(actionUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.bodyAsText()
            }.getOrNull() ?: return@launch
            val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@launch
            val poseRef = parsed["pose_ref"]?.jsonPrimitive?.contentOrNull
            if (parsed["ok"]?.jsonPrimitive?.booleanOrNull == true && poseRef != null) {
                applyPoseFromRef(entityId, poseRef)
            }
        }
    }

    // On-demand textured render of the character's CURRENT pose (the "see the real look" leg of the
    // two-speed loop). The pose is already set by Sit/drag, so the ~10s bpy render is off the
    // interactive path; the returned image shows in the same overlay as the T1 preview.
    fun renderPose(entityId: String) {
        val url = props.actionUrl ?: return
        previewMode = true
        previewImageUrl = null
        previewStatus = "rendering textured…"
        val body = buildJsonObject {
            put("action", "render-pose")
            put("params", anyToJson(mapOf("entity_id" to entityId)))
        }.toString()
        scope.launch {
            val text = runCatching {
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.bodyAsText()
            }.getOrNull()
            if (text == null) { previewStatus = "render request failed"; return@launch }
            val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val relativeUrl = parsed?.get("preview_url")?.jsonPrimitive?.contentOrNull
            if (parsed?.get("ok")?.jsonPrimitive?.booleanOrNull == true && !relativeUrl.isNullOrEmpty()) {
                previewImageUrl = resolveAssetUrl(props.worldStateUrl, relativeUrl)
                previewStatus = ""
            } else {
                previewStatus = "render unavailable: ${text.take(120)}"
            }
        }
    }

    // T1 preview: POST camera + the SMPL body vectors derived from the live client poses to /preview
    // and show the painted still. Body vectors come straight from the reach-IK rotations (clientPoses
    // is the single source of truth) — NOT from refine-pose, which is no longer in the drag loop.
    // Faithful for POSE, not LOOK (different model than the T2 video finisher).
    fun requestPreview() {
        val url = props.actionUrl
        if (url == null) { previewStatus = "no server"; return }
        val poseBodies = clientPoses.mapNotNull { (entityId, pose) ->
            val rig = meshes[entityId]?.rig ?: return@mapNotNull null
            entityId to clientPoseToBodyVector(rig, pose)
        }.toMap()
        if (poseBodies.isEmpty()) { previewStatus = "no posed character yet"; return }
        previewImageUrl = null
        previewStatus = "rendering…"
        val resolution = previewResolution(viewportSize)
        val body = buildJsonObject {
            put("action", "preview")
            put("params", anyToJson(mapOf("camera" to previewCameraParams(camera, resolution), "poses" to poseBodies)))
        }.toString()
        scope.launch {
            val text = runCatching {
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.bodyAsText()
            }.getOrNull()
            if (text == null) { previewStatus = "preview request failed"; return@launch }
            val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val relativeUrl = parsed?.get("preview_url")?.jsonPrimitive?.contentOrNull
            if (parsed?.get("ok")?.jsonPrimitive?.booleanOrNull == true && relativeUrl != null) {
                previewImageUrl = resolveAssetUrl(props.worldStateUrl, relativeUrl)
                previewStatus = ""
            } else {
                previewStatus = "preview unavailable: ${text.take(120)}"
            }
        }
    }

    // Ask the substrate to settle the body's POSE — server reaction_sim streams the damped
    // trajectory back as pose_refs (SSE re-skins it). Root fall is the client's; this is the limbs.
    fun settle(entityId: String, impact: Float, toRest: Boolean, damping: Float = 4.0f) {
        val url = props.actionUrl ?: return
        val body = buildJsonObject {
            put("action", "settle")
            put("params", anyToJson(mapOf("entity_id" to entityId, "impact" to impact, "to_rest" to toRest, "damping" to damping)))
        }.toString()
        scope.launch {
            runCatching {
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        }
    }

    // Server-driven drop: the substrate streams the fall (root) + landing buckle (pose). Clear the
    // optimistic overlay first so the streamed world positions are what shows.
    fun dropFromHeight(entityId: String, height: Float) {
        val url = props.actionUrl ?: return
        localPositions = localPositions - entityId
        val body = buildJsonObject {
            put("action", "drop")
            put("params", anyToJson(mapOf("entity_id" to entityId, "height" to height)))
        }.toString()
        scope.launch {
            runCatching {
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        }
    }

    fun onMoveEntity(entityId: String, position: List<Float>) {
        movePrevPos?.let { prev ->
            if (prev.size >= 3 && position.size >= 3) {
                releaseVelocity = Triple(
                    (position[0] - prev[0]) / MOVE_FRAME_DT,
                    (position[1] - prev[1]) / MOVE_FRAME_DT,
                    (position[2] - prev[2]) / MOVE_FRAME_DT,
                )
            }
        }
        movePrevPos = position
        localPositions = localPositions + (entityId to position)
        if (entityId == "goal" && position.size >= 3) {
            solveTarget = listOf(position[0], -position[2], position[1])
        }
        moveSettleJob?.cancel()
        moveSettleJob = scope.launch {
            delay(CAMERA_SETTLE_MS)
            emitIntent("move-entity", mapOf("entity_id" to entityId, "position" to position))
        }
    }

    // Release of a picked-up body: a real ballistic drop, not a teleport. Carry the throw
    // velocity from the drag, integrate under gravity until the floor, damp with ground
    // friction to rest. Whole-body free-fall is the substrate's simplest release-to-equilibrium;
    // the limb/balance reaction during the fall is the server reaction_sim layer that lands next.
    fun dropAndSettle(entityId: String) {
        val start = localPositions[entityId]
            ?: world.entities.firstOrNull { it.id == entityId }?.position ?: return
        if (start.size < 3) return
        val launch = releaseVelocity
        moveSettleJob?.cancel()
        moveSettleJob = scope.launch {
            var x = start[0]; var y = start[1]; var z = start[2]
            var vx = launch.first.coerceIn(-6f, 6f)
            var vy = launch.second.coerceIn(-6f, 6f)
            var vz = launch.third.coerceIn(-6f, 6f)
            var landed = false
            while (true) {
                vy += GRAVITY * MOVE_FRAME_DT
                x += vx * MOVE_FRAME_DT; y += vy * MOVE_FRAME_DT; z += vz * MOVE_FRAME_DT
                if (y <= 0f) {
                    if (!landed) {
                        landed = true
                        // Fire the body's landing reaction the instant the feet hit: knees buckle to
                        // absorb the impact (scaled by fall speed), low damping = a real give-and-recover.
                        settle(entityId, (abs(vy) * 0.35f).coerceIn(1.0f, 3.0f), true, 2.5f)
                    }
                    y = 0f; vy = 0f; vx *= GROUND_FRICTION; vz *= GROUND_FRICTION
                }
                localPositions = localPositions + (entityId to listOf(x, y, z))
                delay(16)
                if (landed && abs(vx) < 0.1f && abs(vz) < 0.1f) break
            }
            val grounded = listOf(x, 0f, z)
            localPositions = localPositions + (entityId to grounded)
            emitIntent("move-entity", mapOf("entity_id" to entityId, "position" to grounded))
            movePrevPos = null
            releaseVelocity = Triple(0f, 0f, 0f)
        }
    }

    // Prompt loop: sentence -> /director/prompt -> action dispatch -> SSE re-skin; we show narration.
    fun submitPrompt() {
        val actionUrl = props.actionUrl ?: return
        val text = promptText.trim()
        if (text.isEmpty()) return
        val url = actionUrl.removeSuffix("/action") + "/director/prompt"
        promptStatus = "thinking…"
        val body = buildJsonObject { put("text", text) }.toString()
        scope.launch {
            val response = runCatching {
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.bodyAsText()
            }.getOrNull()
            if (response == null) { promptStatus = "request failed"; return@launch }
            val parsed = runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull()
            val narration = parsed?.get("narration")?.jsonPrimitive?.contentOrNull
            val ok = parsed?.get("ok")?.jsonPrimitive?.booleanOrNull == true
            promptOk = ok
            val base = when {
                !narration.isNullOrEmpty() -> narration
                ok -> "done"
                else -> parsed?.get("error")?.jsonPrimitive?.contentOrNull ?: "couldn't do that"
            }
            val timings = parsed?.get("timings")?.jsonObject
            promptTotalMs = timings?.get("total_ms")?.jsonPrimitive?.floatOrNull ?: 0f
            promptStatus = if (timings != null) {
                val provider = timings["provider"]?.jsonPrimitive?.contentOrNull ?: "?"
                val llm = timings["llm_ms"]?.jsonPrimitive?.floatOrNull ?: 0f
                val dispatch = timings["dispatch_ms"]?.jsonPrimitive?.floatOrNull ?: 0f
                "$base   ($provider · llm ${llm}ms · dispatch ${dispatch}ms · total ${promptTotalMs}ms)"
            } else {
                base
            }
            promptText = ""
        }
    }

    LaunchedEffect(world) {
        if (localPositions.isNotEmpty()) {
            localPositions = localPositions.filterNot { (id, optimistic) ->
                val confirmed = world.entities.firstOrNull { it.id == id }?.position
                confirmed != null && positionsClose(confirmed, optimistic)
            }
        }
        for (entity in world.entities) {
            val poseRef = entity.poseRef ?: continue
            if (appliedPoseRefs[entity.id] == poseRef || meshes[entity.id]?.rig == null) continue
            appliedPoseRefs = appliedPoseRefs + (entity.id to poseRef)
            applyPoseFromRef(entity.id, poseRef)
        }
    }

    val effectiveWorld = run {
        val withLocal =
            if (localPositions.isEmpty()) world
            else world.copy(
                entities = world.entities.map { entity ->
                    localPositions[entity.id]?.let { entity.copy(position = it) } ?: entity
                },
            )
        applyImpactPain(withLocal, props.clipImpulse)
    }

    Box(
        modifier = modifier.fillMaxSize().background(backgroundColor(effectiveWorld.background))
            .onSizeChanged { viewportSize = it },
    ) {
        Scene3dView(
            world = effectiveWorld,
            camera = camera,
            meshes = meshes,
            poses = clientPoses,
            selectedJoint = selectedJoint,
            cameraVersion = cameraVersion,
            poseMode = poseMode,
            nodeId = nodeId,
            modifier = Modifier.fillMaxSize(),
            onSelectEntity = { entityId ->
                selectedJoint = null
                emitIntent("select-entity", mapOf("entity_id" to entityId))
            },
            onSelectJoint = { entityId, joint -> selectedJoint = entityId to joint },
            onMoveEntity = { entityId, position -> onMoveEntity(entityId, position) },
            onPoseChange = { entityId, pose -> clientPoses = clientPoses + (entityId to pose) },
            onJointReleased = { entityId ->
                observability.onInteraction(
                    nodeId,
                    ActionRef(action = "set-joint"),
                    mapOf("entity_id" to entityId, "joint" to (selectedJoint?.second ?: -1)),
                )
            },
            onPickReleased = { entityId -> if (entityId != "impact" && entityId != "goal") dropAndSettle(entityId) },
        )
        if (previewMode) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xF01C1C20)),
                contentAlignment = Alignment.Center,
            ) {
                val image = previewImageUrl
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = "T1 staging preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                    )
                } else {
                    Text(
                        text = "T1 preview (blocking)\n$previewStatus",
                        color = Color(0xFFD8D8E0),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PoseMode.entries.forEach { mode ->
                if (mode == poseMode) {
                    Button(onClick = { poseMode = mode }) { Text(mode.label) }
                } else {
                    OutlinedButton(onClick = { poseMode = mode }) { Text(mode.label) }
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(onClick = { previewMode = !previewMode; if (previewMode) requestPreview() }) {
                Text(if (previewMode) "3D" else "Preview")
            }
            selectedJoint?.first?.let { entityId ->
                Button(onClick = { snapToPlausible(entityId) }) { Text("Snap") }
            }
            effectiveWorld.entities.firstOrNull { it.selected && meshes[it.id]?.rig != null }?.id?.let { entityId ->
                Button(onClick = { settle(entityId, 1.2f, true) }) { Text("Settle") }
                Button(onClick = { dropFromHeight(entityId, 1.6f) }) { Text("Drop") }
                Button(onClick = { sitInChair(entityId) }) { Text("Sit") }
                Button(onClick = { renderPose(entityId) }) { Text("Render") }
            }
            Button(onClick = { shotCamera?.let { applyCameraState(camera, it); cameraVersion++ } }) {
                Text("Home")
            }
            Button(onClick = {
                shotCamera = cameraSnapshot(camera)
                emitIntent("camera-update", cameraParams(camera))
            }) {
                Text("Set Shot")
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            if (promptStatus.isNotEmpty()) {
                Text(
                    text = promptStatus,
                    color = when {
                        !promptOk -> Color(0xFFE08A8A)
                        promptTotalMs > 3000f -> Color(0xFFE08A8A)
                        promptTotalMs > 1500f -> Color(0xFFE0C080)
                        else -> Color(0xFFD8D8E0)
                    },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = { Text("Tell the scene what to do…") },
                    singleLine = true,
                    modifier = Modifier.width(360.dp),
                )
                Button(onClick = { submitPrompt() }) { Text("Go") }
            }
        }
        if (engineStatus.isNotEmpty()) {
            Text(
                text = "engine: $engineStatus",
                color = if (engineStatus.contains("offline")) Color(0xFFE08A8A) else Color(0xFF8AD0E0),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    .background(Color(0xCC1C1C20)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        naturalness?.let { (score, smoothness, continuity) ->
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    .background(Color(0xCC1C1C20)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    "physical plausibility  ${(score * 100).toInt()}%",
                    color = plausibilityColor(score),
                    modifier = Modifier.padding(bottom = 5.dp),
                )
                PlausibilityBar("smoothness", smoothness)
                PlausibilityBar("continuity", continuity)
            }
        }
        loadError?.let {
            Text(text = it, color = Color(0xFFE0A0A0), modifier = Modifier.padding(12.dp))
        }
    }
}

private fun plausibilityColor(value: Float): Color = when {
    value >= 0.8f -> Color(0xFF7CD992)
    value >= 0.5f -> Color(0xFFE0C080)
    else -> Color(0xFFE08A8A)
}

@Composable
private fun PlausibilityBar(label: String, value: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color(0xFF9AA0B0))
        Box(Modifier.width(96.dp).height(5.dp).background(Color(0x33FFFFFF))) {
            Box(Modifier.width((96f * value.coerceIn(0f, 1f)).dp).height(5.dp).background(plausibilityColor(value)))
        }
    }
}

private fun backgroundColor(hex: String?): Color {
    val value = hex?.removePrefix("#")?.toLongOrNull(16) ?: return Color(0xFF2B2B30)
    return Color(0xFF000000L or value)
}

private fun positionsClose(a: List<Float>, b: List<Float>): Boolean {
    if (a.size != b.size) return false
    return a.indices.all { abs(a[it] - b[it]) < 1e-3f }
}

private fun cameraSnapshot(camera: Camera): Scene3dCameraState {
    val eye = camera.eyePosition()
    val target = camera.target
    return Scene3dCameraState(
        position = listOf(eye.x, eye.y, eye.z),
        target = listOf(target.x, target.y, target.z),
        fov = camera.fovYRadians,
    )
}

private fun cameraParams(camera: Camera): Map<String, Any?> {
    val eye = camera.eyePosition()
    val target = camera.target
    return mapOf(
        "camera" to mapOf(
            "position" to listOf(eye.x, eye.y, eye.z),
            "target" to listOf(target.x, target.y, target.z),
            "fov" to camera.fovYRadians,
        ),
    )
}

private fun previewCameraParams(camera: Camera, resolution: List<Int>): Map<String, Any?> {
    val eye = camera.eyePosition()
    val target = camera.target
    return mapOf(
        "position" to listOf(eye.x, eye.y, eye.z),
        "target" to listOf(target.x, target.y, target.z),
        "up" to listOf(0f, 1f, 0f),
        "fov" to camera.fovYRadians,
        "resolution" to resolution,
    )
}

// Cap the preview render to a sane pixel budget while preserving the live viewport's aspect,
// so the staging still frames the body the same way the user sees it. Falls back to portrait.
private fun previewResolution(viewport: IntSize): List<Int> {
    if (viewport.width <= 0 || viewport.height <= 0) return listOf(768, 1024)
    val longestEdge = 1024f
    val scale = (longestEdge / maxOf(viewport.width, viewport.height)).coerceAtMost(1f)
    return listOf((viewport.width * scale).toInt(), (viewport.height * scale).toInt())
}

// Pack the live client pose (per-joint local rotations) into the SMPL-H body axis-angle vector the
// preview action expects: joint j's rotvec lands at slot j*3, pelvis (j=0) stays the zero global orient.
private fun clientPoseToBodyVector(rig: Scene3dRig, pose: Array<Matrix4>): List<Float> {
    val bodyVector = MutableList(SMPLH_POSE_LEN) { 0f }
    val jointLimit = minOf(rig.jointCount, SMPLH_BODY_JOINTS, pose.size)
    for (joint in 1 until jointLimit) {
        val rotvec = matrix4ToRotvec(pose[joint])
        val slot = joint * 3
        bodyVector[slot] = rotvec[0]
        bodyVector[slot + 1] = rotvec[1]
        bodyVector[slot + 2] = rotvec[2]
    }
    return bodyVector
}

// Rotation matrix → axis-angle (rotvec), the inverse of the server's axis-angle→matrot. Column-major
// off-diagonals give the axis; the near-π branch recovers it from the diagonal when sin θ collapses.
private fun matrix4ToRotvec(rotation: Matrix4): List<Float> {
    val m = rotation.m
    val cosAngle = (((m[0] + m[5] + m[10]) - 1f) * 0.5f).coerceIn(-1f, 1f)
    val angle = acos(cosAngle)
    if (angle < 1e-5f) return listOf(0f, 0f, 0f)
    val sinAngle = sin(angle)
    if (sinAngle < 1e-4f) {
        val axisX = sqrt(((m[0] + 1f) * 0.5f).coerceAtLeast(0f))
        val axisY = sqrt(((m[5] + 1f) * 0.5f).coerceAtLeast(0f))
        val axisZ = sqrt(((m[10] + 1f) * 0.5f).coerceAtLeast(0f))
        val norm = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ).coerceAtLeast(1e-6f)
        return listOf(axisX / norm * angle, axisY / norm * angle, axisZ / norm * angle)
    }
    val scale = angle / (2f * sinAngle)
    return listOf((m[6] - m[9]) * scale, (m[8] - m[2]) * scale, (m[1] - m[4]) * scale)
}

// Axis-angle (Rodrigues) → column-major Matrix4 — the inverse of matrix4ToRotvec, used to apply a
// server SMPL pose vector (per-joint axis-angle) onto the client rig's local rotations.
private fun axisAngleToMatrix4(ax: Float, ay: Float, az: Float): Matrix4 {
    val angle = sqrt(ax * ax + ay * ay + az * az)
    if (angle < 1e-8f) return Matrix4.identity()
    val x = ax / angle
    val y = ay / angle
    val z = az / angle
    val c = cos(angle)
    val s = sin(angle)
    val t = 1f - c
    return Matrix4(
        floatArrayOf(
            t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0f,
            t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0f,
            t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0f,
            0f, 0f, 0f, 1f,
        ),
    )
}

private fun rowMajorRotationToMatrix4(rowMajor: List<Float>): Matrix4 = Matrix4(
    floatArrayOf(
        rowMajor[0], rowMajor[3], rowMajor[6], 0f,
        rowMajor[1], rowMajor[4], rowMajor[7], 0f,
        rowMajor[2], rowMajor[5], rowMajor[8], 0f,
        0f, 0f, 0f, 1f,
    ),
)

private fun anyToJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to anyToJson(item) })
    is List<*> -> JsonArray(value.map { anyToJson(it) })
    else -> JsonPrimitive(value.toString())
}

private fun resolveAssetUrl(worldStateUrl: String, assetUrl: String): String {
    if (assetUrl.startsWith("http")) return assetUrl
    val schemeEnd = worldStateUrl.indexOf("://")
    if (schemeEnd < 0) return assetUrl
    val pathStart = worldStateUrl.indexOf('/', schemeEnd + 3)
    val origin = if (pathStart < 0) worldStateUrl else worldStateUrl.substring(0, pathStart)
    return origin + assetUrl
}

private fun applyCameraState(camera: Camera, state: Scene3dCameraState) {
    val target = state.target.toVec3()
    val eye = state.position.toVec3()
    val offset = eye - target
    val distance = offset.length()
    camera.target = target
    if (distance > 1e-4f) {
        camera.distance = distance
        val direction = offset.normalize()
        camera.pitchRadians = asin(direction.y.coerceIn(-1f, 1f))
        camera.yawRadians = atan2(direction.x, direction.z)
    }
    state.fov?.let { camera.fovYRadians = it }
}
