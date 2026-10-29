package com.bloo.bluelink.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.bloo.bluelink.data.AccessibilityChoices
import com.bloo.bluelink.data.AccessibilityDevice
import com.bloo.bluelink.data.AccessibilityResolved
import com.bloo.bluelink.data.OverrideReason
import com.bloo.bluelink.data.PowerPolicy
import com.bloo.bluelink.data.ResolvedSwitch
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.resolveAccessibility
import com.bloo.uicommon.reduceMotionCached

/**
 * The accessibility suite as the UI sees it: every switch resolved, once, at the app root. Anything that reads an accessibility choice
 * reads [LocalAccessibility] rather than the stored switch, so Battery Saver, Android's own settings and the app's switches can never
 * disagree.
 */
internal val LocalAccessibility = compositionLocalOf { AccessibilityResolved.Default }

/**
 * Gathers what the device reports and resolves the suite against the user's stored switches. Called once, at the app root.
 */
@Composable
internal fun rememberAccessibility(appearance: SettingsStore.Appearance, powerPolicy: PowerPolicy): AccessibilityResolved {
    val context = LocalContext.current
    val systemReduceMotion = remember(context.contentResolver) { reduceMotionCached(context) }
    val screenReaderOn = rememberScreenReaderOn()
    val choices = AccessibilityChoices(
        reduceMotion = appearance.reduceMotion,
        keyboardShortcuts = appearance.keyboardShortcuts,
    )
    val device = AccessibilityDevice(
        systemReduceMotion = systemReduceMotion,
        batterySaver = powerPolicy.batterySaver,
        screenReaderOn = screenReaderOn,
    )
    return remember(choices, device) { resolveAccessibility(choices, device) }
}

/** Whether a screen reader (TalkBack or another touch-exploration service) is on, kept current while the app is open. */
@Composable
private fun rememberScreenReaderOn(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled -> on = enabled }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * Settings: one group for every accessibility switch. A switch that Battery Saver or Android has decided is shown dimmed, on or off as
 * it is held, and says why, so nobody wonders why it will not move.
 */
@Composable
internal fun AccessibilityGroup(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    val a11y = LocalAccessibility.current
    SettingsGroup("Accessibility") {
        BodySmallText(
            if (a11y.screenReaderOn) {
                "A screen reader is on: every control is labelled and status changes are read out."
            } else {
                "Turn on TalkBack in Android's Accessibility settings and every control here is labelled for it."
            },
        )
        AccessibilitySwitch(
            label = "Reduce motion",
            description = "Less movement: springs settle without bouncing, and numbers change without rolling.",
            resolved = a11y.reduceMotion,
        ) { vm.setReduceMotion(it) }
        AccessibilitySwitch(
            label = "Keyboard shortcuts",
            description = "For a hardware keyboard or a desktop window. Ctrl + / lists them.",
            resolved = a11y.keyboardShortcuts,
        ) { vm.setKeyboardShortcuts(it) }
    }
}

/** One accessibility switch: enabled when the user's choice stands, held and explained when something else decides it. */
@Composable
private fun AccessibilitySwitch(label: String, description: String, resolved: ResolvedSwitch, onChange: (Boolean) -> Unit) {
    val override = resolved.override
    ToggleRow(
        label = label,
        checked = resolved.value,
        description = if (override == null) description else heldBecause(override),
        enabled = override == null,
        onChange = onChange,
    )
}

/** The line under a held switch: what holds it, and how to let go of it. */
private fun heldBecause(reason: OverrideReason): String = when (reason) {
    OverrideReason.BATTERY_SAVER -> "Battery Saver is on, so this is held. It returns to your choice when Battery Saver is off."
    OverrideReason.SYSTEM_SETTING -> "Android's Remove animations is on, so this is held. Turn it off in Android's Accessibility settings."
}

/**
 * Marks a status line as one a screen reader should announce when it changes, politely, without taking focus. Used on the lines that
 * change while the person is doing something else (an update's progress, a car's status).
 */
internal fun Modifier.announcesChanges(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
