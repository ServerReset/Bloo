package com.bloo.bluelink.wear

import com.bloo.uicommon.ButtonTargetHeight
import com.bloo.uicommon.MorphedCornerPercent
import com.bloo.uicommon.PillCornerPercent
import com.bloo.uicommon.newCascade
import com.bloo.uicommon.cascadeItem
import com.bloo.uicommon.LocalCascade
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.role
import com.bloo.uicommon.AppIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.uicommon.MorphButton
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.GroupHeading
import com.bloo.uicommon.IconBadge
import com.bloo.uicommon.SectionHeading
import com.bloo.uicommon.SettingRow
import com.bloo.uicommon.SwitchTrack
import com.bloo.uicommon.switchSemantics
import com.bloo.uicommon.TitleSubtitle
import com.bloo.uicommon.LabelValueRow
import com.bloo.uicommon.standardButtonIcon
import com.bloo.uicommon.SecondaryTextStyle
import com.bloo.uicommon.secondaryTextColor
import androidx.activity.compose.BackHandler
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle

/*
 * THE WATCH'S COMPONENT LIBRARY. Every screen on the watch is built from the pieces in this file and nothing else, so
 * the watch has one vocabulary the way the phone does (and, like the phone, it draws with :uicommon's own MorphButton,
 * RevealText and AnimatedValue rather than copies of them):
 *
 *   Screens   WearListScreen (scrolling, clock on top)   WearMessageScreen / WearConfirmScreen (full-screen states)
 *             WearBackable (a layer that peels off with back or a swipe)
 *   Sections  WearPebble (a headed group)  with  WearActionRow (a tap), WearToggleRow (an on/off), WearStatRow (a fact)
 *             and WearCaption / WearCenteredText (the words under them)
 *   Glass     WearButton: the one button every row is (see WearGlass for the blur and the rim)
 *             WearRoundButton: the same glass as a circle (keypad keys, single-glyph controls)
 *   Buttons   every button is :uicommon's MorphButton (via WearButton) and every glyph is an AppIcons one; nothing here draws its own
 */

/*
 * The watch's reusable screens. Every scrolling screen is a [WearListScreen] (so the clock, the scroll
 * indicator, the round-display padding and the "no per-card scaling, each element fits on its own" rule are
 * written once).
 */

/**
 * A scrolling screen for the round display: the clock on top (always showing, even while the list scrolls), a vertical list whose rows each fit the curve on
 * their own ([fitRound]), padded so the first and last row clear the bezel. [content] adds the rows.
 */
@Composable
fun WearListScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 28.dp),
    content: ScalingLazyListScope.() -> Unit,
) {
    val listState = rememberScalingLazyListState()
    // No scroll state handed to the scaffold: it would hide the clock while the list moves. The clock stays on top, always.
    ScreenScaffold(timeText = { TimeText() }, modifier = modifier) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // No per-item scaling: each element fits the round display on its own.
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
            // Air between the sections, so one group does not run into the next.
            verticalArrangement = Arrangement.spacedBy(SectionGap),
            contentPadding = contentPadding,
            content = content,
        )
    }
}

/**
 * A screen that can be backed out of -- the back button or gesture, or a swipe right across it -- calling [onBack]
 * for either. The face editor and its control list are screens of this kind: each swipe peels one layer, ending on
 * the face. (The swipe is read here rather than left to the system, which would close the whole app.)
 */
@Composable
internal fun WearBackable(onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    val current = androidx.compose.runtime.rememberUpdatedState(onBack)
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var travelled = 0f
            detectHorizontalDragGestures(
                onDragStart = { travelled = 0f },
                onDragEnd = { if (travelled > size.width * 0.3f) current.value() },
                onDragCancel = { travelled = 0f },
                onHorizontalDrag = { _, delta -> travelled += delta },
            )
        },
    ) { content() }
}

/**
 * A section of the watch: its glyph and title on one line, then its rows. No card behind it -- the rows already carry their
 * own glass, and the gap between sections (see [WearListScreen]) is what tells one group from the next.
 *
 * The body is a slot, so a section is whatever the caller puts under its title -- a list of rows, a block of text, a readout.
 */
@Composable
fun WearPebble(title: String, icon: ImageVector, modifier: Modifier = Modifier, body: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    WearCascade {
    Column(modifier.fillMaxWidth().padding(horizontal = ListInset), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        // The same heading as the phone's sections (:uicommon), centred for the round face and in the watch's own tint and size.
        SectionHeading(
            title, icon,
            modifier = Modifier.fitRound().cascadeItem(),
            centered = true,
            iconTint = scheme.primary,
            iconSize = 16.dp,
            titleStyle = MaterialTheme.typography.labelLarge,
            titleColor = scheme.onSurface,
            titleWeight = FontWeight.SemiBold,
        )
        body()
    }
    }
}

/**
 * Opens the cascade for one watch pebble: the components inside pop in, in order, the first time the pebble is composed. It is the
 * same cascade the phone's pebbles use (:uicommon), so a row pops the same way on both.
 */
@Composable
internal fun WearCascade(content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 520, easing = LinearEasing)) }
    val cascade = remember { newCascade(progress = { progress.value }, closing = { false }) }
    CompositionLocalProvider(LocalCascade provides cascade) { content() }
}

/** A quiet heading inside a section, for a group of rows within it (the section has the one title; this names a part). */
@Composable
fun WearSubheading(text: String, modifier: Modifier = Modifier) {
    GroupHeading(
        text,
        modifier = modifier.fillMaxWidth().padding(top = 6.dp).fitRound(),
        style = MaterialTheme.typography.labelMedium,
        weight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}

/**
 * Side margin of the lists, and the gaps that keep their rows and sections apart -- the phone's own spacing tokens (a row gap
 * between rows, a block gap between sections), so the watch breathes in the same units the phone does.
 */
internal val ListInset = 18.dp
internal val RowGap: Dp @Composable get() = com.bloo.uicommon.GapRow
internal val SectionGap: Dp @Composable get() = com.bloo.uicommon.GapBlock
