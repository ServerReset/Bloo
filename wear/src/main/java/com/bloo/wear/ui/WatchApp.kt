package com.bloo.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.bloo.bluelink.data.WearColorRoles
import com.bloo.wear.WearScreen
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import kotlinx.coroutines.delay

/** Rotate this colour's hue by [degrees]; on any parse failure returns the colour unchanged. */
private fun Color.hueShifted(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * A watch-scaled counterpart to the phone's aurora background: a diagonal
 * primary->transparent->tertiary gradient over the base surface, its intensity
 * slowly breathing, instead of the phone's full multi-blob simulation -- cheap
 * enough for the watch's smaller GPU/thermal budget while still reading as a
 * living gradient rather than a flat fill.
 *
 * [colorMode] + [customHex] mirror the phone's three aurora colour sources
 * (complementary / material / custom) so the watch reflects what's chosen on the
 * phone rather than always looking like "material" mode.
 */
@Composable
private fun WearAuroraBackground(
    colors: WearColorRoles?,
    colorMode: String,
    customHex: String?,
    modifier: Modifier = Modifier,
) {
    val themePrimary = colors?.primary?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val themeTertiary = colors?.tertiary?.let { Color(it) } ?: MaterialTheme.colorScheme.tertiary
    val base = colors?.background?.let { Color(it) } ?: MaterialTheme.colorScheme.background
    val customColor = customHex?.let { hx ->
        runCatching { Color(android.graphics.Color.parseColor(hx)) }.getOrNull()
    }
    val primary = when (colorMode) {
        "custom" -> customColor ?: themePrimary
        "complementary" -> base.hueShifted(180f)
        else -> themePrimary // "material"
    }
    val tertiary = when (colorMode) {
        "custom" -> customColor?.hueShifted(180f) ?: themeTertiary
        // "complementary" only recolours the primary blob, matching the phone.
        else -> themeTertiary
    }

    // Hand-ticked at ~12fps instead of riding Compose's animation clock (which
    // would redraw this full-screen gradient on every display frame, up to
    // 120x/sec). This is the watch's near-always-visible backdrop, so an
    // unthrottled 60fps+ redraw loop for a slow multi-second breathe is a real
    // sustained battery/heat cost on hardware with a far smaller power budget
    // than the phone's.
    var breathe by remember { mutableFloatStateOf(0.55f) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            val phase = elapsed % 9000L
            val frac = if (phase < 4500L) phase.toFloat() / 4500L else 2f - phase.toFloat() / 4500L
            breathe = 0.55f + (1f - 0.55f) * frac
            delay(80)
        }
    }

    Box(
        modifier
            .background(base)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.32f * breathe),
                        Color.Transparent,
                        tertiary.copy(alpha = 0.26f * breathe),
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
    )
}

/**
 * A [HapticFeedback] that drops every call -- honours the phone's haptics-off
 * setting app-wide without touching the dozens of existing
 * `LocalHapticFeedback.current` call sites.
 */
private object NoOpHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}

/**
 * The watch app's true root composable. Collects [WearViewModel.ui] as Compose
 * state (so every downstream screen recomposes reactively off one state holder)
 * and does two cross-cutting things before handing off:
 *  1. swaps in [NoOpHapticFeedback] app-wide when the phone-synced haptics
 *     setting is off (via [CompositionLocalProvider] rather than per-call-site);
 *  2. gates the entire app behind [PinLockScreen] whenever `ui.pinLocked` is
 *     true -- the lock screen fully REPLACES [WatchAppContent] rather than
 *     overlaying it, so no other screen's composition (and state) exists
 *     underneath while locked.
 */
@Composable
fun WatchApp(vm: WearViewModel) {
    val ui by vm.ui.collectAsState()
    val hapticsEnabled = ui.settings?.hapticsEnabled ?: true
    val baseHaptics = LocalHapticFeedback.current
    val effectiveHaptics = remember(hapticsEnabled, baseHaptics) {
        if (hapticsEnabled) baseHaptics else NoOpHapticFeedback
    }
    CompositionLocalProvider(LocalHapticFeedback provides effectiveHaptics) {
        if (ui.pinLocked) {
            PinLockScreen(vm)
        } else {
            WatchAppContent(vm, ui)
        }
    }
}

/**
 * The unlocked app: an optional [WearAuroraBackground] behind a transparent
 * [AppScaffold], which hosts a [SwipeDismissableNavHost] switching on [ui].screen
 * for the top-level Loading / SignedOut / Ready states, plus (once Ready) a nested
 * nav graph across home/settings/login/trips/reorder.
 *
 * The Box wrapping everything is what lets the aurora show through: AppScaffold's
 * own background is forced transparent whenever aurora is on, so it doesn't paint
 * over the gradient sitting behind it in the same Box.
 */
@Composable
private fun WatchAppContent(vm: WearViewModel, ui: WearUi) {
    val auroraOn = ui.settings?.auroraEnabled == true
    Box(Modifier.fillMaxSize()) {
        if (auroraOn) {
            WearAuroraBackground(
                colors = ui.settings?.colors,
                colorMode = ui.settings?.auroraColorMode ?: "complementary",
                customHex = ui.settings?.auroraCustomColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Transparent container so the aurora Box behind shows through instead of
        // AppScaffold's opaque background fill covering it.
        AppScaffold(
            containerColor = if (auroraOn) Color.Transparent else MaterialTheme.colorScheme.background,
        ) {
            when (ui.screen) {
                WearScreen.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = roundSafeHorizontalPadding(flat = 24.dp, round = 32.dp)),
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            // Matches the spinner-caption style Login/Trips use for
                            // the same "busy" role.
                            "Loading…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Open Bloo on your phone if this takes a while",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        MorphButton(
                            label = "Sync from phone",
                            icon = Icons.Filled.Sync,
                            active = false,
                            activeColor = MaterialTheme.colorScheme.primary,
                            pending = false,
                            onClick = { vm.resync() },
                            modifier = Modifier.fillMaxWidth(0.8f),
                        )
                    }
                }

                WearScreen.SignedOut -> key(ui.accounts.size) { LoginScreen(vm, ui) }

                WearScreen.Ready -> {
                    val nav = rememberSwipeDismissableNavController()
                    SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                vm, ui,
                                onSettings = { nav.navigate("settings") },
                                onTrips = { vin -> nav.navigate("trips/$vin") },
                                onReorder = { vin -> nav.navigate("reorder/$vin") },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(vm, ui, onAddAccount = { nav.navigate("login") })
                        }
                        composable("login") {
                            LoginScreen(vm, ui)
                        }
                        composable("trips/{vin}") { entry ->
                            TripsScreen(vm, ui, entry.arguments?.getString("vin") ?: "")
                        }
                        composable("reorder/{vin}") { entry ->
                            TileReorderScreen(vm, ui, entry.arguments?.getString("vin") ?: "")
                        }
                    }
                }
            }
        }
    }
}
