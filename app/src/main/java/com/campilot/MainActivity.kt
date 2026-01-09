package com.campilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.campilot.feature.home.HomeRoute
import com.campilot.ui.theme.CamPilotTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CamPilotApp()
        }
    }
}

@Composable
private fun CamPilotApp() {
    CamPilotTheme {
        HomeRoute(modifier = Modifier.fillMaxSize())
    }
}
