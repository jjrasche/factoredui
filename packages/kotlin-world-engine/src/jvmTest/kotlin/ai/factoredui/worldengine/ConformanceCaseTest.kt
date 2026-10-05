package ai.factoredui.worldengine

import kotlinx.serialization.json.JsonObject
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class ConformanceCaseTest(private val fileName: String, private val case: JsonObject) {
    @Test
    fun conforms() {
        val problems = RUNNER.judge(fileName, case)
        assertTrue(problems.isEmpty(), "$fileName:\n    " + problems.joinToString("\n    "))
    }

    companion object {
        private val RUNNER = ConformanceRunner()

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = RUNNER.readCases().map { (name, case) -> arrayOf(name, case) }
    }
}
