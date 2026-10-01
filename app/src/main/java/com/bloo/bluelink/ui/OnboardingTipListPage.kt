@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.bloo.bluelink.data.Powertrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/**
 * The shared shape all three intro/closing pages ([OnboardingIntroPage],
 * [OnboardingCrashCoursePage], [OnboardingFeaturesPage]) turned out to want: an emoji, a big
 * title, a supporting line, then a list of tip cards. Extracted after finding it hand-written
 * three times over -- the pages differ only in their copy, [titleStyle] (the intro page's
 * welcome is a size up from the other two), and their tip list, never in shape, so a future
 * fourth page (or a copy edit to any existing one) has exactly one place to change.
 *
 * Deliberately no per-item entrance animation on the tip list, matching what
 * [OnboardingIntroPage] itself already settled on: [AnimatedContent]'s own slide/fade in
 * [OnboardingScreen] already animates the whole page in, and this exact page family already
 * tried layering a second per-card entrance on top of that once (see intro's own history) and
 * found it fought with the page slide, reading as jittery rather than smooth. The two closing
 * pages get the same plain, instant stack intro already uses -- not a separate animation
 * choice per page that happens to agree today.
 */
@Composable
internal fun OnboardingTipListPage(tips: List<Triple<ImageVector, String, String>>) {
    tips.forEach { (icon, cardTitle, body) ->
        OnboardingTipCard(icon, cardTitle, body)
    }
}


/** Second-to-last step: a quick tip list covering the app's core gestures. Followed by
 *  [OnboardingFeaturesPage], the actual final step. */
@Composable
internal fun OnboardingTipsPage() {
    OnboardingTipListPage(
        tips = listOf(
            Triple(Icons.Filled.SwapHoriz, "Swipe between cars", "Swipe left or right on any pebble's top row, or anywhere on the hero card, to change cars, even when a pebble is open"),
            Triple(Icons.Filled.DragHandle, "Tap to expand, hold to reorder", "Tap any pebble for details, or hold and drag to rearrange them"),
            Triple(Icons.Filled.Refresh, "Hold to refresh", "Press and hold the refresh control to pull the latest status from your car"),
            Triple(AppIcons.Settings, "Tune it anytime", "Powertrain, seats, and lock settings all live in Settings if things change"),
        ),
    )
}


/**
 * The actual final step -- the one screen shown right before "Enter Bloo" hands off to the
 * garage, so it is the one place every new user is guaranteed to see these highlighted at
 * least once, unlike a feature that only shows itself to someone who happens to open
 * Settings. Distinct from [OnboardingCrashCoursePage] just before it: that page is about
 * *how to use the screen you're about to land on* (gestures); this one is about
 * *things the app can do that aren't obvious from looking at it* (AutoLock, live charging,
 * natural-language search). On-device AI is the one entry gated on
 * [UiState.aiSupported] -- the others work on every device, but advertising a feature this
 * phone's own hardware can't run would be a promise the app can't keep.
 */
@Composable
internal fun OnboardingFeaturesPage(state: UiState) {
    val tips = buildList<Triple<ImageVector, String, String>> {
        add(Triple(AppIcons.Lock, "AutoLock", "Locks your car when you walk away -- enable it per car in Settings"))
        add(Triple(AppIcons.Bolt, "Live charging updates", "Watch an EV's charge progress from your lock screen while plugged in"))
        add(Triple(AppIcons.Search, "Just ask", "Search \"lock my car\" or \"start climate at 70\" to run it from the bar"))
        if (state.aiSupported) {
            add(Triple(AppIcons.AutoAwesome, "On-device AI summaries", "Plain-language status summaries, generated on your phone"))
        }
    }
    OnboardingTipListPage(
        tips = tips,
    )
}
