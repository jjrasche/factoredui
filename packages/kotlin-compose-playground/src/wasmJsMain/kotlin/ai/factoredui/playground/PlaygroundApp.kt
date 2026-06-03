package ai.factoredui.playground

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.forcegraph.startSseSubscription
import ai.factoredui.compose.observability.LoggingObservability
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.scene3d.Scene3dWorldState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

@Composable
fun PlaygroundApp() {
    val context = remember {
        RenderContext(
            actions = playgroundActions(actionUrlParam()),
            observability = LoggingObservability(),
        )
    }
    val specFlow = remember { MutableStateFlow(placeholderSpec("Loading spec…")) }
    var editorText by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }

    // Host-mirror: subscribe to the world stream once and reflect entities into
    // the data store as world.entities, so spec nodes (the chip-row) bind to the
    // same server truth scene3d self-fetches. Each SSE frame re-writes the
    // binding, so the chip-row re-renders reactively when selection changes.
    val worldStreamUrl = remember { worldStreamUrlParam() }
    DisposableEffect(worldStreamUrl) {
        val url = worldStreamUrl ?: return@DisposableEffect onDispose { }
        val mirrorJson = Json { ignoreUnknownKeys = true }
        val subscription = startSseSubscription(
            url = url,
            onMessage = { frame ->
                runCatching { mirrorJson.decodeFromString(Scene3dWorldState.serializer(), frame) }
                    .getOrNull()
                    ?.let { state ->
                        val entities = state.entities.map { entity ->
                            mapOf<String, Any?>(
                                "id" to entity.id,
                                "selected" to entity.selected,
                                "status" to entity.status,
                            )
                        }
                        context.setBinding("world.entities", entities)
                    }
            },
            onError = { },
        )
        onDispose { subscription.close() }
    }

    fun applySpec(text: String) {
        parseSpec(text).fold(
            onSuccess = { spec -> specFlow.value = spec; parseError = null },
            onFailure = { failure -> parseError = failure.message ?: "spec parse failed" },
        )
    }

    LaunchedEffect(Unit) {
        val url = specUrlParam() ?: DEFAULT_SPEC_URL
        runCatching { loadSpecText(url) }.fold(
            onSuccess = { text -> editorText = text; applySpec(text) },
            onFailure = { failure -> parseError = "fetch failed for $url — ${failure.message}" },
        )
    }

    val fullscreen = remember { fullscreenParam() }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (fullscreen) {
                RenderedSpecPanel(specFlow = specFlow, context = context, modifier = Modifier.fillMaxSize())
            } else {
                Row(Modifier.fillMaxSize()) {
                    SpecEditorPanel(
                        editorText = editorText,
                        parseError = parseError,
                        onEditorChange = { editorText = it },
                        onReload = { applySpec(editorText) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    RenderedSpecPanel(
                        specFlow = specFlow,
                        context = context,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    BindingControlPanel(
                        context = context,
                        modifier = Modifier.width(300.dp).fillMaxHeight(),
                    )
                }
            }
        }
    }
}
