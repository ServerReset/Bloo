package com.bloo.bluelink.ui

import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapSection

/**
 * Extracted after finding it hand-written three times over -- the pages differ only in their copy,
 * [titleStyle] (the intro page's welcome is a size up from the other two), and their tip list,
 * never in shape, so a future fourth page (or a copy edit to any existing one) has exactly one
 * place to change.
 */
@Composable
internal fun OnboardingTipListPage(tips: List<Triple<ImageVector, String, String>>) {
    tips.forEachIndexed { index, (icon, cardTitle, body) ->
        OnboardingStagger(index) { OnboardingTipCard(icon, cardTitle, body) }
    }
}

/**
 * Second-to-last step: a quick tip list covering the app's core gestures. Followed by
 * [OnboardingFeaturesPage], the actual final step.
 */
@Composable
internal fun OnboardingTipsPage() {
    // A real, tappable pebble first, then the written tips.
    OnboardingPebbleDemo()
    OnboardingTipListPage(
        tips = listOf(
            Triple(Icons.Filled.SwapHoriz, "Swipe between cars", "Swipe the top row or hero card to change cars."),
            Triple(Icons.Filled.DragHandle, "Tap to expand, hold to reorder", "Tap a pebble for details. Hold to rearrange."),
            Triple(Icons.Filled.Refresh, "Hold to refresh", "Hold refresh to pull the latest status."),
            Triple(Icons.Filled.Keyboard, "Keyboard shortcuts", "Ctrl + / lists keyboard shortcuts."),
            Triple(Icons.Filled.Tune, "Tune it anytime", "Car setup is in Settings, per car."),
        ),
    )
}

/**
 * The actual final step -- the one screen shown right before "Enter Bloo" hands off to the garage,
 * so it is the one place every new user is guaranteed to see these highlighted at least once,
 * unlike a feature that only shows itself to someone who happens to open Settings.
 */
@Composable
internal fun OnboardingFeaturesPage(state: UiState) {
    val tips = buildList<Triple<ImageVector, String, String>> {
        add(Triple(AppIcons.Lock, "AutoLock", "Locks your car when you walk away. Per car in Settings."))
        add(Triple(AppIcons.Bolt, "Live charging updates", "Follow an EV's charge from the notification shade."))
        add(Triple(AppIcons.Search, "Just ask", "Search \"lock my car\" or \"start climate at 70\" and run it from the bar"))
        add(Triple(Icons.Filled.CloudSync, "Sync through your Drive", "Settings follow you to your phones, via Google Drive."))
        add(Triple(Icons.Filled.Security, "Your password stays on the phone", "Encrypted on this phone. Bloo has no server."))
        if (state.aiSupported) {
            add(Triple(AppIcons.AutoAwesome, "On-device AI summaries", "Status summaries, made on your phone."))
        }
    }
    OnboardingTipListPage(tips = tips)
}

/**
 * A single "tip" row: a primary-tinted icon beside a bold title and a muted one-line body, with a
 * hairline edge on the glass card. The welcome, tips and features cards each render a list of
 * these.
 */
@Composable
internal fun OnboardingTipCard(icon: ImageVector, title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    // The app's one inner glass panel (innerGlassPanel), like the settings groups and onboarding items.
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().innerGlassPanel(0.dp)) {
        // Same leading-circle icon badge as search results, the update pebble and the settings hero
        // stats.
        IconLeadRow(
            icon,
            tint = scheme.primary,
            title = title,
            subtitle = body,
            badgeSize = 28.dp,
            modifier = Modifier.padding(GapSection),
        )
    }
}
