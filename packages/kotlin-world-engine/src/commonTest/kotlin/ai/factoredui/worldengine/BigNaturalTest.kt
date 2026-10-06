package ai.factoredui.worldengine

import ai.factoredui.worldengine.units.BigNatural
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BigNaturalTest {
    private fun natural(digits: String): BigNatural = BigNatural.parse(digits)

    @Test
    fun parsing_drops_leading_zeros_and_keeps_every_limb() {
        assertEquals("0", natural("000").toString())
        assertTrue(natural("000").isZero)
        assertEquals("1000000000", natural("0001000000000").toString())
        assertEquals("123456789012345678901234567890123456789012", natural("123456789012345678901234567890123456789012").toString())
    }

    @Test
    fun a_longer_number_is_larger_across_a_limb_boundary() {
        assertTrue(natural("999999999") < natural("1000000000"))
        assertTrue(natural("1000000000") > natural("999999999"))
    }

    @Test
    fun the_most_significant_differing_limb_decides_the_order() {
        assertTrue(natural("1000000000000000001") < natural("2000000000000000000"))
        assertTrue(natural("2000000000000000000") > natural("1000000000000000001"))
        assertEquals(0, natural("1000000000000000001").compareTo(natural("0001000000000000000001")))
    }

    @Test
    fun a_forty_digit_number_ordered_by_its_last_digit() {
        assertTrue(natural("1234567890123456789012345678901234567890") < natural("1234567890123456789012345678901234567891"))
    }

    @Test
    fun adding_carries_across_limbs_and_lengths() {
        assertEquals("1000000000", (natural("999999999") + natural("1")).toString())
        assertEquals("1" + "0".repeat(30), (natural("9".repeat(30)) + natural("1")).toString())
        assertEquals("12345678901234567891", (natural("12345678901234567890") + natural("1")).toString())
        assertEquals("1000000000000000005", (natural("999999999999999999") + natural("6")).toString())
        assertEquals("0", (BigNatural.ZERO + BigNatural.ZERO).toString())
    }

    @Test
    fun multiplying_carries_across_every_limb() {
        assertEquals("999999998000000001", (natural("999999999") * natural("999999999")).toString())
        assertEquals("9".repeat(40), (natural("9".repeat(20)) * natural("1" + "0".repeat(19) + "1")).toString())
        assertEquals("1219326311370217952237463801111263526900", (natural("12345678901234567890") * natural("98765432109876543210")).toString())
        assertEquals(
            "15241578753238836750495351562536198787501905199875019052100",
            (natural("123456789012345678901234567890") * natural("123456789012345678901234567890")).toString(),
        )
    }

    @Test
    fun multiplying_by_zero_is_zero() {
        assertTrue((natural("123456789012345678901") * BigNatural.ZERO).isZero)
        assertTrue((BigNatural.ZERO * 7L).isZero)
    }

    @Test
    fun multiplying_by_a_long_is_exact_past_the_long_range() {
        assertEquals("1111111101111111110109000000000000000000", (natural("123456789012345678901") * 9_000_000_000_000_000_000L).toString())
    }

    @Test
    fun the_magnitude_of_the_smallest_long_is_exact() {
        assertEquals("9223372036854775808", BigNatural.magnitudeOf(Long.MIN_VALUE).toString())
        assertEquals("1000000000", BigNatural.magnitudeOf(-1_000_000_000L).toString())
    }

    @Test
    fun a_power_of_ten_shifts_within_and_across_limbs() {
        assertEquals("123", natural("123").timesPowerOfTen(0).toString())
        assertEquals("12300000000", natural("123").timesPowerOfTen(8).toString())
        assertEquals("123000000000", natural("123").timesPowerOfTen(9).toString())
        assertEquals("123" + "0".repeat(25), natural("123").timesPowerOfTen(25).toString())
        assertEquals("9999999990", natural("999999999").timesPowerOfTen(1).toString())
        assertTrue(BigNatural.ZERO.timesPowerOfTen(30).isZero)
    }
}
