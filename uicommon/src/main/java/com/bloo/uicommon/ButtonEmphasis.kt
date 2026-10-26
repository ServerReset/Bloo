package com.bloo.uicommon

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Whether the UI is currently dark.
 *
 * The app honours its own Light/Dark override, so this cannot simply ask the system. The app provides
 * it from its appearance state at its root, and the default below falls back to the system for
 * anything that does not -- which is exactly what the watch wants.
 */
val LocalIsDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * The app's two action colours: green confirms (go ahead, save, sign in, start), red denies (remove, sign out,
 * stop, discard). One pair each, tuned per theme, used by every button that commits or reverses something.
 */
@androidx.compose.runtime.Immutable
class ActionTone(val container: Color, val content: Color)

@Composable
fun confirmTone(): ActionTone =
    if (LocalIsDarkTheme.current) ActionTone(Color(0xFF7BD389), Color(0xFF06290E)) else ActionTone(Color(0xFF1E7B34), Color.White)

@Composable
fun denyTone(): ActionTone =
    if (LocalIsDarkTheme.current) ActionTone(Color(0xFFFF8A80), Color(0xFF4A0507)) else ActionTone(Color(0xFFC62828), Color.White)

/**
 * How a button speaks, resolved to colours in one place. [Tonal]: the standard button. [Confirm]: go ahead
 * (green). [Deny]: remove, sign out, stop (red).
 */
enum class ButtonEmphasis { Tonal, Confirm, Deny }

@Composable
fun ButtonEmphasis.container(): Color = when (this) {
    ButtonEmphasis.Tonal -> MaterialTheme.colorScheme.secondaryContainer
    ButtonEmphasis.Confirm -> confirmTone().container
    ButtonEmphasis.Deny -> denyTone().container
}

@Composable
fun ButtonEmphasis.content(): Color = when (this) {
    ButtonEmphasis.Tonal -> MaterialTheme.colorScheme.onSecondaryContainer
    ButtonEmphasis.Confirm -> confirmTone().content
    ButtonEmphasis.Deny -> denyTone().content
}