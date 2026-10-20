package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.bloo.uicommon.standardButtonIcon

/*
 * THE WATCH'S COMPONENT LIBRARY. Every screen on the watch is built from the pieces in this file and nothing else, so
 * the watch has one vocabulary the way the phone does (and, like the phone, it draws with :uicommon's own MorphButton,
 * RevealText and AnimatedValue rather than copies of them):
 *
 *   Screens   WearListScreen (scrolling, clock on top)   WearMessageScreen / WearConfirmScreen (full-screen states)
 *             WearChoiceScreen (pick one)                WearBackable (a layer that peels off with back or a swipe)
 *   Sections  WearPebble (a headed group)  with  WearActionRow (a tap), WearToggleRow (an on/off), WearStatRow (a fact)
 *             and WearCaption / WearCenteredText (the words under them)
 *   Glass     WearButton: the one button every row is (see WearGlass for the blur and the rim)
 */

/*
 * The watch's reusable screens. Every scrolling screen is a [WearListScreen] (so the clock, the scroll
 * indicator, the round-display padding and the "no per-card scaling, each element fits on its own" rule are
 * written once), and every "pick one of these" screen is a [WearChoiceScreen].
 */

/**
 * A scrolling screen for the round display: the clock on top, a vertical list whose rows each fit the curve on
 * their own ([fitRound]), padded so the first and last row clear the bezel. [content] adds the rows.
 */
@Composable
fun WearListScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 28.dp),
    content: ScalingLazyListScope.() -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }, modifier = modifier) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // No per-item scaling: each element fits the round display on its own.
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
            contentPadding = contentPadding,
            content = content,
        )
    }
}

/** One option on a [WearChoiceScreen]. */
data class WearChoice<T>(val value: T, val label: String, val icon: ImageVector? = null)

/**
 * A "pick one" screen: a title, the options as rows with the current one lit, and optionally an "Empty" row at
 * the end. A tap picks and the caller decides what happens next. Used by the face's control chooser; anything
 * else that needs a choice on the watch (a unit, a delay) is the same screen.
 */
@Composable
fun <T> WearChoiceScreen(
    title: String,
    choices: List<WearChoice<T>>,
    selected: T,
    onPick: (T) -> Unit,
    /** A last "none of these" row, or null for none. */
    empty: WearChoice<T>? = null,
) {
    WearListScreen {
        item { WearCenteredText(title, style = MaterialTheme.typography.titleSmall) }
        (choices + listOfNotNull(empty)).forEachIndexed { i, choice ->
            item(key = i) {
                WearActionRow(choice.label, choice.icon ?: Icons.Filled.Close.takeIf { choice === empty }, active = choice.value == selected, switch = true) {
                    onPick(choice.value)
                }
            }
        }
    }
}

/**
 * A screen that can be backed out of -- the back button or gesture, or a swipe right across it -- calling [onBack]
 * for either. The face editor and its control list are screens of this kind: each swipe peels one layer, ending on
 * the face. (The swipe is read here rather than left to the system, which would close the whole app.)
 */
@Composable
internal fun WearBackable(onBack: () -> Unit, content: @Composable () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val current = androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.compose.foundation.layout.Box(
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
 * The watch's small card: a round-friendly container with a title row (icon + title) and a body.
 *
 * The body is a slot rather than a fixed layout, so a pebble is whatever the caller puts in it -- a
 * row of switches, a block of text, a status readout. [WearPebble] owns the card, nothing else.
 */
@Composable
fun WearPebble(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    // No card around it: on the phone a section is a quiet heading over its own pills, so on the watch the
    // pebble is a small heading and the rows beneath it carry the shape.
    // Inset from both edges: on a round face a full-width row is cut by the bezel at the top and bottom.
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().fitRound(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            RevealText(
                title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
                // Explicit: the shared component draws with the phone Material3's Text, whose
                // unspecified colour is its own LocalContentColor rather than this theme's.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        body()
    }
}

/**
 * A single action inside a [WearPebble] -- the shared phone [MorphButtonCore], so the watch's tap
 * target morphs and presses exactly like every other Bloo button.
 *
 * [switch] marks the row as an on/off setting rather than a one-shot action, and it changes two
 * things. A trailing indicator, because a filled container is a weak signal for "on" when every row
 * in the pebble is a filled container. And the spoken role, because a screen reader otherwise
 * announces a toggle as a plain button and never says which way it is set.
 */
@Composable
fun WearActionRow(
    label: String,
    /** Null asks the app's own vocabulary for the glyph this label uses (see [standardButtonIcon]). */
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    switch: Boolean = false,
    /** The command is in flight: the glyph becomes the app's shared spinner. */
    pending: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (active) scheme.onPrimary else scheme.onSurface
    // The app's glyph for this label when the caller has none, and the app's own icon+label layout --
    // the one that drops the label rather than truncating a word, and swaps the glyph for the shared
    // in-flight spinner. Boxed because the label has to take the row's spare width here, so the
    // trailing switch indicator stays on the end.
    val glyph = icon ?: standardButtonIcon(label)
    WearButton(
        active = active,
        enabled = enabled && !pending,
        modifier = modifier
            .fillMaxWidth()
            .fitRound()
            .then(
                if (switch) {
                    Modifier.semantics {
                        role = Role.Switch
                        toggleableState = if (active) ToggleableState.On else ToggleableState.Off
                    }
                } else {
                    Modifier
                },
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        // The glyph (or the in-flight spinner) and the label side by side, the label taking the row and wrapping to a
        // second line before it is ever cut. The shared icon-and-label layout DROPPED the label when it did not fit,
        // which left long rows ("Lock when I walk away", "Top left: Lock / unlock") as an icon and nothing else.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides tint,
                ) {
                    MorphButtonLabel(icon = glyph, label = "", pending = pending, iconSize = 18.dp)
                }
                Spacer(Modifier.width(10.dp))
            }
            RevealText(
                label,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (switch) {
            Spacer(Modifier.width(8.dp))
            // A filled dot when on, a hollow ring when off -- drawn rather than an icon so it reads at
            // 16dp, where a glyph-based switch icon turns to mud.
            Spacer(
                Modifier
                    .size(16.dp)
                    .background(if (active) tint else Color.Transparent, CircleShape)
                    .border(2.dp, tint, CircleShape),
            )
        }
    }
}

/**
 * The watch's buttons ARE the app's buttons now, not a second set built on the same core: this
 * delegates to [MorphButton], so the pill-to-rounded-square morph, the inert frost and the press
 * feedback are the ones every phone button uses.
 *
 * Two things are the watch's own, and both are about a 227dp round face: no minimum height
 * (ButtonTargetHeight is 56dp, most of the screen) and no press-grow, which widens a button into its
 * row -- on a watch that means into the bezel. The heavy haptic is passed explicitly because the app's
 * is provided through LocalHaptics, which the watch does not set up.
 */
@Composable
internal fun WearButton(
    active: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    MorphButton(
        onClick = onClick,
        // Frosted: the aurora behind the row, blurred (when the device allows), under the translucent fill below.
        modifier = modifier.wearGlassBlur(RoundedCornerShape(50)),
        enabled = enabled,
        active = active,
        // Glass, as on the phone: a translucent pill that lets the aurora through; the filled primary is
        // kept for a switch that is on.
        containerColor = scheme.surfaceContainerHigh.copy(alpha = 0.6f),
        contentColor = scheme.onSurface,
        activeContainerColor = scheme.primary,
        activeContentColor = scheme.onPrimary,
        contentPadding = contentPadding,
        minHeight = 0.dp,
        expressive = false,
        onClickHaptic = {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        },
        content = content,
    )
}

/** An on/off row: a [WearActionRow] drawn as a switch. Stateless; the caller owns [checked] and what [onChange] does. */
@Composable
fun WearToggleRow(
    label: String,
    icon: ImageVector?,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) = WearActionRow(label, icon, modifier = modifier, active = checked, switch = true) { onChange(!checked) }

/** The quiet words under a row or a group: what it does, why, what is wrong. [small] is the finer print under a caption. */
@Composable
fun WearCaption(text: String, modifier: Modifier = Modifier, small: Boolean = false, maxLines: Int = 4) =
    WearCenteredText(
        text,
        modifier = modifier,
        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Normal,
        maxLines = maxLines,
    )

/**
 * A centred block of text for the round face -- the car name, the state line, empty/status
 * messages. Centred horizontally and constrained so nothing runs under the bezel.
 */
@Composable
fun WearCenteredText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onBackground,
    fontWeight: FontWeight? = FontWeight.SemiBold,
    maxLines: Int = 2,
) {
    RevealText(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp).fitRound(),
        textAlign = TextAlign.Center,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/*
 * The watch's standard full screens, so every one-off screen is the same shape: a centred icon badge, a title,
 * a line of detail, and (for a question) the round ✕ and ✓ every Wear OS confirmation uses.
 */

/** A message that fills the screen: no cars, waiting for the phone, a code to confirm. [extra] sits under the text. */
@Composable
fun WearMessageScreen(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    extra: (@Composable () -> Unit)? = null,
) {
    ScreenScaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize().wearAuroraWash(tint).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            // Scrolls: at a large font setting the badge, the words and the round buttons are taller than a small display.
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScreenBadge(icon, tint)
                RevealText(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                detail?.let {
                    RevealText(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                    )
                }
                extra?.invoke()
            }
        }
    }
}

/**
 * A yes/no question on its own screen -- unlocking the car, say: the icon, the question, and the round ✕ and ✓.
 * [destructive] colours the ✓ as a warning for an action that opens or stops something.
 */
@Composable
fun WearConfirmScreen(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    WearMessageScreen(icon = icon, title = title, detail = detail, tint = if (destructive) scheme.error else scheme.primary) {
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundChoice(Icons.Filled.Close, "Cancel", scheme.surfaceContainerHigh.copy(alpha = 0.6f), scheme.onSurface, onCancel)
            RoundChoice(
                Icons.Filled.Check, confirmLabel,
                if (destructive) scheme.error else scheme.primary,
                if (destructive) scheme.onError else scheme.onPrimary,
                onConfirm,
            )
        }
    }
}

/** One of a confirmation's two round buttons: the app's own button, as a 52dp circle. */
@Composable
private fun RoundChoice(icon: ImageVector, label: String, container: Color, content: Color, onClick: () -> Unit) {
    MorphButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp).semantics { contentDescription = label }.wearGlassRim(CircleShape),
        containerColor = container,
        contentColor = content,
        contentPadding = PaddingValues(0.dp),
        minHeight = 0.dp,
        pillCornerPercent = 50f,
        morphedCornerPercent = 50f,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** The tinted circle a full screen's icon sits in. */
@Composable
private fun ScreenBadge(icon: ImageVector, tint: Color) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.2f)).wearGlassRim(CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** One label/value line in a pebble: the label muted on the start, the value on the end. */
@Composable
internal fun WearStatRow(label: String, value: String, valueColor: Color? = null) {
    val scheme = MaterialTheme.colorScheme
    // A glass pill, like the phone's status rows.
    Row(
        Modifier
            .fillMaxWidth()
            .fitRound()
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RevealText(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        RevealText(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor ?: scheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
