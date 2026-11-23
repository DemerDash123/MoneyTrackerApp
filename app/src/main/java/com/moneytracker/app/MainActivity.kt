package com.moneytracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneytracker.app.ui.LocalMoney
import com.moneytracker.app.ui.MoneyTrackerRoot
import com.moneytracker.app.ui.theme.MoneyTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MoneyTrackerApplication).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            MoneyTrackerTheme(mode = settings.theme) {
                CompositionLocalProvider(LocalMoney provides settings.formatter) {
                    MoneyTrackerRoot()
                }
            }
        }
    }
}
