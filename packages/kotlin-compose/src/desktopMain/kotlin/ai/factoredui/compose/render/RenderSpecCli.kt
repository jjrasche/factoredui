package ai.factoredui.compose.render

import java.io.File

// Headless CLI over renderSpecToPng so a non-JVM caller (il-render's Python gate) can shell out:
// args = <spec-json-path> <out-png-path> [width] [height] [density] [transparent]. Exit 0 + PNG on disk, else nonzero.
fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage: render-spec-cli <spec.json> <out.png> [width=800] [height=1280] [density=2.0] [transparent=false]")
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

    val png = renderSpecToPng(specFile.readText(), width = width, height = height, density = density, transparent = transparent)
    if (png.isEmpty()) {
        System.err.println("render-spec-cli: render produced 0 bytes")
        kotlin.system.exitProcess(4)
    }
    outFile.parentFile?.mkdirs()
    outFile.writeBytes(png)
    println("render-spec-cli: wrote ${png.size} bytes -> ${outFile.absolutePath}")
}
