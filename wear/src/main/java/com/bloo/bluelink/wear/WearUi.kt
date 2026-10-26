package com.bloo.bluelink.wear

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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.Surface
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
    Column(modifier.fillMaxWidth().padding(horizontal = ListInset), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        // The same heading as the phone's sections (:uicommon), centred for the round face and in the watch's own tint and size.
        SectionHeading(
            title, icon,
            modifier = Modifier.fitRound(),
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
internal val ListInset = 26.dp
internal val RowGap: Dp @Composable get() = com.bloo.uicommon.GapRow
internal val SectionGap: Dp @Composable get() = com.bloo.uicommon.GapBlock

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
    /** A line under the label saying what the row does (a switch's description, as on the phone). */
    description: String? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (active) scheme.primary else scheme.onSurface
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
                    Modifier.switchSemantics(active)
                } else {
                    Modifier
                },
            ),
        contentPadding = RowPadding,
        onClick = onClick,
    ) {
        // The glyph (or the in-flight spinner) and the label side by side, the label taking the row and wrapping to a
        // second line before it is ever cut. The shared icon-and-label layout DROPPED the label when it did not fit,
        // which left long rows ("Lock when I walk away", "Top left: Lock / unlock") as an icon and nothing else.
        // The phone's switch (:uicommon's SwitchTrack), in the phone's own colours: the accent when on, the muted pill when off.
        val switchTrack: @Composable () -> Unit = {
            SwitchTrack(
                checked = active, trackOn = scheme.primary, trackOff = scheme.surfaceVariant,
                thumbOn = scheme.onPrimary, thumbOff = scheme.onSurfaceVariant,
            )
        }
        // The shared setting layout (:uicommon's SettingRow): the label takes the row, and the switch, when there is one, sits on the end.
        SettingRow(
            modifier = Modifier.weight(1f),
            gap = 8.dp,
            label = { labelModifier ->
                Row(labelModifier, verticalAlignment = Alignment.CenterVertically) {
                    if (glyph != null) {
                        CompositionLocalProvider(LocalContentColor provides tint) {
                            MorphButtonLabel(icon = glyph, label = "", pending = pending, iconSize = 18.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    // The phone's name-and-detail column (:uicommon): the label, and the description under it when there is one.
                    TitleSubtitle(
                        label, description, Modifier.weight(1f),
                        // A switch row's label shares its line with the switch, so it takes the smaller step: "Charging" must not break mid-word.
                        titleStyle = if (switch) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        titleColor = tint, titleWeight = FontWeight.SemiBold,
                        subtitleStyle = MaterialTheme.typography.labelSmall,
                        subtitleColor = scheme.onSurfaceVariant,
                        // A switch row's caption is a sentence ("Off by itself in Battery Saver"): give it room to finish.
                        maxLines = if (switch) 3 else 2,
                    )
                }
            },
            trailing = if (switch) switchTrack else null,
        )
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
    val haptics = LocalHapticFeedback.current
    // Plain, as the phone's setting rows are: no glass and no fill. The active state is carried by the text colour, and by the
    // switch on a switch row, rather than by a filled container.
    MorphButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        active = active,
        containerColor = Color.Transparent,
        contentColor = scheme.onSurface,
        activeContainerColor = Color.Transparent,
        activeContentColor = scheme.primary,
        contentPadding = contentPadding,
        minHeight = RowMinHeight,
        pillCornerPercent = PillCorner,
        morphedCornerPercent = MorphedCorner,
        expressive = false,
        onClickHaptic = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
        content = content,
    )
}

/**
 * The watch's round key: a glass circle of a given [size] with the hairline rim, for a keypad's keys and any single-glyph
 * control. Its [description] is what a screen reader says, because its content is a glyph or a digit. Rows and wide buttons are
 * [WearButton]; this is the same glass for a shape that must stay a circle.
 */
@Composable
fun WearRoundButton(
    size: Dp,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        modifier = modifier
            .size(size)
            .wearGlassRim(CircleShape)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

/** A row's corners: a full pill at rest, the phone's rounded square (28%) while pressed. */
private const val PillCorner = 50f
private const val MorphedCorner = 28f

/** A row's height and padding: taller than a line of text needs, so a row is a target and not a strip. */
internal val RowMinHeight = 56.dp
internal val RowPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)

/**
 * A list row with two lines: the [title] and, under it, the [subtitle] (a preset's name and what it sets, a choice and what it
 * does). The same button as [WearActionRow], so it looks, morphs and presses like every other row; a [trailing] glyph marks
 * what a tap does.
 */
@Composable
fun WearListRow(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    pending: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (active) scheme.primary else scheme.onSurface
    val quiet = secondaryTextColor()
    WearButton(
        active = active,
        enabled = enabled && !pending,
        modifier = modifier.fillMaxWidth().fitRound(),
        contentPadding = RowPadding,
        onClick = onClick,
    ) {
        if (icon != null) {
            CompositionLocalProvider(LocalContentColor provides tint) {
                MorphButtonLabel(icon = icon, label = "", pending = pending, iconSize = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
        }
        // The phone's name-and-detail column (:uicommon), one line each for the round face.
        TitleSubtitle(
            title, subtitle, Modifier.weight(1f),
            titleStyle = MaterialTheme.typography.labelLarge, titleColor = tint, titleWeight = FontWeight.SemiBold,
            subtitleStyle = SecondaryTextStyle().copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), subtitleColor = quiet,
            maxLines = 1,
        )
    }
}

/** An on/off row: a [WearActionRow] drawn as a switch. Stateless; the caller owns [checked] and what [onChange] does. */
@Composable
fun WearToggleRow(
    label: String,
    icon: ImageVector?,
    checked: Boolean,
    modifier: Modifier = Modifier,
    /** What the switch does, under its label. */
    description: String? = null,
    onChange: (Boolean) -> Unit,
) = WearActionRow(label, icon, modifier = modifier, active = checked, switch = true, description = description) { onChange(!checked) }

/** The quiet words under a row or a group: what it does, why, what is wrong. [small] is the finer print under a caption. */
@Composable
fun WearCaption(text: String, modifier: Modifier = Modifier, small: Boolean = false, maxLines: Int = 4) =
    WearCenteredText(
        text,
        modifier = modifier,
        // The phone's secondary text (:uicommon's SecondaryTextStyle: semi-bold, with its halo) at the watch's own sizes.
        style = SecondaryTextStyle().copy(
            fontSize = (if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall).fontSize,
        ),
        color = secondaryTextColor(),
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
    style: TextStyle = MaterialTheme.typography.titleMedium,
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
            RoundChoice(AppIcons.Close, "Cancel", scheme.surfaceContainerHigh.copy(alpha = 0.6f), scheme.onSurface, onCancel)
            RoundChoice(
                AppIcons.Check, confirmLabel,
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
    // The phone's badge (:uicommon), sized for the watch and rimmed like its glass.
    IconBadge(icon, tint, modifier = Modifier.wearGlassRim(CircleShape), containerColor = tint.copy(alpha = 0.2f), size = 44.dp, iconSize = 24.dp)
}

/** One label/value line in a pebble: the label muted on the start, the value on the end. */
@Composable
internal fun WearStatRow(label: String, value: String, valueColor: Color? = null) {
    val scheme = MaterialTheme.colorScheme
    // A glass pill, like the phone's status rows.
    Row(
        Modifier.fillMaxWidth().fitRound().clip(CircleShape)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelValueRow(
            label = label, value = value, labelColor = scheme.onSurfaceVariant,
            valueColor = valueColor ?: scheme.onSurface,
        )
    }
}
