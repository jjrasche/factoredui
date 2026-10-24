package ai.factoredui.compose.media

import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.SpecNode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val json = Json { ignoreUnknownKeys = true }

private fun readFrom(props: String): ReadAlongProps {
    val node = json.decodeFromString(SpecNode.serializer(), """{"id":"text","type":"readalong","props":$props}""")
    return readReadAlongProps(BindingResolver.resolveProps(node.props, emptyMap()), node.props)
}

private const val THREE_WORDS = """[{"t":"In","at":0.0,"end":0.4},{"t":"the","at":0.4,"end":0.7},{"t":"beginning","at":0.9,"end":1.6}]"""

class ReadAlongPropsTest {
    @Test
    fun the_words_are_read_with_their_times_marker_labels_and_paragraph_starts() {
        val props = readFrom("""{"words":[{"t":"In","at":0,"end":0.4,"v":"1","p":true},{"t":"the","at":0.4,"end":0.7}]}""")
        assertEquals(listOf(ReadAlongWord("In", 0.0, 0.4, "1", true), ReadAlongWord("the", 0.4, 0.7, null, false)), props.words)
        assertEquals(emptyList(), props.notices)
    }

    @Test
    fun the_defaults_follow_the_lit_word_at_the_body_size_with_the_accent_highlight() {
        val props = readFrom("""{"words":$THREE_WORDS}""")
        assertEquals(true, props.follows)
        assertNull(props.sizeSp)
        assertNull(props.highlightColor)
        assertNull(props.onWordTap)
    }

    @Test
    fun size_follow_highlight_and_the_tap_action_are_read() {
        val props = readFrom("""{"words":$THREE_WORDS,"size":20,"follow":false,"highlight_color":"#d0bcff","on_word_tap":{"action":"tapped"}}""")
        assertEquals(20.0, props.sizeSp)
        assertEquals(false, props.follows)
        assertEquals("#d0bcff", props.highlightColor)
        assertEquals(ActionRef("tapped", emptyMap()), props.onWordTap)
    }

    @Test
    fun a_word_with_no_text_or_no_times_is_named_and_left_out() {
        val props = readFrom("""{"words":[{"t":"In","at":0,"end":0.4},{"at":0.4,"end":0.7},{"t":"the","end":0.9},{"t":"end","at":2,"end":1}]}""")
        assertEquals(listOf("In"), props.words.map { it.text })
        assertEquals(
            listOf(
                "readalong words[1] needs a 't' text; it is left out.",
                "readalong words[2] needs an 'at' number of seconds; it is left out.",
                "readalong words[3] ends before it starts; it is left out.",
            ),
            props.notices,
        )
    }

    @Test
    fun a_node_with_no_words_says_so_in_its_notices() {
        assertEquals(listOf("readalong has no words; there is nothing to show."), readFrom("{}").notices)
    }

    @Test
    fun the_lit_word_is_the_one_whose_span_holds_the_position() {
        val words = readFrom("""{"words":$THREE_WORDS}""").words
        assertEquals(listOf(0, 0, 1, 2), listOf(0.0, 0.3, 0.5, 1.0).map { litWordIndex(words, it) })
    }

    @Test
    fun between_words_the_previous_word_stays_lit_and_before_the_first_none_is() {
        val words = readFrom("""{"words":$THREE_WORDS}""").words
        assertEquals(1, litWordIndex(words, 0.8))
        assertNull(litWordIndex(readFrom("""{"words":[{"t":"In","at":1.0,"end":1.4}]}""").words, 0.5))
    }

    @Test
    fun after_the_last_word_the_last_word_stays_lit() {
        assertEquals(2, litWordIndex(readFrom("""{"words":$THREE_WORDS}""").words, 99.0))
    }
}
