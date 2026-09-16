package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.domain.StudioAccountManager
import com.example.ui.theme.StudioDarkBg

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    StudioAccountManager.init(this)
    setContent {
      MyApplicationTheme {
        val viewModel: StudioViewModel = viewModel()
        val currentScreen by viewModel.currentScreen.collectAsState()

        BackHandler(enabled = currentScreen != AppScreen.HOME) {
          when (currentScreen) {
            AppScreen.EDITOR -> {
              viewModel.saveCurrentProject()
              viewModel.navigateTo(AppScreen.HOME)
            }
            AppScreen.EXPORT -> viewModel.navigateTo(AppScreen.EDITOR)
            AppScreen.TEMPLATES, AppScreen.AI_SUITE, AppScreen.EXPORTED_LIBRARY, AppScreen.SETTINGS -> {
              viewModel.navigateTo(AppScreen.HOME)
            }
            AppScreen.HOME -> {}
          }
        }

        Surface(
          modifier = Modifier.fillMaxSize(),
          color = StudioDarkBg
        ) {
          Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
            when (screen) {
              AppScreen.HOME -> HomeScreen(viewModel = viewModel)
              AppScreen.EDITOR -> EditorScreen(viewModel = viewModel)
              AppScreen.EXPORT -> ExportScreen(viewModel = viewModel)
              AppScreen.TEMPLATES -> TemplatesScreen(viewModel = viewModel)
              AppScreen.AI_SUITE -> AISuiteScreen(viewModel = viewModel)
              AppScreen.EXPORTED_LIBRARY -> ExportedVideosScreen(viewModel = viewModel)
              AppScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
          }
        }
      }
    }
  }
}
