
package com.bloo.bluelink.ui

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
// `motionScheme` is a MaterialTheme member; no import needed.
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
// The `by` State delegate is a file-scope operator extension and needs this explicit import.
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The phone UI's shared design vocabulary: the sizes, colours and motion that more than one
 * screen must agree on. `internal` because Kotlin's top-level `private` is file-scoped.
 */

// ---- Colour -------------------------------------------------------------------

// Mirrors shared/BlooColors.kt hex values.
internal val ChargeGreen = Color(com.bloo.bluelink.data.BlooColors.chargeGreen)
internal val ChargeGreenDark = Color(com.bloo.bluelink.data.BlooColors.chargeGreenDark)

/** Climate seat/wheel tints, on the canonical semantic heat/cool tokens. */
internal val Heat = Color(com.bloo.bluelink.data.BlooColors.heat)
internal val Cool = Color(com.bloo.bluelink.data.BlooColors.cool)

/** The "something new" notification dot colour (the canonical warning amber). */
internal val UpdateAvailableAmber = Color(com.bloo.bluelink.data.BlooColors.warn)

/** The charge bar's "topped up" state: the pack has reached its configured limit. See ChargeSegmentBar. */
internal val ChargeBlue = Color(com.bloo.bluelink.data.BlooColors.chargeBlue)
internal val ChargeBlueDark = Color(com.bloo.bluelink.data.BlooColors.chargeBlueDark)

/** The app's muted/secondary-text alpha, applied over LocalContentColor. */
internal const val MutedContentAlpha = 0.7f

/** The ambient content colour, muted: secondary text on any surface (tinted pebbles, glass, the map pill). */
@androidx.compose.runtime.Composable
internal fun mutedContentColor(): androidx.compose.ui.graphics.Color =
    androidx.compose.material3.LocalContentColor.current.copy(alpha = MutedContentAlpha)

/** Text and icons drawn on the hero's car photo: always near-white, because the hero is always scrimmed dark. */
internal val HeroOnPhoto = Color(0xFFF7F7FA)

@Composable
internal fun heroOnPhoto(): Color = HeroOnPhoto

// ---- Sizing -------------------------------------------------------------------

/** Shared control height: a collapsed pebble matches the lock/unlock button. */
internal val ControlHeight = 76.dp

/** Uniform collapsed-header height so every pebble lines up at the same size. */
internal val PebbleHeaderHeight = ControlHeight
internal val PebbleCornerCollapsed = 38.dp
internal val PebbleCornerExpanded = 20.dp

/** The charge bar's height, shared by every surface that draws it. */
internal val ChargeBarHeight = 18.dp

/** Gap on both sides of every internal charge-bar boundary so each segment is its own rounded piece. */
internal val ChargeSegmentGap = 5.dp

/**
 * Gap between the hero's collapsed readout and its card's bottom edge.
 * Shared by the readout's bottom padding and the header's reserved height, which must agree.
 */
internal val HeroReadoutBottomInset = 14.dp

/**
 * The horizontal inset every pebble's content sits at (header icon, summary, revealed content);
 * the lock pebble reaches it as 4 + 12 through a nested Box.
 */
internal val PebbleContentInset = 16.dp

/**
 * The horizontal gutter a full page's content sits inside (car pages and Settings).
 */
internal val ScreenGutter = 16.dp

/**
 * The app's vertical rhythm: one base unit ([SpaceUnit]) that every gap and inset is a multiple of.
 * Scales at half the display scale (see [spaceScaleFor]); provided by [BlooTheme], read via [LocalSpaceScale].
 */
internal val LocalSpaceScale = androidx.compose.runtime.compositionLocalOf { 1f }

/** Display scale → gap scale: gaps breathe with the app's display scale at half its swing. */
internal fun spaceScaleFor(scale: Float): Float = 1f + (scale - 1f) * 0.5f

/** The most the app will scale text; keeps fixed floating overlays within their bounds. */
internal const val MaxFontScale = 1.8f

/** The one spacing unit (4dp at 1.0 scale). Everything below is a multiple of it. */
internal val SpaceUnit: Dp @Composable get() = 4.dp * LocalSpaceScale.current
internal val GapHairline: Dp @Composable get() = SpaceUnit
internal val GapRow: Dp @Composable get() = SpaceUnit * 2
internal val GapGroup: Dp @Composable get() = SpaceUnit * 3
internal val GapSection: Dp @Composable get() = SpaceUnit * 4

/** Gap between settings cards: [GapRow] plus a hairline, on the same unit and scale. */
internal val SettingsCardGap: Dp @Composable get() = SpaceUnit * 2.5f

// ---- Shapes ---------------------------------------------------------------------
//
// Common corner radii; change a shape here, not per file.
internal val TinyShape = RoundedCornerShape(8.dp)    // Inline tap targets, swatches
internal val SmallShape = RoundedCornerShape(12.dp)   // Smaller components, chips
internal val StandardShape = RoundedCornerShape(16.dp) // Buttons, most cards
internal val ExtraLargeShape = RoundedCornerShape(28.dp) // Modal dialogs

// ---- Icons ----------------------------------------------------------------------
//
// Commonly used icons centralized to avoid duplicate imports.
object AppIcons {
  // Icons.Filled.X, not a bare `X`: these are extension properties, and a bare name would resolve
  // to this very property (self-referencing initializer).
  val Settings = Icons.Filled.Settings
  val Lock = Icons.Filled.Lock
  val Bolt = Icons.Filled.Bolt
  val Close = Icons.Filled.Close
  val ArrowForward = Icons.AutoMirrored.Filled.ArrowForward
  val Search = Icons.Filled.Search
  val Check = Icons.Filled.Check
  val CheckCircle = Icons.Filled.CheckCircle
  val Info = Icons.Filled.Info
  val DirectionsCar = Icons.Filled.DirectionsCar
  val AutoAwesome = Icons.Filled.AutoAwesome
  val Warning = Icons.Filled.Warning
}

// ---- Blur -----------------------------------------------------------------------
//
// Shared Haze backdrop-blur gate and progressive-blur instances used by every blur site.

/**
 * App-wide source of truth for [android.os.PowerManager.isPowerSaveMode], kept live by one
 * [android.content.BroadcastReceiver] for the whole process; [BlooApplication] calls [ensureInitialized] at startup.
 */
internal object BatterySaverState {
    private fun current(context: android.content.Context) =
        (context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
            ?.isPowerSaveMode == true

    private val _isOn = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isOn: kotlinx.coroutines.flow.StateFlow<Boolean> = _isOn

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: android.content.Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            _isOn.value = current(appContext)
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                    _isOn.value = current(appContext)
                }
            }
            // ContextCompat with NOT_EXPORTED: at targetSdk 34+ the plain 2-arg registerReceiver throws SecurityException,
            // and only the system's own battery-saver broadcast is needed.
            androidx.core.content.ContextCompat.registerReceiver(
                appContext, receiver,
                android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            initialized = true
        }
    }
}

/** Live battery-saver state from the shared [BatterySaverState]; updates immediately everywhere. */
@Composable
internal fun isBatterySaverOn(): Boolean {
    val active by BatterySaverState.isOn.collectAsState()
    return active
}

/** True on API 31+ (Haze's RenderEffect blur; no-op below) and battery saver off.
 *  Call sites fall back to a solid tinted layer otherwise. */
@Composable
internal fun canBlurBackdrops(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !isBatterySaverOn()

/**
 * Battery-saver-aware replacement for a bouncy [spring]: the real spring normally, a short
 * critically-damped [tween] (no overshoot, fewer frames) under battery saver.
 * Gating here calms every [collapseEnter]/[collapseExit]/[PopVisible] site at once.
 */
@Composable
internal fun <T> lowPowerAwareSpring(dampingRatio: Float, stiffness: Float) =
    if (isBatterySaverOn()) {
        tween<T>(durationMillis = 140, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    } else {
        spring<T>(dampingRatio = dampingRatio, stiffness = stiffness)
    }

/** The shape every full-height scrim's blur uses: strong at its edge, tapering to none by the far edge. */
internal val StandardBlurProgressive
    get() = dev.chrisbanes.haze.HazeProgressive.verticalGradient(
        // Strong at top (status bar legibility), none at bottom.
        startIntensity = 1f,
        endIntensity = 0f
    )


/** The one hairline: the outline of an inner box, a disabled button's rim, a divider-weight edge. */
internal const val HairlineAlpha = 0.14f

@androidx.compose.runtime.Composable
internal fun hairlineColor(): androidx.compose.ui.graphics.Color =
    androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = HairlineAlpha)


// ---- Motion durations (ms) -------------------------------------------------------
//
// The handful of tween lengths the UI uses, so the same kind of transition takes the same time
// everywhere. Springs set their own timing; these are for fades, slides and colour changes.
internal const val MotionFast = 140    // micro feedback, hints leaving
internal const val MotionShort = 180   // fades and small slides
internal const val MotionMedium = 320  // panels, cards, colour sweeps
internal const val MotionLong = 400    // large or emphasised moves
