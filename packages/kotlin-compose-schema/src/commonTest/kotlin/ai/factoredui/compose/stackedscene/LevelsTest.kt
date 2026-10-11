package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.SceneType
import ai.factoredui.compose.scene.StackLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun stackedBoard() = stackScene(
    types = listOf(SceneType("block", "Block"), SceneType("bar", "Bar"), SceneType("plate", "Plate")),
    items = listOf(part("plate-1", "plate", level = 2), part("block-1", "block", level = 0), part("block-2", "block", level = 0), part("bar-1", "bar", level = 1), part("plate-2", "plate", level = 2)),
)

private fun List<VisiblePart>.ids() = map { it.footprint.id }

class LevelsTest {
    @Test
    fun the_stack_levels_present_are_listed_once_each_lowest_first() = assertEquals(listOf(0, 1, 2), levelsPresent(stackedBoard()))

    @Test
    fun a_scene_with_no_footprints_has_no_levels() = assertEquals(emptyList(), levelsPresent(stackScene().copy(footprints = null)))

    @Test
    fun a_level_is_named_for_the_type_that_is_most_of_it() {
        assertEquals("Block", levelName(stackedBoard(), 0))
        assertEquals("Bar", levelName(stackedBoard(), 1))
        assertEquals("Plate", levelName(stackedBoard(), 2))
    }

    @Test
    fun a_level_two_types_share_equally_is_named_for_both_in_the_order_they_first_appear() {
        val tied = stackScene(
            types = listOf(SceneType("edge", "Short bar"), SceneType("inner", "Long bar")),
            items = listOf(part("b1", "edge", level = 1), part("b2", "inner", level = 1), part("b3", "inner", level = 1), part("b4", "edge", level = 1)),
        )
        assertEquals("Short bar and Long bar", levelName(tied, 1))
    }

    @Test
    fun a_level_the_file_labels_is_named_by_that_label_not_guessed_from_its_parts() {
        val labelled = stackedBoard().copy(stackLevels = listOf(StackLevel(1, 352, "bars (resting on the block tops)")))
        assertEquals("bars (resting on the block tops)", levelName(labelled, 1))
        assertEquals("Block", levelName(labelled, 0))
    }

    @Test
    fun a_level_with_nothing_on_it_is_named_by_its_number_alone() = assertEquals("Level 7", levelName(stackedBoard(), 7))

    @Test
    fun all_levels_are_drawn_bottom_to_top_so_an_upper_part_covers_a_lower_one() =
        assertEquals(listOf("block-1", "block-2", "bar-1", "plate-1", "plate-2"), visibleParts(stackedBoard(), LevelFocus.AllLevels, showLower = true).ids())

    @Test
    fun focusing_one_level_with_lower_levels_shown_dims_everything_below_it_and_hides_everything_above() {
        val parts = visibleParts(stackedBoard(), LevelFocus.OneLevel(1), showLower = true)
        assertEquals(listOf("block-1" to true, "block-2" to true, "bar-1" to false), parts.map { it.footprint.id to it.isDimmed })
    }

    @Test
    fun focusing_one_level_with_lower_levels_hidden_shows_that_level_alone() {
        val parts = visibleParts(stackedBoard(), LevelFocus.OneLevel(2), showLower = false)
        assertEquals(listOf("plate-1", "plate-2"), parts.ids())
        assertTrue(parts.none { it.isDimmed })
    }

    @Test
    fun with_every_level_focused_nothing_is_dimmed_whatever_the_lower_levels_switch_says() {
        val parts = visibleParts(stackedBoard(), LevelFocus.AllLevels, showLower = false)
        assertTrue(parts.none { it.isDimmed })
        assertEquals(5, parts.size)
    }

    @Test
    fun hiding_the_top_level_takes_the_highest_levels_parts_away_and_leaves_the_rest_solid() {
        val parts = visibleParts(stackedBoard(), LevelFocus.AllLevels, showLower = true, hideTopCount = 1)
        assertEquals(listOf("block-1", "block-2", "bar-1"), parts.ids())
        assertTrue(parts.none { it.isDimmed })
    }

    @Test
    fun hiding_the_top_two_levels_leaves_only_the_lowest_of_three() =
        assertEquals(listOf("block-1", "block-2"), visibleParts(stackedBoard(), LevelFocus.AllLevels, showLower = true, hideTopCount = 2).ids())

    @Test
    fun hiding_none_leaves_every_level() = assertEquals(5, visibleParts(stackedBoard(), LevelFocus.AllLevels, showLower = true, hideTopCount = 0).size)

    @Test
    fun hiding_a_negative_count_hides_nothing() = assertEquals(5, visibleParts(stackedBoard(), LevelFocus.AllLevels, showLower = true, hideTopCount = -3).size)

    @Test
    fun hiding_more_levels_than_there_are_leaves_nothing() = assertEquals(0, visibleParts(stackedBoard(), LevelFocus.AllLevels, showLower = true, hideTopCount = 9).size)

    @Test
    fun hiding_the_top_levels_counts_levels_that_have_parts_not_stack_level_numbers() {
        val gappy = stackScene(items = listOf(part("a", "block", level = 0), part("b", "bar", level = 5), part("c", "plate", level = 9)))
        assertEquals(listOf("a", "b"), visibleParts(gappy, LevelFocus.AllLevels, showLower = true, hideTopCount = 1).ids())
    }

    @Test
    fun hiding_the_top_level_of_a_single_level_scene_hides_it_all() {
        val flat = stackScene(items = listOf(part("a", "block"), part("b", "block")))
        assertEquals(0, visibleParts(flat, LevelFocus.AllLevels, showLower = true, hideTopCount = 1).size)
    }

    @Test
    fun the_top_level_is_named_by_its_highest_stack_level() {
        assertEquals(2, topLevel(stackedBoard()))
        assertNull(topLevel(stackScene()))
    }

    @Test
    fun each_part_carries_its_level() {
        val levels = visibleParts(stackedBoard(), LevelFocus.AllLevels, showLower = true).associate { it.footprint.id to it.level }
        assertEquals(mapOf("block-1" to 0, "block-2" to 0, "bar-1" to 1, "plate-1" to 2, "plate-2" to 2), levels)
    }

    @Test
    fun fading_marks_every_part_outside_the_highlight_as_dimmed_and_leaves_the_highlight_solid() {
        val parts = visibleParts(stackedBoard(), LevelFocus.AllLevels, showLower = true)
        assertEquals(listOf(true, false, true, true, true), fadeUnhighlighted(parts, setOf("block-2")).map { it.isDimmed })
    }

    @Test
    fun an_empty_highlight_leaves_every_part_as_it_was() {
        val parts = visibleParts(stackedBoard(), LevelFocus.OneLevel(1), showLower = true)
        assertEquals(parts, fadeUnhighlighted(parts, emptySet()))
    }

    @Test
    fun a_part_already_dimmed_by_level_stays_dimmed_when_it_is_inside_the_highlight() {
        val parts = visibleParts(stackedBoard(), LevelFocus.OneLevel(1), showLower = true)
        assertTrue(fadeUnhighlighted(parts, setOf("block-1")).first { it.footprint.id == "block-1" }.isDimmed)
    }
}
