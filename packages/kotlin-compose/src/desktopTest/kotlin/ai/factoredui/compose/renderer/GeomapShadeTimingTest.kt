package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.resolveGeomapLayers
import ai.factoredui.compose.score.ScoreShade
import ai.factoredui.compose.score.ScoreShadeReading
import ai.factoredui.compose.score.readScoreShade
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val PARCEL_COLUMNS = 113
private const val PARCEL_ROWS = 112
private const val PARCEL_COUNT = PARCEL_COLUMNS * PARCEL_ROWS - 4
private const val FACTOR_COUNT = 6
private const val PASSES = 50
private const val RECOLOUR_BUDGET_MS = 50.0

private val RAMP = mapOf("stops" to listOf(listOf(0, "#2E86DE"), listOf(1, "#E67E22")), "no_figure" to "#FF00FF")

private fun factorValue(parcel: Int, factor: Int): Double = ((parcel * 31 + factor * 17) % 100) / 100.0

private fun parcelJson(column: Int, row: Int): String {
    val west = -85.9 + column * 0.0009
    val south = 42.5 + row * 0.0007
    val values = (0 until FACTOR_COUNT).joinToString(",") { """"f$it":${factorValue(row * PARCEL_COLUMNS + column, it)}""" }
    return """{"id":"p${column}_$row","values":{$values},"rings":[[[$west,$south],[${west + 0.0007},$south],[${west + 0.0007},${south + 0.0005}],[$west,${south + 0.0005}]]]}"""
}

private fun parcelFeaturesJson(): String =
    (0 until PARCEL_ROWS).flatMap { row -> (0 until PARCEL_COLUMNS).map { column -> row to column } }
        .take(PARCEL_COUNT).joinToString(",") { (row, column) -> parcelJson(column, row) }

private fun weightsOf(emphasis: Int): Map<String, Any?> = (0 until FACTOR_COUNT).associate { "f$it" to (if (it == emphasis) 5.0 else 1.0) }

private fun shadeOf(emphasis: Int): ScoreShade =
    (readScoreShade("shade_by", mapOf("weights" to weightsOf(emphasis), "ramp" to RAMP, "minimum" to 0.3)) as ScoreShadeReading.Readable).shade

private fun nanosToMillis(nanos: Long): Double = nanos / 1_000_000.0

class GeomapShadeTimingTest {
    private val features = parcelFeaturesJson()

    @Test
    fun recolouring_twelve_thousand_parcels_by_the_weighted_score_is_far_inside_a_frame() {
        val layers = resolveGeomapLayers(json.parseToJsonElement("""[{"id":"parcels","kind":"fill","features":[$features]}]""").let { element ->
            (element as kotlinx.serialization.json.JsonArray).map { it.toPlainMap() }
        })
        val tessellationStart = System.nanoTime()
        val tessellation = tessellateGeomapLayers(layers)
        val tessellationMillis = nanosToMillis(System.nanoTime() - tessellationStart)
        val parcels = tessellation.layers.single().features
        assertEquals(PARCEL_COUNT, parcels.size)

        val shades = (0 until FACTOR_COUNT).map(::shadeOf)
        var drawn = 0
        val start = System.nanoTime()
        repeat(PASSES) { pass ->
            val shade = shades[pass % FACTOR_COUNT]
            drawn = parcels.count { featureFillArgb(it, shade) != null }
        }
        val perPassMillis = nanosToMillis(System.nanoTime() - start) / PASSES
        println("GEOMAP-SHADE-TIMING parcels=$PARCEL_COUNT factors=$FACTOR_COUNT tessellate_ms=${"%.1f".format(tessellationMillis)} recolour_ms_per_pass=${"%.2f".format(perPassMillis)} drawn_last_pass=$drawn")
        assertTrue(perPassMillis < RECOLOUR_BUDGET_MS, "recolouring $PARCEL_COUNT parcels took $perPassMillis ms a pass")
    }

    @Test
    fun a_frame_of_the_whole_county_draws_after_a_weight_change_in_the_time_it_prints() {
        val spec = json.decodeFromString(
            Spec.serializer(),
            """{"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{"shade_by":"{map.shade}","layers":[{"id":"parcels","kind":"fill","features":[$features]}]}}}""",
        )
        driveScreen(spec, widthDp = 390, heightDp = 700).use { screen ->
            screen.type("map.shade", mapOf("weights" to weightsOf(0), "ramp" to RAMP))
            val first = System.nanoTime().also { screen.frame() }
            val firstMillis = nanosToMillis(System.nanoTime() - first)
            val runsBefore = geomapTessellationRuns
            val changeStart = System.nanoTime()
            screen.type("map.shade", mapOf("weights" to weightsOf(3), "ramp" to RAMP, "minimum" to 0.3))
            screen.frame()
            val changeMillis = nanosToMillis(System.nanoTime() - changeStart)
            println("GEOMAP-SHADE-FRAME first_frame_ms=${"%.0f".format(firstMillis)} after_weight_change_ms=${"%.0f".format(changeMillis)} (desktop JVM, includes PNG encode)")
            assertEquals(runsBefore, geomapTessellationRuns, "the weight change tessellated again")
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.toPlainMap(): Any? = when (this) {
    is kotlinx.serialization.json.JsonObject -> mapValues { it.value.toPlainMap() }
    is kotlinx.serialization.json.JsonArray -> map { it.toPlainMap() }
    is kotlinx.serialization.json.JsonNull -> null
    is kotlinx.serialization.json.JsonPrimitive -> if (isString) content else content.toBooleanStrictOrNull() ?: content.toDouble()
}
