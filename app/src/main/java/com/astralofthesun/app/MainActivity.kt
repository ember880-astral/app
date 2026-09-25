package com.astralofthesun.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.astralofthesun.app.network.ApiClient
import com.astralofthesun.app.network.Repository
import com.astralofthesun.app.ui.theme.AstralTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Wire the backend: all 60 endpoints live in network/AstralApi.kt,
        // Repository maps responses onto Astral's observable state.
        ApiClient.init(applicationContext)
        Repository.install()

        setContent {
            AstralTheme {
                App()
            }
        }
    }
}
