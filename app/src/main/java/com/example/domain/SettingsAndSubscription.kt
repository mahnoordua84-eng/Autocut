package com.example.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
  val language: String = "English",
  val autoSaveIntervalSec: Int = 15,
  val defaultResolution: String = "1080p",
  val previewQualityProxy: Boolean = false,
  val timelineSnapping: Boolean = true,
  val rippleEditing: Boolean = true,
  val userEmail: String = "creator@ahvideostudio.com",
  val userName: String = "Pro Creator",
  val isProSubscriber: Boolean = true,
  val cloudSyncEnabled: Boolean = false
)

object StudioPreferencesManager {
  private val _settings = MutableStateFlow(UserSettings())
  val settings: StateFlow<UserSettings> = _settings.asStateFlow()

  fun updateLanguage(lang: String) {
    _settings.value = _settings.value.copy(language = lang)
  }

  fun updateSnapping(enabled: Boolean) {
    _settings.value = _settings.value.copy(timelineSnapping = enabled)
  }

  fun updateProxyMode(enabled: Boolean) {
    _settings.value = _settings.value.copy(previewQualityProxy = enabled)
  }

  fun toggleProSubscription() {
    _settings.value = _settings.value.copy(isProSubscriber = !_settings.value.isProSubscriber)
  }

  fun clearAppCache(): Long {
    // Returns cleared bytes
    return 48_500_000L // 48.5 MB
  }
}
