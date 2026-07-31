package com.bloo.wear.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * A small, semantic haptic vocabulary for Wear.
 *
 * The point is to name the *feel* — not the raw platform type — so every
 * control asks for an intent ("this is a plain tap", "this committed a state
 * change") and the mapping to a [HapticFeedbackType] lives in exactly one
 * place. This mirrors the phone's Haptics class in spirit; Wear can't reach the
 * phone's richer VibrationEffect.Composition primitives, but Compose's own
 * HapticFeedbackType already draws the distinctions this app actually needs.
 *
 * Why this exists at all: before it, nearly everything on the watch — ordinary
 * button taps AND state-changing toggles alike — fired the same
 * HapticFeedbackType.TextHandleMove, so nothing felt distinct. Opening a submenu
 * buzzed exactly like locking the car. These four give each class of action its
 * own recognisable feel.
 *
 * Every interactive control should call one of these rather than reaching for a
 * raw HapticFeedbackType directly.
 */

/**
 * The everyday, light "I pressed something" feel — navigation, pickers, plain
 * actions that don't commit a state change.
 *
 * Deliberately lighter than (and distinct from) [click]: [click] used to fire
 * on *every* button tap, which made opening a submenu feel as weighty as
 * committing a lock/charge change. [tap] is the neutral default; reserve the
 * heavier feel for things that actually landed.
 */
fun HapticFeedback.tap() = performHapticFeedback(HapticFeedbackType.VirtualKey)

/**
 * A faint detent — for incremental motion: each rotary/slider step, keypad
 * digit entry, segmented step-throughs. The "something ticked past" feel.
 */
fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.SegmentTick)

/**
 * The heavier, confirming feel — reserved for a committed state change: a toggle
 * landing "on" (lock engaged, charge started), a slider settling on its value.
 * Not for routine taps; see [tap] for those.
 */
fun HapticFeedback.click() = performHapticFeedback(HapticFeedbackType.Confirm)

/**
 * The negative feel — a rejected input or failed action (wrong PIN, denied
 * command). Signals "that didn't take" without any text.
 */
fun HapticFeedback.reject() = performHapticFeedback(HapticFeedbackType.Reject)
