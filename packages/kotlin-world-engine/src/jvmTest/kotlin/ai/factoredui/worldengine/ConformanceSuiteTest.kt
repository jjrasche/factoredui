package ai.factoredui.worldengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConformanceSuiteTest {
    private val runner = ConformanceRunner()

    @Test
    fun the_conformance_cases_exist_so_a_green_run_is_not_blind() {
        val cases = runner.readCases()
        assertTrue(cases.isNotEmpty(), "BLIND: no cases under ${ReferenceLocations.conformanceCasesDir}, so nothing was checked")
        val failing = cases.filter { (name, case) -> runner.judge(name, case).isNotEmpty() }.map { it.first }
        println("${cases.size - failing.size} cases pass")
        println("${failing.size} fail")
        assertEquals(emptyList(), failing)
    }

    @Test
    fun the_frozen_worlds_are_the_ones_the_cases_were_written_against() {
        assertEquals(emptyList(), runner.findFrozenWorldChanges())
    }
}
