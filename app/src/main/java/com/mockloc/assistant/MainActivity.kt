package com.mockloc.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mockloc.assistant.ui.home.HomeViewModel
import com.mockloc.assistant.ui.navigation.AppNavigation
import com.mockloc.assistant.ui.theme.MockLocTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            MockLocTheme {
                AppNavigation(viewModel = viewModel)
            }
        }
    }

    companion object {
        const val EXTRA_RESTORE_SESSION = "com.mockloc.assistant.EXTRA_RESTORE_SESSION"
    }
}
