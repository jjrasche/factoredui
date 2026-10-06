package ai.factoredui.worldbuilder

import ai.factoredui.compose.schema.resolveTilemapInstancesChecked
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun lidarHost() = WorldBuilderHost(openSession(File(designDirectory(), "worlds/parcel-lidar-sample.world.json").path))

@Suppress("UNCHECKED_CAST")
private fun WorldBuilderHost.footprints(): List<Map<String, Any?>> = bindings()["footprints"] as List<Map<String, Any?>>

@Suppress("UNCHECKED_CAST")
private fun WorldBuilderHost.instances(): List<Map<String, Any?>> = bindings()["instances"] as List<Map<String, Any?>>

class WorldBuilderFootprintsTest {

    @Test
    fun aTwoTileObjectIsOneFootprintWithItsWidthAndHeight() {
        val host = parcelHost()
        host.tap(3, 3, "hoop_house")
        val footprint = host.footprints().single()
        assertEquals(listOf("hoop_house", 3, 3, 2, 1), listOf(footprint["use"], footprint["col"], footprint["row"], footprint["width"], footprint["height"]))
        assertTrue((footprint["id"] as String).isNotEmpty())
    }

    @Test
    fun theTileCellsAreNoLongerSentOneRecordPerTile() {
        val host = parcelHost()
        host.tap(3, 3, "hoop_house")
        assertEquals(emptyList<Any?>(), host.bindings()["cells"])
    }

    @Test
    fun aRemovedObjectLeavesNoFootprint() {
        val host = parcelHost()
        host.tap(3, 3, "hoop_house")
        host.tap(3, 3, "erase")
        assertEquals(emptyList<Map<String, Any?>>(), host.footprints())
    }

    @Test
    fun theLidarSampleReachesTheRendererAsTenPlainRecordsAndNoneIsDropped() {
        val instances = lidarHost().instances()
        assertEquals(10, instances.size)
        instances.forEach { record ->
            assertTrue(record["x_mm"] is Number && record["y_mm"] is Number, "positions arrive as numbers: $record")
            assertTrue(record["crown_radius_mm"] is Number || record["crown_radius_mm"] == null, "crown is a number or absent: $record")
            assertEquals("measured", record["provenance"])
        }
        val resolved = resolveTilemapInstancesChecked(instances)
        assertEquals(emptyList<String>(), resolved.dropped)
        assertEquals(10, resolved.items.size)
    }

    @Test
    fun theUsageLineCountsTheTenInstancesNextToTheTiles() {
        val usage = lidarHost().bindings()["usage_text"] as String
        assertTrue("Lidar tree: 10 placed (10 measured), 0 tiles, 0 sq ft" in usage, usage)
    }

    @Test
    fun tappingATreeFillsThePanelWithItsRecordAndATileTapClearsIt() {
        val host = lidarHost()
        assertEquals("", host.bindings()["instance_text"])
        host.selectInstance("tree-01")
        val bindings = host.bindings()
        assertEquals("Lidar tree tree-01 (measured)", bindings["instance_title"])
        assertTrue("Position error: not measured" in (bindings["instance_text"] as String), bindings["instance_text"] as String)
        host.tap(0, 0, "erase")
        assertEquals("", host.bindings()["instance_text"])
    }

    @Test
    fun theInstanceTapActionSelectsTheTreeItNames() {
        val host = lidarHost()
        val handler = host.actions { }.getValue("world.instanceTapped")
        kotlinx.coroutines.runBlocking { handler(mapOf("id" to "tree-02")) }
        assertEquals("Lidar tree tree-02 (measured)", host.bindings()["instance_title"])
    }

    @Test
    fun aTreeThatIsNotThereShowsNoRecord() {
        val host = lidarHost()
        host.selectInstance("no-such-tree")
        assertEquals("", host.bindings()["instance_text"])
    }
}
