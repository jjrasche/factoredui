package ai.factoredui.compose.stackedscene

import kotlin.test.Test
import kotlin.test.assertEquals

class YAxisTest {

    @Test
    fun a_grid_whose_row_0_is_its_north_edge_draws_row_0_at_the_top_when_the_node_states_no_y_axis() =
        assertEquals(YAxisChoice(YAxis.DOWN, null), sceneYAxis(stated = null, gridNorth = "row_0"))

    @Test
    fun a_y_axis_the_node_states_wins_over_the_grid() {
        assertEquals(YAxisChoice(YAxis.UP, null), sceneYAxis(stated = YAxis.UP, gridNorth = "row_0"))
        assertEquals(YAxisChoice(YAxis.DOWN, null), sceneYAxis(stated = YAxis.DOWN, gridNorth = null))
    }

    @Test
    fun with_neither_a_stated_y_axis_nor_a_grid_north_y_is_drawn_up() =
        assertEquals(YAxisChoice(YAxis.UP, null), sceneYAxis(stated = null, gridNorth = null))

    @Test
    fun a_grid_north_this_renderer_does_not_know_draws_up_and_names_the_value_it_knows() =
        assertEquals(
            YAxisChoice(YAxis.UP, "the scene's grid north 'row_last' is not row_0; y is drawn up."),
            sceneYAxis(stated = null, gridNorth = "row_last"),
        )
}
