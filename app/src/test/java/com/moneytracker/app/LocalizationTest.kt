package com.moneytracker.app

import com.moneytracker.app.core.AmountParse
import com.moneytracker.app.core.Dates
import com.moneytracker.app.core.MoneyFormatter
import com.moneytracker.app.data.db.TxType
import com.moneytracker.app.domain.Granularity
import com.moneytracker.app.i18n.AppLanguage
import com.moneytracker.app.i18n.ArStrings
import com.moneytracker.app.i18n.EnStrings
import com.moneytracker.app.i18n.I18n
import com.moneytracker.app.i18n.Strings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.time.LocalDate
import java.time.YearMonth

class LocalizationTest {

    @After fun backToEnglish() {
        I18n.language = AppLanguage.EN
    }

    private val arabicLetter = Regex("[\\u0600-\\u06FF]")

    /** Every text property (and list) of the string table, by name. */
    private fun texts(s: Strings): Map<String, String> =
        Strings::class.java.methods
            .filter { it.parameterCount == 0 && it.name.startsWith("get") && !Modifier.isStatic(it.modifiers) }
            .filter { it.returnType == String::class.java || it.returnType == List::class.java }
            .associate { m ->
                val v = m.invoke(s)
                m.name to if (v is List<*>) v.joinToString("|") else v as String
            }

    @Test fun everyArabicTextIsTranslated() {
        val en = texts(EnStrings)
        val ar = texts(ArStrings)
        assertEquals(en.keys, ar.keys)
        assertTrue("string table looks empty", en.size > 150)
        // Pure symbols are allowed to be identical.
        val symbolsOnly = setOf("getArrow")
        val untranslated = ar.filter { (k, v) -> k !in symbolsOnly && !arabicLetter.containsMatchIn(v) }
        assertTrue("Untranslated Arabic strings: ${untranslated.keys}", untranslated.isEmpty())
        val sameAsEnglish = ar.filter { (k, v) -> k !in symbolsOnly && v == en[k] }
        assertTrue("Arabic equals English for: ${sameAsEnglish.keys}", sameAsEnglish.isEmpty())
        assertEquals(EnStrings.suggestionNames.size, ArStrings.suggestionNames.size)
        assertEquals(EnStrings.incomeSuggestions.size, ArStrings.incomeSuggestions.size)
        assertEquals(EnStrings.csvHeader.size, ArStrings.csvHeader.size)
    }

    @Test fun parameterisedArabicStrings() {
        assertEquals("معاملة واحدة", ArStrings.transactionsCount(1))
        assertEquals("معاملتان", ArStrings.transactionsCount(2))
        assertEquals("5 معاملات", ArStrings.transactionsCount(5))
        assertEquals("24 معاملة", ArStrings.transactionsCount(24))
        assertEquals("متبقٍ 120 يوماً", ArStrings.daysLeft(120))
        assertTrue(ArStrings.notEnoughIn("طعام", "1.000 OMR").contains("طعام"))
        for (t in TxType.entries) assertTrue(arabicLetter.containsMatchIn(ArStrings.typeName(t)))
        for (g in Granularity.entries) {
            assertTrue(arabicLetter.containsMatchIn(ArStrings.granularity(g)))
            assertTrue(ArStrings.averagePer("5.000 OMR", g).contains("5.000 OMR"))
        }
        assertEquals("1 transaction", EnStrings.transactionsCount(1))
        assertEquals("3 transactions", EnStrings.transactionsCount(3))
    }

    @Test fun languageSwitchChangesStringsAndDates() {
        I18n.language = AppLanguage.EN
        assertEquals("November 2026", YearMonth.of(2026, 11).format(Dates.monthYear))
        assertEquals("Save", com.moneytracker.app.i18n.S.save)

        I18n.language = AppLanguage.AR
        val month = YearMonth.of(2026, 11).format(Dates.monthYear)
        assertTrue("Arabic month name expected, got $month", arabicLetter.containsMatchIn(month))
        assertTrue("Western digits expected, got $month", month.contains("2026"))
        val day = LocalDate.of(2026, 11, 24).format(Dates.fullDate)
        assertTrue(arabicLetter.containsMatchIn(day) && day.contains("24"))
        assertEquals("حفظ", com.moneytracker.app.i18n.S.save)
        // Validation messages follow the language too.
        val fmt = MoneyFormatter("OMR", 3)
        assertEquals(ArStrings.amountGtZero, (fmt.parse("0") as AmountParse.Error).message)
        assertEquals(ArStrings.invalidAmount, (fmt.parse("abc") as AmountParse.Error).message)
    }

    @Test fun amountsAreIsolatedForRightToLeftText() {
        val ar = MoneyFormatter("OMR", 3, isolate = true)
        assertEquals("⁦-12.500 OMR⁩", ar.format(-12_500))
        assertEquals("⁦−7.000 OMR⁩", ar.signed(7_000, false))
        assertEquals("1,234.500", ar.number(1_234_500))
        // Arabic-Indic digits typed on an Arabic keyboard are accepted.
        assertEquals(25_000L, (ar.parse("٢٥") as AmountParse.Ok).milli)
        val en = MoneyFormatter("OMR", 3)
        assertEquals("-12.500 OMR", en.format(-12_500))
    }
}
