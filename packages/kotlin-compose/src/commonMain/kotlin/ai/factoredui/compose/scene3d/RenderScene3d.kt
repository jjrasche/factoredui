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
import ai.factoredui.compose.schema.Scene3dProps
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.asin
import kotlin.math.atan2

@Composable
fun RenderScene3d(props: Scene3dProps, modifier: Modifier = Modifier) {
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

    fun postIntent(body: String) {
        val url = props.actionUrl ?: return
        scope.launch {
            runCatching {
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF2B2B30))) {
        Scene3dView(
            world = world,
            camera = camera,
            meshes = meshes,
            modifier = Modifier.fillMaxSize(),
            onSelectEntity = { entityId -> postIntent(selectIntent(entityId)) },
            onCameraChange = { postIntent(cameraIntent(camera)) },
        )
        loadError?.let {
            Text(text = it, color = Color(0xFFE0A0A0), modifier = Modifier.padding(12.dp))
        }
    }
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

private fun selectIntent(entityId: String): String = buildJsonObject {
    put("action", "select-entity")
    put("params", buildJsonObject { put("entity_id", entityId) })
}.toString()

private fun cameraIntent(camera: Camera): String {
    val eye = camera.eyePosition()
    val target = camera.target
    return buildJsonObject {
        put("action", "camera-update")
        put("params", buildJsonObject {
            put("camera", buildJsonObject {
                put("position", buildJsonArray { add(JsonPrimitive(eye.x)); add(JsonPrimitive(eye.y)); add(JsonPrimitive(eye.z)) })
                put("target", buildJsonArray { add(JsonPrimitive(target.x)); add(JsonPrimitive(target.y)); add(JsonPrimitive(target.z)) })
                put("fov", camera.fovYRadians)
            })
        })
    }.toString()
}
