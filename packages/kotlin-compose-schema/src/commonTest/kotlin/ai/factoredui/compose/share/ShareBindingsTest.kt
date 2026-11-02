package ai.factoredui.compose.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeShareHost(
    var query: Map<String, String> = emptyMap(),
    val store: MutableMap<String, String> = mutableMapOf(),
    private val storeThrows: Boolean = false,
) : ShareHost {
    var queryWrites = 0
    var storeWrites = 0

    override fun readQuery(): Map<String, String> = query

    override fun replaceQuery(query: Map<String, String>) {
        queryWrites += 1
        this.query = query
    }

    override fun readStore(key: String): String? {
        if (storeThrows) error("storage is blocked")
        return store[key]
    }

    override fun writeStore(key: String, value: String?) {
        if (storeThrows) error("storage is blocked")
        storeWrites += 1
        if (value == null) store.remove(key) else store[key] = value
    }
}

private val VIEW = ShareEntry(path = "map.view", query = "view", store = "last-view", format = ShareFormat.NUMBERS, decimals = 5)
private val PICKED = ShareEntry(path = "map.picked", query = "select", format = ShareFormat.LIST)
private val CUTOFF = ShareEntry(path = "map.cutoff", query = "cut", format = ShareFormat.NUMBER)
private val OPEN = ShareEntry(path = "panel.open", query = "open", store = "panel-open", format = ShareFormat.BOOL)
private val LAYERS = ShareEntry(path = "map.layers", query = "layers", format = ShareFormat.LIST, keepEmpty = true)

private val SNAPPED_PERCENT = ShareEntry(path = "map.cutoff", query = "cut", format = ShareFormat.NUMBER, step = 5.0, min = 0.0, max = 100.0)
private val WEIGHT = ShareEntry(path = "weights.slope", query = "w", format = ShareFormat.NUMBER, step = 1.0, min = -10.0, max = 10.0)
private val WEIGHTS = ShareEntry(path = "weights.all", query = "ws", format = ShareFormat.NUMBERS, step = 1.0, min = -10.0, max = 10.0)

private fun valueOf(entry: ShareEntry, raw: String): Any? =
    resolveShared(listOf(entry), FakeShareHost(query = mapOf(entry.query!! to raw))).values[entry.path]

private fun noticesOf(entry: ShareEntry, raw: String): List<String> =
    resolveShared(listOf(entry), FakeShareHost(query = mapOf(entry.query!! to raw))).notices

class ShareRangeTest {
    @Test
    fun a_number_snaps_to_the_nearest_step_with_halves_going_up() {
        assertEquals(45.0, valueOf(SNAPPED_PERCENT, "43"))
        assertEquals(40.0, valueOf(SNAPPED_PERCENT, "42"))
        assertEquals(45.0, valueOf(SNAPPED_PERCENT, "42.5"))
    }

    @Test
    fun snapping_alone_gives_no_notice() {
        assertEquals(emptyList(), noticesOf(SNAPPED_PERCENT, "43"))
    }

    @Test
    fun a_number_past_the_range_is_clamped_with_a_notice_and_never_an_error() {
        assertEquals(100.0, valueOf(SNAPPED_PERCENT, "250"))
        assertEquals(0.0, valueOf(SNAPPED_PERCENT, "-3"))
        assertTrue(noticesOf(SNAPPED_PERCENT, "250").single().contains("cut"))
    }

    @Test
    fun a_value_is_snapped_first_and_the_snapped_value_is_clamped() {
        assertEquals(100.0, valueOf(SNAPPED_PERCENT, "103"))
        assertEquals(1, noticesOf(SNAPPED_PERCENT, "103").size)
        assertEquals(emptyList(), noticesOf(SNAPPED_PERCENT, "98"))
    }

    @Test
    fun weights_are_whole_numbers_from_minus_ten_to_ten() {
        assertEquals(7.0, valueOf(WEIGHT, "7.4"))
        assertEquals(10.0, valueOf(WEIGHT, "12"))
        assertEquals(-10.0, valueOf(WEIGHT, "-11"))
        assertEquals(-3.0, valueOf(WEIGHT, "-3"))
    }

    @Test
    fun each_number_in_a_list_is_snapped_and_clamped_with_one_notice() {
        assertEquals(listOf(3.0, 10.0, -10.0), valueOf(WEIGHTS, "3,12,-14"))
        assertEquals(1, noticesOf(WEIGHTS, "3,12,-14").size)
    }

    @Test
    fun an_entry_with_no_step_or_range_leaves_numbers_alone() {
        assertEquals(43.3, valueOf(CUTOFF, "43.3"))
        assertEquals(1000.0, valueOf(CUTOFF, "1000"))
    }

    @Test
    fun a_clamped_stored_value_also_gives_a_notice() {
        val entry = SNAPPED_PERCENT.copy(query = null, store = "cut-store")
        val host = FakeShareHost(store = mutableMapOf("cut-store" to "400"))
        val start = resolveShared(listOf(entry), host)
        assertEquals(100.0, start.values["map.cutoff"])
        assertTrue(start.notices.single().contains("cut-store"))
    }
}

class ShareResolveTest {
    @Test
    fun a_value_in_the_address_wins_over_the_stored_one() {
        val host = FakeShareHost(query = mapOf("view" to "43.1,-85.3,14"), store = mutableMapOf("last-view" to "1,2,3"))
        assertEquals(listOf(43.1, -85.3, 14.0), resolveShared(listOf(VIEW), host).values["map.view"])
    }

    @Test
    fun a_bare_visit_takes_the_stored_value() {
        val host = FakeShareHost(store = mutableMapOf("last-view" to "1,2,3"))
        assertEquals(listOf(1.0, 2.0, 3.0), resolveShared(listOf(VIEW), host).values["map.view"])
    }

    @Test
    fun with_neither_the_specs_own_starting_value_is_left_alone() {
        assertEquals(emptyMap(), resolveShared(listOf(VIEW, PICKED), FakeShareHost()).values)
    }

    @Test
    fun the_store_fills_only_what_the_address_lacks() {
        val host = FakeShareHost(query = mapOf("open" to "0"), store = mutableMapOf("panel-open" to "1", "last-view" to "5,6,7"))
        val values = resolveShared(listOf(OPEN, VIEW), host).values
        assertEquals(false, values["panel.open"])
        assertEquals(listOf(5.0, 6.0, 7.0), values["map.view"])
    }

    @Test
    fun an_unknown_address_parameter_is_ignored() {
        val host = FakeShareHost(query = mapOf("zzz" to "1", "spec" to "/land.spec.json", "cut" to "45"))
        val start = resolveShared(listOf(CUTOFF), host)
        assertEquals(45.0, start.values["map.cutoff"])
        assertEquals(emptyList(), start.notices)
    }

    @Test
    fun a_malformed_address_value_is_ignored_with_a_notice_and_the_store_fills_in() {
        val host = FakeShareHost(query = mapOf("view" to "north,south"), store = mutableMapOf("last-view" to "1,2,3"))
        val start = resolveShared(listOf(VIEW), host)
        assertEquals(listOf(1.0, 2.0, 3.0), start.values["map.view"])
        assertTrue(start.notices.single().contains("view"), start.notices.toString())
    }

    @Test
    fun a_malformed_list_never_becomes_a_selection() {
        listOf("a,,b", "a b", ",", "ok,<script>").forEach { raw ->
            val start = resolveShared(listOf(PICKED), FakeShareHost(query = mapOf("select" to raw)))
            assertEquals(emptyMap(), start.values, "'$raw' was read as a selection")
            assertEquals(1, start.notices.size, "'$raw' gave no notice")
        }
    }

    @Test
    fun a_list_of_parcel_keys_reads_whole() {
        val host = FakeShareHost(query = mapOf("select" to "411635100014#751e247b4b9c,412009426001#ab12"))
        assertEquals(listOf("411635100014#751e247b4b9c", "412009426001#ab12"), resolveShared(listOf(PICKED), host).values["map.picked"])
    }

    @Test
    fun storage_that_throws_is_survived_and_the_address_still_applies() {
        val host = FakeShareHost(query = mapOf("view" to "1,2,3"), storeThrows = true)
        assertEquals(listOf(1.0, 2.0, 3.0), resolveShared(listOf(VIEW, OPEN), host).values["map.view"])
    }

    @Test
    fun an_empty_layers_parameter_means_no_layers_not_the_default() {
        assertEquals(emptyList<String>(), resolveShared(listOf(LAYERS), FakeShareHost(query = mapOf("layers" to ""))).values["map.layers"])
    }

    @Test
    fun bools_read_one_and_zero() {
        assertEquals(true, resolveShared(listOf(OPEN), FakeShareHost(query = mapOf("open" to "1"))).values["panel.open"])
        assertEquals(emptyMap(), resolveShared(listOf(OPEN), FakeShareHost(query = mapOf("open" to "maybe"))).values)
    }
}

class ShareSpecFieldTest {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @Test
    fun a_spec_lists_what_it_shares_with_the_address_and_the_store() {
        val spec = json.decodeFromString(
            ai.factoredui.compose.schema.Spec.serializer(),
            """{"spec_version":1,"renderer_min":1,"share":[{"path":"map.view","query":"view","store":"last-view","format":"numbers","decimals":5},{"path":"map.layers","query":"layers","format":"list","keep_empty":true}],"root":{"id":"n","type":"text"}}""",
        )
        assertEquals(listOf(VIEW, LAYERS), spec.share)
    }

    @Test
    fun a_spec_that_shares_nothing_has_an_empty_list() {
        val spec = json.decodeFromString(ai.factoredui.compose.schema.Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":{"id":"n","type":"text"}}""")
        assertEquals(emptyList(), spec.share)
    }
}

class ShareFormatTest {
    @Test
    fun numbers_are_written_rounded_with_no_trailing_zeros() {
        assertEquals("43.1,-85.31235,14", VIEW.render(listOf(43.1, -85.3123456, 14.0)))
    }

    @Test
    fun a_number_with_no_fraction_is_written_whole() {
        assertEquals("45", CUTOFF.render(45.0))
    }

    @Test
    fun a_bool_is_written_one_or_zero() {
        assertEquals("1", OPEN.render(true))
        assertEquals("0", OPEN.render(false))
    }

    @Test
    fun an_empty_list_is_left_out_unless_the_entry_keeps_it() {
        assertEquals(null, PICKED.render(emptyList<String>()))
        assertEquals("", LAYERS.render(emptyList<String>()))
    }

    @Test
    fun a_value_the_format_cannot_hold_is_left_out() {
        assertEquals(null, CUTOFF.render("forty"))
        assertEquals(null, VIEW.render(listOf(1.0, "x")))
        assertEquals(null, PICKED.render(null))
    }
}

class ShareWriterTest {
    private val entries = listOf(VIEW, PICKED, OPEN)

    @Test
    fun changed_state_is_written_to_the_address_and_the_store_and_foreign_parameters_stay() {
        val host = FakeShareHost(query = mapOf("spec" to "/land.spec.json", "full" to "1"))
        ShareWriter(entries, host).onData(mapOf("map" to mapOf("view" to listOf(43.1, -85.3, 14.0), "picked" to listOf("a#1")), "panel" to mapOf("open" to true)))
        assertEquals(mapOf("spec" to "/land.spec.json", "full" to "1", "view" to "43.1,-85.3,14", "select" to "a#1", "open" to "1"), host.query)
        assertEquals(mapOf("last-view" to "43.1,-85.3,14", "panel-open" to "1"), host.store)
    }

    @Test
    fun the_same_state_is_not_written_again() {
        val host = FakeShareHost()
        val writer = ShareWriter(entries, host)
        val data = mapOf("map" to mapOf("view" to listOf(1.0, 2.0, 3.0)))
        repeat(50) { writer.onData(data) }
        assertEquals(1, host.queryWrites)
        assertEquals(1, host.storeWrites)
    }

    @Test
    fun new_state_is_written_after_the_old() {
        val host = FakeShareHost()
        val writer = ShareWriter(entries, host)
        writer.onData(mapOf("map" to mapOf("view" to listOf(1.0, 2.0, 3.0))))
        writer.onData(mapOf("map" to mapOf("view" to listOf(1.0, 2.0, 4.0))))
        assertEquals(2, host.queryWrites)
        assertEquals("1,2,4", host.query["view"])
    }

    @Test
    fun a_cleared_value_leaves_its_parameter_out() {
        val host = FakeShareHost()
        val writer = ShareWriter(entries, host)
        writer.onData(mapOf("map" to mapOf("picked" to listOf("a#1"))))
        writer.onData(mapOf("map" to mapOf("picked" to emptyList<String>())))
        assertEquals(false, host.query.containsKey("select"))
    }

    @Test
    fun storage_that_throws_gives_one_notice_and_the_address_is_still_written() {
        val host = FakeShareHost(storeThrows = true)
        val writer = ShareWriter(entries, host)
        val first = writer.onData(mapOf("map" to mapOf("view" to listOf(1.0, 2.0, 3.0))))
        val second = writer.onData(mapOf("map" to mapOf("view" to listOf(1.0, 2.0, 4.0))))
        assertEquals("1,2,4", host.query["view"])
        assertEquals(1, first.size)
        assertEquals(emptyList(), second)
    }
}
