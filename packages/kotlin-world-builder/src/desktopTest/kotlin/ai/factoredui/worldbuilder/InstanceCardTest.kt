package ai.factoredui.worldbuilder

import ai.factoredui.worldengine.state.InstanceRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun number(value: Double) = JsonPrimitive(value)

private fun text(value: String) = JsonPrimitive(value)

private fun objectOf(vararg fields: Pair<String, kotlinx.serialization.json.JsonElement>) = JsonObject(mapOf(*fields))

private fun measuredTree(crown: kotlinx.serialization.json.JsonElement = number(6000.0), error: kotlinx.serialization.json.JsonElement = JsonNull) = InstanceRecord(
    id = "tree-01",
    type = "lidar_tree",
    xMm = number(12147.0),
    yMm = number(238379.0),
    zMm = number(0.0),
    rotationDeg = number(0.0),
    heightMm = number(29650.0),
    crownRadiusMm = crown,
    provenance = "measured",
    source = objectOf("file" to text("plot-twin/receipts/trees.json#/trees/0"), "tag" to text("USGS 3DEP QL2")),
    error = error,
)

class InstanceCardTest {

    @Test
    fun theTitleNamesTheTypeTheIdAndHowItWasMeasured() {
        assertEquals("Lidar tree tree-01 (measured)", instanceTitle(measuredTree(), "Lidar tree"))
    }

    @Test
    fun millimetresReadAsMetresWithTwoPlaces() {
        assertEquals("12.15 m", metres(number(12147.0)))
        assertEquals("29.65 m", metres(number(29650.0)))
    }

    @Test
    fun aMissingFigureIsNotRecordedNeverZero() {
        assertEquals("not recorded", metres(JsonNull))
    }

    @Test
    fun theCardGivesPositionHeightCrownAndSource() {
        val lines = instanceCardLines(measuredTree())
        assertEquals("Position: 12.15 m east, 238.38 m north of the south-west corner", lines[0])
        assertEquals("Height: 29.65 m", lines[1])
        assertEquals("Crown radius: 6 m", lines[2])
        assertEquals("Source: file plot-twin/receipts/trees.json#/trees/0; tag USGS 3DEP QL2", lines[3])
    }

    @Test
    fun anErrorFigureWithoutAValueIsShownAsNotMeasuredWithItsReason() {
        val error = objectOf("position_mm" to objectOf("value" to JsonNull, "null_reason" to text("no detection error has been measured")))
        val lines = instanceCardLines(measuredTree(error = error))
        assertTrue("Position error: not measured (no detection error has been measured)" in lines, lines.toString())
        assertTrue("Height error: not measured" in lines, "a figure the record does not carry is not measured: $lines")
    }

    @Test
    fun aMeasuredErrorShowsInMetres() {
        val error = objectOf("height_mm" to objectOf("value" to number(500.0)))
        assertTrue("Height error: plus or minus 0.5 m" in instanceCardLines(measuredTree(error = error)))
    }

    @Test
    fun aMissingCrownRadiusSaysNotRecorded() {
        assertEquals("Crown radius: not recorded", instanceCardLines(measuredTree(crown = JsonNull))[2])
    }

    @Test
    fun aLongSourceTagIsShortened() {
        val longTag = "x".repeat(400)
        val record = measuredTree().copy(source = objectOf("tag" to text(longTag)))
        val source = instanceCardLines(record).first { it.startsWith("Source:") }
        assertTrue(source.length < 200 && source.endsWith("..."), source)
    }

    @Test
    fun aProposedInstanceSaysThereIsNoMeasurementBehindIt() {
        val proposed = measuredTree().copy(provenance = "proposed", source = JsonNull, error = JsonNull)
        val lines = instanceCardLines(proposed)
        assertEquals("Proposed on this branch; no measurement behind it.", lines.last())
        assertTrue(lines.none { it.startsWith("Source:") || it.contains("error") })
    }
}
