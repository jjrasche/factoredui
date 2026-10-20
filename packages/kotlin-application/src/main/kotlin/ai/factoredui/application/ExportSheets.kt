package ai.factoredui.application

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class ExportOptions(val system: Path, val runtime: Path, val out: Path)

private val EXPORT_FLAGS = setOf("--system", "--runtime", "--out")

fun parseExportOptions(args: List<String>): ExportOptions {
    val pairs = args.chunked(2)
    pairs.forEach { pair ->
        if (pair[0] !in EXPORT_FLAGS) throw UsageException("unknown option '${pair[0]}'; export-sheets reads ${EXPORT_FLAGS.sorted().joinToString()}")
        if (pair.size < 2) throw UsageException("${pair[0]} needs a value")
    }
    val values = pairs.associate { it[0] to it[1] }
    fun required(flag: String) = values[flag] ?: throw UsageException("$flag is required")
    return ExportOptions(Path.of(required("--system")), Path.of(required("--runtime")), Path.of(required("--out")))
}

private fun git(directory: Path, vararg arguments: String): String? =
    runCatching {
        val process = ProcessBuilder(listOf("git", "-C", directory.toString()) + arguments).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0) text else null
    }.getOrNull()

private const val PIN_FILE = "engine.pin"
private const val SHORT_SHA = 7

/** The engine the world is proven against, from engine.pin; the checkout the sheets were rendered with is added only when it is another one. */
internal fun pinText(pin: String?, checkout: String): String {
    if (pin == null) return "no $PIN_FILE beside the world file; rendered with we $checkout"
    val isSame = pin.startsWith(checkout) || checkout.startsWith(pin)
    return "we ${pin.take(SHORT_SHA)}" + if (isSame) "" else "; rendered with we $checkout"
}

internal fun enginePinText(system: Path, runtime: Path): String {
    val pinFile = system.toAbsolutePath().parent.resolve(PIN_FILE)
    val pin = pinFile.takeIf { Files.isRegularFile(it) }?.let { Files.readString(it).trim() }?.takeIf { it.isNotEmpty() }
    return pinText(pin, runtimePin(runtime))
}

/** The commit the config file is at, and a say when the file differs from it. */
internal fun configSource(system: Path): String {
    val directory = system.toAbsolutePath().parent
    val commit = git(directory, "rev-parse", "--short", "HEAD") ?: return "not in a git checkout"
    val changed = git(directory, "status", "--porcelain", "--", system.fileName.toString()).orEmpty().isNotEmpty()
    return if (changed) "$commit + uncommitted edits to ${system.fileName}" else commit
}

private fun unmeasuredNotes(geometry: Geometry): NoteGroup =
    NoteGroup("NOT MEASURED BY THE ENGINE", geometry.unmeasured.map { "${it.id}: ${it.field} is unknown (${it.why})" }.ifEmpty { listOf("none") })

private fun isAssumed(setting: DescribedSetting) = setting.reason?.contains("assum", ignoreCase = true) == true

private fun assumedNotes(described: List<DescribedSystem>): NoteGroup =
    NoteGroup(
        "ASSUMED SETTINGS",
        described.flatMap { system -> system.settings.filter(::isAssumed).map { "${system.id}.${it.id} = ${settingText(it)}: ${it.reason}" } }.ifEmpty { listOf("none") },
    )

private fun settingText(setting: DescribedSetting): String =
    (setting.value as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty() + (setting.unit?.let { " $it" } ?: "")

private fun slug(title: String) = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

internal class Drawn(val title: String, val shapes: List<Map<String, Any?>>)

private fun drawingsOf(geometry: Geometry, expansion: Expansion): List<Drawn> =
    listOf(Drawn("Plan", blueprintPlanShapes(geometry, expansion))) + elevationDrawings(geometry).map { Drawn(it.title, it.shapes) } +
        listOfNotNull(sectionDrawing(geometry)?.let { Drawn("Section through the middle of the depth", it.shapes) })

/** Every sheet, named by file stem: the design summary, the drawings, and, when the notes do not fit the column of a drawing, the sheets that continue them, which each drawing points to. */
internal fun buildSheets(drawings: List<Drawn>, notes: List<NoteGroup>, summary: List<NoteGroup>, meta: SheetMeta): List<Pair<String, Sheet>> {
    val plan = planNotes(notes)
    val continuation = if (plan.continued.isEmpty()) emptyList() else textSheets("Notes, continued", { page, pages -> "N-1 (page $page of $pages)" }, plan.continued, meta)
    val pointer = continuation.takeIf { it.isNotEmpty() }?.let { "Notes continue on sheet N-1, Notes, continued: ${it.size} page${if (it.size == 1) "" else "s"}." }
    val summaries = textSheets("Design summary", { page, pages -> "S-1 (page $page of $pages)" }, summary, meta).mapIndexed { index, sheet -> "S-1-design-summary-p${index + 1}" to sheet }
    val drawn = drawings.mapIndexed { index, item -> "A-${index + 1}-${slug(item.title)}" to sheetOf(item.title, "A-${index + 1} of ${drawings.size}", item.shapes, plan.onSheet, meta, pointer) }
    return summaries + drawn + continuation.mapIndexed { index, sheet -> "N-1-notes-continued-p${index + 1}" to sheet }
}

/** Writes each sheet as SVG and PDF under the out directory and returns the files, named by sheet number and title. */
fun exportSheets(options: ExportOptions): List<Path> {
    val loaded = readLoaded(loadSystem(options.runtime, options.system, null))
    val file = readSystemFile(Files.readString(options.system))
    val geometry = readGeometry(loaded.textOf("geometry"))
    val expansion = readExpansion(loaded.textOf("expansion"))
    val notes = listOf(unmeasuredNotes(geometry), assumedNotes(readDescribed(loaded.textOf("describe"))))
    val drawings = drawingsOf(geometry, expansion)
    val meta = SheetMeta(file.title, LocalDate.now().toString(), configSource(options.system), enginePinText(options.system, options.runtime), drawings.size)
    Files.createDirectories(options.out)
    return buildSheets(drawings, notes, summaryGroups(loaded, geometry), meta).flatMap { (stem, sheet) ->
        listOf(
            options.out.resolve("$stem.svg").also { Files.writeString(it, sheetSvg(sheet)) },
            options.out.resolve("$stem.pdf").also { Files.write(it, sheetPdf(sheet)) },
        )
    }
}
