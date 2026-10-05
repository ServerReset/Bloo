package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Call
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.DataSource
import coil.request.ImageRequest
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import kotlinx.coroutines.delay

/**
 * The hero's car-photo rendering: tonal fallback brush, photo backdrop, and the shared charge/fuel
 * bar (also used by Cover.kt and EnergyPebble.kt).
 */

/**
 * Tonal primary-tertiary-secondary gradient used as the fallback fill behind car photos. Callers
 * apply their own `.alpha(...)`; this returns only the brush.
 */
@Composable
internal fun carTonalBrush(scheme: ColorScheme): Brush {
    // The hero is the opposite tone of the theme: deep, saturated accents in the light theme (under light text),
    // pale tints of the same accents in the dark theme (under dark text). Primary leads, tertiary and secondary
    // sweep across it, so the card always reads as the palette's own colours.
    val dark = appIsDarkTheme()
    val toward = if (dark) Color.White else Color.Black
    val amount = if (dark) 0.62f else 0.5f
    val tone = { c: Color -> lerp(c, toward, amount) }
    return Brush.linearGradient(
        listOf(tone(scheme.primary), tone(scheme.tertiary), tone(scheme.secondary)),
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset.Infinite,
    )
}

/**
 * Coil model for a stored car photo: a [java.io.File] for a cropped local path, else the raw URL.
 */
@Composable
internal fun rememberPhotoModel(url: String): Any =
    remember(url) { if (url.startsWith("/")) java.io.File(url) else url }

// collapseEnter / collapseExit (the app's one collapse spec) live in UiTokens.kt.

/**
 * The car photo plus a contrast scrim so overlaid text (title, chevron, charge readout) stays
 * legible on any photo. The gradient covers the FULL height and never reaches transparent; heaviest
 * at top and bottom, where content sits.
 */
@Composable
internal fun HeroPhotoBackdrop(
    v: Vehicle,
    imageUrl: String?,
    height: Dp,
    aspectRatio: Float? = null,
    corner: Dp = PebbleCornerExpanded,
) {
    Box(Modifier.fillMaxWidth()) {
        HeroVisual(v, imageUrl, height, corner, aspectRatio = aspectRatio)
        // The scrim is the card's own opposite tone: darkening in the light theme (light text), lightening in the
        // dark theme (dark text). A colour wash with no photo needs only a gentle one.
        val dark = appIsDarkTheme()
        val gentle = imageUrl.isNullOrBlank()
        val scrim = remember(gentle, dark) {
            val tone = if (dark) Color.White else Color.Black
            val (top, mid, low, bottom) = when {
                gentle -> listOf(0.10f, 0.02f, 0.04f, 0.16f)
                dark -> listOf(0.55f, 0.22f, 0.28f, 0.62f)
                else -> listOf(0.5f, 0.16f, 0.2f, 0.62f)
            }
            Brush.verticalGradient(
                0f to tone.copy(alpha = top),
                0.30f to tone.copy(alpha = mid),
                0.62f to tone.copy(alpha = low),
                1f to tone.copy(alpha = bottom),
            )
        }
        Spacer(Modifier.matchParentSize().background(scrim))
    }
}

/**
 * Spaces out hero photo loads that start close together so a multi-car account doesn't decode and
 * upload several bitmaps in the same frame (the per-request `.size()` cap bounds each decode, not
 * simultaneous ones).
 */
private object HeroLoadStagger {
    private const val COALESCE_WINDOW_MS = 80L
    private const val STAGGER_STEP_MS = 220L
    private val lock = Any()
    private var lastClaimAtMs = 0L
    private var burstSlot = 0

    /**
     * Call once per load attempt (inside `remember(model) {}`), never from a plain composable body.
     */
    fun claimDelayMs(): Long = synchronized(lock) {
        val now = android.os.SystemClock.uptimeMillis()
        burstSlot = if (now - lastClaimAtMs < COALESCE_WINDOW_MS) burstSlot + 1 else 0
        lastClaimAtMs = now
        burstSlot * STAGGER_STEP_MS
    }
}

/** Clean brand gradient by default; the user's photo if set. */
@Composable
internal fun HeroVisual(
    v: Vehicle,
    imageUrl: String?,
    height: Dp,
    corner: Dp = 18.dp,
    /** When set, size by aspect ratio instead of [height] (16:9 on the phone hero). */
    aspectRatio: Float? = null,
) {
    com.bloo.bluelink.data.StartupTrace.once("hero-visual-${v.vin}", "HeroVisual composing for ${v.name}")
    val sizeModifier = when {
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
        // A transparent PNG renders edge-to-edge with no opaque box, so it blends seamlessly into
        // the pebble (fit, not crop, so the whole subject shows).
        val transparent = imageUrl.endsWith(".png", ignoreCase = true)
        // The photo arrives with a fade+slide+scale rather than popping (same language as
        // ReorderColumn's intro).
        var loadedFrom by remember(model) { mutableStateOf<DataSource?>(null) }
        val entrance = remember(model) { Animatable(0f) }
        LaunchedEffect(loadedFrom) {
            when (loadedFrom) {
                null -> {} // still loading -- nothing to animate to yet.
                DataSource.MEMORY_CACHE -> entrance.snapTo(1f)
                else -> entrance.animateTo(1f, tween(MotionLong, easing = FastOutSlowInEasing))
            }
        }
        // See HeroLoadStagger: claimed once per model; delays only when another hero load just
        // started.
        var staggerReady by remember(model) { mutableStateOf(false) }
        // Cold-start diagnostic: marks when AsyncImage starts so Success can log this photo's own
        // decode time.
        var loadStartedAtMs by remember(model) { mutableLongStateOf(0L) }
        LaunchedEffect(model) {
            val delayMs = HeroLoadStagger.claimDelayMs()
            if (delayMs > 0) delay(delayMs)
            loadStartedAtMs = System.currentTimeMillis()
            staggerReady = true
        }
        // Memoized like the map tiles: a fresh ImageRequest per recomposition would reload and
        // flicker.
        val context = LocalContext.current
        val imageRequest = remember(model) {
            ImageRequest.Builder(context)
                .data(model)
                // Explicit decode cap (not Coil's automatic sizing): photos from older builds or
                // Drive sync may be larger than the crop export's 1080px, and two large decodes
                // stall the main thread. Never trust the input, always cap the output.
                .size(1080, 1080)
                .build()
        }
        if (!staggerReady) {
            // Same tonal fallback as the no-photo branch, shown while the stagger holds the load
            // back.
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
                        // Cold-start trace: when the photo finished decoding, keyed per-VIN with
                        // elapsed time and source file size, to diagnose bitmap-decode memory.
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
                        // A short upward drift and 0.97-1 scale: a hint of settling into a place
                        // the photo already occupies.
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
 * The battery/fuel percentage readout: headline percent + range, a status line (charging details >
 * driving/parked > plain label), and a gradient progress bar with a charge-limit marker when
 * plugged in.
 */
@Composable
internal fun ChargeFuelBar(
    status: VehicleStatus?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    drivingLabel: String? = null,
    metric: Boolean = false,
) {
    // [HeroMorphReadout] held at its expanded end (`t = 1f`, inert): the one readout implementation
    // shared by the hero, the flip cover tile and the EV Charge pebble.
    HeroMorphReadout(chargeReadoutOf(status, hasBattery, hasFuel, drivingLabel, metric), t = 1f)
}

/**
 * Everything the charge/fuel readout says, derived once so the collapsed one-line and expanded
 * block densities agree on the percentage, the charging > driving > plain priority order and the
 * charging colour. Only the layout differs.
 */

// Colours, sizes and motion specs shared across screens live in UiTokens.kt. The shared
// floating/card edge (glassRim) lives in GlassChrome.kt.
