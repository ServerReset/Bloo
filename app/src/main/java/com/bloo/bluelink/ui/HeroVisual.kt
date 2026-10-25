package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.imageLoader
import com.bloo.uicommon.MotionMedium
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.DataSource
import coil.request.ImageRequest
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import kotlinx.coroutines.delay
import com.bloo.uicommon.MotionLong

/**
 * The hero's car-photo rendering: tonal fallback brush, photo backdrop, and the shared charge/fuel
 * bar (also used by the EV Charge pebble).
 */

/**
 * The hero's no-photo backdrop, built from the app's own palette: a diagonal sweep from primary through
 * tertiary to secondary (deep in the light theme, pale in the dark one, opposite the card text), with a
 * soft light of the primary pooling in the upper corner and one of the tertiary in the lower, so it reads as
 * a lit surface rather than a flat ramp. Drawn, not an image, so it follows the palette and the theme.
 */
@Composable
internal fun Modifier.carTonalBackground(scheme: ColorScheme): Modifier {
    val dark = appIsDarkTheme()
    val toward = if (dark) Color.White else Color.Black
    val tone = { c: Color -> lerp(c, toward, if (dark) 0.62f else 0.5f) }
    val glow = { c: Color -> lerp(c, if (dark) Color.White else Color.White, if (dark) 0.8f else 0.35f) }
    val base = tone(scheme.primary); val mid = tone(scheme.tertiary); val end = tone(scheme.secondary)
    return this.drawBehind {
        drawRect(
            Brush.linearGradient(
                0f to base, 0.55f to mid, 1f to end,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
            ),
        )
        val r = size.maxDimension * 0.85f
        drawRect(
            Brush.radialGradient(
                listOf(glow(scheme.primary).copy(alpha = if (dark) 0.34f else 0.30f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.05f), radius = r,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(glow(scheme.tertiary).copy(alpha = if (dark) 0.30f else 0.26f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.98f), radius = r * 0.9f,
            ),
        )
    }
}

/**
 * Coil model for a stored car photo: a [java.io.File] for a cropped local path, else the raw URL.
 */
@Composable
internal fun rememberPhotoModel(url: String): Any = remember(url) { photoModelOf(url) }

/** [rememberPhotoModel] outside composition. */
internal fun photoModelOf(url: String): Any = if (url.startsWith("/")) java.io.File(url) else url

/**
 * Which hero photos have already been decoded this session (by the page that showed them, or by [PrefetchHeroPhotos]). A page showing one
 * of them skips the load-stagger and the fade-in: it is in the memory cache, so it is drawn on the first frame instead of vanishing
 * behind a placeholder and fading back.
 */
internal object HeroPhotoReady {
    private val keys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun has(key: String) = key in keys
    fun add(key: String) { keys.add(key) }
}

/** The one request for a hero photo, so the page and the prefetch share a memory-cache entry. */
internal fun heroImageRequest(context: android.content.Context, model: Any): ImageRequest =
    ImageRequest.Builder(context)
        .data(model)
        // Explicit decode cap (not Coil's automatic sizing): photos from older builds or Drive sync may be larger than the crop
        // export's 1080px, and two large decodes stall the main thread. Never trust the input, always cap the output.
        .size(1080, 1080)
        .build()

/**
 * Warms every car's hero photo before its page is swiped to: decodes it into Coil's memory cache (the sizes the hero and its frost
 * layer ask for) and measures the tone its text is chosen from. A page then draws its photo and its text colours on the first
 * frame. The decodes run one after another off the main thread, so a multi-car account never decodes them in one frame.
 */
@Composable
internal fun PrefetchHeroPhotos(imageUrls: androidx.compose.runtime.State<Map<String, String>>) {
    val context = LocalContext.current
    // Read here, in this small scope, so a change to the photo list recomposes this and not the screen that hosts it.
    val urls = remember(imageUrls.value) { imageUrls.value.values.filter { it.isNotBlank() }.distinct().sorted() }
    LaunchedEffect(urls) {
        val loader = context.imageLoader
        for (url in urls) {
            val model = photoModelOf(url)
            val key = model.toString()
            if (!HeroPhotoReady.has(key)) {
                val result = loader.execute(heroImageRequest(context, model))
                if (result is coil.request.SuccessResult) HeroPhotoReady.add(key)
            }
            if (!isCutoutPhoto(url)) {
                loader.enqueue(frostImageRequest(context, model))
                measureHeroToneCached(context, model)
            }
        }
    }
}

/** The blurred copy behind the hero's text asks for this one. */
internal fun frostImageRequest(context: android.content.Context, model: Any): ImageRequest =
    ImageRequest.Builder(context).data(model).size(540, 540).crossfade(true).build()

// collapseEnter / collapseExit (the app's one collapse spec) live in UiTokens.kt.

/** The faintest tint kept on a measured photo, so its edges still sit together when no scrim is needed. */
private const val MinPhotoScrim = 0.08f

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
        // Frost behind the text, not a wash over the whole photo: a blurred copy of the same photo, shown only in the top and
        // bottom bands where the title, the numbers and the charge bar sit, fading out so the car in the middle stays sharp.
        // It is a GPU blur on the photo's own layer, so it follows the content with no extra recomposition. (On a phone
        // that cannot blur, the tint below is all there is.) A transparent cut-out PNG has no backdrop to frost.
        val frostable = !imageUrl.isNullOrBlank() && !isCutoutPhoto(imageUrl)
        if (frostable) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val photoModel = rememberPhotoModel(imageUrl)
            val frostRequest = remember(photoModel) { frostImageRequest(context, photoModel) }
            val frostMask = remember {
                Brush.verticalGradient(
                    0f to Color.Black, 0.30f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black,
                )
            }
            AsyncImage(
                model = frostRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
                    .clip(RoundedCornerShape(corner))
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(frostMask, blendMode = BlendMode.DstIn)
                    }
                    .blur(16.dp, BlurredEdgeTreatment.Unbounded),
            )
        }
        // A tint at the top and bottom edges in the OPPOSITE tone of the text there, as strong as that band's worst-case contrast needs
        // (see overlayFor): a light photo gets almost none under dark text, a busy or mid-tone one gets enough to hold the text
        // up. The two bands are judged apart, so a photo that is light at the top and dark at the bottom gets dark text above
        // and light text below. Until the photo is measured (and for no photo or a cut-out) the theme's own tone is the fallback.
        val tone = LocalHeroTone.current
        val dark = appIsDarkTheme()
        val gentle = imageUrl.isNullOrBlank()
        val themeTone = if (dark) Color.White else Color.Black
        val (themeTop, themeBottom) = when {
            gentle -> 0.10f to 0.16f
            dark -> 0.34f to 0.40f
            else -> 0.30f to 0.40f
        }
        val topTone by animateColorAsState(tone.top?.scrimTone ?: themeTone, tween(MotionMedium), label = "scrimTopTone")
        val bottomTone by animateColorAsState(tone.bottom?.scrimTone ?: themeTone, tween(MotionMedium), label = "scrimBottomTone")
        val topAlpha by animateFloatAsState(tone.top?.scrimAlpha?.coerceAtLeast(MinPhotoScrim) ?: themeTop, tween(MotionMedium), label = "scrimTopAlpha")
        val bottomAlpha by animateFloatAsState(tone.bottom?.scrimAlpha?.coerceAtLeast(MinPhotoScrim) ?: themeBottom, tween(MotionMedium), label = "scrimBottomAlpha")
        val midAlpha = if (gentle) 0.02f else 0f
        val scrim = Brush.verticalGradient(
            0f to topTone.copy(alpha = topAlpha),
            0.30f to topTone.copy(alpha = midAlpha),
            0.62f to bottomTone.copy(alpha = midAlpha),
            1f to bottomTone.copy(alpha = bottomAlpha),
        )
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
     * Call once per actual load attempt (i.e. from inside a `remember(model) {}`), never from a
     * plain composable body -- see the class doc for why this must not be charged against every
     * recomposition.
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
    val density = LocalDensity.current
    val sizeModifier = when {
        // The photo fills the column at 16:9, but never grows past a height cap: on a wide column a
        // plain aspect ratio made it a huge slab, and a fixed height alone cut the expanded hero's
        // own text on a phone. Capped, it is a proper photo band on every width.
        aspectRatio != null -> Modifier.fillMaxWidth().cappedAspect(aspectRatio, with(density) { MaxHeroPhotoHeight.toPx() })
        else -> Modifier.fillMaxWidth().height(height)
    }
    if (imageUrl.isNullOrBlank()) {
        val scheme = MaterialTheme.colorScheme
        Box(
            sizeModifier
                .clip(RoundedCornerShape(corner))
                .carTonalBackground(scheme),
        )
    } else {
        // A locally-cropped photo is an absolute path; a pasted one is a URL.
        val model: Any = rememberPhotoModel(imageUrl)
        // A transparent PNG renders edge-to-edge with no opaque box, so it blends seamlessly into
        // the pebble (fit, not crop, so the whole subject shows).
        val transparent = isCutoutPhoto(imageUrl)
        // The photo arrives with a fade+slide+scale rather than popping (same language as
        // ReorderColumn's intro).
        val photoKey = remember(model) { model.toString() }
        // Already decoded this session (see HeroPhotoReady): shown at once, no stagger, no fade.
        val warm = remember(model) { HeroPhotoReady.has(photoKey) }
        var loadedFrom by remember(model) { mutableStateOf<DataSource?>(null) }
        val entrance = remember(model) { Animatable(if (warm) 1f else 0f) }
        LaunchedEffect(loadedFrom) {
            when (loadedFrom) {
                null -> {} // still loading -- nothing to animate to yet.
                DataSource.MEMORY_CACHE -> entrance.snapTo(1f)
                else -> entrance.animateTo(1f, tween(MotionLong, easing = FastOutSlowInEasing))
            }
        }
        // See HeroLoadStagger's own doc: claimed once per model (a fresh photo, not every
        // recomposition), and only actually delays anything when another hero load just started
        // within the same short burst window -- an isolated load (expanding one car well after cold
        // start, say) claims 0ms and starts immediately.
        var staggerReady by remember(model) { mutableStateOf(warm) }
        // Cold-start diagnostic: marks when AsyncImage starts so Success can log this photo's own
        // decode time.
        var loadStartedAtMs by remember(model) { mutableLongStateOf(0L) }
        LaunchedEffect(model) {
            if (warm) { loadStartedAtMs = System.currentTimeMillis(); return@LaunchedEffect }
            val delayMs = HeroLoadStagger.claimDelayMs()
            if (delayMs > 0) delay(delayMs)
            loadStartedAtMs = System.currentTimeMillis()
            staggerReady = true
        }
        // Memoized like the map tiles: a fresh ImageRequest per recomposition would reload and
        // flicker.
        val context = LocalContext.current
        val imageRequest = remember(model) { heroImageRequest(context, model) }
        if (!staggerReady) {
            // Same tonal fallback the no-photo branch above shows -- a car whose hero load is being
            // held back by the stagger looks exactly like one that simply hasn't loaded yet, for
            // the brief window (a couple hundred ms, at most, and only when another hero just
            // started loading) until its turn comes.
            val scheme = MaterialTheme.colorScheme
            Box(
                sizeModifier
                    .clip(RoundedCornerShape(corner))
                    .carTonalBackground(scheme),
            )
        } else {
            AsyncImage(
                model = imageRequest,
                contentDescription = v.model,
                contentScale = if (transparent) ContentScale.Fit else ContentScale.Crop,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        loadedFrom = state.result.dataSource
                        HeroPhotoReady.add(photoKey)
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
    // shared by the hero and the EV Charge pebble.
    HeroMorphReadout(chargeReadoutOf(status, hasBattery, hasFuel, drivingLabel, metric), t = 1f)
}

/**
 * Everything the charge/fuel readout says, derived once so the collapsed one-line and expanded
 * block densities agree on the percentage, the charging > driving > plain priority order and the
 * charging colour. Only the layout differs.
 */

// Colours, sizes and motion specs shared across screens live in UiTokens.kt. The shared
// floating/card edge (glassRim) lives in GlassChrome.kt.

/** The tallest the hero photo may get, however wide its column is (a wide column must not make it a slab). */
private val MaxHeroPhotoHeight = 300.dp

/**
 * Lays the content out at [ratio] against its own width, but no taller than [maxHeightPx]. A plain
 * [androidx.compose.foundation.layout.aspectRatio] on a wide column produced a giant card; this keeps
 * the photo a band on any width. A plain [layout] modifier, not a SubcomposeLayout, so it is cheap.
 */
private fun Modifier.cappedAspect(ratio: Float, maxHeightPx: Float): Modifier = layout { measurable, constraints ->
    val w = constraints.maxWidth.coerceAtLeast(0)
    val h = (w / ratio).toInt().coerceIn(0, maxHeightPx.toInt().coerceAtLeast(0))
    val p = measurable.measure(Constraints.fixed(w, h))
    layout(w, h) { p.place(0, 0) }
}
