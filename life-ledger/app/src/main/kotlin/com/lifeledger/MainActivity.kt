package com.lifeledger

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeledger.core.designsystem.theme.LifeLedgerTheme
import com.lifeledger.ui.AppUiState
import com.lifeledger.ui.LifeLedgerApp
import com.lifeledger.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's single Activity.
 *
 * It extends [FragmentActivity] rather than `ComponentActivity` because `BiometricPrompt`
 * requires a fragment host — that is the only reason, and it is worth stating so nobody
 * "simplifies" it back and breaks unlock on every device.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash until preferences are loaded, so the app never renders in the
        // wrong theme for a frame and never flashes content before the lock screen.
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is AppUiState.Loading }

        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val windowSizeClass = calculateWindowSizeClass(this)

            LifeLedgerTheme(
                darkTheme = viewModel.shouldUseDarkTheme(uiState),
                dynamicColor = viewModel.shouldUseDynamicColor(uiState),
            ) {
                LifeLedgerApp(
                    uiState = uiState,
                    windowSizeClass = windowSizeClass,
                    activity = this,
                    onUnlockRequested = viewModel::unlock,
                    onSmsPermissionResult = viewModel::onSmsPermissionResult,
                    onOnboardingComplete = viewModel::completeOnboarding,
                )
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        // Feeds the idle timer that drives auto-lock; cheap enough to call on every touch.
        viewModel.onUserInteraction()
    }
}
