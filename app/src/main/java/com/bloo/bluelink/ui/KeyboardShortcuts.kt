package com.bloo.bluelink.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.GapRow

/** What a keyboard shortcut asks the app to do. Pure data: [shortcutFor] maps keys to these, [onShortcut] carries them out. */
internal sealed interface Shortcut {
    data object Help : Shortcut
    data object Search : Shortcut
    data object Refresh : Shortcut
    data object NextCar : Shortcut
    data object PreviousCar : Shortcut
    data object Settings : Shortcut
    data object Lock : Shortcut
    data object Unlock : Shortcut
    data object Climate : Shortcut
    data object Back : Shortcut
    data class GoToCar(val index: Int) : Shortcut
}

/** The keys that jump to a car: Ctrl+1 is the first. */
private val CarNumberKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

/**
 * The shortcut for a key press, or null. Every one but Esc and F1/F5 needs Ctrl (or Meta, on keyboards that have it), so typing
 * in a field is never taken; the ones that move between cars use Ctrl+PageUp/PageDown and Ctrl+Tab (as browser tabs do) rather
 * than Ctrl+Arrow, which a text field uses to move by word. Unlocking needs Shift as well, so it is never one slip of a key.
 */
internal fun shortcutFor(key: Key, ctrl: Boolean, shift: Boolean): Shortcut? = when {
    key == Key.Escape && !ctrl -> Shortcut.Back
    key == Key.F1 -> Shortcut.Help
    key == Key.F5 -> Shortcut.Refresh
    !ctrl -> null
    key == Key.Slash -> Shortcut.Help
    key == Key.K || key == Key.F -> Shortcut.Search
    key == Key.R -> Shortcut.Refresh
    key == Key.Comma -> Shortcut.Settings
    key == Key.PageDown || (key == Key.Tab && !shift) -> Shortcut.NextCar
    key == Key.PageUp || (key == Key.Tab && shift) -> Shortcut.PreviousCar
    key == Key.L && !shift -> Shortcut.Lock
    key == Key.U && shift -> Shortcut.Unlock
    key == Key.C && shift -> Shortcut.Climate
    key in CarNumberKeys -> Shortcut.GoToCar(CarNumberKeys.indexOf(key))
    else -> null
}

/** What the help pop-up lists: the keys, and what they do. */
internal val ShortcutHelp: List<Pair<String, String>> = listOf(
    "Ctrl + /   or   F1" to "Show these shortcuts",
    "Ctrl + K   or   Ctrl + F" to "Search settings",
    "Ctrl + R   or   F5" to "Refresh the car",
    "Ctrl + PageDown   or   Ctrl + Tab" to "Next car",
    "Ctrl + PageUp   or   Ctrl + Shift + Tab" to "Previous car",
    "Ctrl + 1 to 9" to "Go to that car",
    "Ctrl + ," to "Open settings",
    "Ctrl + L" to "Lock the car",
    "Ctrl + Shift + U" to "Unlock the car",
    "Ctrl + Shift + C" to "Start or stop the climate",
    "Esc" to "Go back or close",
)

/**
 * Makes this the place hardware-keyboard shortcuts are heard: focusable (and focused once on start, so keys have somewhere to
 * go before anything is tabbed to), and reading key presses before any child so a shortcut works wherever focus is.
 */
@Composable
internal fun Modifier.appShortcuts(enabled: Boolean, onShortcut: (Shortcut) -> Unit): Modifier {
    val focus = remember { FocusRequester() }
    val latest by rememberUpdatedState(onShortcut)
    val on by rememberUpdatedState(enabled)
    // Takes focus again whenever the window gains it. In a desktop or freeform window, switching away and back drops the focus the
    // app had, and shortcuts would go quiet until something was tapped; this keeps them live without a tap.
    // Also on first composition, so keys have somewhere to go before the window reports focus (a test window, or a window that is
    // not yet focused, never reports it).
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (windowFocused) runCatching { focus.requestFocus() } }
    return this
        .focusRequester(focus)
        .focusable()
        .onPreviewKeyEvent { event: KeyEvent ->
            if (!on || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val shortcut = shortcutFor(event.key, event.isCtrlPressed || event.isMetaPressed, event.isShiftPressed)
                ?: return@onPreviewKeyEvent false
            latest(shortcut)
            true
        }
}

/** Closes whatever is on top, the way the system Back does (Esc on a keyboard). */
@Composable
internal fun rememberBackAction(): () -> Unit {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    return { dispatcher?.onBackPressed() }
}

/** The keyboard shortcuts pop-up. */
@Composable
internal fun ShortcutsDialog(onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Keyboard,
        title = "Keyboard shortcuts",
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GapRow),
            ) {
                ShortcutHelp.forEach { (keys, what) ->
                    Column(Modifier.semantics(mergeDescendants = true) {}) {
                        BodyMediumText(what)
                        Row { BodySmallText(keys, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        },
        buttons = { DialogButton("Close", onDismiss, ButtonEmphasis.Confirm) },
    )
}
