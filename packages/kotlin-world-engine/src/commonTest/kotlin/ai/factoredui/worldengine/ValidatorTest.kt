package ai.factoredui.worldengine

import ai.factoredui.worldengine.validate.ValidationReport
import ai.factoredui.worldengine.validate.WorldValidator
import ai.factoredui.worldengine.validate.applyMutation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidatorTest {
    private val mutations: List<JsonObject> = Json.parseToJsonElement(MUTATIONS_JSON).jsonObject.getValue("mutations").jsonArray.map { it.jsonObject }

    private fun trippedRules(report: ValidationReport): Set<String> = report.fired.map { it.rule }.toSet() + report.blind.map { it.rule }

    private fun assertEveryBrokenCopyTrips(rule: String) {
        val entries = mutations.filter { it.getValue("rule").jsonPrimitive.content == rule }
        assertTrue(entries.isNotEmpty(), "mutations.json has no broken copy for $rule")
        entries.forEach { mutation ->
            val report = WorldValidator.validate(applyMutation(REFERENCE_WORLD_FILES, mutation))
            assertTrue(rule in trippedRules(report), "$rule did not fire; fired ${trippedRules(report)}")
            val world = mutation["world"]?.takeIf { it is JsonPrimitive && it.isString }?.jsonPrimitive?.content
            if (world != null) assertTrue(world in report.invalid, "$world stayed valid under the $rule copy")
        }
    }

    @Test
    fun the_shipped_worlds_are_all_valid_and_nothing_fires() {
        val report = WorldValidator.validate(REFERENCE_WORLD_FILES)
        assertEquals(emptyList(), report.fired)
        assertEquals(emptyList(), report.blind)
        assertEquals(listOf(DUNGEON_FILE, LOCALITY_FILE, PARCEL_FILE, GROUND_DEMO_FILE, LIDAR_FILE), report.valid.sorted())
    }

    @Test
    fun every_rule_in_rules_json_has_a_broken_copy_and_a_test() {
        val ruleIds = WorldValidator.rules.map { it.getValue("id").jsonPrimitive.content }.toSet()
        assertEquals(ruleIds, mutations.map { it.getValue("rule").jsonPrimitive.content }.toSet())
        assertEquals(ruleIds, RULES_WITH_A_TEST)
    }

    @Test fun broken_copy_number_exponent_too_large() = assertEveryBrokenCopyTrips("number-exponent-too-large")
    @Test fun broken_copy_footprint_too_large() = assertEveryBrokenCopyTrips("footprint-too-large")
    @Test fun broken_copy_blind_worlds() = assertEveryBrokenCopyTrips("blind-worlds")
    @Test fun broken_copy_world_parses() = assertEveryBrokenCopyTrips("world-parses")
    @Test fun broken_copy_world_schema() = assertEveryBrokenCopyTrips("world-schema")
    @Test fun broken_copy_expression_parses() = assertEveryBrokenCopyTrips("expression-parses")
    @Test fun broken_copy_unknown_word() = assertEveryBrokenCopyTrips("unknown-word")
    @Test fun broken_copy_unit_mismatch() = assertEveryBrokenCopyTrips("unit-mismatch")
    @Test fun broken_copy_expression_bound() = assertEveryBrokenCopyTrips("expression-bound")
    @Test fun broken_copy_name_cycle() = assertEveryBrokenCopyTrips("name-cycle")
    @Test fun broken_copy_seed_replays() = assertEveryBrokenCopyTrips("seed-replays")
    @Test fun broken_copy_rule_message() = assertEveryBrokenCopyTrips("rule-message")
    @Test fun broken_copy_unknown_target() = assertEveryBrokenCopyTrips("unknown-target")
    @Test fun broken_copy_action_emits() = assertEveryBrokenCopyTrips("action-emits")
    @Test fun broken_copy_link_resolves() = assertEveryBrokenCopyTrips("link-resolves")
    @Test fun broken_copy_link_fits() = assertEveryBrokenCopyTrips("link-fits")
    @Test fun broken_copy_projection_not_binding() = assertEveryBrokenCopyTrips("projection-not-binding")
    @Test fun broken_copy_figure_sourced() = assertEveryBrokenCopyTrips("figure-sourced")
    @Test fun broken_copy_sprite_known() = assertEveryBrokenCopyTrips("sprite-known")
    @Test fun broken_copy_footprint_mm_agrees() = assertEveryBrokenCopyTrips("footprint-mm-agrees")
    @Test fun broken_copy_instance_id_unique() = assertEveryBrokenCopyTrips("instance-id-unique")
    @Test fun broken_copy_instance_source() = assertEveryBrokenCopyTrips("instance-source")
    @Test fun broken_copy_instance_error() = assertEveryBrokenCopyTrips("instance-error")
    @Test fun broken_copy_instance_error_reason() = assertEveryBrokenCopyTrips("instance-error-reason")
    @Test fun broken_copy_ground_size() = assertEveryBrokenCopyTrips("ground-size")
    @Test fun broken_copy_ground_range() = assertEveryBrokenCopyTrips("ground-range")
    @Test fun broken_copy_ground_error_reason() = assertEveryBrokenCopyTrips("ground-error-reason")

    @Test
    fun an_empty_worlds_directory_is_blind_not_green() {
        val report = WorldValidator.validate(emptyMap())
        assertEquals(listOf("blind-worlds"), report.blind.map { it.rule })
        assertEquals(emptyList(), report.valid)
    }

    @Test
    fun a_child_whose_parent_is_missing_reports_a_dangling_link() {
        val report = WorldValidator.validate(REFERENCE_WORLD_FILES - LOCALITY_FILE)
        assertTrue(report.fired.any { it.rule == "link-resolves" && it.world == PARCEL_FILE })
    }

    private fun parcelWithSupportEquation(require: String, isScoreBinding: Boolean): Map<String, String> {
        val edited = editedWorld(PARCEL_FILE) { document ->
            val equations = JsonArray(document.getValue("equations").jsonArray + buildJsonObject {
                put("id", "support_now")
                put("expr", "projected_support('neighbor')")
                put("unit", "1")
            })
            val rules = document.getValue("rules").jsonArray
            val firstRule = JsonObject(rules[0].jsonObject + ("require" to JsonPrimitive(require)))
            val scoring = JsonArray(document.getValue("scoring").jsonArray + buildJsonObject {
                put("id", "support_copy")
                put("label", "Support")
                put("expr", "support_now")
                put("unit", "1")
                put("binding", isScoreBinding)
            })
            JsonObject(document + mapOf("equations" to equations, "rules" to JsonArray(listOf(firstRule) + rules.drop(1)), "scoring" to scoring))
        }
        return REFERENCE_WORLD_FILES + (PARCEL_FILE to edited)
    }

    private fun projectionFindings(files: Map<String, String>): List<String> =
        WorldValidator.validate(files).fired.filter { it.rule == "projection-not-binding" }.map { it.message }

    @Test
    fun a_projection_read_through_an_equation_by_a_rule_is_refused() {
        val findings = projectionFindings(parcelWithSupportEquation("support_now >= 0.5", isScoreBinding = false))
        assertTrue(findings.any { "rules.van-pad-needs-path" in it })
    }

    @Test
    fun a_projection_read_through_an_equation_by_a_binding_score_is_refused() {
        val findings = projectionFindings(parcelWithSupportEquation("neighbors(tile, 1 [tile], 'path') >= 1 [tile]", isScoreBinding = true))
        assertTrue(findings.any { "scoring.support_copy" in it })
    }

    @Test
    fun a_projection_read_through_an_equation_by_nothing_binding_passes() {
        assertEquals(emptyList(), projectionFindings(parcelWithSupportEquation("neighbors(tile, 1 [tile], 'path') >= 1 [tile]", isScoreBinding = false)))
    }

    private companion object {
        val RULES_WITH_A_TEST = setOf(
            "blind-worlds", "world-parses", "world-schema", "expression-parses", "unknown-word", "unit-mismatch", "expression-bound",
            "name-cycle", "seed-replays", "rule-message", "unknown-target", "action-emits", "link-resolves", "link-fits",
            "projection-not-binding", "figure-sourced", "sprite-known", "footprint-mm-agrees", "instance-id-unique", "instance-source",
            "instance-error", "instance-error-reason", "ground-size", "ground-range", "ground-error-reason",
            "number-exponent-too-large", "footprint-too-large",
        )
    }
}
