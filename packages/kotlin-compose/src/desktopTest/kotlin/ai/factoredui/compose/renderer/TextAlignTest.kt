package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.DrivenScreen
import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val WIDTH = 400
private const val CELL_LEFT = 200
private const val CELL_RIGHT = 400

private fun tableSpec(align: String): Spec = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"table","type":"column","props":{"gap":8},"children":[
      {"id":"row-a","type":"row","children":[
        {"id":"label-a","type":"text","props":{"value":"first","flex":1}},
        {"id":"figure-a","type":"text","props":{"value":"0","flex":1$align}}]},
      {"id":"row-b","type":"row","children":[
        {"id":"label-b","type":"text","props":{"value":"second","flex":1}},
        {"id":"figure-b","type":"text","props":{"value":"10","flex":1$align}}]}
    ]}}
    """.trimIndent(),
)

private data class InkSpan(val left: Int, val right: Int) {
    val centre: Double get() = (left + right) / 2.0
}

private fun isInk(rgb: Int): Boolean = listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF).average() < 160

/** The leftmost and rightmost inked columns of the figure cell, across the band the figure's row occupies. */
private fun inkSpan(screen: DrivenScreen, rowId: String): InkSpan {
    val band = checkNotNull(screen.boundsOf(rowId)) { "$rowId drew no region" }
    val image = ImageIO.read(ByteArrayInputStream(screen.frame().png))
    val inked = (CELL_LEFT until CELL_RIGHT).filter { x ->
        (band.top.value.toInt() until band.bottom.value.toInt()).any { y -> isInk(image.getRGB(x, y)) }
    }
    check(inked.isNotEmpty()) { "$rowId drew no ink in its figure cell" }
    return InkSpan(inked.first(), inked.last())
}

private fun spans(align: String): Pair<InkSpan, InkSpan> = driveScreen(tableSpec(align), emptyMap(), WIDTH, 300).use { screen ->
    inkSpan(screen, "row-a") to inkSpan(screen, "row-b")
}

class TextAlignTest {

    @Test
    fun right_aligned_figures_in_equal_flex_cells_end_at_the_same_x_near_the_cells_right_edge() {
        val (zero, ten) = spans(""","align":"right"""")
        assertTrue(abs(zero.right - ten.right) <= 1, "0 ends at ${zero.right}, 10 at ${ten.right}")
        assertTrue(CELL_RIGHT - zero.right <= 6, "0 ends ${CELL_RIGHT - zero.right} px from the cell's right edge")
    }

    @Test
    fun centred_figures_in_equal_flex_cells_share_a_centre_near_the_cells_centre() {
        val (zero, ten) = spans(""","align":"center"""")
        assertTrue(abs(zero.centre - ten.centre) <= 1.0, "0 centred at ${zero.centre}, 10 at ${ten.centre}")
        assertTrue(abs(zero.centre - (CELL_LEFT + CELL_RIGHT) / 2.0) <= 3.0, "centre ${zero.centre}")
    }

    @Test
    fun left_aligned_and_unaligned_figures_start_at_the_cells_left_edge() {
        listOf(""","align":"left"""", "").forEach { align ->
            val (zero, ten) = spans(align)
            assertTrue(abs(zero.left - ten.left) <= 1, "'$align': 0 starts at ${zero.left}, 10 at ${ten.left}")
            assertTrue(zero.left - CELL_LEFT <= 4, "'$align' starts ${zero.left - CELL_LEFT} px into the cell")
        }
    }

    @Test
    fun a_right_aligned_text_with_no_flex_in_a_row_leaves_its_flex_sibling_its_room() {
        val spec = json.decodeFromString(
            Spec.serializer(),
            """
            {"spec_version":1,"renderer_min":1,"root":{"id":"line","type":"row","children":[
              {"id":"name","type":"text","props":{"value":"Total","flex":1}},
              {"id":"amount","type":"text","props":{"value":"5","align":"right"}}]}}
            """.trimIndent(),
        )
        driveScreen(spec, emptyMap(), WIDTH, 100).use { screen ->
            assertEquals(listOf("Total", "5"), screen.textsUnder("line"))
            val amount = checkNotNull(screen.boundsOf("amount"))
            assertTrue(amount.left.value > WIDTH / 2, "the amount stays at the row's end, from ${amount.left.value}")
            assertTrue(amount.right.value - amount.left.value < 50, "and only as wide as its words: ${amount.right.value - amount.left.value}")
        }
    }
}
