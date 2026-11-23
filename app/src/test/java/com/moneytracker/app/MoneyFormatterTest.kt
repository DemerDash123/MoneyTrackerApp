package com.moneytracker.app

import com.moneytracker.app.core.AmountParse
import com.moneytracker.app.core.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFormatterTest {
    private val omr = MoneyFormatter("OMR", 3)
    private val usd = MoneyFormatter("USD", 2)

    private fun ok(f: MoneyFormatter, s: String): Long = (f.parse(s) as AmountParse.Ok).milli

    @Test fun parsesWholeAndDecimalAmounts() {
        assertEquals(50_000L, ok(omr, "50"))
        assertEquals(12_345L, ok(omr, "12.345"))
        assertEquals(500L, ok(omr, "0.5"))
        assertEquals(500L, ok(omr, ".5"))
        assertEquals(1_250_750L, ok(omr, "1,250.750"))
        assertEquals(12_500L, ok(omr, "12,5"))
        assertEquals(1_250_000L, ok(omr, "1,250"))
        assertEquals(1L, ok(omr, "0.001"))
    }

    @Test fun acceptsArabicIndicDigits() {
        assertEquals(15_500L, ok(omr, "١٥٫٥"))
    }

    @Test fun rejectsZeroNegativeAndGarbage() {
        assertTrue(omr.parse("0") is AmountParse.Error)
        assertTrue(omr.parse("0.000") is AmountParse.Error)
        assertTrue(omr.parse("-5") is AmountParse.Error)
        assertTrue(omr.parse("abc") is AmountParse.Error)
        assertTrue(omr.parse("1.2.3") is AmountParse.Error)
        assertTrue(omr.parse("") is AmountParse.Empty)
        assertTrue(omr.parse("   ") is AmountParse.Empty)
    }

    @Test fun rejectsTooManyDecimals() {
        assertTrue(omr.parse("1.2345") is AmountParse.Error)
        assertTrue(usd.parse("1.234") is AmountParse.Error)
        assertEquals(1_230L, ok(usd, "1.23"))
        assertEquals(1_230L, ok(usd, "1.230"))
    }

    @Test fun rejectsAbsurdAmounts() {
        assertTrue(omr.parse("1000000001") is AmountParse.Error)
        assertTrue(omr.parse("99999999999999999999999") is AmountParse.Error)
    }

    @Test fun formatsWithConfiguredDecimals() {
        assertEquals("1,234.500 OMR", omr.format(1_234_500))
        assertEquals("0.015 OMR", omr.format(15))
        assertEquals("-20.000 OMR", omr.format(-20_000))
        assertEquals("1,234.50 USD", usd.format(1_234_500))
        assertEquals("+50.000 OMR", omr.signed(50_000, true))
        assertEquals("−20.000 OMR", omr.signed(20_000, false))
    }

    @Test fun inputRoundTrips() {
        for (v in listOf(1L, 500L, 12_345L, 100_000L, 1_000_000_000L)) {
            assertEquals(v, ok(omr, omr.input(v)))
        }
        assertEquals("100", omr.input(100_000))
        assertEquals("12.5", omr.input(12_500))
    }

    @Test fun compactLabels() {
        assertEquals("950", omr.compact(950_000))
        assertEquals("1.2K", omr.compact(1_200_000))
        assertEquals("3.4M", omr.compact(3_400_000_000))
        assertEquals("0", omr.compact(0))
    }
}
