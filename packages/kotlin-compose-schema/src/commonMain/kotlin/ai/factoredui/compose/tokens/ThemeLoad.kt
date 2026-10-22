package ai.factoredui.compose.tokens

class ThemeLoad(val tokens: TokenSet, val notices: List<String>)

class ContrastFailure(val token: String, val against: String, val ratio: Double, val needed: Double)

private const val TEXT_CONTRAST = 4.5
private const val GRAPHIC_CONTRAST = 3.0

private val TEXTS = listOf("color.ink", "color.muted", "color.accent", "color.status.pass", "color.status.warn", "color.status.fail")
private val SERIES = (1..8).map { "color.series.$it" }
private val GROUNDS = listOf("surface", "raised", "ground")

private fun colourOf(tokens: TokenSet, name: String): Long? = (tokens[name] as? ColorValue)?.argb

private fun surfaceOf(tokens: TokenSet, level: String): Long? {
    val ground = colourOf(tokens, "color.ground") ?: return null
    if (level == "ground") return ground
    val ink = colourOf(tokens, "color.ink") ?: return null
    val step = (tokens["elevation.$level"] as? NumberValue)?.value ?: return null
    return mixColours(ground, ink, step)
}

private fun pairs(tokens: TokenSet): List<Triple<String, String, Double>> =
    TEXTS.flatMap { text -> GROUNDS.map { Triple(text, it, TEXT_CONTRAST) } } +
        GROUNDS.flatMap { ground -> SERIES.map { Triple(it, ground, GRAPHIC_CONTRAST) } } +
        listOf(Triple("color.on-accent", "color.accent", TEXT_CONTRAST))

fun contrastFailures(tokens: TokenSet): List<ContrastFailure> = pairs(tokens).mapNotNull { (foreground, background, needed) ->
    val front = colourOf(tokens, foreground) ?: return@mapNotNull null
    val back = if (background.startsWith("color.")) colourOf(tokens, background) else surfaceOf(tokens, background)
    val ratio = back?.let { contrastRatio(front, it) } ?: return@mapNotNull null
    if (ratio < needed) ContrastFailure(foreground, if (background.startsWith("color.")) background else "color.ground ($background)", ratio, needed) else null
}

private fun ratioText(value: Double): String = (kotlin.math.round(value * 100) / 100).toString().removeSuffix(".0")

private fun kindOf(value: TokenValue): String = when (value) {
    is ColorValue -> "color"
    is DimensionValue -> "dimension"
    is NumberValue -> "number"
    is TypographyValue -> "typography"
    is OtherValue -> value.type
}

private fun usable(defaults: TokenSet, read: TokenFileRead, notices: MutableList<String>): Map<String, TokenValue> {
    val taken = LinkedHashMap<String, TokenValue>()
    read.tokens.forEach { (path, value) ->
        val standard = defaults[path]
        when {
            standard == null -> notices += "token $path is not used by this renderer; it is ignored."
            kindOf(standard) != kindOf(value) -> notices += "token $path: expected a ${kindOf(standard)}, not a ${kindOf(value)}; the default is kept."
            else -> taken[path] = value
        }
    }
    return taken
}

private fun reconciled(defaults: TokenSet, user: MutableMap<String, TokenValue>, notices: MutableList<String>): TokenSet {
    repeat(user.size + 1) {
        val merged = defaults + user
        val failure = contrastFailures(merged).firstOrNull { it.token in user || it.against.substringBefore(" ") in user } ?: return merged
        val reverted = if (failure.token in user) failure.token else failure.against.substringBefore(" ")
        user.remove(reverted)
        val ratio = ratioText(failure.ratio)
        val needed = ratioText(failure.needed)
        notices += if (reverted == failure.token) {
            "token $reverted: contrast $ratio:1 on ${failure.against} is below $needed:1; the default is kept."
        } else {
            "token $reverted: the default ${failure.token} would have contrast $ratio:1 on it, below $needed:1; the default is kept."
        }
    }
    return defaults + user
}

fun applyUserTokens(mode: String, text: String): ThemeLoad {
    val defaults = defaultTokens(mode)
    val read = readTokenFile(text)
    if (!read.isUsable) return ThemeLoad(defaults, read.notices)
    if (read.mode != null && read.mode != mode) return ThemeLoad(defaults, listOf("the theme file is for the ${read.mode} theme; the $mode theme is used."))
    val notices = read.notices.toMutableList()
    val user = LinkedHashMap(usable(defaults, read, notices))
    return ThemeLoad(reconciled(defaults, user, notices), notices)
}
