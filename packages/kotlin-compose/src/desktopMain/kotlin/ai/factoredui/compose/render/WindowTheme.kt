package ai.factoredui.compose.render

const val DEFAULT_WINDOW_THEME = "dark"
const val WINDOW_THEME_ENVIRONMENT_VARIABLE = "FACTOREDUI_THEME"

private val KNOWN_THEMES = setOf("light", "dark")

fun resolveWindowTheme(flag: String?, environment: String?, dataValue: Any?): String =
    listOf(flag, environment, dataValue as? String).firstOrNull { it in KNOWN_THEMES } ?: DEFAULT_WINDOW_THEME

fun windowThemeFromEnvironment(): String? = System.getenv(WINDOW_THEME_ENVIRONMENT_VARIABLE)
