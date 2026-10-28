package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.IconBadge
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.SecondaryTextStyle
import com.bloo.uicommon.canBlurBackdrops
import dev.chrisbanes.haze.HazeState

/*
 * The search results' look: every result is its own floating glass pill, on a page that is frosted uniformly behind them. The pieces
 * are the app's standard ones (GlassSurface, IconBadge, the text roles); this file only fixes how they are used for search, so the
 * results, the suggestions and the AI cards all share one pill.
 */

/** The pill every search result is drawn in: rounded enough to read as a pill, with room for a control inside. */
internal val SearchPillShape = RoundedCornerShape(30.dp)

/** One glass pill. The page behind search is already frosted, so a pill is a near-solid glass with an edge and a shadow, not a second refraction. */
@Composable
internal fun SearchGlassPill(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val blurred = hazeState != null && canBlurBackdrops()
    GlassSurface(
        shape = SearchPillShape,
        modifier = modifier.fillMaxWidth(),
        hazeState = hazeState,
        tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (blurred) 0.66f else 0.96f),
        onClick = onClick,
        contentDescription = contentDescription,
        contentAlignment = Alignment.CenterStart,
        liquid = false,
        cheap = true,
        content = content,
    )
}

/** The inside of a pill: an optional round icon, then the text [content] taking the width, then an optional [trailing] mark. */
@Composable
private fun PillBody(
    icon: ImageVector?,
    tint: Color,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = GapSection, vertical = GapGroup), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconBadge(icon, tint = tint, size = 40.dp)
            Spacer(Modifier.width(GapGroup))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GapHairline), content = content)
        trailing?.invoke()
    }
}

/** A command (or an example to try): tapping the pill does it, or fills the search box. */
@Composable
internal fun ActionPill(icon: ImageVector, title: String, description: String?, tint: Color, hazeState: HazeState?, onClick: () -> Unit) {
    SearchGlassPill(hazeState, onClick = onClick, contentDescription = title) {
        PillBody(icon, tint, trailing = { ButtonIcon(AppIcons.ArrowForward) }) {
            TitleSmallText(title)
            description?.let { MutedText(it) }
        }
    }
}

/** A setting's own control (a switch, a slider, a picker) in a pill, with the car it belongs to above it when it is per-car. */
@Composable
internal fun ControlPill(caption: String?, hazeState: HazeState?, content: @Composable () -> Unit) {
    SearchGlassPill(hazeState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = GapSection, vertical = GapGroup),
            verticalArrangement = Arrangement.spacedBy(GapHairline),
        ) {
            caption?.let { MutedText(it) }
            content()
        }
    }
}

/** A short plain message in a pill ("No matches", "Ask for anything"). */
@Composable
internal fun MessagePill(title: String, body: String?, hazeState: HazeState?) {
    SearchGlassPill(hazeState) {
        PillBody(null, Color.Unspecified) {
            RevealText(title, style = PrimaryTextStyle())
            body?.let { MutedText(it) }
        }
    }
}

/** A small heading chip that sits between runs of pills ("Your cars", "Actions", "Settings"). */
@Composable
internal fun SearchHeading(text: String, hazeState: HazeState?) {
    GlassSurface(
        shape = CircleShape,
        hazeState = hazeState,
        shadow = false,
        liquid = false,
        cheap = true,
        tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
    ) {
        RevealText(
            text,
            style = SecondaryTextStyle(),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = GapSection, vertical = GapRow),
        )
    }
}

/** The scrolling list the pills float in: each pill is its own item, so they move, enter and leave one at a time. */
@Composable
internal fun SearchPillList(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(GapRow),
        contentPadding = PaddingValues(vertical = GapSection),
        content = content,
    )
}

/**
 * One item of the pill list: it eases into place when the list changes, and bends as it nears an edge with more list past it
 * (flattening, narrowing and fading), so a pill leaves and arrives instead of being cut off by the edge.
 */
internal fun LazyListScope.pillItem(key: Any, listState: LazyListState, content: @Composable () -> Unit) {
    item(key = key) { Box(Modifier.animateItem().pillEdgeFocus(listState, key)) { content() } }
}

private fun Modifier.pillEdgeFocus(state: LazyListState, key: Any): Modifier = graphicsLayer {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
    val zone = 64.dp.toPx()
    val top = (item.offset - info.viewportStartOffset).toFloat()
    val bottom = top + item.size
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val intoTop = if (state.canScrollBackward) ((zone - bottom) / zone).coerceIn(0f, 1f) else 0f
    val intoBottom = if (state.canScrollForward) ((top - (viewport - zone)) / zone).coerceIn(0f, 1f) else 0f
    val t = maxOf(intoTop, intoBottom)
    alpha = 1f - 0.7f * t
    scaleX = 1f - 0.08f * t
    scaleY = 1f - 0.2f * t
    transformOrigin = TransformOrigin(0.5f, if (intoTop >= intoBottom) 0f else 1f)
}

/**
 * A piece of the app's own UI, lifted out and floating: a bigger, livelier glass than an ordinary result (the glass refracts), tagged
 * with where it was lifted from, and set a little askew like a sheet pulled loose. It springs up into place when it arrives
 * (peeling off the page), and a person who has asked for less motion gets it already in place.
 */
@Composable
internal fun LiftedPiece(
    origin: String,
    icon: ImageVector,
    key: Any,
    hazeState: HazeState?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduce = com.bloo.uicommon.LocalReduceMotion.current
    val lift = remember(key) { androidx.compose.animation.core.Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduce) lift.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
    }
    // A faint, steady angle per piece (a fraction of a degree to a degree and a half), so a stack reads as loose sheets.
    val tilt = ((key.hashCode() and 7) - 3) * 0.4f
    val blurred = hazeState != null && canBlurBackdrops()
    GlassSurface(
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val t = lift.value
                val rest = 1f - t
                translationY = rest * 36.dp.toPx()
                scaleX = 0.92f + 0.08f * t
                scaleY = 0.92f + 0.08f * t
                rotationZ = tilt + rest * -4f
                alpha = minOf(1f, t * 1.6f)
            },
        hazeState = hazeState,
        tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (blurred) 0.6f else 0.96f),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxWidth().padding(GapSection), verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tint = MaterialTheme.colorScheme.primary, size = 28.dp, iconSize = 16.dp)
                Spacer(Modifier.width(GapRow))
                MutedText(origin)
            }
            content()
        }
    }
}
