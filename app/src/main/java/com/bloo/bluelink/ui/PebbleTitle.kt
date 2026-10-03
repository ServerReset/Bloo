package com.bloo.bluelink.ui


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * The pebble header's title line: the title itself (which can swell on expand for the hero) with an optional
 * trailing stat, either beside the name or pushed to the far end. Split out of [PebbleShell], which only decides
 * where this row goes; it needs nothing from the header's size or press state.
 */
@Composable
internal fun PebbleTitleRow(
    title: String,
    expanded: Boolean,
    growTitleOnExpand: Boolean,
    titleColor: Color,
    titleTrailing: (@Composable () -> Unit)?,
    titleTrailingAtEnd: Boolean,
) {
    // Same heading fix as SettingsCard: with 8+ pebbles per
    // car and no heading structure, TalkBack users could
    // only reach a given section (Climate, Charge, ...) by
    // swiping through every row of every pebble above it.
    // The header grows and hardens as the pebble opens. Expanded, the
    // hero's header sits over a photo, so bigger and higher-contrast
    // is legibility rather than flourish -- and it makes opening feel
    // like the card is coming forward instead of just getting taller.
    //
    // Interpolated on the theme's SPATIAL spec (type size is a spatial
    // property) so it moves with the same physics as the expansion it
    // belongs to, and lerped through real type steps rather than being
    // scaled, so every frame is a genuine font size.
    // A slow, lightly-bouncy spring, NOT the theme's default spatial
    // spec. That default is tuned for a card's whole bounds, and driving
    // a TYPE STEP with it read as rough: it is quick enough that a
    // 16sp -> 24sp change lands in a handful of frames, and each of
    // those frames is a genuine re-layout at a new font size, so what
    // you see is a few discrete jumps rather than a glide.
    //
    // Expand: dampingRatio 0.62 gives a real overshoot -- the name grows a
    // touch past its target and settles back -- and StiffnessVeryLow
    // stretches it over enough frames for the intermediate sizes to
    // read as motion instead of steps. Both halves matter: bounce with
    // a fast spring is still steppy, and a slow spring without bounce
    // is just a slower version of the same flat move. (Collapse no longer
    // shares this stiffness -- see its own doc below.)
    // Only animates for the pebble that asked (the hero). For the
    // rest the target is a constant 0, so the spring never leaves its
    // resting value, titleStyle stays titleMedium, and the per-frame
    // font-size relayout never happens at all.
    // The STATE, not its value. Expanding runs for a second or more
    // (StiffnessVeryLow); collapsing is quicker now (PebbleBounceStiffness,
    // see below) but still real per-frame work either way, and reading it
    // here put every one of those frames on the composition path for this
    // whole header -- the Row, the title, the trailing slot, the split
    // button -- when its only consumers are the .layout{} and graphicsLayer
    // lambdas below, which invalidate layout and draw respectively and
    // nothing else.
    // Different damping each direction, not one spring run in reverse: a
    // bounce that reads as delight growing INTO its target reads as a
    // jump/wobble shrinking back OUT of it -- reported jumpiness on
    // collapse that the expand side never had a matching complaint for.
    // Collapse gets a critically-damped spring (1.0, no overshoot at all)
    // -- but at PebbleBounceStiffness (200), the SAME stiffness the card's
    // own frame (corner, body collapseExit) collapses with, not the
    // expand side's StiffnessVeryLow (50). Still reported jumpy after three
    // prior fixes to this exact spring's OWN internal behavior (the
    // atRestScale threshold/Crossfade work above) -- because none of those
    // addressed the actual mismatch: at 4x the card frame's settle speed,
    // the card visibly finished collapsing to its final size well before
    // the name had finished shrinking inside it, so the name kept visibly
    // resizing after everything around it had already stopped moving,
    // which reads as the animation "catching up" rather than one continuous
    // motion. Matching stiffness means both finish together. Expand keeps
    // its own slower StiffnessVeryLow (with the 0.62 overshoot) --
    // untouched, since the complaint was specifically about collapse, and a
    // slow, lightly-bouncy GROWTH still reads as intentional delight rather
    // than lag the way a slow SHRINK reads as disconnected.
    val expandingTitle = expanded && growTitleOnExpand
    val headerTState = animateFloatAsState(
        targetValue = if (expandingTitle) 1f else 0f,
        animationSpec = if (expandingTitle) {
            lowPowerAwareSpring(dampingRatio = 0.62f, stiffness = Spring.StiffnessVeryLow)
        } else {
            lowPowerAwareSpring(dampingRatio = 1f, stiffness = PebbleBounceStiffness)
        },
        label = "pebbleHeaderGrow",
    )
    // Drawn at the LARGER size always and SCALED down, rather than
    // lerping the font size. The lerp was the choppiness: a Text
    // measures through ParagraphLayoutCache, which is single-slot, so a
    // font size that changes every frame misses it every frame -- 100%
    // invalidation, a full text relayout per frame, and the visible
    // result is a few discrete steps rather than a glide. Scaling a
    // layout measured ONCE is what Compose itself recommends for
    // animated type, and it is draw-phase only.
    //
    // headlineSmall is the base and it scales DOWN, never up: text
    // scaled down stays crisp, upscaling is what goes soft.
    //
    // transformOrigin pins the LEFT edge so the name grows out of its
    // own start position instead of drifting sideways from the centre.
    val titleStyle = MaterialTheme.typography.headlineSmall
    // Ratio of the two real type steps, so the collapsed size still
    // equals titleMedium exactly rather than a hand-picked number.
    val collapsedTitleScale = with(LocalDensity.current) {
        MaterialTheme.typography.titleMedium.fontSize.toPx() /
            MaterialTheme.typography.headlineSmall.fontSize.toPx()
    }
    // Plain arithmetic, not lerp(): this file imports the Color, TextStyle
    // and Dp overloads of `lerp` but NOT the Float one from
    // androidx.compose.ui.util, so a Float call does not resolve -- which
    // is exactly how the first attempt at this broke the build. Spelling
    // out the interpolation removes the dependency on which overload
    // happens to be in scope.
    // A lambda, so each reader pulls the current value at ITS phase.
    // growTitleOnExpand is false for every pebble but the hero, and then
    // this is a constant that never reads the spring at all.
    val titleScale: () -> Float = if (!growTitleOnExpand) {
        { collapsedTitleScale }
    } else {
        { collapsedTitleScale + (1f - collapsedTitleScale) * headerTState.value }
    }
    // True whenever the title is sitting at its collapsed rest size rather
    // than mid-grow -- which is EVERY pebble but the hero (growTitleOnExpand
    // is false for the rest, so titleScale() is permanently
    // collapsedTitleScale), and the hero itself whenever headerTState has
    // settled BACK DOWN near 0f rather than mid-spring.
    //
    // A THRESHOLD, not `== 0f`: a StiffnessVeryLow spring is exactly the
    // one this session already flagged as running "for a second or more",
    // and collapsing the hero once and never expanding it again was the
    // one path that could leave headerTState sitting at some very-nearly-
    // but-not-bit-for-bit-zero value indefinitely -- which pinned this
    // Text on the SCALED path forever afterward, permanently reproducing
    // the very baseline mismatch this whole mechanism exists to avoid.
    // Confirmed from a real report: expand the hero once, collapse it, and
    // the name stayed misaligned from then on -- until swiping to another
    // car and back gave it a fresh composition (headerTState starting
    // exactly at its target, since there is no previous value to animate
    // from on first composition) which "corrected itself" for exactly that
    // reason.
    //
    // At rest, render the title in a NATIVE titleMedium Text instead of a
    // scaled-down headlineSmall one. The scale trick above exists so the
    // hero's name can grow smoothly through every intermediate size as the
    // card expands -- a discrete style swap mid-animation would relayout
    // and jank every frame (see the scale block's own doc). But scaling a
    // BIGGER style down by its font-size ratio does not necessarily
    // reproduce a native SMALLER style's own baseline-to-box-centre ratio --
    // lineHeight is not always a fixed fraction of fontSize across type
    // steps -- so a scaled headlineSmall sitting beside the hero's own
    // titleMedium-styled numbers left their glyphs a few px off each
    // other's baseline no matter how precisely CenterVertically (or a
    // computed correction) tried to reconcile two DIFFERENT styles' boxes.
    // Confirmed after two earlier attempts at exactly that, both from real
    // screenshots.
    //
    // At true rest there is no animation to protect, so this renders the
    // SAME font size through the SAME style object the numbers already use
    // (HeroNumbers' pctStyle is titleMedium at t=0) -- identical metrics,
    // so CenterVertically genuinely cannot land them apart. Every other
    // pebble's title was already reading fine here (nothing beside it needs
    // a text baseline), and this only swaps which style produces the same
    // on-screen font size for them too.
    //
    // 0.02f, not the tighter 0.001f this used to be: animateFloatAsState's
    // own spring implementation SNAPS its value to the exact target the
    // instant it's within its (much smaller) internal visibility threshold,
    // rather than creeping the rest of the way over further frames -- so at
    // 0.001f this swap fired on the SAME frame as that internal snap, one
    // discontinuity (the scale jumping straight to its exact rest value
    // instead of the smoothly-decaying delta every prior frame had) landing
    // on top of another (this Crossfade beginning). Reported as a jump right
    // at the tail of the collapse even with the Crossfade below already in
    // place -- it was softening the STYLE swap, not this scale discontinuity
    // feeding into it. 0.02f fires the swap a little earlier, while the
    // scaled title is still visibly mid-decay (imperceptibly close to rest --
    // 2% of the gap between collapsed and expanded scale, not 2% of the
    // glyph size), so the Crossfade's own blend absorbs the last bit of
    // motion instead of colliding with the spring's own hard snap.
    val atRestScale = !growTitleOnExpand || headerTState.value < 0.02f
    Row(
        // Only stretched when the trailing slot is being pushed to the
        // end -- a row that merely holds a name and a stat must stay
        // shrink-wrapped, or the stat drifts away from the name.
        modifier = if (titleTrailingAtEnd) Modifier.fillMaxWidth() else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        // SpaceBetween, not a filled/weighted title, pushes titleTrailing to
        // the row's far end. `weight(1f, fill = true)` did that job before by
        // forcing this Text's own MEASURE constraints (minWidth == maxWidth ==
        // the whole remaining row) regardless of how short the title actually
        // was -- and the scaled-title `.layout{}` below measures against
        // whatever width it is handed before shrinking it back down, so a
        // short title ("Updates", "AI") in a wide forced box came out
        // reporting -- and drawing -- a box far wider than its own glyphs,
        // with the word adrift inside it rather than hugging the icon.
        // Confirmed from two separate real screenshots. SpaceBetween reaches
        // the same "trailing sits hard right" result from the OUTSIDE, off two
        // children's natural widths, so the title is never measured wider than
        // its own (possibly ellipsized) content.
        horizontalArrangement = if (titleTrailingAtEnd) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.Start
        },
    ) {
    // The title Text's own modifier chain up to (not including) the scale
    // machinery -- shared by both branches below.
    val titleBaseModifier = Modifier
        // fill = false always now (see the Row's own doc above) -- weight
        // still caps the title's MAX width to its fair share so a long
        // title ellipsizes instead of pushing titleTrailing off the row,
        // it just no longer forces the title to measure wider than its
        // own content.
        .weight(1f, fill = false)
        // The hard minimum gap "Sounds & vibration" needed -- previously a
        // Spacer sitting between title and titleTrailing as a third row
        // child, moved onto the title itself so SpaceBetween above still
        // sees exactly two children and gives the whole remaining width
        // to one gap rather than splitting it around a spacer.
        .then(if (titleTrailingAtEnd) Modifier.padding(end = 12.dp) else Modifier)
    if (growTitleOnExpand) {
        // Only the hero ever actually flips atRestScale -- every other
        // pebble's is permanently true, so it takes the plain branch below
        // with no Crossfade at all. Wrapping this in Crossfade softens the
        // one moment the scaled-headlineSmall path and the native-titleMedium
        // path hand off to each other: their box geometry isn't pixel-
        // identical (see atRestScale's own doc -- scaling a bigger style down
        // doesn't reproduce a smaller style's own baseline-to-box-centre ratio
        // exactly), so swapping which one is drawn on a single frame was a
        // real, reported pop right at the tail of the collapse. A short
        // alpha cross-dissolve over the swap doesn't need either path to be
        // pixel-perfect against the other -- it just means the viewer's eye
        // is never asked to register a same-frame jump.
        // Wrapped in a Box with an explicit CenterStart alignment, rather
        // than relying on Crossfade's own default (TopStart): the two
        // outgoing/incoming Texts below are NOT guaranteed the same box
        // height (that's the whole reason this Crossfade exists -- see its
        // doc above), so top-aligning them stacks glyphs at visibly
        // different vertical offsets for the whole 140ms blend instead of
        // sharing the row's own CenterVertically. Matches the Row's own
        // alignment and the left-anchored transformOrigin the scaled path
        // already uses.
        Box(modifier = titleBaseModifier, contentAlignment = Alignment.CenterStart) {
        Crossfade(
            targetState = atRestScale,
            animationSpec = tween(MotionFast),
            label = "heroTitleRestSwap",
        ) { atRest ->
            Text(
                title,
                modifier = if (atRest) {
                    Modifier
                } else {
                    Modifier
                        // Reports the DRAWN size. graphicsLayer scales the
                        // drawing and leaves the measured size alone, so
                        // without this the title's box stayed headline-TALL
                        // while its glyphs were title-sized -- which made the
                        // row taller than the text in it and pushed
                        // everything beside the name out of line.
                        //
                        // Measure once at headlineSmall, then report width and
                        // height multiplied by the same scale the layer draws
                        // with, and place the (still full-size) content
                        // centred on that smaller box so scaling about its
                        // left-centre keeps the glyphs where the box says they
                        // are. This is what lets `titleTrailing` sit against
                        // the name's real edge rather than a headline-sized
                        // box.
                        .layout { measurable, constraints ->
                            // Measured against constraints widened by
                            // 1/titleScale. The Text is measured at
                            // headlineSmall and DRAWN scaled down to
                            // titleScale (~0.7), so measuring it against the
                            // raw width made it ellipsize on headline-sized
                            // glyphs and then shrink the result --
                            // "Announcements" became "Announce..." with a
                            // third of the row still empty. Widening first
                            // means it ellipsizes on the width it is actually
                            // drawn at, and the scaled-down report below still
                            // lands inside the real constraint.
                            val scale = titleScale()
                            val room = if (constraints.hasBoundedWidth && scale > 0f) {
                                constraints.copy(
                                    maxWidth = (constraints.maxWidth / scale)
                                        .roundToInt()
                                        .coerceAtLeast(constraints.maxWidth),
                                )
                            } else {
                                constraints
                            }
                            val placeable = measurable.measure(room)
                            val w = (placeable.width * scale).roundToInt()
                            val h = (placeable.height * scale).roundToInt()
                            val yOffset = (h - placeable.height) / 2
                            layout(w, h) {
                                placeable.place(0, yOffset)
                            }
                        }
                        .graphicsLayer {
                            val s = titleScale()
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                },
                style = if (atRest) MaterialTheme.typography.titleMedium else titleStyle,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        }
    } else {
        // The common case: no grow/shrink, no rest-scale swap ever, so no
        // Crossfade wrapper either -- always native titleMedium.
        Text(
            title,
            modifier = titleBaseModifier,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            fontWeight = FontWeight.Bold,
            // Cap at one line: at a large display/font size the
            // header action button (SplitExpandButton, now width-
            // bounded below) used to squeeze this weighted Column
            // so a title like "Location"/"Weather"/"Diagnostics"
            // wrapped and visually collided with the button. One
            // line + ellipsis keeps the title on its own line.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // Trailing content on the TITLE row, so a pebble that wants a
    // headline stat does not need a third row for it. The hero puts
    // its percentage and range here, which is what lets the collapsed
    // card be name-and-numbers over a bar instead of three stacked
    // lines with the bar stranded at the bottom.
    //
    // No Spacer before it any more, and no styling applied here:
    // the slot owns both. The hero shows this only while collapsed,
    // and a 10dp gap left behind when it goes would squeeze the
    // expanded title for a node that is no longer in the row. The hard
    // minimum gap for titleTrailingAtEnd now lives on the title's own
    // trailing padding above, not as a separate Spacer here -- see that
    // Text modifier's own doc.
    // Plain Box, no forced baseline alignment: a `Modifier.alignByBaseline()`
    // here once tried to line titleTrailing's glyphs up with the title's own
    // baseline, reasoning that a Box (like Row/Column) forwards a single
    // child's first baseline as its own. That held for the Box itself, but
    // the hero's numbers report no baseline AT ALL by the time it would
    // reach here -- AnimatedContent, RollingNumber's own Row and HeroNumbers'
    // three-child Row each sit between the digits and this slot, and none of
    // them forward one without an explicit alignByBaseline() opt-in on a
    // child. With no baseline to align to, the Row's baseline placement fell
    // back to the box's OWN bottom edge -- worse than the plain
    // `verticalAlignment = CenterVertically` this Row already carries, which
    // is what the numbers were built to sit in (see HeroNumbers' own
    // `verticalAlign` doc). Falling back to that default here is the fix.
    // AnimatedVisibility, not a bare `if` -- titleTrailing flips in and
    // out purely as a SIDE EFFECT of switching Settings between simple
    // and advanced (see Pebble()'s inlineSimple/canToggle above), and
    // that used to just pop the inline control in or the chevron back
    // out with no transition at all, while the body a few lines below
    // was already gliding open/closed on a real spring -- the header
    // rearranging felt disconnected from the card it was on. Same
    // fade+scale language as this card's own lock-state readout
    // elsewhere in the app (see StateControl's "lockStateAnim").
    //
    // `lastTitleTrailing`, not `titleTrailing` itself, inside the
    // AnimatedVisibility content: the mode switch that hides this slot
    // sets titleTrailing to null on the SAME recomposition that starts
    // the exit animation, and AnimatedVisibility's content lambda keeps
    // re-running every frame of that exit -- rendering the live (already
    // null) value would fade out nothing instead of the control that was
    // actually there.
    var lastTitleTrailing by remember { mutableStateOf(titleTrailing) }
    if (titleTrailing != null) lastTitleTrailing = titleTrailing
    AnimatedVisibility(
        visible = titleTrailing != null,
        enter = fadeIn(tween(MotionShort)) + scaleIn(tween(MotionShort), initialScale = 0.85f),
        exit = fadeOut(tween(MotionFast)) + scaleOut(tween(MotionFast), targetScale = 0.85f),
    ) {
        lastTitleTrailing?.let { Box { it() } }
    }
    }
}
