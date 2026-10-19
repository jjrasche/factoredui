package ai.factoredui.compose.volume

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun box(id: String, x: Number = 0, y: Number = 0, z: Number = 0, width: Any? = 10, depth: Any? = 10, height: Any? = 10, extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
    mapOf("id" to id, "x_mm" to x, "y_mm" to y, "z_mm" to z, "width_mm" to width, "depth_mm" to depth, "height_mm" to height) + extra

class VolumeBoxesTest {
    @Test
    fun a_box_is_read_with_its_style_its_turn_and_its_estimate_mark() {
        val read = readVolume(
            listOf(box("a", 5, 6, 7, 2, 3, 4, mapOf("rotation_deg" to 90, "style" to "base", "estimate" to true))),
            mapOf("base" to mapOf("stroke" to "#112233")),
            listOf(mapOf("label" to "Base", "style" to "base"), mapOf("label" to "Unknown", "hatched" to true)),
        )
        assertEquals(VolumeBox("a", 5.0, 6.0, 7.0, 2.0, 3.0, 4.0, 90.0, "base", true), read.boxes.single())
        assertEquals(listOf(VolumeLegendEntry("Base", "base", false), VolumeLegendEntry("Unknown", null, true)), read.legend)
        assertEquals(emptyList(), read.notices)
    }

    @Test
    fun a_box_without_numbers_for_every_size_is_dropped_and_named() {
        val read = readVolume(listOf(box("no-height", height = null), box("negative", width = -1), mapOf("id" to "no-place", "width_mm" to 1), "not an object"), null, null)
        assertEquals(emptyList(), read.boxes)
        assertEquals(
            listOf(
                "volume box 'no-height': width_mm, depth_mm and height_mm must be numbers of 0 or more; it is not drawn.",
                "volume box 'negative': width_mm, depth_mm and height_mm must be numbers of 0 or more; it is not drawn.",
                "volume box 'no-place': x_mm, y_mm and z_mm must be numbers; it is not drawn.",
                "volume box 3 is not an object; it is not drawn.",
            ),
            read.notices,
        )
    }

    @Test
    fun a_style_the_styles_do_not_hold_is_named_once() {
        val read = readVolume(listOf(box("a", extra = mapOf("style" to "ghost")), box("b", extra = mapOf("style" to "ghost"))), null, null)
        assertEquals(listOf("volume style 'ghost' is not in styles; its boxes are drawn plain."), read.notices)
    }

    @Test
    fun the_span_follows_the_turned_corners_and_the_height() {
        val turned = VolumeBox("t", 0.0, 0.0, 100.0, 10.0, 2.0, 5.0, rotationDeg = 90.0)
        val span = volumeSpan(listOf(turned, VolumeBox("g", 20.0, 0.0, 0.0, 2.0, 2.0, 1.0)))!!
        assertEquals(-1.0, span.west, 1e-9)
        assertEquals(-5.0, span.south, 1e-9)
        assertEquals(21.0, span.east, 1e-9)
        assertEquals(5.0, span.north, 1e-9)
        assertEquals(0.0, span.floor)
        assertEquals(105.0, span.ceiling)
        assertTrue(volumeSpan(emptyList()) == null)
    }

    @Test
    fun a_plane_lying_in_another_plane_is_painted_after_it_wherever_the_eye_sorts_them() {
        val wall = VolumeBox("wall", 1829.0, 2438.0, 600.0, 2438.0, 0.0, 3658.0)
        val glass = VolumeBox("glass", 1829.0, 2438.0, 600.0, 2438.0, 0.0, 400.0, isEstimate = true)
        val northGlass = VolumeBox("north-glass", 3048.0, 3657.0, 600.0, 2438.0, 0.0, 400.0, rotationDeg = 90.0)
        val sideWall = VolumeBox("side", 3048.0, 3657.0, 600.0, 0.0, 2438.0, 3658.0)
        assertTrue(isDrawnOn(glass, wall))
        assertTrue(isDrawnOn(northGlass, sideWall))
        assertEquals(false, isDrawnOn(glass, sideWall))
        assertEquals(false, isDrawnOn(wall, glass))
        assertEquals(listOf("wall", "glass"), paintOnTop(listOf(glass, wall)) { it }.map { it.id })
        assertEquals(listOf("wall", "glass"), paintOnTop(listOf(wall, glass)) { it }.map { it.id })
    }

    @Test
    fun a_line_and_a_label_are_read_in_millimetres_and_one_without_a_point_is_dropped_and_named() {
        val read = readVolume(
            emptyList<Any>(), null, null,
            lines = listOf(mapOf("id" to "seam", "from" to listOf(1, 2, 3), "to" to listOf(1, 2, 9), "style" to "seam"), mapOf("id" to "bad", "from" to listOf(1, 2))),
            labels = listOf(mapOf("id" to "width", "at" to listOf(0, 0, 0), "text" to "2438 mm"), mapOf("id" to "lost", "at" to listOf(0, 0, 0))),
        )
        assertEquals(VolumeLine("seam", VolumePoint(1.0, 2.0, 3.0), VolumePoint(1.0, 2.0, 9.0), "seam"), read.lines.single())
        assertEquals(VolumeLabel("width", VolumePoint(0.0, 0.0, 0.0), "2438 mm"), read.labels.single())
        assertEquals(
            listOf(
                "volume line 'bad': from and to must each be [x_mm, y_mm, z_mm]; it is not drawn.",
                "volume label 'lost': at must be [x_mm, y_mm, z_mm] and text must be words; it is not drawn.",
            ),
            read.notices,
        )
    }
}
