package ai.factoredui.worldengine

import ai.factoredui.worldengine.session.DispatchResult
import ai.factoredui.worldengine.session.GroundView
import ai.factoredui.worldengine.session.WorldAction
import ai.factoredui.worldengine.session.WorldSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorldSessionGroundTest {
    private val tileOneOneCorners = listOf(10, 11, 19, 20)

    private fun groundSession(): WorldSession = WorldSession.fromJson(GROUND_DEMO_WORLD_JSON, GROUND_DEMO_FILE, referenceLibrary())

    private fun groundOf(session: WorldSession, branch: String = session.currentBranch): GroundView = assertNotNull(session.ground(branch))

    private fun cutFillAt(view: GroundView, indices: List<Int>): List<Double> = indices.map { view.cutFillMm[it] }

    @Test
    fun a_dig_dispatched_by_the_host_lowers_the_tiles_four_corners_and_reads_back_as_cut() {
        val session = groundSession()
        assertIs<DispatchResult.Accepted>(session.dispatch(WorldAction.Dig(1, 1, 100)))
        val view = groundOf(session)
        assertEquals(1L, view.version)
        assertEquals(listOf(-100.0, -100.0, -100.0, -100.0), cutFillAt(view, tileOneOneCorners))
        assertEquals(4, view.cutFillMm.count { it != 0.0 })
        assertEquals(tileOneOneCorners.map { view.baseHeightsMm[it] - 100 }, tileOneOneCorners.map { view.heightsMm[it] })
        assertEquals(9 to 9, view.vertexCols to view.vertexRows)
    }

    @Test
    fun a_raise_on_a_branch_leaves_the_parent_surface_untouched() {
        val session = groundSession()
        assertIs<DispatchResult.Accepted>(session.createBranch("idea"))
        assertIs<DispatchResult.Accepted>(session.dispatch(WorldAction.Raise(0, 0, 40), branch = "idea"))
        assertEquals(0L, groundOf(session, "main").version)
        assertEquals(listOf(40.0, 40.0, 40.0, 40.0), cutFillAt(groundOf(session, "idea"), listOf(0, 1, 9, 10)))
    }

    @Test
    fun undo_last_reverts_a_dig_and_counts_the_revert_as_a_height_change() {
        val session = groundSession()
        session.dispatch(WorldAction.Dig(1, 1, 100))
        assertIs<DispatchResult.Accepted>(session.undoLast())
        val view = groundOf(session)
        assertEquals(2L, view.version)
        assertEquals(0, view.cutFillMm.count { it != 0.0 })
    }

    @Test
    fun a_world_without_ground_offers_the_host_no_ground() {
        assertNull(WorldSession.fromJson(PARCEL_WORLD_JSON, PARCEL_FILE, referenceLibrary()).ground())
    }
}
