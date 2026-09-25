@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.DataSource
import coil.request.ImageRequest
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.uicommon.coldStartIntroPlayed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The hero's car-photo rendering: the tonal fallback brush, photo backdrop, and
 * the shared charge/fuel bar (also used by Cover.kt and EnergyPebble.kt). Split
 * out of Hero.kt to separate this reusable visual layer from HeroHeader itself.
 */

/** The tonal primary→tertiary→secondary gradient used as the fallback fill
 *  behind car photos across the garage/settings surfaces. Callers apply their
 *  own `.alpha(...)` where they want it dimmed -- this returns only the brush. */
@Composable
internal fun carTonalBrush(scheme: ColorScheme): Brush {
    // appIsDarkTheme() (Theme.kt), not a raw isSystemInDarkTheme() read -- the same fix
    // already made in pebbleCardEdge/glassTint (GlassChrome.kt) and CarMap
    // (WeatherPebble.kt). isSystemInDarkTheme() only ever sees the PHONE's setting.
    // This brush is the hero's whole backdrop for any car without a photo, and it picked
    // its branch off the wrong source while the [scheme] it draws from is the app's real
    // (possibly force-dark, possibly a custom palette) one: an app forced to Light
    // on a dark phone got the "vivid primary/tertiary" branch under light-theme content,
    // and an app forced to Dark on a light phone got the near-white surfaceContainerLowest
    // branch -- the two cases where the hero's fallback fill and everything drawn on it
    // disagreed about which theme they were in.
    val dark = appIsDarkTheme()
    val colors = if (dark) {
        // Dark mode: vivid primary/tertiary/secondary with light text over them
        listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    } else {
        // Light mode: use very light surface variants for minimal visual weight
        listOf(scheme.surfaceContainerLowest, scheme.surfaceContainerLowest, scheme.surfaceContainer)
    }
    return Brush.linearGradient(colors)
}

/** The Coil model for a stored car photo: a [java.io.File] for a locally-cropped
 *  absolute path, or the raw URL string for a pasted one. */
@Composable
internal fun rememberPhotoModel(url: String): Any =
    remember(url) { if (url.startsWith("/")) java.io.File(url) else url }

// collapseEnter / collapseExit -- the app's one collapse spec -- now live in UiTokens.kt,
// with the reasoning that goes with them. 14 call sites in this file still use them.

/**
 * The car photo plus the contrast scrim that makes text on top of it legible. ONE
 * definition, used by the phone hero's expanded background and by the flip cover's tile.
 *
 * Contrast, not decoration. Every element overlaid on the hero -- title, chevron, the whole
 * charge readout -- sits on an arbitrary car photo, and against a light car they all
 * disappear. A scrim under the text is the cheap, reliable answer and is what the hero does.
 *
 * The gradient covers the FULL height and never reaches transparent. An earlier version
 * scrimmed only the top strip and faded to clear by 45%, on the assumption that only the
 * header row was overlaid -- it is not, the readout is over the image too. Heaviest at the
 * top and bottom because those are the two bands that carry content (title and chevron up
 * top, the charge readout along the bottom); the middle can afford to be clear because
 * nothing sits there, which is what lets the photo still read as a photo.
 *
 * remember-ed: Brush.verticalGradient allocates a stop list, and this sits inside a card
 * that recomposes on every status change.
 *
 * [aspectRatio] null means size by [height] -- the flip cover, whose tile height is given.
 */
@Composable
internal fun HeroPhotoBackdrop(
    v: Vehicle,
    imageUrl: String?,
    height: Dp,
    aspectRatio: Float? = null,
    corner: Dp = PebbleCornerExpanded,
    /** See [HeroVisual.fill] -- the flip cover fills its tile. */
    fill: Boolean = false,
) {
    Box(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        HeroVisual(v, imageUrl, height, corner, aspectRatio = aspectRatio, fill = fill)
        val scrim = remember {
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.55f),
                0.30f to Color.Black.copy(alpha = 0.22f),
                0.62f to Color.Black.copy(alpha = 0.28f),
                1f to Color.Black.copy(alpha = 0.62f),
            )
        }
        Spacer(Modifier.matchParentSize().background(scrim))
    }
}

/**
 * Spaces out hero photo loads that start within a short window of each other, so a
 * multi-car account's cars don't all decode and upload their (still individually
 * capped, see [HeroVisual]'s own `.size(1080, 1080)`) hero bitmaps on the very same
 * frame.
 *
 * A real device report showed a single ~2.3s frame with a ~255MB heap jump the
 * instant two cars' hero photos both composed for the first time on a wide/dual-
 * column layout (this device's Z Fold showing two cars side by side) -- worse than
 * the single-photo version of this same report, which the per-request `.size()` cap
 * was already added for. That cap bounds each decode's OWN cost, but does nothing
 * about two independently-bounded decodes landing in the same frame: Coil already
 * decodes off the main thread, but two large bitmaps finishing at once still forces
 * two GPU texture uploads and two full page recompositions into the same Choreographer
 * frame, which is what actually froze the UI.
 *
 * Coalescing, not a flat per-instance index: a car expanded much later in the same
 * session (long after cold start) must load its photo immediately, not wait out a
 * delay computed from how many hero photos have EVER loaded this process. Only a
 * request that starts within [COALESCE_WINDOW_MS] of the previous one is treated as
 * "the same burst" and pushed back by [STAGGER_STEP_MS]; anything after a quiet gap
 * starts immediately and resets the burst.
 */
private object HeroLoadStagger {
    private const val COALESCE_WINDOW_MS = 80L
    private const val STAGGER_STEP_MS = 220L
    private val lock = Any()
    private var lastClaimAtMs = 0L
    private var burstSlot = 0

    /** Call once per actual load attempt (i.e. from inside a `remember(model) {}`), never
     *  from a plain composable body -- see the class doc for why this must not be charged
     *  against every recomposition. */
    fun claimDelayMs(): Long = synchronized(lock) {
        val now = android.os.SystemClock.uptimeMillis()
        burstSlot = if (now - lastClaimAtMs < COALESCE_WINDOW_MS) burstSlot + 1 else 0
        lastClaimAtMs = now
        burstSlot * STAGGER_STEP_MS
    }
}

/** Default = a clean brand gradient. If the user set a photo, show that instead. */
@Composable
internal fun HeroVisual(
    v: Vehicle,
    imageUrl: String?,
    height: Dp,
    corner: Dp = 18.dp,
    /** When set, size by aspect ratio instead of [height] -- 16:9 for the phone hero, so
     *  the image keeps its shape at any screen width instead of being letterboxed or
     *  cropped by a fixed dp height. */
    aspectRatio: Float? = null,
    /** Fill the parent in BOTH axes, ignoring [height] and [aspectRatio] -- the flip cover,
     *  whose tile height is the frame, so cropping to fill it is what a full-screen glance
     *  wants. Requires a bounded parent, which the cover tile is (its Card fills height). */
    fill: Boolean = false,
) {
    com.bloo.bluelink.data.StartupTrace.once("hero-visual-${v.vin}", "HeroVisual composing for ${v.name}")
    val sizeModifier = when {
        fill -> Modifier.fillMaxSize()
        aspectRatio != null -> Modifier.fillMaxWidth().aspectRatio(aspectRatio)
        else -> Modifier.fillMaxWidth().height(height)
    }
    if (imageUrl.isNullOrBlank()) {
        val scheme = MaterialTheme.colorScheme
        Box(
            sizeModifier
                .clip(RoundedCornerShape(corner))
                .background(carTonalBrush(scheme)),
        )
    } else {
        // A locally-cropped photo is an absolute path; a pasted one is a URL.
        val model: Any = rememberPhotoModel(imageUrl)
        // A transparent PNG renders edge-to-edge with no opaque box, so it blends
        // seamlessly into the pebble (fit, not crop, so the whole subject shows).
        val transparent = imageUrl.endsWith(".png", ignoreCase = true)
        // The car photo ARRIVES instead of popping. This is the one hero element that
        // had no animation of any kind: the pebble's collapse animates, the readout's
        // numbers roll, the bar's fill springs -- and then the photo itself appeared
        // between two frames. The map tiles below already did a plain Coil crossfade;
        // the hero, the largest image in the app and the one the eye lands on first,
        // got the same treatment first -- but a bare alpha fade read as flat next to
        // everything else here springing or sliding into place, so this now does its
        // own fade+slide+scale "arrival" (same language as ReorderColumn's cold-start
        // row intro: alpha 0->1 alongside a short upward translation) instead of
        // leaning on Coil's built-in crossfade.
        //
        // `loadedFrom` (not a plain Boolean) carries WHICH kind of success this was,
        // because a memory-cache hit must NOT replay the arrival -- scrolling back to
        // an already-decoded photo (flipping cars and back on the pager, most commonly)
        // should show it instantly, not fade it in again every time. This is exactly
        // the distinction Coil's own `crossfade(true)` already made automatically; doing
        // the animation by hand means re-deriving that distinction from the callback's
        // own DataSource instead of getting it for free.
        var loadedFrom by remember(model) { mutableStateOf<DataSource?>(null) }
        val entrance = remember(model) { Animatable(0f) }
        LaunchedEffect(loadedFrom) {
            when (loadedFrom) {
                null -> {} // still loading -- nothing to animate to yet.
                DataSource.MEMORY_CACHE -> entrance.snapTo(1f)
                else -> entrance.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            }
        }
        // See HeroLoadStagger's own doc: claimed once per model (a fresh photo, not every
        // recomposition), and only actually delays anything when another hero load just
        // started within the same short burst window -- an isolated load (expanding one
        // car well after cold start, say) claims 0ms and starts immediately.
        var staggerReady by remember(model) { mutableStateOf(false) }
        // Cold-start diagnostic: when AsyncImage actually starts (right after the stagger
        // delay, if any), so the Success callback below can log how long THIS photo's own
        // decode took -- distinct from `hero photo decoded`'s old single shared key, which
        // could only ever report the FIRST of a multi-car account's photos (StartupTrace.once
        // dedupes by key, and every car used the same one), leaving every later photo's own
        // timing invisible in every report so far.
        var loadStartedAtMs by remember(model) { mutableStateOf(0L) }
        LaunchedEffect(model) {
            val delayMs = HeroLoadStagger.claimDelayMs()
            if (delayMs > 0) delay(delayMs)
            loadStartedAtMs = System.currentTimeMillis()
            staggerReady = true
        }
        // Memoized like the map tiles: creating a fresh ImageRequest every recomposition
        // would trigger unnecessary reloads and cause visible flicker/jank.
        val context = LocalContext.current
        val imageRequest = remember(model) {
            ImageRequest.Builder(context)
                .data(model)
                // Explicit upper bound, not left to Coil's automatic view-constraint
                // sizing: a real device report showed a ~280MB heap spike and a
                // ~1.4s main-thread stall the instant two cars' hero photos first
                // composed for a real account with photos actually set. The crop
                // screen's own export already caps a NEWLY saved photo at 1080px
                // wide, but that cap does nothing for a photo saved by an OLDER
                // build (this app ships a fresh build on every commit; nothing
                // re-processes a file already on disk), a Drive-synced photo from
                // another device, or automatic view-based sizing simply not
                // engaging the way it's expected to for this Modifier chain. This
                // bounds the decode itself to roughly the crop export's own target
                // regardless of what the source file on disk actually is, the same
                // "never trust the input, always cap the output" rule
                // downscaledJpegBytes (SettingsStore.kt) already follows for the
                // Drive-sync path.
                .size(1080, 1080)
                .build()
        }
        if (!staggerReady) {
            // Same tonal fallback the no-photo branch above shows -- a car whose hero
            // load is being held back by the stagger looks exactly like one that simply
            // hasn't loaded yet, for the brief window (a couple hundred ms, at most, and
            // only when another hero just started loading) until its turn comes.
            val scheme = MaterialTheme.colorScheme
            Box(
                sizeModifier
                    .clip(RoundedCornerShape(corner))
                    .background(carTonalBrush(scheme)),
            )
        } else {
            AsyncImage(
                model = imageRequest,
                contentDescription = v.model,
                contentScale = if (transparent) ContentScale.Fit else ContentScale.Crop,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        loadedFrom = state.result.dataSource
                        // Cold-start: when the car photo actually finished DECODING and is
                        // being drawn, not when the request was dispatched. A hero photo
                        // arriving late is one of the few startup costs that visibly pops in.
                        // Keyed per-VIN (not one shared key) and carrying its own elapsed time
                        // and source file size -- a real device log showed a ~270MB heap jump
                        // and ~188MB of NATIVE heap growth (a bitmap-decode signature, not a
                        // JSON-parse one) in this same window despite the `.size(1080, 1080)`
                        // decode cap, which two properly-capped ARGB_8888 bitmaps could never
                        // need (under 10MB combined). Either that cap isn't taking effect for
                        // these particular files, or the source files themselves are large
                        // enough that decode needs far more transient memory than the final
                        // bitmap does -- this is what will actually show which.
                        val elapsedMs = if (loadStartedAtMs > 0) System.currentTimeMillis() - loadStartedAtMs else -1
                        val sourceSize = (model as? java.io.File)?.let {
                            runCatching { it.length() }.getOrNull()
                        }
                        com.bloo.bluelink.data.StartupTrace.once(
                            "hero-photo-decoded-${v.vin}",
                            "hero photo decoded for ${v.name} (${state.result.dataSource}) in " +
                                "${elapsedMs}ms, source=${sourceSize?.let { "${it / 1024}KB local file" } ?: "remote/unknown"}",
                        )
                    }
                },
                modifier = sizeModifier
                    .then(if (transparent) Modifier else Modifier.clip(RoundedCornerShape(corner)))
                    .graphicsLayer {
                        alpha = entrance.value
                        // A short upward drift, not a full ReorderColumn-sized 28dp one -- this
                        // is a photo arriving into place it already occupies, not a row sliding
                        // in from off-list, so the motion is a hint of settling rather than a
                        // real journey. Same reasoning for the scale: 0.97->1 reads as the photo
                        // gently coming forward, not a distracting zoom.
                        translationY = (1f - entrance.value) * 10.dp.toPx()
                        val s = 0.97f + 0.03f * entrance.value
                        scaleX = s
                        scaleY = s
                    },
            )
        }
    }
}

/**
 * The battery/fuel percentage readout: headline percent + range, a status
 * line beneath (charging details > driving/parked > plain "Battery"/"Fuel"
 * label, in that priority order), and a gradient progress bar. The bar's
 * fill animates via a spring (`animatedFrac`) rather than snapping to the
 * new percentage, and -- when plugged in -- a small dot marks the
 * charge-limit target percentage on the track so the user can see at a
 * glance how much further it'll charge.
 */
@Composable
internal fun ChargeFuelBar(
    status: VehicleStatus?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    drivingLabel: String? = null,
    metric: Boolean = false,
) {
    // Now literally [HeroMorphReadout] held at its expanded end. There is ONE readout
    // implementation in the app, and every surface that shows this -- the hero on the phone,
    // the flip cover's tile, the EV Charge pebble -- renders that same one.
    //
    // This function had grown a near-duplicate of it: a ChargeStatsBlock with the same Row,
    // the same weighted spacer, the same two RollingNumbers at the same two type steps, then
    // the same fuel row and the same bar. Two implementations of one readout is how the
    // collapsed bar ended up silently dropping the charge-limit marker the expanded one drew,
    // and how the morph pass dropped the fuel icon this file had always had. `t = 1f` is a
    // constant, so nothing here animates -- the morph is inert at its endpoint.
    HeroMorphReadout(chargeReadoutOf(status, hasBattery, hasFuel, drivingLabel, metric), t = 1f)
}

/**
 * Everything the charge/fuel readout says, derived ONCE.
 *
 * The hero renders this readout at two densities — one line in the collapsed header,
 * the full block at the bottom of the expanded card — and until now those were two
 * independent derivations of the same numbers: two answers to "battery percentage or
 * fuel percentage", two copies of the charging > driving > plain priority order for
 * the state line, two charging-colour rules. That is this codebase's recurring class
 * of bug (a rule that exists in one place and is re-typed in another), and here it
 * had already produced a visible one — both copies on screen simultaneously,
 * disagreeing about whether to mention charging.
 *
 * Now both densities render from one of these, and only the LAYOUT differs.
 */

// Colours, sizes and motion specs shared across screens live in UiTokens.kt.
// The shared floating/card edge (glassRim) now lives solely in GlassChrome.kt.
