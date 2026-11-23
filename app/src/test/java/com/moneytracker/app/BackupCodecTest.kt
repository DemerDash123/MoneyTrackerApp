package com.moneytracker.app

import com.moneytracker.app.core.MoneyFormatter
import com.moneytracker.app.data.AppSettings
import com.moneytracker.app.data.BackupCodec
import com.moneytracker.app.data.BackupData
import com.moneytracker.app.data.ThemeMode
import com.moneytracker.app.domain.FinanceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private fun sample(): BackupData {
        val b = Books()
        val food = b.cat("Food", limit = 100.0)
        val trip = b.cat("Trip, \"Salalah\"", target = 300.0)
        b.income(500.0)
        b.transfer(100.0, null, food)
        b.transfer(50.0, food, trip)
        b.expense(12.345, food)
        return BackupData(AppSettings("OMR", 3, ThemeMode.DARK), b.categories, b.txs)
    }

    @Test fun roundTripPreservesEverything() {
        val data = sample()
        val decoded = BackupCodec.decode(BackupCodec.encode(data, exportedAt = 123L))
        assertEquals(data.categories, decoded.categories)
        assertEquals(data.transactions, decoded.transactions)
        assertEquals(data.settings, decoded.settings)
    }

    private fun expectFailure(json: String) {
        try {
            BackupCodec.decode(json)
            fail("Expected FinanceException")
        } catch (e: FinanceException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test fun rejectsInvalidFiles() {
        expectFailure("not json at all")
        expectFailure("{\"app\":\"Other\",\"formatVersion\":1,\"categories\":[],\"transactions\":[]}")
        expectFailure("{\"app\":\"MoneyTrackerApp\",\"formatVersion\":99,\"categories\":[],\"transactions\":[]}")
        val good = BackupCodec.encode(sample(), 0)
        expectFailure(good.replace("\"amount\": 500000", "\"amount\": -5"))
        expectFailure(good.replace("\"type\": \"INCOME\"", "\"type\": \"BOGUS\""))
        expectFailure(good.substring(0, good.length / 2))
    }

    @Test fun rejectsDanglingCategoryReference() {
        val d = sample()
        val broken = d.copy(categories = d.categories.drop(1))
        expectFailure(BackupCodec.encode(broken, 0))
    }

    @Test fun csvEscapesAndSigns() {
        val d = sample()
        val csv = BackupCodec.csv(d.transactions, d.categories, MoneyFormatter("OMR", 3), Books().zone)
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(5, lines.size)
        assertTrue(lines[0].startsWith("Date,Time,Type,Amount"))
        assertTrue(lines.any { it.contains("Expense,12.345,-12.345,OMR") })
        assertTrue(lines.any { it.contains("\"Trip, \"\"Salalah\"\"\"") })
        assertTrue(lines[1].contains("Income,500.000,500.000"))
    }
}
