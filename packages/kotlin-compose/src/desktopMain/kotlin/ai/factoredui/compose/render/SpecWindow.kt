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

private const val DEFAULT_WIDTH = 1280
private const val DEFAULT_HEIGHT = 800
private const val DATA_FLAG = "--data"

data class WindowArgs(val source: String, val width: Int, val height: Int, val dataPath: String?)

fun parseWindowArgs(args: Array<String>): WindowArgs? {
    if (args.isEmpty()) return null
    val dataIndex = args.indexOf(DATA_FLAG)
    if (dataIndex >= 0 && dataIndex + 1 >= args.size) return null
    val dataPath = if (dataIndex >= 0) args[dataIndex + 1] else null
    val positional = args.filterIndexed { index, _ -> dataIndex < 0 || (index != dataIndex && index != dataIndex + 1) }
    val sizes = positional.drop(1).mapNotNull { it.toIntOrNull() }
    return WindowArgs(
        source = positional.first(),
        width = sizes.getOrNull(0) ?: DEFAULT_WIDTH,
        height = sizes.getOrNull(1) ?: DEFAULT_HEIGHT,
        dataPath = dataPath,
    )
}

// The pointer-driven counterpart to renderSpecToPng. --data seeds the spec's bindings from a JSON
// file's top-level keys, so a host's own JSON opens under a generic spec with no generated wrapper.
fun main(args: Array<String>) {
    val parsed = parseWindowArgs(args)
    if (parsed == null) {
        System.err.println("usage: spec-window <spec.json | world.json — path or http URL> [width=1280] [height=800] [--data data.json]")
        kotlin.system.exitProcess(2)
    }
    val source = parsed.source

    val sourceJson = runCatching { readSource(source) }.getOrElse { failure ->
        System.err.println("spec-window: cannot read $source — ${failure.message ?: failure::class.simpleName}")
        kotlin.system.exitProcess(3)
    }
    val spec = runCatching { specOf(sourceJson, source) }.getOrElse { failure ->
        System.err.println("spec-window: $source is neither a spec nor a world state — ${failure.message ?: failure::class.simpleName}")
        kotlin.system.exitProcess(4)
    }
    val data = parsed.dataPath?.let { path ->
        runCatching { jsonObjectToMap(specDecoder.parseToJsonElement(File(path).readText()) as JsonObject) }.getOrElse { failure ->
            System.err.println("spec-window: cannot read data file $path — ${failure.message ?: failure::class.simpleName}")
            kotlin.system.exitProcess(5)
        }
    } ?: emptyMap()

    println("spec-window: ${spec.root.type} '${spec.root.id}' from $source with ${data.size} data keys — drag to pan, scroll to zoom")
    singleWindowApplication(
        state = WindowState(size = DpSize(parsed.width.dp, parsed.height.dp), position = WindowPosition.Aligned(Alignment.Center)),
        title = "factoredui — ${spec.root.id}",
    ) {
        RenderSpec(spec = spec, context = RenderContext(initialData = data))
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
