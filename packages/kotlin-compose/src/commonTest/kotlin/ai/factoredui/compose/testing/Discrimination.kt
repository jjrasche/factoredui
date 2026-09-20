package ai.factoredui.compose.testing

fun <T> assertDiscriminates(intact: T, broken: T, brokenBy: String, check: (T) -> Unit) {
    runCatching { check(intact) }.exceptionOrNull()?.let { failure ->
        throw AssertionError("the check failed on the intact value: ${failure.message}", failure)
    }
    if (runCatching { check(broken) }.exceptionOrNull() == null) {
        throw AssertionError("the check passed with $brokenBy — it discriminates nothing")
    }
}
