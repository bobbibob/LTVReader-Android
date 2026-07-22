package com.ltvreader.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
            LTVNavHost(navController)
        }
    }
}
