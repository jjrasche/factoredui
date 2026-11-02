package ai.factoredui.compose.crawl

import ai.factoredui.compose.render.jsonObjectToMap
import ai.factoredui.compose.renderer.SpecTheme
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.system.exitProcess

private val specDecoder = Json { ignoreUnknownKeys = true }
private val SHEET_STOPS = listOf("peek", "half", "full")

private fun hasSheet(tree: JsonObject): Boolean =
    tree["type"]?.jsonPrimitive?.content == "sheet" || tree.getValue("children").let { it as JsonArray }.any { hasSheet(it.jsonObject) }

private fun writeCapture(screen: DrivenScreen, directory: File, name: String, stop: String?): JsonObject {
    val tree = screen.tree() ?: error("the screen drew no tree")
    File(directory, "$name.tree.json").writeText(tree.toString())
    File(directory, "$name.png").writeBytes(screen.frame().png)
    return buildJsonObject {
        put("stop", stop)
        put("tree", "$name.tree.json")
        put("shot", "$name.png")
    }
}

private fun captureStops(screen: DrivenScreen, directory: File, name: String, sheetBinding: String?): List<JsonObject> {
    val first = screen.tree() ?: error("the screen drew no tree")
    if (sheetBinding == null || !hasSheet(first)) return listOf(writeCapture(screen, directory, name, null))
    return SHEET_STOPS.map { stop ->
        screen.type(sheetBinding, stop)
        writeCapture(screen, directory, "$name-$stop", stop)
    }
}

/** args: spec.json widthDp heightDp outDir name [sheetBinding]; the spec's own `state` seeds the bindings, as the browser host does. */
fun main(args: Array<String>) {
    if (args.size < 5) {
        System.err.println("usage: LayoutCaptureCli <spec.json> <widthDp> <heightDp> <outDir> <name> [sheetBinding]")
        exitProcess(2)
    }
    val text = File(args[0]).readText()
    val spec = specDecoder.decodeFromString<Spec>(text)
    val state = (specDecoder.parseToJsonElement(text).jsonObject["state"] as? JsonObject)?.let(::jsonObjectToMap).orEmpty()
    val directory = File(args[3]).also { it.mkdirs() }
    driveScreen(spec, state, args[1].toInt(), args[2].toInt(), SpecTheme.LIGHT).use { screen ->
        val captures = captureStops(screen, directory, args[4], args.getOrNull(5))
        val root = screen.tree()!!.getValue("bounds").jsonObject
        println(buildJsonObject {
            put("captures", JsonArray(captures))
            put("width", root.getValue("w").jsonPrimitive.double)
            put("height", root.getValue("h").jsonPrimitive.double)
        })
    }
}
