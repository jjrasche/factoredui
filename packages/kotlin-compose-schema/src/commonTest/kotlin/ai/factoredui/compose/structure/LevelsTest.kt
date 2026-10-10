package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.SceneType
import ai.factoredui.compose.scene.StackLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun stackedHouse() = stackScene(
    types = listOf(SceneType("pier", "Helical pier"), SceneType("beam", "Floor beam"), SceneType("panel", "SIP panel")),
    items = listOf(part("panel-1", "panel", level = 2), part("pier-1", "pier", level = 0), part("pier-2", "pier", level = 0), part("beam-1", "beam", level = 1), part("panel-2", "panel", level = 2)),
)

private fun List<VisiblePart>.ids() = map { it.footprint.id }

class LevelsTest {
    @Test
    fun the_stack_levels_present_are_listed_once_each_lowest_first() = assertEquals(listOf(0, 1, 2), levelsPresent(stackedHouse()))

    @Test
    fun a_scene_with_no_footprints_has_no_levels() = assertEquals(emptyList(), levelsPresent(stackScene().copy(footprints = null)))

    @Test
    fun a_level_is_named_for_the_type_that_is_most_of_it() {
        assertEquals("Helical pier", levelName(stackedHouse(), 0))
        assertEquals("Floor beam", levelName(stackedHouse(), 1))
        assertEquals("SIP panel", levelName(stackedHouse(), 2))
    }

    @Test
    fun a_level_two_types_share_equally_is_named_for_both_in_the_order_they_first_appear() {
        val tied = stackScene(
            types = listOf(SceneType("edge", "Edge beam"), SceneType("inner", "Interior beam")),
            items = listOf(part("b1", "edge", level = 1), part("b2", "inner", level = 1), part("b3", "inner", level = 1), part("b4", "edge", level = 1)),
        )
        assertEquals("Edge beam and Interior beam", levelName(tied, 1))
    }

    @Test
    fun a_level_the_file_labels_is_named_by_that_label_not_guessed_from_its_parts() {
        val labelled = stackedHouse().copy(stackLevels = listOf(StackLevel(1, 352, "beams (on the 76 mm pier caps)")))
        assertEquals("beams (on the 76 mm pier caps)", levelName(labelled, 1))
        assertEquals("Helical pier", levelName(labelled, 0))
    }

    @Test
    fun a_level_with_nothing_on_it_is_named_by_its_number_alone() = assertEquals("Level 7", levelName(stackedHouse(), 7))

    @Test
    fun all_levels_are_drawn_bottom_to_top_so_an_upper_part_covers_a_lower_one() =
        assertEquals(listOf("pier-1", "pier-2", "beam-1", "panel-1", "panel-2"), visibleParts(stackedHouse(), LevelFocus.AllLevels, showLower = true).ids())

    @Test
    fun focusing_one_level_with_lower_levels_shown_dims_everything_below_it_and_hides_everything_above() {
        val parts = visibleParts(stackedHouse(), LevelFocus.OneLevel(1), showLower = true)
        assertEquals(listOf("pier-1" to true, "pier-2" to true, "beam-1" to false), parts.map { it.footprint.id to it.isDimmed })
    }

    @Test
    fun focusing_one_level_with_lower_levels_hidden_shows_that_level_alone() {
        val parts = visibleParts(stackedHouse(), LevelFocus.OneLevel(2), showLower = false)
        assertEquals(listOf("panel-1", "panel-2"), parts.ids())
        assertTrue(parts.none { it.isDimmed })
    }

    @Test
    fun with_every_level_focused_nothing_is_dimmed_whatever_the_lower_levels_switch_says() {
        val parts = visibleParts(stackedHouse(), LevelFocus.AllLevels, showLower = false)
        assertTrue(parts.none { it.isDimmed })
        assertEquals(5, parts.size)
    }

    @Test
    fun hiding_the_top_level_takes_the_highest_levels_parts_away_and_leaves_the_rest_solid() {
        val parts = visibleParts(stackedHouse(), LevelFocus.AllLevels, showLower = true, hideTopCount = 1)
        assertEquals(listOf("pier-1", "pier-2", "beam-1"), parts.ids())
        assertTrue(parts.none { it.isDimmed })
    }

    @Test
    fun hiding_the_top_two_levels_leaves_only_the_lowest_of_three() =
        assertEquals(listOf("pier-1", "pier-2"), visibleParts(stackedHouse(), LevelFocus.AllLevels, showLower = true, hideTopCount = 2).ids())

    @Test
    fun hiding_none_leaves_every_level() = assertEquals(5, visibleParts(stackedHouse(), LevelFocus.AllLevels, showLower = true, hideTopCount = 0).size)

    @Test
    fun hiding_a_negative_count_hides_nothing() = assertEquals(5, visibleParts(stackedHouse(), LevelFocus.AllLevels, showLower = true, hideTopCount = -3).size)

    @Test
    fun hiding_more_levels_than_there_are_leaves_nothing() = assertEquals(0, visibleParts(stackedHouse(), LevelFocus.AllLevels, showLower = true, hideTopCount = 9).size)

    @Test
    fun hiding_the_top_levels_counts_levels_that_have_parts_not_stack_level_numbers() {
        val gappy = stackScene(items = listOf(part("a", "pier", level = 0), part("b", "beam", level = 5), part("c", "panel", level = 9)))
        assertEquals(listOf("a", "b"), visibleParts(gappy, LevelFocus.AllLevels, showLower = true, hideTopCount = 1).ids())
    }

    @Test
    fun hiding_the_top_level_of_a_single_level_scene_hides_it_all() {
        val flat = stackScene(items = listOf(part("a", "pier"), part("b", "pier")))
        assertEquals(0, visibleParts(flat, LevelFocus.AllLevels, showLower = true, hideTopCount = 1).size)
    }

    @Test
    fun the_top_level_is_named_by_its_highest_stack_level() {
        assertEquals(2, topLevel(stackedHouse()))
        assertNull(topLevel(stackScene()))
    }

    @Test
    fun each_part_carries_its_level() {
        val levels = visibleParts(stackedHouse(), LevelFocus.AllLevels, showLower = true).associate { it.footprint.id to it.level }
        assertEquals(mapOf("pier-1" to 0, "pier-2" to 0, "beam-1" to 1, "panel-1" to 2, "panel-2" to 2), levels)
    }

    @Test
    fun fading_marks_every_part_outside_the_highlight_as_dimmed_and_leaves_the_highlight_solid() {
        val parts = visibleParts(stackedHouse(), LevelFocus.AllLevels, showLower = true)
        assertEquals(listOf(true, false, true, true, true), fadeUnhighlighted(parts, setOf("pier-2")).map { it.isDimmed })
    }

    @Test
    fun an_empty_highlight_leaves_every_part_as_it_was() {
        val parts = visibleParts(stackedHouse(), LevelFocus.OneLevel(1), showLower = true)
        assertEquals(parts, fadeUnhighlighted(parts, emptySet()))
    }

    @Test
    fun a_part_already_dimmed_by_level_stays_dimmed_when_it_is_inside_the_highlight() {
        val parts = visibleParts(stackedHouse(), LevelFocus.OneLevel(1), showLower = true)
        assertTrue(fadeUnhighlighted(parts, setOf("pier-1")).first { it.footprint.id == "pier-1" }.isDimmed)
    }
}
