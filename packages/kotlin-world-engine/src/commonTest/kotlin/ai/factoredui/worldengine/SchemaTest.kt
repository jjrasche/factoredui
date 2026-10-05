package ai.factoredui.worldengine

import ai.factoredui.worldengine.schema.schemaErrors
import ai.factoredui.worldengine.world.WorldLoadException
import ai.factoredui.worldengine.world.WorldLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchemaTest {
    private val dungeon: JsonObject = Json.parseToJsonElement(DUNGEON_WORLD_JSON).jsonObject

    private fun errorsAfter(edit: (JsonObject) -> JsonObject): List<String> = schemaErrors(edit(dungeon), WorldLoader.worldSchema)

    private fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(this + (key to value))

    private fun JsonObject.withGrid(key: String, value: JsonElement): JsonObject = with("grid", getValue("grid").jsonObject.with(key, value))

    @Test
    fun the_shipped_worlds_match_the_schema() {
        REFERENCE_WORLD_FILES.values.forEach { assertEquals(emptyList(), schemaErrors(Json.parseToJsonElement(it), WorldLoader.worldSchema)) }
    }

    @Test
    fun counts_and_bounds_are_enforced() {
        assertEquals(listOf("$.object_types: needs at least 1 items"), errorsAfter { it.with("object_types", JsonArray(emptyList())) })
        assertEquals(listOf("$.grid.rows: above 512"), errorsAfter { it.withGrid("rows", JsonPrimitive(513)) })
        assertEquals(listOf("$.grid.tile_ft: below 0.1"), errorsAfter { it.withGrid("tile_ft", JsonPrimitive(0.05)) })
    }

    @Test
    fun an_integer_field_refuses_text_and_a_float() {
        assertEquals(listOf("$.grid.cols: expected integer"), errorsAfter { it.withGrid("cols", JsonPrimitive("twelve")) })
        assertEquals(listOf("$.grid.cols: expected integer"), errorsAfter { it.withGrid("cols", JsonPrimitive(12.0)) })
    }

    @Test
    fun enums_patterns_and_closed_objects_are_enforced() {
        assertEquals(listOf("$.grid.shape: 'hex' is not one of ['square']"), errorsAfter { it.withGrid("shape", JsonPrimitive("hex")) })
        val recoloured = { document: JsonObject ->
            val types = document.getValue("object_types").jsonArray
            document.with("object_types", JsonArray(listOf(types[0].jsonObject.with("color", JsonPrimitive("red"))) + types.drop(1)))
        }
        assertEquals(listOf("$.object_types[0].color: does not match ^#[0-9A-Fa-f]{6}$"), errorsAfter(recoloured))
        assertEquals(listOf("$: unexpected 'extra'"), errorsAfter { it.with("extra", JsonPrimitive(1)) })
        assertEquals(listOf("$: missing 'seed'"), errorsAfter { JsonObject(it - "seed") })
    }

    @Test
    fun a_rule_needs_a_require_or_an_effect() {
        val stripped = { document: JsonObject ->
            val rules = document.getValue("rules").jsonArray
            document.with("rules", JsonArray(listOf(JsonObject(rules[0].jsonObject - "require")) + rules.drop(1)))
        }
        assertEquals(listOf("$.rules[0]: matches none of its allowed shapes"), errorsAfter(stripped))
    }

    @Test
    fun a_world_that_breaks_the_schema_does_not_load_and_says_where() {
        val broken = dungeon.withGrid("cols", JsonPrimitive("twelve")).toString()
        val refused = assertFailsWith<WorldLoadException> { WorldLoader.loadFromJson(broken, DUNGEON_FILE) }
        assertEquals("dungeon-tiny.world.json does not match world.schema.json: ['$.grid.cols: expected integer']", refused.message)
    }
}
