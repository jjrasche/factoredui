package ai.factoredui.compose.tokens

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val VALUE = "\$value"
private const val TYPE = "\$type"

private fun colourValue(hex: String): JsonObject {
    val parts = hex.removePrefix("#").chunked(2).map { it.toInt(16) / 255.0 }
    return JsonObject(mapOf("colorSpace" to JsonPrimitive("srgb"), "components" to JsonArray(parts.map { JsonPrimitive(it) }), "hex" to JsonPrimitive(hex)))
}

private fun dimensionValue(value: Double, unit: String): JsonObject = JsonObject(mapOf("value" to JsonPrimitive(value), "unit" to JsonPrimitive(unit)))

private fun leaf(type: String?, value: JsonElement): JsonObject = JsonObject(listOfNotNull(type?.let { TYPE to JsonPrimitive(it) }, VALUE to value).toMap())

private fun colour(hex: String) = leaf("color", colourValue(hex))

private fun nested(path: String, inner: JsonObject): JsonObject = path.split('.').reversed().fold(inner) { held, name -> JsonObject(mapOf(name to held)) }

private fun merged(first: JsonObject, second: JsonObject): JsonObject =
    JsonObject((first.keys + second.keys).associateWith { key ->
        val a = first[key]
        val b = second[key]
        if (a is JsonObject && b is JsonObject && VALUE !in a && VALUE !in b) merged(a, b) else b ?: a!!
    })

private fun fileOf(mode: String, vararg tokens: Pair<String, JsonObject>): String {
    val body = tokens.map { (path, token) -> nested(path, token) }.fold(JsonObject(emptyMap())) { all, next -> merged(all, next) }
    val extensions = JsonObject(mapOf("ai.factoredui" to JsonObject(mapOf("mode" to JsonPrimitive(mode)))))
    return JsonObject(body + ("\$extensions" to extensions)).toString()
}

private val DARK = defaultTokens("dark")

class ThemeLoadTest {
    @Test
    fun the_default_dark_theme_written_as_a_dtcg_file_reads_back_as_the_same_tokens_with_no_notice() {
        val read = readTokenFile(writeTokenFile(DARK, "dark"))
        assertEquals(DARK, read.tokens)
        assertEquals("dark", read.mode)
        assertEquals(emptyList(), read.notices)
    }

    @Test
    fun the_default_light_theme_round_trips_too() {
        val light = defaultTokens("light")
        assertEquals(light, readTokenFile(writeTokenFile(light, "light")).tokens)
    }

    @Test
    fun a_user_file_that_changes_one_token_changes_only_that_token() {
        val loaded = applyUserTokens("dark", fileOf("dark", "color.accent" to colour("#ff8a4c")))
        assertEquals(ColorValue(0xFFFF8A4C), loaded.tokens.getValue("color.accent"))
        assertEquals(DARK.getValue("color.ground"), loaded.tokens.getValue("color.ground"))
        assertEquals(emptyList(), loaded.notices)
    }

    @Test
    fun a_color_written_as_a_bare_hex_string_in_the_older_style_is_read() {
        val loaded = applyUserTokens("dark", fileOf("dark", "color.accent" to leaf("color", JsonPrimitive("#ff8a4c"))))
        assertEquals(ColorValue(0xFFFF8A4C), loaded.tokens.getValue("color.accent"))
    }

    @Test
    fun an_alias_resolves_to_the_token_it_names_and_a_chain_of_aliases_resolves_through() {
        val file = fileOf("dark", "palette.orange" to colour("#ff8a4c"), "palette.hot" to leaf("color", JsonPrimitive("{palette.orange}")), "color.accent" to leaf("color", JsonPrimitive("{palette.hot}")))
        assertEquals(ColorValue(0xFFFF8A4C), applyUserTokens("dark", file).tokens.getValue("color.accent"))
    }

    @Test
    fun a_circular_alias_is_a_notice_naming_the_token_and_the_default_is_kept() {
        val file = fileOf("dark", "color.accent" to leaf("color", JsonPrimitive("{color.muted}")), "color.muted" to leaf("color", JsonPrimitive("{color.accent}")))
        val loaded = applyUserTokens("dark", file)
        assertEquals(DARK.getValue("color.accent"), loaded.tokens.getValue("color.accent"))
        assertTrue(loaded.notices.any { it.startsWith("token color.accent:") && it.contains("circular") }, loaded.notices.toString())
    }

    @Test
    fun a_type_set_on_a_group_is_inherited_by_the_tokens_in_it() {
        val group = JsonObject(mapOf(TYPE to JsonPrimitive("dimension"), "lg" to leaf(null, dimensionValue(20.0, "px"))))
        val file = JsonObject(mapOf("dimension" to JsonObject(mapOf("space" to group)))).toString()
        assertEquals(DimensionValue(20.0), applyUserTokens("dark", file).tokens.getValue("dimension.space.lg"))
    }

    @Test
    fun a_dimension_in_rem_is_16_px_to_the_rem() {
        val file = fileOf("dark", "dimension.space.lg" to leaf("dimension", dimensionValue(1.5, "rem")))
        assertEquals(DimensionValue(24.0), applyUserTokens("dark", file).tokens.getValue("dimension.space.lg"))
    }

    @Test
    fun a_value_that_does_not_match_its_type_keeps_the_default_and_says_why() {
        val file = fileOf("dark", "color.accent" to leaf("color", JsonObject(mapOf("colorSpace" to JsonPrimitive("srgb")))))
        val loaded = applyUserTokens("dark", file)
        assertEquals(DARK.getValue("color.accent"), loaded.tokens.getValue("color.accent"))
        assertTrue(loaded.notices.any { it.startsWith("token color.accent:") && it.contains("components") }, loaded.notices.toString())
    }

    @Test
    fun a_token_with_no_type_anywhere_is_invalid_because_a_type_is_never_guessed_from_the_value() {
        val loaded = applyUserTokens("dark", fileOf("dark", "color.accent" to leaf(null, JsonPrimitive("#ff8a4c"))))
        assertEquals(DARK.getValue("color.accent"), loaded.tokens.getValue("color.accent"))
        assertTrue(loaded.notices.any { it.startsWith("token color.accent:") && it.contains("type") }, loaded.notices.toString())
    }

    @Test
    fun a_colour_space_the_renderer_does_not_read_uses_the_hex_fallback_or_is_refused() {
        fun oklch(extra: Map<String, JsonElement>) = leaf("color", JsonObject(mapOf("colorSpace" to JsonPrimitive("oklch"), "components" to JsonArray(listOf(0.7, 0.1, 40).map { JsonPrimitive(it) })) + extra))
        val withHex = applyUserTokens("dark", fileOf("dark", "color.accent" to oklch(mapOf("hex" to JsonPrimitive("#ff8a4c")))))
        assertEquals(ColorValue(0xFFFF8A4C), withHex.tokens.getValue("color.accent"))
        val without = applyUserTokens("dark", fileOf("dark", "color.accent" to oklch(emptyMap())))
        assertEquals(DARK.getValue("color.accent"), without.tokens.getValue("color.accent"))
        assertTrue(without.notices.any { it.contains("oklch") }, without.notices.toString())
    }

    @Test
    fun a_colour_that_is_not_opaque_is_refused_because_contrast_cannot_be_judged_through_it() {
        val translucent = leaf("color", JsonObject(colourValue("#ff8a4c") + ("alpha" to JsonPrimitive(0.5))))
        val loaded = applyUserTokens("dark", fileOf("dark", "color.accent" to translucent))
        assertEquals(DARK.getValue("color.accent"), loaded.tokens.getValue("color.accent"))
        assertTrue(loaded.notices.any { it.startsWith("token color.accent:") && it.contains("opaque") }, loaded.notices.toString())
    }

    @Test
    fun a_text_colour_below_aa_on_the_ground_keeps_the_default_and_says_the_ratio() {
        val loaded = applyUserTokens("dark", fileOf("dark", "color.muted" to colour("#1a2233")))
        assertEquals(DARK.getValue("color.muted"), loaded.tokens.getValue("color.muted"))
        assertTrue(loaded.notices.any { it.startsWith("token color.muted:") && it.contains("4.5:1") }, loaded.notices.toString())
    }

    @Test
    fun a_ground_that_makes_the_default_text_unreadable_is_refused_not_the_text() {
        val loaded = applyUserTokens("dark", fileOf("dark", "color.ground" to colour("#e8eaee")))
        assertEquals(DARK.getValue("color.ground"), loaded.tokens.getValue("color.ground"))
        assertEquals(DARK.getValue("color.ink"), loaded.tokens.getValue("color.ink"))
        assertTrue(loaded.notices.any { it.startsWith("token color.ground:") }, loaded.notices.toString())
    }

    @Test
    fun a_user_ground_with_its_own_readable_ink_is_taken_whole() {
        val light = defaultTokens("light")
        val tokens = listOf("color.ground", "color.ink", "color.muted", "color.accent", "color.on-accent", "color.status.pass", "color.status.warn", "color.status.fail") + (1..8).map { "color.series.$it" }
        val file = fileOf("dark", *tokens.map { it to colour("#" + (light.getValue(it) as ColorValue).argb.toString(16).takeLast(6)) }.toTypedArray())
        val loaded = applyUserTokens("dark", file)
        assertEquals(light.getValue("color.ground"), loaded.tokens.getValue("color.ground"))
        assertEquals(emptyList(), loaded.notices)
    }

    @Test
    fun a_series_colour_below_3_to_1_on_the_ground_keeps_the_default() {
        val loaded = applyUserTokens("dark", fileOf("dark", "color.series.2" to colour("#0d1424")))
        assertEquals(DARK.getValue("color.series.2"), loaded.tokens.getValue("color.series.2"))
        assertTrue(loaded.notices.any { it.startsWith("token color.series.2:") && it.contains("3:1") }, loaded.notices.toString())
    }

    @Test
    fun a_token_the_renderer_does_not_use_is_named_and_left_out() {
        val loaded = applyUserTokens("dark", fileOf("dark", "brand.logo" to colour("#ff0000")))
        assertEquals(DARK, loaded.tokens)
        assertTrue(loaded.notices.contains("token brand.logo is not used by this renderer; it is ignored."), loaded.notices.toString())
    }

    @Test
    fun a_file_that_is_not_json_leaves_the_default_theme_whole() {
        val loaded = applyUserTokens("dark", "not json")
        assertEquals(DARK, loaded.tokens)
        assertTrue(loaded.notices.single().startsWith("the theme file is not valid JSON:"), loaded.notices.toString())
    }

    @Test
    fun a_file_made_for_the_other_mode_is_not_applied_to_this_one() {
        val loaded = applyUserTokens("dark", fileOf("light", "color.accent" to colour("#ff8a4c")))
        assertEquals(DARK, loaded.tokens)
        assertEquals(listOf("the theme file is for the light theme; the dark theme is used."), loaded.notices)
    }

    @Test
    fun a_typography_token_sets_size_weight_and_a_line_height_that_is_a_multiplier() {
        val value = JsonObject(
            mapOf(
                "fontFamily" to JsonPrimitive("sans-serif"), "fontSize" to dimensionValue(18.0, "px"), "fontWeight" to JsonPrimitive("bold"),
                "letterSpacing" to dimensionValue(0.0, "px"), "lineHeight" to JsonPrimitive(1.5),
            ),
        )
        val typed = applyUserTokens("dark", fileOf("dark", "type.body" to leaf("typography", value))).tokens.getValue("type.body") as TypographyValue
        assertEquals(listOf(18.0, 700, 27.0), listOf(typed.sizePx, typed.weight, typed.sizePx * typed.lineMultiplier))
    }

    @Test
    fun every_default_text_pairing_passes_aa_so_the_default_never_needs_a_notice() {
        listOf("dark", "light").forEach { mode -> assertEquals(emptyList(), contrastFailures(defaultTokens(mode)), mode) }
    }
}
