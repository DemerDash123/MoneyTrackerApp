package com.moneytracker.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneytracker.app.data.SettingsRepository
import com.moneytracker.app.i18n.AppLanguage
import com.moneytracker.app.i18n.I18n
import com.moneytracker.app.ui.LocalMoney
import com.moneytracker.app.ui.MoneyTrackerRoot
import com.moneytracker.app.ui.theme.MoneyTrackerTheme

class MainActivity : ComponentActivity() {

    /** The language this activity instance was created with. */
    private lateinit var appliedLanguage: AppLanguage

    override fun attachBaseContext(newBase: Context) {
        val language = SettingsRepository.readLanguage(newBase)
        appliedLanguage = language
        I18n.language = language
        super.attachBaseContext(localized(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MoneyTrackerApplication).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            // Switching language rebuilds the screen with the new locale and layout direction
            // (right-to-left for Arabic). Navigation state is kept by the activity's saved state.
            LaunchedEffect(settings.language) {
                if (settings.language != appliedLanguage) recreate()
            }
            MoneyTrackerTheme(mode = settings.theme) {
                CompositionLocalProvider(LocalMoney provides settings.formatter) {
                    MoneyTrackerRoot()
                }
            }
        }
    }

    companion object {
        fun localized(base: Context, language: AppLanguage): Context {
            val locale = I18n.locale.takeIf { language == I18n.language } ?: java.util.Locale.forLanguageTag(language.tag)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }
    }
}
