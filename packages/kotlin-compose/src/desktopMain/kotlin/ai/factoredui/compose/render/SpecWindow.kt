package ai.factoredui.compose.render

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.renderer.SpecTheme
import ai.factoredui.compose.renderer.themeNamed
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.decodeSpecFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.URI

private val specDecoder = Json { ignoreUnknownKeys = true }

private const val DEFAULT_WIDTH = 1280
private const val DEFAULT_HEIGHT = 800
private const val DATA_FLAG = "--data"
private const val THEME_FLAG = "--theme"
private const val STATE_FLAG = "--state"
private const val CONTROL_FLAG = "--control"
private const val TITLE_FLAG = "--title"
private const val SECTION_FLAG = "--section"
private val WINDOW_FLAGS = setOf(DATA_FLAG, THEME_FLAG, STATE_FLAG, CONTROL_FLAG, TITLE_FLAG, SECTION_FLAG)

data class WindowArgs(
    val source: String,
    val width: Int,
    val height: Int,
    val dataPath: String?,
    val theme: String? = null,
    val stateFiles: List<StateFile> = emptyList(),
    val controlPort: Int? = null,
    val title: String? = null,
    val section: String? = null,
)

fun windowTitle(titleFlag: String?, rootId: String): String = titleFlag ?: "factoredui — $rootId"

fun parseWindowArgs(args: Array<String>): WindowArgs? {
    if (args.isEmpty()) return null
    val flagIndexes = args.indices.filter { args[it] in WINDOW_FLAGS }
    if (flagIndexes.any { it + 1 >= args.size }) return null
    val flags = flagIndexes.map { args[it] to args[it + 1] }
    val stateFiles = flags.filter { it.first == STATE_FLAG }.map { parseStateFile(it.second) ?: return null }
    val controlPort = flags.lastOrNull { it.first == CONTROL_FLAG }?.let { it.second.toIntOrNull() ?: return null }
    val consumed = flagIndexes.flatMap { listOf(it, it + 1) }.toSet()
    val positional = args.filterIndexed { index, _ -> index !in consumed }
    val sizes = positional.drop(1).mapNotNull { it.toIntOrNull() }
    return WindowArgs(
        source = positional.first(),
        width = sizes.getOrNull(0) ?: DEFAULT_WIDTH,
        height = sizes.getOrNull(1) ?: DEFAULT_HEIGHT,
        dataPath = flags.lastOrNull { it.first == DATA_FLAG }?.second,
        theme = flags.lastOrNull { it.first == THEME_FLAG }?.second,
        stateFiles = stateFiles,
        controlPort = controlPort,
        title = flags.lastOrNull { it.first == TITLE_FLAG }?.second,
        section = flags.lastOrNull { it.first == SECTION_FLAG }?.second,
    )
}

// The pointer-driven counterpart to renderSpecToPng. --data seeds the spec's bindings from a JSON
// file's top-level keys, so a host's own JSON opens under a generic spec with no generated wrapper.
fun main(args: Array<String>) {
    val parsed = parseWindowArgs(args)
    if (parsed == null) {
        System.err.println("usage: spec-window <spec.json | world.json — path or http URL> [width=1280] [height=800] [--data data.json] [--state a.b=text|json|reports:file ...] [--control port] [--theme light|dark] [--title text] [--section key]")
        kotlin.system.exitProcess(2)
    }
    val source = parsed.source

    val sourceJson = runCatching { readSource(source) }.getOrElse { failure ->
        System.err.println("spec-window: cannot read $source — ${failure.message ?: failure::class.simpleName}")
        kotlin.system.exitProcess(3)
    }
    val spec = runCatching { windowSpecOf(sourceJson, source, parsed.section) }.getOrElse { failure ->
        val reason = failure.message ?: failure::class.simpleName
        System.err.println(if (parsed.section != null) "spec-window: $source: $reason" else "spec-window: $source is neither a spec nor a world state — $reason")
        kotlin.system.exitProcess(4)
    }
    val data = parsed.dataPath?.let { path ->
        runCatching { jsonObjectToMap(specDecoder.parseToJsonElement(File(path).readText()) as JsonObject) }.getOrElse { failure ->
            System.err.println("spec-window: cannot read data file $path — ${failure.message ?: failure::class.simpleName}")
            kotlin.system.exitProcess(5)
        }
    } ?: emptyMap()
    val state = runCatching { windowState(data, parsed.stateFiles) }.getOrElse { failure ->
        System.err.println("spec-window: cannot read a state file — ${failure.message ?: failure::class.simpleName}")
        kotlin.system.exitProcess(6)
    }
    val theme = resolveWindowTheme(parsed.theme, windowThemeFromEnvironment(), state["theme"])
    val seeded = state + ("theme" to theme)

    val context = RenderContext(initialData = seeded, theme = themeNamed(theme) ?: SpecTheme.DARK)
    val surface = AwtWindowSurface()
    val control = WindowControl(context, surface)
    parsed.controlPort?.let { port -> println("spec-window: control on 127.0.0.1:${control.start(port).address.port}") }

    println("spec-window: ${spec.root.type} '${spec.root.id}' from $source with ${data.size} data keys and ${parsed.stateFiles.size} state files, $theme theme")
    singleWindowApplication(
        state = WindowState(size = DpSize(parsed.width.dp, parsed.height.dp), position = WindowPosition.Aligned(Alignment.Center)),
        title = windowTitle(parsed.title, spec.root.id),
    ) {
        SideEffect { surface.window = window }
        RenderSpec(spec = spec, context = context)
    }
}

private fun readSource(source: String): String =
    if (source.startsWith("http")) URI(source).toURL().readText() else File(source).readText()

fun windowSpecOf(sourceJson: String, source: String, section: String?): Spec {
    if (section != null) return decodeSpecFile(sourceJson, section)
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
