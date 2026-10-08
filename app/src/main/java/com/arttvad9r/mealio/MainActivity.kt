package com.arttvad9r.mealio

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.arttvad9r.mealio.ui.navigation.MealioRoot
import com.arttvad9r.mealio.ui.theme.MealioTheme

/**
 * Extends [AppCompatActivity] (not bare ComponentActivity) so the AppCompat
 * per-app locale API can apply the chosen language on Android 12 and lower too.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MealioApp).container

        setContent {
            val themeMode by container.settingsStore.themeMode.collectAsState()
            MealioTheme(themeMode = themeMode) {
                MealioRoot(container = container)
            }
        }
    }
}
