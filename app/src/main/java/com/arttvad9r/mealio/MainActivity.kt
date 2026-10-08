package com.arttvad9r.mealio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.arttvad9r.mealio.ui.navigation.MealioRoot
import com.arttvad9r.mealio.ui.theme.MealioTheme

class MainActivity : ComponentActivity() {

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
