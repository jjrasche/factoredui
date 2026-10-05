package ai.factoredui.worldengine

import ai.factoredui.worldengine.outputs.reportOutputs
import ai.factoredui.worldengine.script.formatOutputs
import ai.factoredui.worldengine.script.runScript
import ai.factoredui.worldengine.text.formatGeneral
import ai.factoredui.worldengine.world.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoEquivalenceTest {
    private fun transcript(world: World): List<String> {
        val run = runScript(world, demoSteps(world))
        val header = "world ${world.id}: ${world.cols} x ${world.rows} tiles of ${formatGeneral(world.tileFt, 6)} ft"
        return listOf(header) + run.lines + formatOutputs(world, reportOutputs(world, run.log.stateOf("main")))
    }

    @Test
    fun the_parcel_demo_prints_what_engine_ref_prints() {
        assertEquals(PARCEL_TRANSCRIPT.trimIndent().lines(), transcript(parcelWorld()))
    }

    @Test
    fun the_dungeon_demo_prints_what_engine_ref_prints() {
        assertEquals(DUNGEON_TRANSCRIPT.trimIndent().lines(), transcript(dungeonWorld()))
    }

    @Test
    fun the_demo_prints_the_van_pad_count() {
        assertTrue("count van_pad 1 tiles 625 sq_ft" in transcript(parcelWorld()))
    }

    private companion object {
        val PARCEL_TRANSCRIPT = """
            world parcel-five-acre: 13 x 26 tiles of 25 ft
            applied e1 place on main
            applied e2 place on main
            applied e3 place on main
            applied e4 place on main
            applied e5 place on main
            applied e6 place on main
            applied e7 place on main
            applied e8 place on main
            refused place {"col": 10, "row": 10, "type": "van_pad"} by van-pad-needs-path: a van pad needs a path on one of its four sides
            applied e9 place on main
            refused place {"col": 0, "row": 10, "type": "commons_building"} by structure-setback: a structure must stand at least the parcel's setback from the lot line
            applied e10 place on main
            refused place {"col": 2, "row": 9, "type": "woodland_tree"} by tall-not-south-of-hoop-house: a tall object would shade the hoop house directly north of it
            applied e11 place on main
            applied e12 place on main
            applied e13 place on main
            applied e14 place on main
            applied e15 place on main
            applied e16 place on main
            applied e17 place on main
            applied e18 place on main
            applied e19 place on main
            applied e20 place on main
            applied e21 place on main
            applied e22 place on main
            applied e23 place on main
            applied e24 place on main
            applied e25 place on main
            applied e26 place on main
            applied e27 place on main
            applied e28 remove on main
            applied e29 branch on proposal-more-trees
            applied e30 place on proposal-more-trees
            refused merge {} by proposal-unendorsed: proposal proposal-more-trees carries no endorsement from a real person
            refused endorse {"weight_class": "nearby"} by projection-not-binding: neighbor-1 is a synthetic agent; its vote is a projection and cannot endorse
            applied e31 endorse on proposal-more-trees
            applied e32 merge on main
            applied e33 tick on main
            count paddock 12 tiles 7500 sq_ft
            count hoop_house 2 tiles 1250 sq_ft
            count commons_building 4 tiles 2500 sq_ft
            count van_pad 1 tiles 625 sq_ft
            count path 6 tiles 3750 sq_ft
            count pond 2 tiles 1250 sq_ft
            count woodland_tree 4 tiles 2500 sq_ft
            equation pasture_yield 0.688705 ton/year
            stock standing_forage 0.688705 ton
            score area_paddock 7500 sq_ft
            score area_hoop_house 1250 sq_ft
            score area_commons_building 2500 sq_ft
            score area_van_pad 625 sq_ft
            score area_path 3750 sq_ft
            score area_pond 1250 sq_ft
            score area_woodland_tree 2500 sq_ft
            score pasture_yield_annual 0.688705 ton/year
            score hoop_house_labor 49.1536 hour/year
            score labor_hours_total 87.4036 hour/year
            score capex_floor 219.98 usd
            score paddock_dm_yield 1377.41 lb/year
            score neighbor_support 0.679644 1
            ticks 365
        """

        val DUNGEON_TRANSCRIPT = """
            world dungeon-tiny: 12 x 8 tiles of 5 ft
            applied e1 place on main
            applied e2 place on main
            applied e3 place on main
            applied e4 place on main
            applied e5 place on main
            applied e6 place on main
            applied e7 place on main
            applied e8 place on main
            applied e9 place on main
            applied e10 place on main
            applied e11 place on main
            applied e12 place on main
            applied e13 place on main
            applied e14 place on main
            applied e15 place on main
            applied e16 place on main
            applied e17 place on main
            applied e18 place on main
            refused place {"col": 5, "row": 4, "type": "door"} by door-connects-floors: a door must connect two floor tiles on opposite sides
            applied e19 place on main
            refused place {"col": 10, "row": 6, "type": "monster"} by monster-stands-by-floor: a monster must stand beside a floor tile
            applied e20 place on main
            applied e21 place on main
            applied e22 tick on main
            count wall 10 tiles 250 sq_ft
            count floor 7 tiles 175 sq_ft
            count door 1 tiles 25 sq_ft
            count monster 1 tiles 25 sq_ft
            count treasure 2 tiles 50 sq_ft
            score treasure_per_monster 2 1
            score guarded_treasure 1 1
            score walkable_area 200 sq_ft
            ticks 10
        """
    }
}
