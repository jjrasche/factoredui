package ai.factoredui.compose.render

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.URI

private val specDecoder = Json { ignoreUnknownKeys = true }

// A real window on a real spec — the pointer-driven counterpart to renderSpecToPng, so a spec can
// be orbited and zoomed rather than only asserted against. A world_state_url may be handed in
// directly; a served world becomes lookable without anyone hand-writing a spec around it.
// args = <spec or world-state json: file path or http URL> [width] [height]
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: spec-window <spec.json | world.json — path or http URL> [width=1280] [height=800]")
        kotlin.system.exitProcess(2)
    }
    val source = args[0]
    val width = args.getOrNull(1)?.toIntOrNull() ?: 1280
    val height = args.getOrNull(2)?.toIntOrNull() ?: 800

    val sourceJson = runCatching { readSource(source) }.getOrElse { failure ->
        System.err.println("spec-window: cannot read $source — ${failure.message ?: failure::class.simpleName}")
        kotlin.system.exitProcess(3)
    }
    val spec = runCatching { specOf(sourceJson, source) }.getOrElse { failure ->
        System.err.println("spec-window: $source is neither a spec nor a world state — ${failure.message ?: failure::class.simpleName}")
        kotlin.system.exitProcess(4)
    }

    println("spec-window: ${spec.root.type} '${spec.root.id}' from $source — drag to orbit, scroll to zoom")
    singleWindowApplication(
        state = WindowState(size = DpSize(width.dp, height.dp), position = WindowPosition.Aligned(Alignment.Center)),
        title = "factoredui — ${spec.root.id}",
    ) {
        RenderSpec(spec = spec, context = RenderContext())
    }
}

private fun readSource(source: String): String =
    if (source.startsWith("http")) URI(source).toURL().readText() else File(source).readText()

private fun specOf(sourceJson: String, source: String): Spec {
    val fields = specDecoder.parseToJsonElement(sourceJson) as JsonObject
    val specJson = if (fields.containsKey("root")) sourceJson else sceneSpecAround(source)
    return specDecoder.decodeFromString(Spec.serializer(), specJson)
}

private fun sceneSpecAround(worldStateUrl: String) = """
    {
      "spec_version": 1,
      "renderer_min": 1,
      "root": {
        "id": "served-scene",
        "type": "scene3d",
        "props": { "world_state_url": "$worldStateUrl", "background": "neutral-gray" }
      }
    }
""".trimIndent()
