package ai.factoredui.compose.media

import kotlin.test.Test
import kotlin.test.assertEquals

class ElapsedFormatTest {
    @Test
    fun under_an_hour_it_reads_minutes_and_two_digit_seconds() {
        assertEquals(listOf("0:00", "0:09", "1:05", "59:59"), listOf(0.0, 9.4, 65.0, 3599.9).map(::formatElapsed))
    }

    @Test
    fun an_hour_or_more_adds_hours_and_two_digit_minutes() {
        assertEquals(listOf("1:00:00", "2:03:07"), listOf(3600.0, 7387.0).map(::formatElapsed))
    }

    @Test
    fun a_time_that_cannot_be_shown_reads_as_a_dash() {
        assertEquals(listOf("–", "–"), listOf(Double.NaN, Double.POSITIVE_INFINITY).map(::formatElapsed))
    }
}
