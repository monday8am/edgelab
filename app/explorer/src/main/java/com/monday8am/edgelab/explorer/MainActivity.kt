package com.monday8am.edgelab.explorer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.monday8am.edgelab.explorer.ui.navigation.AppNavigation
import com.monday8am.edgelab.explorer.ui.theme.EdgeLabTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize service locator with application context
        Dependencies.appContext = applicationContext

        // Only handle intent if we are starting fresh, to avoid re-processing 
        // stale intents on recreation (e.g. rotation or process restoration)
        if (savedInstanceState == null) {
            Dependencies.oAuthManager.onHandleIntent(intent)
        }

        setContent {
            EdgeLabTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Dependencies.oAuthManager.onHandleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            Dependencies.dispose()
        }
    }
}
