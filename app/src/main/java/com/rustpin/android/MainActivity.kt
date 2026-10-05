package com.rustpin.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.*
import com.rustpin.android.ui.AppViewModel
import com.rustpin.android.ui.Routes
import com.rustpin.android.ui.RustpinTheme
import com.rustpin.android.ui.screens.*

/** Dark-only theme. No dock, no bottom bar: Browse <-> Settings via nav, detail overlays all. */
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RustpinTheme(accent = vm.effectiveAccent()) {
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                    }
                    @Suppress("DEPRECATION")
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                }
                val nav = rememberNavController()
                NavHost(nav, startDestination = Routes.BROWSE, modifier = Modifier.fillMaxSize()) {
                    composable(Routes.BROWSE) { BrowseScreen(vm, onOpenSettings = { nav.navigate(Routes.SETTINGS) }) }
                    composable(Routes.SETTINGS) { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
                }
                vm.selected?.let { pin ->
                    // system back / gesture: fullscreen -> viewer, detail -> feed (never kills the app)
                    BackHandler { if (vm.fullscreen) vm.toggleFullscreen() else vm.closeDetail() }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        DetailScreen(vm, pin, onBack = vm::closeDetail)
                    }
                }
            }
        }
    }
}
