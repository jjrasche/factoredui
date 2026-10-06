package ai.factoredui.worldengine

import ai.factoredui.worldengine.schema.EVENTS_SCHEMA_JSON
import ai.factoredui.worldengine.schema.VALIDATION_RULES_JSON
import ai.factoredui.worldengine.schema.WORLD_SCHEMA_JSON
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReferenceParityTest {
    private fun assertEmbeddedMatches(embedded: String, relative: String) {
        val file = ReferenceLocations.designDir.resolve(relative)
        assertTrue(Files.exists(file), "$file is missing, so nothing proves the embedded copy is current")
        assertEquals(
            Json.parseToJsonElement(Files.readString(file)),
            Json.parseToJsonElement(embedded),
            "$relative drifted from the reference; rerun scripts/embed_reference.py",
        )
    }

    @Test
    fun the_embedded_schemas_and_rules_are_the_reference_files() {
        assertEmbeddedMatches(WORLD_SCHEMA_JSON, "world.schema.json")
        assertEmbeddedMatches(EVENTS_SCHEMA_JSON, "events.schema.json")
        assertEmbeddedMatches(VALIDATION_RULES_JSON, "rules.json")
    }

    @Test
    fun the_embedded_worlds_demos_and_mutations_are_the_reference_files() {
        assertEmbeddedMatches(PARCEL_WORLD_JSON, "worlds/parcel-five-acre.world.json")
        assertEmbeddedMatches(LOCALITY_WORLD_JSON, "worlds/locality-stub.world.json")
        assertEmbeddedMatches(DUNGEON_WORLD_JSON, "worlds/dungeon-tiny.world.json")
        assertEmbeddedMatches(LIDAR_WORLD_JSON, "worlds/parcel-lidar-sample.world.json")
        assertEmbeddedMatches(GROUND_DEMO_WORLD_JSON, "worlds/parcel-ground-demo.world.json")
        assertEmbeddedMatches(PARCEL_DEMO_JSON, "demos/parcel-five-acre.demo.json")
        assertEmbeddedMatches(DUNGEON_DEMO_JSON, "demos/dungeon-tiny.demo.json")
        assertEmbeddedMatches(MUTATIONS_JSON, "mutations.json")
    }
}
