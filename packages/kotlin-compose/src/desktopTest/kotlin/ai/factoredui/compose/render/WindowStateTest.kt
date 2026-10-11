package ai.factoredui.compose.render

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

private const val REPORTS_ONE = """{ "reports": [ { "id": "cost", "label": "Cost", "columns": [], "rows": [ { "id": "r1", "label": "one", "object_ids": ["p1"], "values": { "usd": 12.5, "unknown": null } } ] } ] }"""
private const val REPORTS_TWO = """{ "reports": [ { "id": "compare", "label": "Compare", "columns": [], "rows": [] } ] }"""

class WindowStateTest {

    private val folder: File = createTempDirectory("window-state").toFile()

    private fun fileHolding(name: String, text: String): String = File(folder, name).apply { writeText(text) }.path

    @Test
    fun aTextFileIsBoundAsItsOwnTextUndecoded() {
        val text = """{ "version": 12345678901234567890 }"""
        val state = windowState(emptyMap(), listOf(StateFile("board.payload", StateFileKind.TEXT, fileHolding("p.json", text))))
        assertEquals(text, (state["board"] as Map<*, *>)["payload"])
    }

    @Test
    fun aTextFileJoinsTheSeedsUnderTheSameParentRatherThanReplacingThem() {
        val state = windowState(mapOf("board" to mapOf("view" to "plan")), listOf(StateFile("board.payload", StateFileKind.TEXT, fileHolding("p.json", "{}"))))
        assertEquals(mapOf("view" to "plan", "payload" to "{}"), state["board"])
    }

    @Test
    fun aJsonFileIsDecodedIntoMapsListsAndScalars() {
        val state = windowState(emptyMap(), listOf(StateFile("cfg", StateFileKind.JSON, fileHolding("c.json", """{ "on": true, "ids": ["a"], "n": null }"""))))
        assertEquals(mapOf("on" to true, "ids" to listOf("a"), "n" to null), state["cfg"])
    }

    @Test
    fun reportsFilesKeyEachReportByIdAndTwoFilesShareOneParent() {
        val state = windowState(
            emptyMap(),
            listOf(
                StateFile("reports", StateFileKind.REPORTS, fileHolding("one.json", REPORTS_ONE)),
                StateFile("reports", StateFileKind.REPORTS, fileHolding("two.json", REPORTS_TWO)),
            ),
        )
        val reports = state["reports"] as Map<*, *>
        assertEquals(setOf("cost", "compare"), reports.keys)
        val row = ((reports["cost"] as Map<*, *>)["rows"] as List<*>).single() as Map<*, *>
        assertEquals(listOf("p1"), row["object_ids"])
        assertEquals(mapOf("usd" to 12.5, "unknown" to null), row["values"])
    }
}
