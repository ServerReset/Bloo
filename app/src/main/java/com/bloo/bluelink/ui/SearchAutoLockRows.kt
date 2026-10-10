package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bloo.bluelink.autolock.AutoLockConfig
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.autoLockConfig
import com.bloo.bluelink.data.setAutoLockConfig
import kotlin.math.roundToInt

// --- AutoLock controls surfaced as search results ---

/** One AutoLock toggle, as a search result. */
@Composable
internal fun AutoLockSearchToggle(
    v: Vehicle,
    vm: AppViewModel,
    label: String,
    checked: (AutoLockConfig) -> Boolean,
    update: (AutoLockConfig, Boolean) -> AutoLockConfig,
) {
    var config by remember(v.vin) { mutableStateOf<AutoLockConfig?>(null) }
    LaunchedEffect(v.vin) { config = vm.autoLockConfig(v.vin) }
    val current = config ?: return
    ToggleRow(label, checked(current)) { value ->
        val updated = update(current, value)
        config = updated
        vm.setAutoLockConfig(v.vin, updated)
    }
}

/**
 * AutoLock's grace period as a search result: the same slider the AutoLock card shows, loaded and
 * saved the same way.
 */
@Composable
internal fun AutoLockGraceSearchRow(v: Vehicle, vm: AppViewModel) {
    var config by remember(v.vin) { mutableStateOf<AutoLockConfig?>(null) }
    LaunchedEffect(v.vin) { config = vm.autoLockConfig(v.vin) }
    val current = config ?: return
    StepRow("Grace period", "${current.graceSeconds}s")
    AnimatedSlider(
        value = current.graceSeconds.toFloat(),
        onValueChange = { config = current.copy(graceSeconds = it.roundToInt()) },
        onValueSettled = {
            val updated = current.copy(graceSeconds = it.roundToInt())
            config = updated
            vm.setAutoLockConfig(v.vin, updated)
        },
        valueRange = 10f..120f,
        steps = 10,
    )
}
