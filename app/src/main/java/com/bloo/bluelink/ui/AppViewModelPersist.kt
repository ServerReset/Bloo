package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Saves preferences off the caller's thread, on the view model's scope. Every one-line settings write goes through here, so a
 * save has one shape across the view model rather than a copy of `viewModelScope.launch { settingsStore... }` per setter.
 */
internal fun AppViewModel.persist(write: suspend SettingsStore.() -> Unit): Job =
    viewModelScope.launch { settingsStore.write() }
