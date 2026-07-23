package com.ltvreader.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.ltvreader.app.AppContainer
import com.ltvreader.ui.navigation.Routes
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ltvreader.ui.navigation.LTVNavHost
import com.ltvreader.ui.theme.LTVTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LTVApp() }
    }
}

@Composable
fun LTVApp() {
    LTVTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            val settings by AppContainer.settings(LocalContext.current).flow.collectAsState(initial = null)
            // Do not render a transient editor first: the mode choice is required only once.
            if (settings != null) {
                val start = if (settings!!.onboardingCompleted) Routes.Editor else Routes.Onboarding
                LTVNavHost(navController, start)
            }
        }
    }
}
