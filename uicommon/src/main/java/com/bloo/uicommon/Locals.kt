package com.bloo.uicommon

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The two device-condition locals the shared components read.
 *
 * They live here rather than in the app because [RevealText] -- the single text component, the one
 * every surface that shows a string goes through -- reads both, and the watch app needs the same
 * component. A shared component that cannot see the shared state it depends on is not shared.
 *
 * The app still PROVIDES them, at its root, exactly as before; only the declarations moved.
 */

/** True when the user has disabled animations in Accessibility settings. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * The device-resource policy (battery saver, thermal throttling), collected ONCE at the app root and
 * read here. [com.bloo.bluelink.data.PowerPolicy] is the single place that says what changes when the
 * device is short on power: every visual or animated "lean" decision reads it, never the system
 * directly. Collected once because every call site used to collect the flow itself: about ten per
 * pebble, hundreds of live collectors across the pages in the pager, all created mid-swipe.
 */
/**
 * Whether motion blur draws at all. The app sets it from the user's switch and Battery Saver ([motionBlur] reads it); its default is on.
 */
val LocalMotionBlur = compositionLocalOf { true }

val LocalPowerPolicy = compositionLocalOf {
    com.bloo.bluelink.data.PowerPolicy.of(com.bloo.bluelink.data.DeviceState())
}

/**
 * Whether the user has turned animations off in Accessibility settings, cached for the PROCESS.
 *
 * A synchronous binder IPC to the settings provider, and it sits on the first-frame path inside
 * composition -- remember{} alone still pays it once per theme instance, and the theme is applied on
 * every screen. Shared for the same reason the locals above are: the watch needs the same answer.
 */
private var reduceMotionCache: Boolean? = null

fun reduceMotionCached(context: android.content.Context): Boolean =
    reduceMotionCache ?: runCatching {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false).also { reduceMotionCache = it }
