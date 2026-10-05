package ai.factoredui.compose.render

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// Headless CLI over renderSpecToPng so a non-JVM caller (il-render's Python gate) can shell out:
// args = <spec-json-path> <out-png-path> [width] [height] [density] [transparent] [--data data.json]. Exit 0 + PNG on disk, else nonzero.
fun main(rawArgs: Array<String>) {
    val dataIndex = rawArgs.indexOf("--data")
    val dataFile = if (dataIndex >= 0) rawArgs.getOrNull(dataIndex + 1)?.let { File(it) } else null
    val args = rawArgs.filterIndexed { index, _ -> dataIndex < 0 || (index != dataIndex && index != dataIndex + 1) }.toTypedArray()
    if (args.size < 2 || (dataIndex >= 0 && dataFile?.isFile != true)) {
        System.err.println("usage: render-spec-cli <spec.json> <out.png> [width=800] [height=1280] [density=2.0] [transparent=false] [--data data.json]")
        kotlin.system.exitProcess(2)
    }
    val specFile = File(args[0])
    if (!specFile.isFile) {
        System.err.println("render-spec-cli: spec file not found: ${specFile.absolutePath}")
        kotlin.system.exitProcess(3)
    }
    val outFile = File(args[1])
    val width = args.getOrNull(2)?.toIntOrNull() ?: 800
    val height = args.getOrNull(3)?.toIntOrNull() ?: 1280
    val density = args.getOrNull(4)?.toFloatOrNull() ?: 2f
    val transparent = args.getOrNull(5)?.toBooleanStrictOrNull() ?: false

    val data = dataFile?.let { jsonObjectToMap(Json.parseToJsonElement(it.readText()) as JsonObject) } ?: emptyMap()
    val png = renderSpecToPng(specFile.readText(), width = width, height = height, density = density, transparent = transparent, data = data)
    if (png.isEmpty()) {
        System.err.println("render-spec-cli: render produced 0 bytes")
        kotlin.system.exitProcess(4)
    }
    outFile.parentFile?.mkdirs()
    outFile.writeBytes(png)
    println("render-spec-cli: wrote ${png.size} bytes -> ${outFile.absolutePath}")
}
