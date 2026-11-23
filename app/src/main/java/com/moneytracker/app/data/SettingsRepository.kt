package com.moneytracker.app.data

import android.content.Context
import com.moneytracker.app.core.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val currency: String = "OMR",
    val decimals: Int = 3,
    val theme: ThemeMode = ThemeMode.SYSTEM,
) {
    val formatter: MoneyFormatter get() = MoneyFormatter(currency, decimals)
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    private fun read(): AppSettings = AppSettings(
        currency = prefs.getString(KEY_CURRENCY, "OMR") ?: "OMR",
        decimals = prefs.getInt(KEY_DECIMALS, 3).coerceIn(0, 3),
        theme = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM),
    )

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).commit()
        state.value = read()
    }

    fun setCurrency(code: String, decimals: Int) {
        prefs.edit()
            .putString(KEY_CURRENCY, code.trim().take(6))
            .putInt(KEY_DECIMALS, decimals.coerceIn(0, 3))
            .commit()
        state.value = read()
    }

    fun reset() {
        prefs.edit().clear().commit()
        state.value = read()
    }

    companion object {
        private const val KEY_CURRENCY = "currency"
        private const val KEY_DECIMALS = "decimals"
        private const val KEY_THEME = "theme"
    }
}
