package com.mosquetracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.mosquetracker.app.monitoring.MonitoringController
import com.mosquetracker.app.ui.MainScreen
import com.mosquetracker.app.ui.theme.MosqueTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var monitoringController: MonitoringController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MosqueTrackerTheme {
                MainScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The one moment every Android version allows a location service to start: the app is
        // on screen. Detection that the system stopped, or refused to start, resumes here, and
        // fences refused for a permission granted meanwhile in system settings are retried.
        lifecycleScope.launch { monitoringController.recheck() }
    }
}
