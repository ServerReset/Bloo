package com.bloo.bluelink.wear

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.uicommon.LocalPowerPolicy
import com.bloo.uicommon.LocalReduceMotion
import com.bloo.uicommon.reduceMotionCached

/** The watch theme: a dark, high-contrast scheme for a small AMOLED watch face. */
@Composable
internal fun WearTheme(content: @Composable () -> Unit) {
    // The phone's palette when it has sent one (a chosen palette, or Material You's from its wallpaper), else the brand's.
    val appContext = LocalContext.current
    remember(appContext) { WearAccent.load(appContext) }
    val accent by WearAccent.accent.collectAsStateWithLifecycle()
    val primary = accent?.let { Color(it.primary) } ?: Color(com.bloo.bluelink.data.BlooColors.brandAccent)
    val onPrimary = accent?.let { Color(it.onPrimary) } ?: Color.White
    val scheme = remember(accent) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = accent?.let { Color(it.secondary) } ?: WearSecondary,
            tertiary = accent?.let { Color(it.tertiary) } ?: WearTertiary,
            secondaryContainer = WearSecondaryContainer,
            onSecondaryContainer = Color.White,
            surfaceContainer = WearSurfaceContainer,
            background = Color.Black,
            onBackground = Color.White,
        )
    }
    // The same device-condition locals the phone provides at its root, so the SHARED text component
    // behaves identically here: the line-by-line entrance is off under reduce-motion and off under
    // battery saver, exactly as on the phone -- and the watch is the device most likely to be in both.
    val context = LocalContext.current
    val reduceMotion = remember(context.contentResolver) { reduceMotionCached(context) }
    val policy by com.bloo.bluelink.data.DeviceStatus.policy.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        // Reduced effects (the system setting, Battery Saver, a hot watch, or the wearer's own switch) turn the motion off.
        LocalReduceMotion provides (reduceMotion || policy.lean),
        // The watch theme is dark, and the shared tones need to know that.
        com.bloo.uicommon.LocalIsDarkTheme provides true,
        LocalPowerPolicy provides policy,
        // RevealText draws with the PHONE Material3's Text, which resolves an unspecified colour from
        // ITS LocalContentColor -- black by default, and therefore invisible on this theme. Setting it
        // here means a call site that forgets a colour is merely bland rather than unreadable.
        androidx.compose.material3.LocalContentColor provides scheme.onBackground,
    ) {
        // The Wear OS theme under it too, with Bloo's colours, so the platform's own pieces (the clock, the
        // scroll and page indicators, the edge button) are Bloo-coloured rather than stock.
        androidx.wear.compose.material3.MaterialTheme(
            colorScheme = androidx.wear.compose.material3.ColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                background = Color.Black,
                onBackground = Color.White,
            ),
        ) {
            MaterialTheme(colorScheme = scheme, content = content)
        }
    }
}

// The watch's dark palette beyond the user's accent: the one place these values live. The accent (primary, onPrimary, and the watch's
// secondary and tertiary when the phone sends them) comes from WearAccent; these are the fallbacks and the surfaces.
private val WearSecondary = Color(0xFFCCC2DC)
private val WearTertiary = Color(0xFFEFB8C8)
private val WearSecondaryContainer = Color(0xFF2A2A33)
private val WearSurfaceContainer = Color(0xFF1C1C22)
