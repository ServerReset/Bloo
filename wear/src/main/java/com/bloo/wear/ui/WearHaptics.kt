package com.bloo.wear.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * A small, semantic haptic vocabulary for Wear — mirrors the phone's Haptics
 * class in spirit (name the *feel*, not the raw platform type). Wear doesn't
 * expose the phone's richer VibrationEffect.Composition primitives, but
 * Compose's own HapticFeedbackType already distinguishes exactly the cases
 * this app needs.
 *
 * Every interactive control in the wear app should call one of these instead
 * of reaching for a raw HapticFeedbackType directly — before this, almost
 * everything (plain button taps AND toggles alike) used the same
 * HapticFeedbackType.TextHandleMove, so nothing actually felt distinct.
 */
/** A light, ordinary "I pressed a button" feel — navigation, pickers, plain
 *  actions. Distinct from (and lighter than) [click], which was previously fired
 *  on EVERY button tap, so opening a submenu felt as heavy as committing a state
 *  change. Use this for the everyday tap. */
fun HapticFeedback.tap() = performHapticFeedback(HapticFeedbackType.VirtualKey)
fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.SegmentTick)
/** A heavier confirming feel — reserve for committed state changes / toggles
 *  landing "on" (lock engaged, charge started), not routine taps. */
fun HapticFeedback.click() = performHapticFeedback(HapticFeedbackType.Confirm)
fun HapticFeedback.reject() = performHapticFeedback(HapticFeedbackType.Reject)
