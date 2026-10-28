package com.bloo.bluelink.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.bloo.uicommon.MotionMedium
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The hero's text colour follows the photo. The photo is sampled once where the text sits -- the title band at the top and the
 * readout band at the bottom -- and each band takes whichever of dark or light text contrasts more with what is behind it. The
 * photo is never washed or scrimmed to make the text work.
 */

/** Relative luminance (WCAG) of one sRGB colour, 0 (black) to 1 (white). */
internal fun relativeLuminance(r: Int, g: Int, b: Int): Float {
    fun lin(c: Int): Float {
        val s = c / 255f
        return if (s <= 0.03928f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b)
}

/** WCAG contrast ratio between two relative luminances, in either order. */
internal fun contrastRatio(a: Float, b: Float): Float {
    val hi = maxOf(a, b)
    val lo = minOf(a, b)
    return (hi + 0.05f) / (lo + 0.05f)
}

/**
 * The brightness of a colour, for judging text over a cut-out photo that shows the page through it.
 */
internal fun backdropLumaOf(c: Color): Float =
    relativeLuminance((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())

/**
 * The page brightness a photo's transparent pixels are judged against: the page colour for a cut-out (it shows the page through),
 * and none for a photo that fills its frame.
 */
internal fun toneBackdropFor(url: String, page: Color): Float? = if (isCutoutPhoto(url)) backdropLumaOf(page) else null

/** How bright a horizontal band of the photo is: its mean and its 10th and 90th percentile luminance (0..1). */
@Immutable
internal data class BandLuma(val mean: Float, val low: Float, val high: Float)

/**
 * The text a band of the photo wears: whichever of dark or light text contrasts more with the band's average brightness. There is no
 * scrim: the photo is shown as it is, and the text is the colour that reads best on it.
 */
@Immutable
internal data class BandOverlay(val darkText: Boolean, val behind: BandLuma) {
    val textColor: Color get() = if (darkText) HeroDarkText else HeroLightText
}

/** The hero's two text colours, matching the dark and light tones the card has always used. */
internal val HeroDarkText = Color(0xFF1A1B20)
internal val HeroLightText = Color(0xFFF7F7FA)
private val DarkTextLuma = relativeLuminance(0x1A, 0x1B, 0x20)
private val LightTextLuma = relativeLuminance(0xF7, 0xF7, 0xFA)

/**
 * Dark or light text for [band]: whichever has the better contrast against the worst part of the pixels behind it (their 10th and
 * 90th percentile brightness), so text on a photo with both light and dark areas takes the side that stays readable on most of it.
 */
internal fun overlayFor(band: BandLuma): BandOverlay =
    BandOverlay(darkText = worstContrast(DarkTextLuma, band) >= worstContrast(LightTextLuma, band), behind = band)

/**
 * The text colours for a page of colour [page], for text that sits on the page rather than on the photo. The text takes whichever of dark
 * or light has the better contrast against the page's brightness, so it reads on a dark page as well as a light one.
 */
internal fun pageTone(page: Color): HeroPhotoTone {
    val luma = backdropLumaOf(page)
    val text = overlayFor(BandLuma(mean = luma, low = luma, high = luma))
    return HeroPhotoTone(top = text, bottom = text)
}

/** The decision for the two bands the hero's text sits in; a null band means "not measured", and the theme's own tone applies. */
@Immutable
internal data class HeroPhotoTone(val top: BandOverlay?, val bottom: BandOverlay?) {
    companion object { val None = HeroPhotoTone(null, null) }
}

/**
 * Luminance of the rows [fromRow, toRow) of a [width]-wide pixel grid, ignoring transparent pixels. Null when the band is mostly
 * transparent (a cut-out has no photo behind the text to measure).
 */
internal fun bandLuma(pixels: IntArray, width: Int, fromRow: Int, toRow: Int, backdrop: Float? = null): BandLuma? {
    val values = FloatArray((toRow - fromRow) * width)
    var n = 0
    for (y in fromRow until toRow) for (x in 0 until width) {
        val p = pixels[y * width + x]
        val a = (p ushr 24) / 255f
        // Transparent pixels are skipped on a photo that fills its frame; on a cut-out they are the page, so they count as it.
        if (backdrop == null && a < 0.5f) continue
        val lum = relativeLuminance((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
        values[n++] = if (backdrop == null) lum else a * lum + (1f - a) * backdrop
    }
    if (n < values.size / 2 || n == 0) return null
    values.sort(0, n)
    return BandLuma(mean = values.take(n).average().toFloat(), low = values[(n * 0.10f).toInt()], high = values[((n - 1) * 0.90f).toInt()])
}

/** The bands of a photo shown with Crop: the title sits in the top 30%, the readout in the bottom 38%. */
internal fun heroToneOf(pixels: IntArray, width: Int, height: Int, backdrop: Float? = null): HeroPhotoTone {
    val top = bandLuma(pixels, width, 0, (height * 0.30f).toInt().coerceAtLeast(1), backdrop)
    val bottom = bandLuma(pixels, width, (height * 0.62f).toInt().coerceAtMost(height - 1), height, backdrop)
    return HeroPhotoTone(top?.let(::overlayFor), bottom?.let(::overlayFor))
}

private const val SampleW = 160
private const val SampleH = 90

/** What the photo [model] looks like behind the hero's text, or null when it cannot be loaded. The size and crop match the card. */
private val toneCache = java.util.concurrent.ConcurrentHashMap<String, HeroPhotoTone>()

/** [measureHeroTone], remembered for the session so a page swiped to later has its text colours on the first frame. */
internal suspend fun measureHeroToneCached(context: Context, model: Any, backdrop: Float?): HeroPhotoTone? {
    val key = toneCacheKey(model, backdrop)
    toneCache[key]?.let { return it }
    return measureHeroTone(context, model, backdrop)?.also { toneCache[key] = it }
}

/** A cut-out is remembered per page brightness it was judged against, so a theme change measures it again. */
private fun toneCacheKey(model: Any, backdrop: Float?): String = "$model|${backdrop?.let { (it * 20f).roundToInt() } ?: -1}"

private suspend fun measureHeroTone(context: Context, model: Any, backdrop: Float?): HeroPhotoTone? = withContext(Dispatchers.Default) {
    runCatching {
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(SampleW, SampleH)
            .scale(Scale.FILL)
            .precision(Precision.EXACT)
            .allowHardware(false)
            .build()
        val bitmap = context.imageLoader.execute(request).drawable?.toBitmap(SampleW, SampleH) ?: return@runCatching null
        val pixels = IntArray(SampleW * SampleH).also { bitmap.getPixels(it, 0, SampleW, 0, 0, SampleW, SampleH) }
        heroToneOf(pixels, SampleW, SampleH, backdrop)
    }.getOrNull()
}

/**
 * The tone of the hero's photo, measured off the main thread once per photo and page brightness; [HeroPhotoTone.None] until then, and
 * for no photo. [page] is the colour a cut-out's transparent parts show.
 */
@Composable
internal fun rememberHeroPhotoTone(imageUrl: String?, page: Color): State<HeroPhotoTone> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val model = if (imageUrl.isNullOrBlank()) null else rememberPhotoModel(imageUrl)
    val backdrop = imageUrl?.let { toneBackdropFor(it, page) }
    return produceState(model?.let { toneCache[toneCacheKey(it, backdrop)] } ?: HeroPhotoTone.None, model, backdrop) {
        value = model?.let { measureHeroToneCached(context, it, backdrop) } ?: HeroPhotoTone.None
    }
}

/** The tone the hero's text and scrim follow, provided by the hero card around its shell. */
internal val LocalHeroTone = compositionLocalOf { HeroPhotoTone.None }

/** The title's on-photo colour: the top band's text, else the theme's own, eased when it changes. */
@Composable
internal fun heroTitleOnPhoto(tone: HeroPhotoTone = LocalHeroTone.current): Color =
    animateColorAsState(tone.top?.textColor ?: heroOnPhoto(), tween(MotionMedium), label = "heroTitleTone").value

/** The readout's on-photo colour: the bottom band's text, else the theme's own, eased when it changes. */
@Composable
internal fun heroReadoutOnPhoto(tone: HeroPhotoTone = LocalHeroTone.current): Color =
    animateColorAsState(tone.bottom?.textColor ?: heroOnPhoto(), tween(MotionMedium), label = "heroReadoutTone").value
