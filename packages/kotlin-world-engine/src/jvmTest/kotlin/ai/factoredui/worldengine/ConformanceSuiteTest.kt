package ai.factoredui.worldengine

import kotlin.test.Test
import kotlin.test.assertEquals

class ConformanceSuiteTest {
    private val runner = ConformanceRunner()

    @Test
    fun the_reference_is_the_pinned_commit_untouched() {
        assertEquals(emptyList(), ReferenceLocations.findManifestProblems())
    }

    @Test
    fun every_pinned_case_is_read_and_judged_not_just_one() {
        val cases = runner.readCases()
        assertEquals(ReferenceLocations.expectedCaseCount, cases.size, "cases read from ${ReferenceLocations.conformanceCasesDir} against the ${ReferenceLocations.expectedCaseCount} pinned at ${ReferenceLocations.pinnedCommit}")
        val failing = cases.filter { (name, case) -> runner.judge(name, case).isNotEmpty() }.map { it.first }
        println("${cases.size - failing.size} of ${cases.size} cases pass at ${ReferenceLocations.pinnedCommit}")
        println("${failing.size} fail")
        assertEquals(emptyList(), failing)
    }

    @Test
    fun the_frozen_worlds_are_the_ones_the_cases_were_written_against() {
        assertEquals(emptyList(), runner.findFrozenWorldChanges())
    }
}
