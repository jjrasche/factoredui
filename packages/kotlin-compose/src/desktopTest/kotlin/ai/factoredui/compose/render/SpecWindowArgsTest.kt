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
}
