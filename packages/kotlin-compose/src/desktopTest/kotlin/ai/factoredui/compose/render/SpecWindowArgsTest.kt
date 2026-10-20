package ai.factoredui.compose.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpecWindowArgsTest {

    @Test
    fun aBareSourceUsesTheDefaultWindowSize() {
        assertEquals(WindowArgs("spec.json", 1280, 800, null), parseWindowArgs(arrayOf("spec.json")))
    }

    @Test
    fun widthAndHeightAreTheTwoNumbersAfterTheSource() {
        assertEquals(WindowArgs("spec.json", 1800, 1000, null), parseWindowArgs(arrayOf("spec.json", "1800", "1000")))
    }

    @Test
    fun aDataFlagNamesTheFileThatSeedsTheBindings() {
        val parsed = parseWindowArgs(arrayOf("spec.json", "1800", "1000", "--data", "graph.json"))
        assertEquals("graph.json", parsed?.dataPath)
    }

    @Test
    fun theDataFlagMayComeBeforeTheSizes() {
        val parsed = parseWindowArgs(arrayOf("spec.json", "--data", "graph.json", "900", "600"))
        assertEquals(WindowArgs("spec.json", 900, 600, "graph.json"), parsed)
    }

    @Test
    fun noArgumentsMeansNothingToOpen() {
        assertNull(parseWindowArgs(emptyArray()))
    }

    @Test
    fun aDataFlagWithoutAFileIsRefused() {
        assertNull(parseWindowArgs(arrayOf("spec.json", "--data")))
    }

    @Test
    fun aThemeFlagIsReadWithTheDataFlagInAnyOrder() {
        val parsed = parseWindowArgs(arrayOf("spec.json", "--theme", "light", "900", "--data", "graph.json", "600"))
        assertEquals(WindowArgs("spec.json", 900, 600, "graph.json", "light"), parsed)
    }

    @Test
    fun noThemeFlagLeavesTheThemeToTheSharedDefault() {
        assertNull(parseWindowArgs(arrayOf("spec.json"))?.theme)
    }

    @Test
    fun aThemeFlagWithoutAValueIsRefused() {
        assertNull(parseWindowArgs(arrayOf("spec.json", "--theme")))
    }

    @Test
    fun stateFlagsRepeatAndEachNamesAPathAKindAndAFile() {
        val parsed = parseWindowArgs(arrayOf("spec.json", "--state", "a.scene=text:s.json", "900", "--state", "reports=reports:r.json", "600"))
        assertEquals(
            WindowArgs("spec.json", 900, 600, null, stateFiles = listOf(StateFile("a.scene", StateFileKind.TEXT, "s.json"), StateFile("reports", StateFileKind.REPORTS, "r.json"))),
            parsed,
        )
    }

    @Test
    fun aStateFileMayLiveOnAWindowsDrive() {
        val parsed = parseWindowArgs(arrayOf("spec.json", "--state", "a=json:C:/x/y.json"))
        assertEquals(listOf(StateFile("a", StateFileKind.JSON, "C:/x/y.json")), parsed?.stateFiles)
    }

    @Test
    fun aStateFlagWithAnUnknownKindIsRefused() {
        assertNull(parseWindowArgs(arrayOf("spec.json", "--state", "a=yaml:s.yaml")))
    }

    @Test
    fun aStateFlagWithoutAPathIsRefused() {
        assertNull(parseWindowArgs(arrayOf("spec.json", "--state", "text:s.json")))
    }

    @Test
    fun aStateFlagWithoutAValueIsRefused() {
        assertNull(parseWindowArgs(arrayOf("spec.json", "--state")))
    }

    @Test
    fun aControlFlagNamesTheLoopbackPort() {
        assertEquals(47110, parseWindowArgs(arrayOf("spec.json", "--control", "47110"))?.controlPort)
    }

    @Test
    fun aControlFlagThatIsNotAPortIsRefused() {
        assertNull(parseWindowArgs(arrayOf("spec.json", "--control", "high")))
    }

    @Test
    fun aTitleFlagNamesTheWindowAndWithoutOneTheTitleIsLeftToTheSpec() {
        assertEquals("Board review", parseWindowArgs(arrayOf("spec.json", "--title", "Board review"))?.title)
        assertNull(parseWindowArgs(arrayOf("spec.json"))?.title)
        assertNull(parseWindowArgs(arrayOf("spec.json", "--title")))
    }

    @Test
    fun aSectionFlagNamesTheTopLevelKeyWhoseSpecIsDrawn() {
        assertEquals("screen", parseWindowArgs(arrayOf("ui.json", "--section", "screen"))?.section)
        assertNull(parseWindowArgs(arrayOf("ui.json"))?.section)
        assertNull(parseWindowArgs(arrayOf("ui.json", "--section")))
    }

    @Test
    fun theWindowReadsTheSpecFromTheNamedSectionAndRefusesAMissingOneNamingTheKeys() {
        val file = """{"screen":{"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"column"}},"types":{}}"""
        assertEquals("page", windowSpecOf(file, "screen").root.id)
        assertEquals(
            "the file has no section 'scene'; its top-level keys are screen, types",
            kotlin.test.assertFailsWith<ai.factoredui.compose.schema.SpecSectionException> { windowSpecOf(file, "scene") }.message,
        )
    }

    @Test
    fun theWindowTitleIsTheTitleFlagOrNamesTheSpecRoot() {
        assertEquals("Board review", windowTitle("Board review", "root"))
        assertEquals("factoredui — root", windowTitle(null, "root"))
    }
}
