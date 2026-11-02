package ai.factoredui.compose.tokens

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

private fun committed(mode: String): String = File("../../design/$mode.tokens.json").readText().replace("\r\n", "\n")

class DefaultThemeFilesTest {
    @Test
    fun the_committed_default_theme_files_are_what_the_tokens_write_so_they_cannot_drift() {
        listOf("dark", "light").forEach { mode -> assertEquals(writeTokenFile(defaultTokens(mode), mode), committed(mode), "design/$mode.tokens.json differs from the default $mode tokens; rewrite it from writeTokenFile") }
    }

    @Test
    fun a_committed_file_loaded_as_a_users_theme_changes_nothing_and_says_nothing() {
        listOf("dark", "light").forEach { mode ->
            val loaded = applyUserTokens(mode, committed(mode))
            assertEquals(defaultTokens(mode), loaded.tokens, mode)
            assertEquals(emptyList(), loaded.notices, mode)
        }
    }
}
