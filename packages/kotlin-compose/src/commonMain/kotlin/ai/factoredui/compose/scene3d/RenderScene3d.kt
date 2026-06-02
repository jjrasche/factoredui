package ai.factoredui.compose.scene3d

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.forcegraph.math.Camera
import ai.factoredui.compose.forcegraph.startSseSubscription
import ai.factoredui.compose.observability.NoOpObservability
import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.Scene3dProps
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

private const val CAMERA_SETTLE_MS = 150L

@Composable
fun RenderScene3d(
    props: Scene3dProps,
    nodeId: String = "scene3d",
    observability: Observability = NoOpObservability,
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
    var cameraSettleJob by remember { mutableStateOf<Job?>(null) }
    var localPositions by remember { mutableStateOf<Map<String, List<Float>>>(emptyMap()) }
    var moveSettleJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(props.worldStateUrl) {
        if (props.worldStateUrl.isEmpty()) {
            loadError = "scene3d: missing world_state_url"
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

    fun emitCameraSettled() {
        cameraSettleJob?.cancel()
        cameraSettleJob = scope.launch {
            delay(CAMERA_SETTLE_MS)
            emitIntent("camera-update", cameraParams(camera))
        }
    }

    fun onMoveEntity(entityId: String, position: List<Float>) {
        localPositions = localPositions + (entityId to position)
        moveSettleJob?.cancel()
        moveSettleJob = scope.launch {
            delay(CAMERA_SETTLE_MS)
            emitIntent("move-entity", mapOf("entity_id" to entityId, "position" to position))
        }
    }

    LaunchedEffect(world) {
        if (localPositions.isNotEmpty()) {
            localPositions = localPositions.filterNot { (id, optimistic) ->
                val confirmed = world.entities.firstOrNull { it.id == id }?.position
                confirmed != null && positionsClose(confirmed, optimistic)
            }
        }
    }

    val effectiveWorld =
        if (localPositions.isEmpty()) world
        else world.copy(
            entities = world.entities.map { entity ->
                localPositions[entity.id]?.let { entity.copy(position = it) } ?: entity
            },
        )

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF2B2B30))) {
        Scene3dView(
            world = effectiveWorld,
            camera = camera,
            meshes = meshes,
            modifier = Modifier.fillMaxSize(),
            onSelectEntity = { entityId -> emitIntent("select-entity", mapOf("entity_id" to entityId)) },
            onCameraChange = { emitCameraSettled() },
            onMoveEntity = { entityId, position -> onMoveEntity(entityId, position) },
        )
        loadError?.let {
            Text(text = it, color = Color(0xFFE0A0A0), modifier = Modifier.padding(12.dp))
        }
    }
}

private fun positionsClose(a: List<Float>, b: List<Float>): Boolean {
    if (a.size != b.size) return false
    return a.indices.all { abs(a[it] - b[it]) < 1e-3f }
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
