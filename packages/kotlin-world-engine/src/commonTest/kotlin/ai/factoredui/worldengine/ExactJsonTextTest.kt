package ai.factoredui.worldengine

import ai.factoredui.worldengine.json.exactJsonText
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExactJsonTextTest {

    private val written = """{"a": [1e400, 7620.00000000000000001, "x\"y", null, true, -0.5e-400], "b": {"c": "é"}}"""

    @Test
    fun everyNumberKeepsTheDigitsItWasWrittenWith() {
        val text = exactJsonText(Json.parseToJsonElement(written))
        assertTrue("1e400" in text && "7620.00000000000000001" in text && "-0.5e-400" in text, text)
    }

    @Test
    fun theTextReadsBackAsTheSameDocument() {
        val document = Json.parseToJsonElement(written)
        assertEquals(document, Json.parseToJsonElement(exactJsonText(document)))
    }

    @Test
    fun aNumberLongerThanAnyDoubleSurvivesWhereTheLibraryEncoderWouldFail() {
        val longInteger = "1" + "0".repeat(401)
        assertEquals("[$longInteger]", exactJsonText(Json.parseToJsonElement("[$longInteger]")))
    }
}
