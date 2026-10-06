package ai.factoredui.worldengine

import ai.factoredui.worldengine.expression.planarDistanceMm
import ai.factoredui.worldengine.world.NumberExponentTooLarge
import ai.factoredui.worldengine.world.findOversizedNumber
import ai.factoredui.worldengine.world.parseBoundedJson
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BoundedJsonTest {

    @Test
    fun aNumberAnywhereInTheDocumentIsCheckedAndTheFirstOversizedOneIsNamed() {
        val text = """{"a": [1, {"b": 2e500}], "c": 3e600}"""
        assertEquals("number-exponent-too-large: 2e500 has a decimal exponent beyond ±400", findOversizedNumber(Json.parseToJsonElement(text)))
        assertFailsWith<NumberExponentTooLarge> { parseBoundedJson(text) }
    }

    @Test
    fun aStringOrABooleanThatLooksLikeAnOversizedNumberIsNotANumber() {
        assertNull(findOversizedNumber(Json.parseToJsonElement("""{"a": "1e999", "b": true, "c": null, "d": 1e400}""")))
    }

    @Test
    fun aDocumentWithinTheBoundParsesUnchanged() {
        assertEquals(Json.parseToJsonElement("""{"a": [1.5, -2e-400]}"""), parseBoundedJson("""{"a": [1.5, -2e-400]}"""))
    }

    @Test
    fun distanceIsFloat64ArithmeticAndNotALibraryHypot() {
        assertEquals(1.0, planarDistanceMm(1.0, 0.0))
        assertEquals(5.0, planarDistanceMm(3.0, 4.0))
        assertEquals(0.0, planarDistanceMm(0.0, 0.0))
        assertEquals(Double.POSITIVE_INFINITY, planarDistanceMm(1e200, 0.0))
    }
}
