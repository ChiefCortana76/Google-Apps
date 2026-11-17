package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ALARM_TONE_CUSTOM
import com.example.audio.BundledAlarmTone
import com.example.data.model.RefreshInterval
import com.example.data.model.ThemeMode
import com.example.data.repository.AppPreferences
import com.example.data.repository.DEFAULT_BOTTOM_BAR_ORDER
import com.example.data.repository.PreferencesRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreferencesViewModel(
  private val repository: PreferencesRepository,
  private val currentUserId: String?,
) : ViewModel() {

  val preferences: StateFlow<AppPreferences> =
    repository.localPreferences.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = AppPreferences(),
    )

  private val _saveError = MutableStateFlow<String?>(null)
  val saveError: StateFlow<String?> = _saveError.asStateFlow()

  init {
    if (currentUserId != null) {
      viewModelScope.launch {
        repository
          .observeRemotePreferences(currentUserId)
          .catch { error ->
            val firestoreError =
              generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Failed to sync remote preferences", firestoreError ?: error)
          }
          .collect { remote ->
            if (remote != null) {
              val local = repository.localPreferences.first()
              repository.saveLocalPreferences(
                local.copy(
                  themeMode = remote.themeMode,
                  marketOpenMinutesEt = remote.marketOpenMinutesEt,
                  marketCloseMinutesEt = remote.marketCloseMinutesEt,
                  refreshInterval = remote.refreshInterval,
                )
              )
            }
          }
      }
    }
  }

  fun setThemeMode(mode: ThemeMode) {
    updatePrefs { it.copy(themeMode = mode) }
  }

  fun setMarketHours(openMinutesEt: Int, closeMinutesEt: Int) {
    updatePrefs {
      it.copy(
        marketOpenMinutesEt = openMinutesEt.coerceIn(0, 1439),
        marketCloseMinutesEt = closeMinutesEt.coerceIn(0, 1439),
      )
    }
  }

  fun setRefreshInterval(interval: RefreshInterval) {
    updatePrefs { it.copy(refreshInterval = interval) }
  }

  fun setAlarmEnabled(enabled: Boolean) {
    updateLocalOnlyPrefs { it.copy(alarmEnabled = enabled) }
  }

  fun selectBundledAlarmTone(tone: BundledAlarmTone) {
    updateLocalOnlyPrefs {
      it.copy(
        alarmEnabled = true,
        alarmToneId = tone.id,
      )
    }
  }

  fun selectExistingCustomAlarmTone() {
    updateLocalOnlyPrefs { prefs ->
      if (!prefs.alarmCustomUri.isNullOrBlank()) {
        prefs.copy(alarmEnabled = true, alarmToneId = ALARM_TONE_CUSTOM)
      } else {
        prefs
      }
    }
  }

  fun setCustomAlarmAudio(uriString: String, displayName: String) {
    updateLocalOnlyPrefs {
      it.copy(
        alarmEnabled = true,
        alarmToneId = ALARM_TONE_CUSTOM,
        alarmCustomUri = uriString,
        alarmCustomLabel = displayName,
      )
    }
  }

  fun setBottomBarVisible(visible: Boolean) {
    updateLocalOnlyPrefs { it.copy(bottomBarVisible = visible) }
  }

  fun moveBottomBarTab(route: String, direction: Int) {
    updateLocalOnlyPrefs { prefs ->
      val normalized =
        (prefs.bottomBarTabOrder.filter { it in DEFAULT_BOTTOM_BAR_ORDER }.distinct() +
            DEFAULT_BOTTOM_BAR_ORDER.filter { it !in prefs.bottomBarTabOrder })
          .toMutableList()
      val idx = normalized.indexOf(route)
      val targetIdx = idx + direction
      if (idx == -1 || targetIdx !in normalized.indices) return@updateLocalOnlyPrefs prefs
      val moved = normalized.removeAt(idx)
      normalized.add(targetIdx, moved)
      prefs.copy(bottomBarTabOrder = normalized)
    }
  }

  fun setBottomBarTabEnabled(route: String, enabled: Boolean) {
    updateLocalOnlyPrefs { prefs ->
      val nextHidden =
        if (enabled) prefs.hiddenBottomBarTabs - route
        else prefs.hiddenBottomBarTabs + route
      prefs.copy(hiddenBottomBarTabs = nextHidden)
    }
  }

  fun resetBottomBarDefaults() {
    updateLocalOnlyPrefs {
      it.copy(
        bottomBarVisible = true,
        bottomBarTabOrder = DEFAULT_BOTTOM_BAR_ORDER,
        hiddenBottomBarTabs = emptySet(),
      )
    }
  }

  fun clearError() {
    _saveError.value = null
  }

  private fun updateLocalOnlyPrefs(transform: (AppPreferences) -> AppPreferences) {
    viewModelScope.launch {
      val current = repository.localPreferences.first()
      repository.saveLocalPreferences(transform(current))
    }
  }

  private fun updatePrefs(transform: (AppPreferences) -> AppPreferences) {
    viewModelScope.launch {
      val current = repository.localPreferences.first()
      val updated = transform(current)
      val result = repository.savePreferences(updated)
      result.onFailure { e ->
        _saveError.value = e.localizedMessage ?: "Failed to save settings"
      }
    }
  }

  private companion object {
    const val TAG = "PreferencesVM"
  }
}
