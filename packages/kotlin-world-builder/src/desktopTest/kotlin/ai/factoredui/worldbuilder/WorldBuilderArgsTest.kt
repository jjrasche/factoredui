package ai.factoredui.worldbuilder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorldBuilderArgsTest {

    @Test
    fun aWorldAndASpecAreEnoughAndTheRestDefault() {
        val parsed = parseWorldBuilderArgs(arrayOf("--world", "p.world.json", "--spec", "s.json"))
        assertEquals(WorldBuilderArgs("p.world.json", "s.json", null, "light", true, 1500, 900), parsed)
    }

    @Test
    fun everyFlagIsRead() {
        val parsed = parseWorldBuilderArgs(
            arrayOf("--world", "w", "--spec", "s", "--presentation", "p", "--theme", "dark", "--animate", "false", "--width", "1200", "--height", "700"),
        )
        assertEquals(WorldBuilderArgs("w", "s", "p", "dark", false, 1200, 700), parsed)
    }

    @Test
    fun aMissingWorldOrSpecIsRefused() {
        assertNull(parseWorldBuilderArgs(arrayOf("--spec", "s")))
        assertNull(parseWorldBuilderArgs(arrayOf("--world", "w")))
        assertNull(parseWorldBuilderArgs(emptyArray()))
    }
}
