package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.bloo.bluelink.data.GeoLocation
import kotlinx.coroutines.flow.first

/**
 * The "Me" button's action: centre the map on the phone. With no fix yet it asks for location (see
 * [DeviceLocationRequest]) and centres as soon as the first fix lands.
 */
@Composable
internal fun rememberShowMyLocation(
    location: GeoLocation,
    deviceLocation: GeoLocation?,
    mapState: CarMapState,
    request: DeviceLocationRequest?,
): () -> Unit {
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(pending, deviceLocation) {
        val d = deviceLocation
        if (pending && d != null) {
            mapState.showDevice(location.latitude, location.longitude, d.latitude, d.longitude)
            pending = false
        }
    }
    return {
        val d = deviceLocation
        if (d != null) {
            mapState.showDevice(location.latitude, location.longitude, d.latitude, d.longitude)
        } else {
            pending = true
            request?.ask?.invoke()
        }
    }
}

/**
 * The buttons under a map: one standard [MorphActionButton] per [MapFeature] in the app's standard
 * [ExpressiveButtonRow] -- as many to a line as fit, each line balanced and filled edge to edge,
 * a press pushing its neighbours, a button alone on its line resting on the start edge and widening
 * when pressed. Nothing here is special to maps: it is the same row every other group of buttons is.
 */
@Composable
internal fun MapFeatureRow(
    features: List<MapFeature>,
    modifier: Modifier = Modifier,
) {
    ExpressiveButtonRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = GapRow),
        spacing = GapRow,
    ) {
        features.forEach { feature ->
            MorphActionButton(
                label = feature.label,
                icon = feature.icon,
                onClick = feature.onClick,
                enabled = feature.enabled,
            )
        }
    }
}

/**
 * Everything that used to float over the top of an expanded map sheet as three
 * separate glass pills -- the vehicle-name pill (top-left), the drag handle
 * (top-center), and the refresh chip (top-right) -- consolidated into ONE bar
 * with one shared floating pill background. They used to each carry their own
 * hand-rolled (or near-identical) [GlassSurface], each with its own blur, its
 * own shadow, its own rim -- three separate pieces of glass chrome doing what
 * reads, and should always have read, as a single control strip. Giving up on
 * making that strip feel "uniform" with every other floating surface in the
 * app in some deeper sense and just building it as one plain bar was the
 * actual ask: a name on the left, a drag handle centered above a divider, and
 * a refresh action on the right, all inside one rounded rect.
 *
 * [onRefreshLocation] null (the [CarMapSheet]/[CarMapSheetBody] default) omits
 * the refresh side entirely rather than showing a dead button.
 *
 * [dragModifier] carries the vertical-drag-to-dismiss gesture -- built by the
 * caller, since the two call sites each close over their own [dragPx]/`scope`/
 * `close()`, and passing the finished modifier in is simpler than exporting a
 * matching set of callback params for the same thing.
 *
 * ONE line, not the old two-row layout (a drag-handle row stacked above a
 * name/refresh row) -- name at [Alignment.CenterStart], the drag handle nub
 * genuinely centred on the bar regardless of the name's length or whether a
 * refresh icon is showing, and the refresh icon at [Alignment.CenterEnd], all
 * three positioned independently in one [Box] rather than flowing through a
 * [Row]. Reported directly as wanting one line with bigger text and the
 * handle in the middle.
 */
@Composable
internal fun MapTopBar(
    vehicleName: String,
    mapHazeState: HazeState,
    modifier: Modifier = Modifier,
    dragModifier: Modifier,
    /** "88% · 415 km": the car's charge and range, under its name. Null leaves the name alone. */
    statusLine: String? = null,
    onRefreshLocation: (() -> Unit)? = null,
    /** True while a refresh this bar's own [onRefreshLocation] kicked off is
     *  still in flight -- the real command-pending flag from the caller
     *  (state.isPending(vin, "locate")), not a locally-faked timer, so the
     *  icon's spin genuinely tracks "still fetching" rather than a guessed
     *  duration. */
    refreshing: Boolean = false,
) {
    // Press-and-hold "pop" on the WHOLE pill -- not just the drag handle nub --
    // the instant any part of it is touched, before any actual drag motion,
    // reading as "I feel you, go ahead" rather than staying inert until it
    // moves. Reported directly as wanting the entire bar (name, handle AND
    // refresh indicator together) to pop, not just the nub in isolation.
    // requireUnconsumed = false: this OBSERVES the down/up sequence without
    // consuming it, so the same touch still reaches dragModifier's own gesture
    // detector -- popping must never steal the drag it's advertising, and the
    // refresh button's own click still fires normally (a click is a down+up
    // with no net movement, exactly what this passes through unconsumed).
    var pillPressed by remember { mutableStateOf(false) }
    val pillScale by animateFloatAsState(
        targetValue = if (pillPressed) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "mapBarPop",
    )
    GlassSurface(
        // A true pill, like the toasts and the search bar.
        shape = CircleShape,
        modifier = modifier.fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pillPressed = true
                    waitForUpOrCancellation()
                    pillPressed = false
                }
            }
            .graphicsLayer { scaleX = pillScale; scaleY = pillScale },
        hazeState = mapHazeState,
        // No contentColor override: [GlassSurface]'s own default (onSurface) is the
        // right answer here, and the `Color.White` this used to force was wrong in
        // light mode. This bar is glass over the MAP, and the map follows the app's
        // theme -- CarMap dark-filters these same OSM tiles only when
        // appIsDarkTheme() (see darkMapFilter's own doc), so in light mode the
        // backdrop behind this pill is a BRIGHT raster map. Unlike the hero's photo,
        // there is no dark scrim under it to make white legible: the only fill is
        // glassTint, which in light mode is surfaceContainer at 0.08-0.12 alpha, so
        // the car's name, the drag nub and the refresh glyph were near-white on
        // near-white -- the bar read as empty. onSurface tracks the same theme the
        // map filter does, so it is dark-on-light map and light-on-dark map by
        // construction, and it honours an active custom palette the way
        // the pin beside it already does. It is also what the map's own
        // MapFeatureRow buttons below already use.
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(dragModifier)
                // 48dp: tall enough for the bigger titleLarge name and a comfortable
                // touch target for both the drag handle and the refresh icon, all on
                // one line -- the old two-row layout (a 14dp handle strip stacked
                // above a name/refresh row) took noticeably more vertical space for
                // the same content.
                // heightIn(min), not a fixed 60dp: the bar stacks the car name over its
                // charge/range status line, and at a large font that two-line block is taller
                // than 60dp -- a hard height clipped the status line. A minimum keeps the
                // single-line look while letting the bar grow with the text.
                .heightIn(min = 60.dp)
                .padding(horizontal = 18.dp),
        ) {
            // Name and bigger, titleLarge (was titleMedium) -- reported directly as
            // wanting bigger text. Reserves room on the end for the refresh icon
            // (never under it) regardless of alignment, since both float independently
            // in this Box rather than sharing a Row's own space-distribution. Reserves
            // the SAME 40dp the refresh circle itself occupies (see below) -- previously
            // this reserved 44dp against a 36dp circle, a mismatched pair that was the
            // "refresh button is in the incorrect place, not equal" report: the circle
            // sat 8dp closer to the edge than the space carved out for it implied, so it
            // read as off-centre against its own reserved slot.
            // The car icon, its name, and under it the charge and range: the pill says which car this map
            // is and how it is doing, not just what it is called.
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = if (onRefreshLocation != null) 48.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GapGroup),
            ) {
                Icon(AppIcons.DirectionsCar, contentDescription = null, modifier = Modifier.size(24.dp))
                Column {
                    RollingNumber(
                        vehicleName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (statusLine != null) {
                        RollingNumber(
                            statusLine,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = mutedContentColor(),
                        )
                    }
                }
            }
            // The drag handle: a frosted glass pill, the same GlassSurface treatment as the
            // refresh circle at the other end of the row, so the whole header reads as one
            // glass material. It replaces a flat 28x3dp muted bar pinned to TopCenter, which
            // was reported as too small, misaligned against the name and refresh circle (both
            // vertically centred), and missing the frost the rest of the chrome has.
            //
            // CENTRE-aligned so its middle lines up with the name block and the refresh
            // circle's centre; 44x18dp with a 24x4dp inner bar, a real handle silhouette
            // rather than a hairline. Purely visual -- the pop and the drag are the whole
            // bar's (see `dragModifier` and the press tracker above), so this carries no
            // pointerInput of its own.
            GlassSurface(
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.Center).size(width = 44.dp, height = 18.dp),
                hazeState = mapHazeState,
                shadow = false,
            ) {
                Box(
                    Modifier
                        .size(width = 24.dp, height = 4.dp)
                        .background(LocalContentColor.current.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
                )
            }
            if (onRefreshLocation != null) {
                // Icon-only: a refresh indicator that spins while it is actually working (the real
                // pending flag), in the same ramp-up/steady-spin language as every in-progress icon.
                val angle = rememberSpinAngle(refreshing)
                // 40dp, matching the Text's own reserved end space above exactly --
                // was 36dp against a 44dp reservation, a mismatched pair (see the
                // Text's own comment). Centred in the 48dp-tall bar the same way the
                // drag handle and the name both are, so all three read as one row.
                // Extra right padding pushes the button further right (away from the edges).
                GlassSurface(
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(40.dp),
                    hazeState = mapHazeState,
                    onClick = onRefreshLocation,
                    contentDescription = "Refresh location",
                    // Same reason the bar around it dropped its own white override
                    // (see the outer GlassSurface): this nested circle is over the
                    // same theme-following map, and a forced white glyph vanished on
                    // a light one. Inherits GlassSurface's onSurface default.
                    // Nested inside the bar's own already-elevated GlassSurface --
                    // see glassEdge's own doc for why a nested panel skips the second
                    // shadow (GlassChrome.kt).
                    shadow = false,
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = angle.value },
                    )
                }
            }
        }
    }
}


/**
 * The pull-down-to-close drag both map sheets use on their top bar: the sheet follows the finger
 * ([dragPx]), and on release a pull of 96dp or more closes it while anything less springs back.
 * Distance, not velocity: a fixed 96dp pull is a close enough stand-in for "clearly meant to close".
 */
internal fun Modifier.pullDownToDismiss(
    dragPx: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    scope: kotlinx.coroutines.CoroutineScope,
    density: androidx.compose.ui.unit.Density,
    spring: androidx.compose.animation.core.AnimationSpec<Float>,
    close: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    detectVerticalDragGestures(
        onVerticalDrag = { change, amount ->
            change.consume()
            scope.launch { dragPx.snapTo((dragPx.value + amount).coerceAtLeast(0f)) }
        },
        onDragEnd = {
            val thresholdPx = with(density) { 96.dp.toPx() }
            if (dragPx.value > thresholdPx) close() else scope.launch { dragPx.animateTo(0f, spring) }
        },
        onDragCancel = { scope.launch { dragPx.animateTo(0f, spring) } },
    )
}


/** "88% · 415 km": a car's charge and range for the map pill, or null when it has neither yet. */
// chargeReadoutOf is @Composable (it reads MaterialTheme/LocalContentColor for the state-colour
// pair it derives), so this wrapper must be too -- not for composition itself, but because a
// plain function cannot resolve those locals.
@Composable
internal fun mapStatusLine(state: UiState, v: com.bloo.bluelink.data.Vehicle, metric: Boolean): String? {
    val r = chargeReadoutOf(state.statusFor(v), state.hasBattery(v), state.hasFuel(v), state.drivingLabel(v), metric)
    return listOfNotNull(r.pctText.takeIf { it.isNotBlank() }, r.rangeText?.takeIf { it.isNotBlank() })
        .joinToString(" · ").ifBlank { null }
}
