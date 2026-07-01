package com.bloo.wear.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * A small, semantic haptic vocabulary for Wear — mirrors the phone's Haptics
 * class in spirit (name the *feel*, not the raw platform type). Wear doesn't
 * expose the phone's richer VibrationEffect.Composition primitives, but
 * Compose's own HapticFeedbackType already distinguishes exactly the cases
 * this app needs (there are dedicated ToggleOn/ToggleOff/Confirm/Reject/
 * SegmentTick types, not just a single generic one).
 *
 * Every interactive control in the wear app should call one of these instead
 * of reaching for a raw HapticFeedbackType directly — before this, almost
 * everything (plain button taps AND toggles alike) used the same
 * HapticFeedbackType.TextHandleMove, so nothing actually felt distinct.
 */
fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.SegmentTick)
fun HapticFeedback.click() = performHapticFeedback(HapticFeedbackType.Confirm)
fun HapticFeedback.toggleOn() = performHapticFeedback(HapticFeedbackType.ToggleOn)
fun HapticFeedback.toggleOff() = performHapticFeedback(HapticFeedbackType.ToggleOff)
fun HapticFeedback.longPress() = performHapticFeedback(HapticFeedbackType.LongPress)
fun HapticFeedback.reject() = performHapticFeedback(HapticFeedbackType.Reject)
