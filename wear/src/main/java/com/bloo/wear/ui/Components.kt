package com.bloo.wear.ui

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.ScalingParams
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Horizontal ScalingLazyColumn inset that actually widens on a round screen.
 * Only HomeScreen accounted for `isScreenRound` before this; Settings, Login,
 * Trips, and the tile-reorder screen all used a flat inset regardless of
 * screen shape, so on a genuinely round watch their text/card edges sat
 * noticeably closer to the curved bezel than Home's did -- worst on the
 * reorder screen, which used only 8dp total.
 *
 * Mechanism: reads the current [androidx.compose.ui.platform.LocalConfiguration]
 * composition local (recomposes automatically if config ever changes, e.g. a
 * multi-window/display config swap) and picks between the two supplied
 * constants based on its `isScreenRound` flag. No measurement or layout pass
 * is involved -- this is a pure lookup, cheap enough to call on every screen.
 */
@Composable
fun roundSafeHorizontalPadding(flat: Dp = 14.dp, round: Dp = 22.dp): Dp =
    if (androidx.compose.ui.platform.LocalConfiguration.current.isScreenRound) round else flat

/**
 * A card with a consistent uppercase, bold, primary-tinted header (optional
 * icon), then content -- the one card language every screen (Home's tiles,
 * Settings' sections) should share instead of each rolling its own styling.
 */
@Composable
fun SectionCard(
    title: String?,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A plain Box, not Wear Compose Material3's Card -- that component has no
    // non-interactive overload (verified against the library source: every
    // Card requires onClick), so `Card(onClick = {})` made every single
    // section on every watch screen its own focusable "double tap does
    // nothing" TalkBack stop before you ever reached its actual content. A
    // Box with the same clip+background reproduces the identical flat-tonal
    // look (this call only ever set containerColor, no border/elevation/
    // interaction-dependent styling that a real Card would otherwise add).
    val cardShape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            // Buttons inside these cards already have a visible outline
            // (see MorphButton), so without one here the card containing
            // them read as flatter/less "material" than its own contents --
            // backwards from what should draw the eye. A faint rim gives
            // the card the same depth language.
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)), cardShape)
            // Without this, a card's row count changing (an "Unsaved changes"
            // hint appearing, a preset row being deleted) snapped instantly
            // while every button/dot/page transition elsewhere in the app is
            // spring-animated -- the one un-animated size change left in an
            // otherwise motion-consistent app. StiffnessLow+LowBouncy (the
            // slowest, bounciest combination in the file, vs. every other
            // spring here using Medium/High stiffness) noticeably overshot
            // and settled slowly for something that fires on routine events
            // like a status refresh clearing an alert row mid-scroll.
            .animateContentSize(spring(dampingRatio = com.bloo.uicommon.SoftDamping, stiffness = Spring.StiffnessMediumLow)),
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Used for nearly every section on every watch screen
                    // (Home, Settings, Trips) -- without heading(), TalkBack's
                    // heading-navigation gesture found zero headings anywhere
                    // in this app despite section titles being the natural
                    // navigation landmarks.
                    Text(
                        title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            content()
        }
    }
}

/**
 * The Wear text-entry pattern: tapping launches the system input overlay
 * (keyboard / voice / handwriting) and the typed text comes back via RemoteInput.
 * Returns a lambda to trigger it.
 *
 * Mechanism: [rememberLauncherForActivityResult] registers an
 * ActivityResultContracts.StartActivityForResult() launcher tied to this
 * composable's lifecycle (registered once, survives recomposition because it's
 * `remember`ed internally by the launcher API). Calling the returned lambda
 * builds a RemoteInput "action" intent (the same system surface the watch uses
 * for notification quick-replies) carrying one RemoteInput slot keyed by [KEY]
 * with [label] as its prompt, then launches it. When the system input overlay
 * returns, the launcher's callback pulls the typed/spoken text back out of the
 * result Intent's extras via [RemoteInput.getResultsFromIntent], keyed by the
 * same [KEY] constant used to build the request -- and only invokes [onResult]
 * if non-null/non-blank text actually came back (a cancelled overlay returns a
 * null `res.data`, which the early return guards against).
 */
@Composable
fun rememberWearTextInput(label: String, onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)?.getCharSequence(KEY)?.toString()
        if (!text.isNullOrBlank()) onResult(text)
    }
    return {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(
            intent,
            listOf(RemoteInput.Builder(KEY).setLabel(label).build()),
        )
        launcher.launch(intent)
    }
}

private const val KEY = "bloo_input"

/** Charge/fuel percentage as a ring with the value centred. The ring colour
 *  reflects state: green while charging, red when critically low, else accent.
 *  Percentage ring and color animate smoothly on value change.
 *
 *  Mechanism: `percent` is treated as "unknown" when null -- the ring still
 *  renders (so layout never collapses/jumps when a snapshot briefly lacks a
 *  reading) but shows a "—" label and defaults the color branch's null-percent
 *  case to 100 (i.e. "not critically low") so a momentarily-missing percent
 *  never flashes error-red. The progress value is clamped to 0..100 before
 *  converting to the CircularProgressIndicator's 0f..1f range, guarding
 *  against out-of-range API data driving the indicator past full or negative.
 *  Both the fill fraction and the ring's color are wrapped in their own
 *  `animateFloatAsState`/`animateColorAsState`, so a snapshot update (say,
 *  percent jumping 42 -> 45, or charging flipping true) tweens smoothly
 *  instead of snapping -- each animation runs independently and on its own
 *  duration (progress: 800ms, color: 400ms), since a color change is meant to
 *  read faster than the physical fill sweeping around. */
@Composable
fun ChargeRing(
    percent: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    charging: Boolean = false,
) {
    val ringDesc = percent?.let { "Charge $it percent" } ?: "Charge level unknown"
    val ringColor = when {
        charging -> WearColors.chargeGreen
        (percent ?: 100) < 15 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val animatedProgress by animateFloatAsState(
        targetValue = (percent ?: 0).coerceIn(0, 100) / 100f,
        animationSpec = tween(800),
        label = "chargeProgress",
    )
    val animatedColor by animateColorAsState(
        targetValue = ringColor,
        animationSpec = tween(400),
        label = "chargeColor",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).semantics { contentDescription = ringDesc },
    ) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(size),
            colors = ProgressIndicatorDefaults.colors(indicatorColor = animatedColor),
        )
        // Was a hand-rolled AnimatedContent with the exact same transitionSpec
        // uicommon's shared AnimatedValue already centralizes.
        com.bloo.uicommon.AnimatedValue(
            value = percent?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

/** A small OSM map thumbnail centred on the car, with a marker. Shows a
 *  loading indicator while the tile downloads and an error state on failure
 *  with tap-to-retry. */
@Composable
fun MapThumbnail(lat: Double, lon: Double, modifier: Modifier = Modifier) {
    // Round to ~11m precision before keying: GPS jitter between status
    // refreshes shouldn't force a tile URL recompute (and, since the URL is
    // the Coil cache/request key, a live car sitting still shouldn't look
    // like it's re-fetching the same map tile on every poll).
    val latKey = (lat * 10000).toInt()
    val lonKey = (lon * 10000).toInt()
    // `remember(latKey, lonKey)` only recomputes the tile math when the rounded
    // key actually changes, so this whole block (including the network-bound
    // URL) is stable across recompositions caused by unrelated state.
    val tile = remember(latKey, lonKey) {
        // Standard "slippy map" Web Mercator tile projection at fixed zoom 15:
        // convert lat/lon (degrees) into fractional tile coordinates (xf, yf) in
        // the 0..n range, where n = 2^zoom is the number of tiles per axis at
        // this zoom level. `xf` is a simple linear scale of longitude; `yf` uses
        // the standard Mercator latitude formula (via tan/cos/ln) that maps
        // latitude non-linearly so the map's y-axis stays visually undistorted.
        val z = 15
        val n = (1 shl z).toDouble()
        val latRad = Math.toRadians(lat)
        val xf = (lon + 180.0) / 360.0 * n
        val yf = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        // Truncating to Int gives the actual tile indices (xt, yt) to fetch;
        // the fractional remainder (xf - xt, yf - yt) is the car's position
        // *within* that tile (0..1 on each axis), used below to place the
        // marker dot at the right pixel offset inside the downloaded image.
        val xt = xf.toInt()
        val yt = yf.toInt()
        Triple("https://tile.openstreetmap.org/$z/$xt/$yt.png", (xf - xt).toFloat(), (yf - yt).toFloat())
    }
    val url = tile.first
    val mx = tile.second
    val my = tile.third
    val marker = MaterialTheme.colorScheme.error
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHigh
    val context = androidx.compose.ui.platform.LocalContext.current
    // Bumped by the retry tap below; resets to 0 whenever the underlying tile
    // `url` changes (car moved to a new tile) via the `remember(url)` key, so a
    // stale retry counter from a previous location never lingers.
    var retryKey by remember(url) { mutableStateOf(0) }
    // Appending a `?retry=N` query param busts Coil's cache key (which is
    // derived from the request URL) without changing the actual tile fetched —
    // this forces a fresh network attempt on tap instead of Coil just
    // re-serving the same cached failure.
    val loadUrl = if (retryKey > 0) "$url?retry=$retryKey" else url
    val asyncPainter = rememberAsyncImagePainter(
        model = loadUrl,
        imageLoader = com.bloo.wear.WearImage.loader(context),
    )
    val paintState = (asyncPainter as? AsyncImagePainter)?.state
    val isError = paintState is AsyncImagePainter.State.Error
    val isLoading = paintState is AsyncImagePainter.State.Loading
    val thumbShape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .size(116.dp)
            .clip(thumbShape)
            .background(placeholder)
            // Same flat surfaceContainerHigh tone as the SectionCard it sits
            // inside -- without a border it visually merges with its parent
            // card while loading/erroring, before the map image gives it any
            // definition of its own.
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)), thumbShape)
            .clickable(enabled = isError) { retryKey++ },
        contentAlignment = Alignment.Center,
    ) {
        if (isError) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Tap to retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp),
            )
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            androidx.compose.foundation.Image(
                painter = asyncPainter,
                contentDescription = "Map of car location",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Canvas(Modifier.matchParentSize()) {
                drawCircle(marker, radius = 6.dp.toPx(), center = Offset(mx * size.width, my * size.height))
            }
        }
    }
}

/** Relative "x min ago" for a wall-clock timestamp. */
fun relativeLabel(ms: Long?): String = com.bloo.bluelink.data.relativeLabel(ms)

/** "1h 20m" / "45 min". */
fun fmtMinutes(min: Int): String = com.bloo.bluelink.data.fmtMinutes(min)

/** A label → value row used in the details card. Both sides truncate so a long
 *  value (efficiency, address, kWh) can never collide with the label on a round face. */
@Composable
fun StatusRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Was a plain Text -- phone's StatusRow already animates its value
        // cell via uicommon.AnimatedValue; this was the one platform where
        // the same conceptual row (battery %, range, tire pressure, the
        // diagnostics roll-up count, ...) still popped instead of crossfading.
        AnimatedValue(
            value = value,
            style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.End),
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** A labelled value + the phone's exact custom slider (ported). */
@Composable
fun SliderRow(
    label: String,
    valueLabel: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    accent: Color? = null,
    /** See [AnimatedSlider]'s onSettle -- pass this for a caller whose commit
     *  (a DataStore write, a phone push) shouldn't fire on every drag tick. */
    onSettle: (() -> Unit)? = null,
    onValue: (Int) -> Unit,
) {
    val fill = accent ?: MaterialTheme.colorScheme.primary
    // Guard the divisor: a step of 0 would throw ArithmeticException in composition.
    val safeStep = step.coerceAtLeast(1)
    val steps = ((max - min) / safeStep - 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        AnimatedSlider(
            value = value.toFloat(),
            onValueChange = { onValue(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = steps,
            accent = fill,
            onSettle = onSettle,
        )
    }
}

/** Map a 62-82°F setpoint to the blue->green->warm slider colour -- shared
 *  with the phone (uicommon.tempColor) so both platforms use the same
 *  canonical palette and the same spring animation instead of each having
 *  drifted a stop away from BlooColors' cool/tempMid/tempHot and only the
 *  phone side actually animating the transition. */
@Composable
fun tempColor(tempF: Int): Color = com.bloo.uicommon.tempColor(tempF)

/**
 * The app's fully custom slider — now a thin wrapper over the single shared
 * implementation in :uicommon so the hand-drawn track/thumb/tick logic lives in
 * exactly one place.
 */
@Composable
fun AnimatedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    accent: Color = MaterialTheme.colorScheme.primary,
    /** Fires once when the drag ends, after [onValueChange] has already landed
     *  the final value -- the hook for callers whose commit is expensive
     *  (a DataStore write, a phone push) and shouldn't fire on every tick. */
    onSettle: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    com.bloo.uicommon.AnimatedSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        accent = accent,
        inactiveColor = scheme.surfaceContainerHigh,
        dotOnActive = scheme.onPrimary.copy(alpha = 0.7f),
        dotOnInactive = scheme.onSurfaceVariant.copy(alpha = 0.5f),
        reduceMotion = LocalReduceMotion.current,
        onStepTick = { haptics.tick() },
        onSettle = { haptics.click(); onSettle?.invoke() },
    )
}

/** The app's pill→rounded-square morphing button, for Wear. Matches the phone's MorphButton.
 *  [secondaryLabel] adds a small caption line below [label] (e.g. a field name
 *  under its current value) — every button-shaped control in the wear app
 *  should go through this one component rather than a raw Wear Button/
 *  FilledTonalButton/OutlinedButton/SwitchButton, so contrast, press feedback,
 *  and the pill-morph motion stay consistent everywhere. */
@Composable
fun MorphButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    pending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    /** False disables the button (dimmed, unclickable) without pending's
     *  spinning-icon "something is in flight" implication -- for states like
     *  "no data yet" that aren't actually a network request in progress. */
    enabled: Boolean = true,
    /** Non-null exposes this as a real switch to TalkBack (Role.Switch +
     *  the current on/off state), for genuine toggles (Lock, Climate,
     *  Charge, AI enabled, PIN lock). MorphButton is also used for plain
     *  action buttons ("Settings", "Refresh") and pickers, where announcing
     *  a toggle role/state would be actively wrong -- null (the default)
     *  leaves those exactly as before, relying on the label text alone. */
    toggled: Boolean? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    // 50% = true pill; 28% = rounded rectangle — phone's exact values with the same spring.
    val pct by animateFloatAsState(
        targetValue = if (active || pressed) 28f else 50f,
        animationSpec = spring(dampingRatio = com.bloo.uicommon.SoftDamping, stiffness = Spring.StiffnessMedium),
        label = "morphCorner",
    )
    // A quick, snappy press-punch independent of the (slower, shape-driven)
    // morph above — corner-radius alone was too subtle to register as "the
    // button reacted" against the dark, low-contrast card background.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "morphPressScale",
    )
    // Shared buttonContainer formula — phone uses surfaceContainerHighest,
    // watch has only surfaceContainerHigh. Passing the closest available
    // surface + onSurface gives a consistent visual result across platforms.
    val containerColor = com.bloo.uicommon.BlooColors.buttonContainer(
        scheme.surfaceContainerHigh, scheme.onSurface
    )
    val bg by animateColorAsState(
        targetValue = if (active) activeColor else containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "morphBg",
    )
    val resolvedContent = if (active) scheme.onPrimary else scheme.onSurface

    Button(
        onClick = { haptics.click(); onClick() },
        enabled = enabled && !pending,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .animateContentSize(
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
            .then(
                if (toggled != null) {
                    Modifier.semantics {
                        role = Role.Switch
                        this.toggleableState = ToggleableState(toggled)
                    }
                } else Modifier,
            ),
        shape = RoundedCornerShape(percent = pct.roundToInt()),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = resolvedContent,
            disabledContainerColor = bg,
            // A flat surfaceContainerHigh fill with 38%-alpha content and no
            // border (the old `pending` case) had nothing left to read as
            // "busy" rather than "broken" once Liquid/Ultra Glass stopped
            // giving buttons a second depth cue -- keeping a dimmer rim and a
            // less severe alpha dip keeps a pending button legibly "still
            // there, just working" instead of washed-out.
            disabledContentColor = resolvedContent.copy(alpha = 0.55f),
        ),
        border = when {
            active -> null
            pending -> BorderStroke(1.5.dp, scheme.outline.copy(alpha = 0.35f))
            else -> BorderStroke(1.5.dp, scheme.outline.copy(alpha = 0.85f))
        },
        label = {
            AnimatedContent(targetState = label, transitionSpec = {
                (fadeIn(tween(150)) + slideInVertically(tween(150)) { -it / 3 }) togetherWith
                    (fadeOut(tween(100)) + slideOutVertically(tween(100)) { it / 3 })
            }, label = "btnLabel") { lbl ->
                Text(lbl, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            }
        },
        secondaryLabel = secondaryLabel?.let { s ->
            { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        icon = {
            if (pending) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
    )
}

/** One option in a [MorphSegmented] control; re-exported from :uicommon.
 *  (Watch options carry no icon.) */
typealias WearSegmentOption = com.bloo.uicommon.SegmentOption

/**
 * The watch's full-width segmented selector. Thin wrapper over the shared
 * :uicommon [com.bloo.uicommon.MorphSegmented], supplying the watch's Material 3
 * colours (surfaceContainerHigh lerped toward onSurface for the track, matching
 * MorphButton), label typography and haptics.
 */
@Composable
fun MorphSegmented(
    options: List<WearSegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    com.bloo.uicommon.MorphSegmented(
        options = options,
        selectedKey = selectedKey,
        onSelect = onSelect,
        containerColor = lerp(scheme.surfaceContainerLow, scheme.onSurface, 0.20f),
        indicatorColor = scheme.primary,
        selectedTextColor = scheme.onPrimary,
        unselectedTextColor = scheme.onSurface.copy(alpha = 0.65f),
        textStyle = MaterialTheme.typography.labelMedium,
        onTick = { haptics.tick() },
        modifier = modifier,
        trackHeight = 48.dp,
        // Every other interactive surface (MorphButton, SectionCard, PinKey)
        // has a hairline rim; this was the one flat, borderless control left.
        borderColor = scheme.outline.copy(alpha = 0.18f),
    )
}

// ---- Weather helpers (mirror the phone's WeatherCode mapping) -------------

fun weatherLabel(code: Int): String = com.bloo.bluelink.data.weatherLabel(code)

fun weatherIcon(code: Int, isDay: Boolean): ImageVector =
    com.bloo.uicommon.weatherIcon(code, isDay)

fun weatherTemp(tempC: Double, fahrenheit: Boolean): String =
    com.bloo.bluelink.data.weatherTemp(tempC, fahrenheit)

/**
 * Watch-side wrapper over the shared `:uicommon` AnimatedValue that crossfades
 * a piece of text when it changes (e.g. a percentage or a status string
 * ticking over), instead of the new value popping in instantly.
 *
 * Mechanism: this function itself does no animation -- it just resolves the
 * final TextStyle to hand off. [color] defaults to [Color.Unspecified]
 * (Compose's "no color specified" sentinel), and since a TextStyle can't
 * meaningfully render "unspecified", it's resolved here to the theme's
 * onSurface color before merging into [style]; [fontWeight] similarly only
 * overrides the style's own weight when explicitly passed. The actual
 * crossfade/transition logic, plus honoring the system's reduce-motion
 * setting (read here via [LocalReduceMotion] and passed straight through),
 * lives in the shared `com.bloo.uicommon.AnimatedValue` implementation so the
 * phone and watch never have two independently-tuned animations for the same
 * concept.
 */
@Composable
fun AnimatedValue(
    value: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    val resolvedColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    val mergedStyle = style.copy(
        color = resolvedColor,
        fontWeight = fontWeight ?: style.fontWeight,
    )
    com.bloo.uicommon.AnimatedValue(
        value = value,
        style = mergedStyle,
        maxLines = maxLines,
        reduceMotion = LocalReduceMotion.current,
        modifier = modifier,
    )
}

/**
 * The centred "busy" state every full-screen loading view on the watch shares:
 * a fade-in spinner over a caption (LoginScreen's "Signing in…", Trips' load,
 * WatchApp's connect). Was copy-pasted per screen; now one component so the
 * fade timing (200ms), spinner + 10dp gap + labelMedium/onSurfaceVariant
 * caption all stay identical everywhere.
 */
@Composable
fun BusySpinner(caption: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(200)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(10.dp))
                Text(
                    caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The rotary-scrollable ScalingLazyColumn scaffold every list screen (Login,
 * Settings, Trips, Home) hand-rolled identically: a FocusRequester + a
 * LaunchedEffect(Unit) that requests focus (guarded with runCatching so a
 * not-yet-attached requester can't crash), plus the crown/bezel wiring itself.
 * Uses the native foundation [rotaryScrollable] (stable in wear.compose 1.4+),
 * which routes the crown/bezel into [state] with real fling + snap + haptic
 * detents and claims focus via the supplied [focusRequester] (so no separate
 * .focusable() is needed). Centralised so the focus + rotary wiring lives in one
 * place; callers keep their own wrapping Box/siblings and just supply [content].
 */
@Composable
fun RotaryScalingColumn(
    modifier: Modifier = Modifier,
    state: ScalingLazyListState = rememberScalingLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = roundSafeHorizontalPadding(), vertical = 30.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(6.dp),
    scalingParams: ScalingParams = ScalingLazyColumnDefaults.scalingParams(),
    content: ScalingLazyListScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .rotaryScrollable(
                RotaryScrollableDefaults.behavior(scrollableState = state),
                focusRequester = focusRequester,
            ),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        scalingParams = scalingParams,
        // First/last items can scroll to screen center on a round face
        // (the enabled default; stated explicitly for clarity).
        autoCentering = AutoCenteringParams(),
        content = content,
    )
}
