package ai.factoredui.compose.schema

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

class SpecStateTest {
    @Test
    fun a_spec_may_carry_the_values_its_bound_paths_start_at() {
        val spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"state":{"day.from":2757,"view":"day"},"root":{"id":"n","type":"text"}}""")
        assertEquals(mapOf("day.from" to SpecValue.NumberValue(2757.0), "view" to SpecValue.StringValue("day")), spec.state)
    }

    @Test
    fun a_spec_without_state_starts_nothing() {
        val spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":{"id":"n","type":"text"}}""")
        assertEquals(emptyMap(), spec.state)
    }
}
