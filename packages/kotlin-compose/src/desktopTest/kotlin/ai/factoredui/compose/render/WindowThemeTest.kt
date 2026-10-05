package ai.factoredui.compose.render

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowThemeTest {

    @Test
    fun withNothingSetEveryWindowOpensDark() {
        assertEquals("dark", resolveWindowTheme(flag = null, environment = null, dataValue = null))
    }

    @Test
    fun anExplicitFlagBeatsEverythingElse() {
        assertEquals("light", resolveWindowTheme(flag = "light", environment = "dark", dataValue = "dark"))
    }

    @Test
    fun theEnvironmentBeatsTheDataFileButNotTheFlag() {
        assertEquals("light", resolveWindowTheme(flag = null, environment = "light", dataValue = "dark"))
    }

    @Test
    fun aDataFilesOwnThemeBeatsTheDefault() {
        assertEquals("light", resolveWindowTheme(flag = null, environment = null, dataValue = "light"))
    }

    @Test
    fun anUnknownNameIsIgnoredAndTheNextSourceIsUsed() {
        assertEquals("light", resolveWindowTheme(flag = "sepia", environment = "light", dataValue = null))
        assertEquals("dark", resolveWindowTheme(flag = "sepia", environment = "mauve", dataValue = 3))
    }

    @Test
    fun theSharedDefaultIsNamedOnceAndIsDark() {
        assertEquals("dark", DEFAULT_WINDOW_THEME)
    }
}
