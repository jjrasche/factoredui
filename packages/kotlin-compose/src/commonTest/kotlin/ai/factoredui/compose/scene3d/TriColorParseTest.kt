package ai.factoredui.compose.scene3d

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

private const val FALLBACK_GREY = 0xFF888888

class TriColorParseTest {

    private fun colourOf(hex: String): Color =
        Scene3dMesh(
            vertices = listOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            triangles = listOf(0, 1, 2),
            triColors = listOf(hex),
        ).prepare().colors.single()

    @Test
    fun a_hash_prefixed_hex_is_the_colour_it_names() {
        assertEquals(Color(0xFFB5C1ED), colourOf("#b5c1ed"))
    }

    @Test
    fun a_bare_hex_is_the_colour_it_names() {
        assertEquals(Color(0xFFB5C1ED), colourOf("b5c1ed"))
    }

    @Test
    fun an_unreadable_hex_falls_back_to_grey() {
        assertEquals(Color(FALLBACK_GREY), colourOf("not-a-colour"))
    }
}
