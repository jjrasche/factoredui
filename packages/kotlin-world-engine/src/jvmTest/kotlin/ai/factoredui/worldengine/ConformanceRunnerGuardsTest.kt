package ai.factoredui.worldengine

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private fun caseWith(expect: String) = Json.parseToJsonElement(
    """{"id": "guard", "kind": "world_load", "source": "hand", "world": "w", "input": {}, "expect": $expect, "notes": "n"}""",
).jsonObject

class ConformanceRunnerGuardsTest {
    private val runner = ConformanceRunner()

    @Test
    fun aCaseWhoseExpectStatesNothingIsRefusedNotPassed() {
        val problems = runner.judge("guard.json", caseWith("{}"))
        assertTrue(problems.any { "states nothing" in it }, problems.toString())
    }

    @Test
    fun anExpectKeyTheComparisonDoesNotReadIsRefusedNotIgnored() {
        val problems = runner.judge("guard.json", caseWith("""{"valid": true, "finals": {}}"""))
        assertTrue(problems.any { "finals" in it && "not one the world_load comparison reads" in it }, problems.toString())
    }
}
