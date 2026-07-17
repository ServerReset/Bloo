package com.bloo.wear.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import com.bloo.wear.WearViewModel

/** Rotates a hex/ARGB colour's hue by the given degrees; falls back to the colour itself on parse failure. */
private fun Color.hueShifted(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * A simplified, watch-scaled counterpart to the phone's aurora background: a
 * diagonal primary→tertiary gradient over the base surface, its blend slowly
 * breathing between the two, instead of the phone's full multi-blob
 * simulation -- cheap enough for the watch's smaller GPU budget while still
 * reading as a living gradient rather than a flat fill.
 *
 * [colorMode] and [customHex] mirror the phone's three aurora colour sources
 * (complementary / material / custom) so the watch doesn't always look like
 * "material" mode regardless of what's chosen on the phone.
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
    val customColor = customHex?.let { hx -> runCatching { Color(android.graphics.Color.parseColor(hx)) }.getOrNull() }
    val primary = when (colorMode) {
        "custom" -> customColor ?: themePrimary
        "complementary" -> base.hueShifted(180f)
        else -> themePrimary // "material"
    }
    val tertiary = when (colorMode) {
        "custom" -> customColor?.hueShifted(180f) ?: themeTertiary
        else -> themeTertiary // complementary only recolours the primary blob, same as the phone
    }
    val breathe by rememberInfiniteTransition(label = "wearAurora").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Reverse),
        label = "wearAuroraBreathe",
    )
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

@Composable
fun WatchApp(vm: WearViewModel) {
    val ui by vm.ui.collectAsState()
    if (ui.pinLocked) {
        PinLockScreen(vm)
        return
    }
    val auroraOn = ui.settings?.auroraEnabled == true
    Box(Modifier.fillMaxSize()) {
    if (auroraOn) {
        WearAuroraBackground(
            ui.settings?.colors,
            ui.settings?.auroraColorMode ?: "complementary",
            ui.settings?.auroraCustomColor,
            Modifier.fillMaxSize(),
        )
    }
    // Transparent container so the aurora Box behind shows through instead of
    // AppScaffold's own opaque background fill covering it.
    AppScaffold(containerColor = if (auroraOn) Color.Transparent else MaterialTheme.colorScheme.background) {
        when (ui.screen) {
            WearScreen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Loading…",
                        style = MaterialTheme.typography.bodySmall,
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
