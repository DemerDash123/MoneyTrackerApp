package com.moneytracker.app.data

import android.content.Context
import com.moneytracker.app.core.MoneyFormatter
import com.moneytracker.app.i18n.AppLanguage
import com.moneytracker.app.i18n.I18n
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val currency: String = "OMR",
    val decimals: Int = 3,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.EN,
    /** False until the user has picked a language once (first-launch picker). */
    val languageChosen: Boolean = false,
) {
    val formatter: MoneyFormatter
        get() = MoneyFormatter(currency, decimals, isolate = language == AppLanguage.AR)
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    private fun read(): AppSettings = AppSettings(
        currency = prefs.getString(KEY_CURRENCY, "OMR") ?: "OMR",
        decimals = prefs.getInt(KEY_DECIMALS, 3).coerceIn(0, 3),
        theme = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM),
        language = readLanguage(prefs),
        languageChosen = prefs.contains(KEY_LANGUAGE),
    )

    /** Saves the language; the activity recreates itself to apply locale and layout direction. */
    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).commit()
        I18n.language = language
        state.value = read()
    }

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
        I18n.language = readLanguage(prefs)
        state.value = read()
    }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_LANGUAGE = "language"

        private fun readLanguage(prefs: android.content.SharedPreferences): AppLanguage {
            val saved = prefs.getString(KEY_LANGUAGE, null)
            if (saved != null) return runCatching { AppLanguage.valueOf(saved) }.getOrDefault(AppLanguage.EN)
            return if (Locale.getDefault().language == "ar") AppLanguage.AR else AppLanguage.EN
        }

        /** Reads the saved language without creating the repository (used before the activity attaches). */
        fun readLanguage(context: Context): AppLanguage =
            readLanguage(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

        private const val KEY_CURRENCY = "currency"
        private const val KEY_DECIMALS = "decimals"
        private const val KEY_THEME = "theme"
    }
}
