package ai.factoredui.worldbuilder

private const val ROOT_PLAN_NAME = "My plan"
private const val ALTERNATIVE_PREFIX = "Alternative"
private const val LONGEST_NAME = 40

internal class PlanNames(private val rootBranch: String, private val proposalPrefix: String, initial: Map<String, String> = emptyMap()) {
    private val renamed = initial.toMutableMap()

    fun exported(): Map<String, String> = renamed.toMap()

    fun nameOf(branch: String): String = renamed[branch] ?: defaultName(branch)

    fun rename(branch: String, requested: String): Boolean {
        val name = requested.trim().take(LONGEST_NAME)
        if (name.isEmpty()) return false
        renamed[branch] = name
        return true
    }

    private fun defaultName(branch: String): String =
        if (branch == rootBranch) ROOT_PLAN_NAME else "$ALTERNATIVE_PREFIX ${branch.removePrefix(proposalPrefix)}"
}
