package ai.factoredui.worldengine

import ai.factoredui.worldengine.units.describeExponentRefusal
import ai.factoredui.worldengine.units.isExponentTooLarge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecimalBoundTest {

    private val oneThenZeros = { zeros: Int -> "1" + "0".repeat(zeros) }

    @Test
    fun anExponentOfFourHundredIsReadAndFourHundredAndOneIsNot() {
        assertFalse(isExponentTooLarge("1e400"))
        assertTrue(isExponentTooLarge("1e401"))
        assertFalse(isExponentTooLarge("1e-400"))
        assertTrue(isExponentTooLarge("1e-401"))
    }

    @Test
    fun theBoundIsOnTheLeadingDigitNotOnTheWrittenExponent() {
        assertFalse(isExponentTooLarge("10e399"))
        assertTrue(isExponentTooLarge("10e400"))
        assertFalse(isExponentTooLarge("0.1e401"))
        assertTrue(isExponentTooLarge("0.01e-399"))
        assertFalse(isExponentTooLarge("0.01e-398"))
    }

    @Test
    fun anIntegerIsBoundedByItsDigitsAndNotOnlyByAnExponent() {
        assertFalse(isExponentTooLarge(oneThenZeros(400)))
        assertTrue(isExponentTooLarge(oneThenZeros(401)))
    }

    @Test
    fun aFractionIsBoundedByItsLeadingZeros() {
        assertFalse(isExponentTooLarge("0." + "0".repeat(399) + "1"))
        assertTrue(isExponentTooLarge("0." + "0".repeat(400) + "1"))
    }

    @Test
    fun anExponentTooLongForAnyIntegerIsRefusedFromItsText() {
        assertTrue(isExponentTooLarge("1e99999999999999999999"))
        assertTrue(isExponentTooLarge("1e-99999999999999999999"))
        assertTrue(isExponentTooLarge("1e999999999"))
        assertTrue(isExponentTooLarge("1e-999999999"))
    }

    @Test
    fun zeroInEveryWrittenFormIsWithinTheBound() {
        listOf("0", "0.0", "-0", "0e5", "0.000", "00").forEach { assertFalse(isExponentTooLarge(it), it) }
        assertTrue(isExponentTooLarge("0e401"))
    }

    @Test
    fun aSignOnTheNumberOrItsExponentChangesNothingAboutTheBound() {
        assertTrue(isExponentTooLarge("-1e401"))
        assertFalse(isExponentTooLarge("-1e400"))
        assertFalse(isExponentTooLarge("1e+400"))
        assertTrue(isExponentTooLarge("1E+401"))
    }

    @Test
    fun aShortNumeralIsNamedWholeAndALongOneByItsFirstTwelveCharacters() {
        assertEquals("number-exponent-too-large: 1e401 has a decimal exponent beyond ±400", describeExponentRefusal("1e401"))
        val long = oneThenZeros(401)
        assertEquals("number-exponent-too-large: 100000000000... (402 characters) has a decimal exponent beyond ±400", describeExponentRefusal(long))
    }
}
