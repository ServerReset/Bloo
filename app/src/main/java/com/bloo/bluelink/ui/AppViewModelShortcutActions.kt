package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Carries out a keyboard [shortcut]. Actions on a car (lock, refresh, climate, switching) are done here against the car the pager
 * is on; the rest (help, search, settings, back) are broadcast on [AppViewModel.shortcutEvents] for the screen that owns them.
 * Nothing runs unless the garage is showing, so a shortcut can never reach a car from the sign-in or setup screens.
 */
internal fun AppViewModel.onShortcut(shortcut: Shortcut) {
    val state = _state.value
    if (state.screen != Screen.Garage) {
        if (shortcut is Shortcut.Help || shortcut is Shortcut.Back) viewModelScope.launch { shortcutEvents.emit(shortcut) }
        return
    }
    val cars = state.vehicles
    val index = _currentIndex.value
    val car = cars.getOrNull(index)
    when (shortcut) {
        Shortcut.Refresh -> car?.let { refreshStatus(it) }
        Shortcut.Lock -> car?.let { lock(it) }
        Shortcut.Unlock -> car?.let { unlock(it) }
        Shortcut.Climate -> car?.let {
            if (state.statuses[it.vin]?.airCtrlOn == true) stopClimate(it) else startSmartClimate(it)
        }
        Shortcut.NextCar -> if (cars.size > 1) selectIndex((index + 1) % cars.size)
        Shortcut.PreviousCar -> if (cars.size > 1) selectIndex((index - 1 + cars.size) % cars.size)
        is Shortcut.GoToCar -> if (shortcut.index in cars.indices) selectIndex(shortcut.index)
        Shortcut.Help, Shortcut.Search, Shortcut.Settings, Shortcut.Back -> viewModelScope.launch { shortcutEvents.emit(shortcut) }
    }
}
