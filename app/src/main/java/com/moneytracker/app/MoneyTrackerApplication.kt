package com.moneytracker.app

import android.app.Application
import android.content.Context
import com.moneytracker.app.data.BackupManager
import com.moneytracker.app.data.FinanceRepository
import com.moneytracker.app.data.SettingsRepository
import com.moneytracker.app.data.db.AppDatabase

/** Manual dependency container: one database, one repository for the whole app. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val finance = FinanceRepository(database, { settings.settings.value.formatter })
    val backup = BackupManager(context, finance, settings)
}

class MoneyTrackerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.moneytracker.app.i18n.I18n.language = SettingsRepository.readLanguage(this)
        container = AppContainer(this)
    }
}
