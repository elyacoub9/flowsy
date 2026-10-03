package com.elyacoub9.flowsy

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elyacoub9.flowsy.ui.navigation.FlowsyNavHost
import com.elyacoub9.flowsy.ui.onboarding.OnboardingRoute
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            FlowsyTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val restoreCount by viewModel.restoreCount.collectAsStateWithLifecycle()
                // Provides LocalContentColor = onBackground to every screen below.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (state) {
                        AppState.LOADING -> Unit
                        AppState.ONBOARDING -> OnboardingRoute()
                        // A new key after a restore: fresh navigation and screens (Month, current month).
                        AppState.READY -> key(restoreCount) { FlowsyNavHost() }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}
