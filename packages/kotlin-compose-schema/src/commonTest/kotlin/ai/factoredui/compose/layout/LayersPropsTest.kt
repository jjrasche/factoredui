package ai.factoredui.compose.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SIP = listOf(
    mapOf("label" to "OSB", "size" to 0.4375, "size_text" to "7/16 in", "note" to "R 0.5"),
    mapOf("label" to "EPS", "size" to 3.5, "size_text" to "3 1/2 in", "note" to "R 14"),
    mapOf("label" to "OSB", "size" to 0.4375, "size_text" to "7/16 in", "note" to "R 0.5"),
)

class LayersPropsTest {
    @Test
    fun the_layers_are_read_in_order_with_their_text_and_notes() {
        val props = readLayersProps(mapOf("layers" to SIP, "unit" to "in"))
        assertEquals(listOf("OSB", "EPS", "OSB"), props.layers.map { it.label })
        assertEquals(listOf("7/16 in", "3 1/2 in", "7/16 in"), props.layers.map { it.sizeText })
        assertEquals(listOf("R 0.5", "R 14", "R 0.5"), props.layers.map { it.note })
        assertEquals(emptyList(), props.notices)
    }

    @Test
    fun with_no_size_text_the_size_is_shown_with_the_unit() {
        val props = readLayersProps(mapOf("layers" to listOf(mapOf("label" to "Stud", "size" to 5.5)), "unit" to "in"))
        assertEquals("5.5 in", props.layers.single().sizeText)
    }

    @Test
    fun a_layer_with_no_label_or_no_positive_size_is_named_and_left_out() {
        val props = readLayersProps(mapOf("layers" to listOf(mapOf("size" to 1), mapOf("label" to "Air", "size" to 0), mapOf("label" to "Foam", "size" to 2))))
        assertEquals(listOf("Foam"), props.layers.map { it.label })
        assertEquals(
            listOf("layers layers[0] needs a 'label'; it is left out.", "layers layers[1] needs a 'size' above zero; it is left out."),
            props.notices,
        )
    }

    @Test
    fun no_layers_says_so_in_the_notices() {
        assertEquals(listOf("layers has no layers; there is nothing to show."), readLayersProps(emptyMap()).notices)
    }

    @Test
    fun widths_are_in_proportion_to_size_and_add_up_to_one() {
        val fractions = layerFractions(readLayersProps(mapOf("layers" to SIP)).layers, 0.0)
        assertEquals(1.0, fractions.sum(), 1e-9)
        assertEquals(0.4375 / 4.375, fractions[0], 1e-9)
        assertEquals(fractions[0], fractions[2], 1e-9)
    }

    @Test
    fun a_layer_thinner_than_the_smallest_share_is_widened_to_it_and_the_others_give_way() {
        val fractions = layerFractions(readLayersProps(mapOf("layers" to listOf(mapOf("label" to "A", "size" to 0.01), mapOf("label" to "B", "size" to 10)))).layers, 0.05)
        assertEquals(1.0, fractions.sum(), 1e-9)
        assertTrue(fractions[0] >= 0.05 - 1e-9)
    }
}
