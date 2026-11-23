package com.moneytracker.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneytracker.app.TestSupport.u
import com.moneytracker.app.data.CategoryInput
import com.moneytracker.app.data.TxDraft
import com.moneytracker.app.data.db.TxType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run in two separate processes by CI:
 *   1. PersistenceWriteTest writes data into the real app database.
 *   2. The app process is force-stopped (and later the emulator is rebooted).
 *   3. PersistenceVerifyTest runs in a brand-new process and checks the data is still there, exactly once.
 */
@RunWith(AndroidJUnit4::class)
class PersistenceWriteTest {
    @Test fun writeKnownData() = runBlocking<Unit> {
        TestSupport.resetApp()
        val f = TestSupport.container.finance
        val now = System.currentTimeMillis() - 60_000
        val id = f.createCategory(CategoryInput("Persisted fund", "savings", 0xFF10B981.toInt()))
        f.addTransaction(TxDraft(TxType.INCOME, u(777.777), null, null, "Persistence check", "survives restarts", now))
        f.addTransaction(TxDraft(TxType.TRANSFER, u(111.111), null, id, "", "", now))
        f.addTransaction(TxDraft(TxType.EXPENSE, u(11.111), id, null, "Persisted expense", "", now))
        TestSupport.container.settings.setCurrency("OMR", 3)
    }
}

@RunWith(AndroidJUnit4::class)
class PersistenceVerifyTest {
    @Test fun dataIsStillThere() = runBlocking<Unit> {
        val s = TestSupport.container.finance.currentSnapshot()
        assertEquals(3, s.transactions.size)
        assertEquals(1, s.transactions.count { it.description == "Persistence check" })
        val fund = s.categories.single { it.name == "Persisted fund" }
        assertEquals(u(766.666), s.balances.total)
        assertEquals(u(100.0), s.balances.of(fund.id))
        assertEquals(u(666.666), s.balances.available)
        assertEquals("survives restarts", s.transactions.single { it.description == "Persistence check" }.note)
    }
}
