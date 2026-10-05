package ai.factoredui.worldbuilder

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.render.resolveWindowTheme
import ai.factoredui.compose.render.windowThemeFromEnvironment
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.renderer.SpecTheme
import ai.factoredui.compose.renderer.themeNamed
import ai.factoredui.compose.schema.Spec
import ai.factoredui.worldengine.session.WorldSession
import ai.factoredui.worldengine.world.MapWorldLibrary
import ai.factoredui.worldengine.world.World
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val DEFAULT_WIDTH = 1500
private const val DEFAULT_HEIGHT = 900
private const val WORLD_FILE_SUFFIX = ".world.json"

data class WorldBuilderArgs(
    val world: String,
    val spec: String,
    val presentation: String?,
    val theme: String?,
    val animate: Boolean,
    val width: Int,
    val height: Int,
)

fun parseWorldBuilderArgs(args: Array<String>): WorldBuilderArgs? {
    val flags = args.toList().chunked(2).filter { it.size == 2 && it[0].startsWith("--") }.associate { it[0].removePrefix("--") to it[1] }
    val world = flags["world"] ?: return null
    val spec = flags["spec"] ?: return null
    return WorldBuilderArgs(
        world = world,
        spec = spec,
        presentation = flags["presentation"],
        theme = flags["theme"],
        animate = flags["animate"]?.toBooleanStrictOrNull() ?: true,
        width = flags["width"]?.toIntOrNull() ?: DEFAULT_WIDTH,
        height = flags["height"]?.toIntOrNull() ?: DEFAULT_HEIGHT,
    )
}

fun openSession(worldPath: String): WorldSession {
    val file = File(worldPath)
    val files = file.absoluteFile.parentFile.listFiles { candidate -> candidate.name.endsWith(WORLD_FILE_SUFFIX) }.orEmpty()
        .associate { it.name to it.readText() }
    return WorldSession(World.open(file.name, MapWorldLibrary(files)))
}

fun loadPresentation(path: String?): Map<String, UsePresentation> {
    if (path == null) return emptyMap()
    val root = Json.parseToJsonElement(File(path).readText()).jsonObject
    return root.entries.associate { (use, entry) -> use to usePresentationOf(entry as JsonObject) }
}

private fun usePresentationOf(entry: JsonObject) = UsePresentation(
    height = entry["height"]?.jsonPrimitive?.doubleOrNull,
    critter = entry["critter"]?.jsonPrimitive?.content,
    image = entry["image"]?.jsonPrimitive?.content,
)

fun main(args: Array<String>) {
    val parsed = parseWorldBuilderArgs(args)
    if (parsed == null) {
        System.err.println("usage: world-builder --world <x.world.json> --spec <spec.json> [--presentation p.json] [--theme light|dark] [--animate true|false] [--width N] [--height N]")
        kotlin.system.exitProcess(2)
    }
    val host = WorldBuilderHost(openSession(parsed.world), loadPresentation(parsed.presentation))
    val spec = Json { ignoreUnknownKeys = true }.decodeFromString(Spec.serializer(), File(parsed.spec).readText())
    val theme = resolveWindowTheme(parsed.theme, windowThemeFromEnvironment(), null)
    var publish: () -> Unit = {}
    val context = RenderContext(
        actions = host.actions { publish() },
        initialData = host.bindings() + mapOf("theme" to theme, "animate" to parsed.animate, "brush" to host.initialBrush()),
        theme = themeNamed(theme) ?: SpecTheme.DARK,
    )
    publish = { context.applyBindings(host.bindings()) }
    println("world-builder: ${parsed.world} on branch ${host.session.currentBranch}")
    singleWindowApplication(
        state = WindowState(size = DpSize(parsed.width.dp, parsed.height.dp), position = WindowPosition.Aligned(Alignment.Center)),
        title = "factoredui - world-builder-engine",
    ) {
        RenderSpec(spec = spec, context = context)
    }
}
