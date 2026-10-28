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

/** Dark or light text for [band], whichever reads better against the band's average brightness. */
internal fun overlayFor(band: BandLuma): BandOverlay =
    BandOverlay(darkText = contrastRatio(DarkTextLuma, band.mean) >= contrastRatio(LightTextLuma, band.mean), behind = band)

/** The decision for the two bands the hero's text sits in; a null band means "not measured", and the theme's own tone applies. */
@Immutable
internal data class HeroPhotoTone(val top: BandOverlay?, val bottom: BandOverlay?) {
    companion object { val None = HeroPhotoTone(null, null) }
}

/**
 * Luminance of the rows [fromRow, toRow) of a [width]-wide pixel grid, ignoring transparent pixels. Null when the band is mostly
 * transparent (a cut-out has no photo behind the text to measure).
 */
internal fun bandLuma(pixels: IntArray, width: Int, fromRow: Int, toRow: Int): BandLuma? {
    val values = FloatArray((toRow - fromRow) * width)
    var n = 0
    for (y in fromRow until toRow) for (x in 0 until width) {
        val p = pixels[y * width + x]
        if ((p ushr 24) < 128) continue
        values[n++] = relativeLuminance((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
    }
    if (n < values.size / 2 || n == 0) return null
    values.sort(0, n)
    return BandLuma(mean = values.take(n).average().toFloat(), low = values[(n * 0.10f).toInt()], high = values[((n - 1) * 0.90f).toInt()])
}

/** The bands of a photo shown with Crop: the title sits in the top 30%, the readout in the bottom 38%. */
internal fun heroToneOf(pixels: IntArray, width: Int, height: Int): HeroPhotoTone {
    val top = bandLuma(pixels, width, 0, (height * 0.30f).toInt().coerceAtLeast(1))
    val bottom = bandLuma(pixels, width, (height * 0.62f).toInt().coerceAtMost(height - 1), height)
    return HeroPhotoTone(top?.let(::overlayFor), bottom?.let(::overlayFor))
}

private const val SampleW = 160
private const val SampleH = 90

/** What the photo [model] looks like behind the hero's text, or null when it cannot be loaded. The size and crop match the card. */
private val toneCache = java.util.concurrent.ConcurrentHashMap<String, HeroPhotoTone>()

/** [measureHeroTone], remembered for the session so a page swiped to later has its text colours on the first frame. */
internal suspend fun measureHeroToneCached(context: Context, model: Any): HeroPhotoTone? {
    val key = model.toString()
    toneCache[key]?.let { return it }
    return measureHeroTone(context, model)?.also { toneCache[key] = it }
}

private suspend fun measureHeroTone(context: Context, model: Any): HeroPhotoTone? = withContext(Dispatchers.Default) {
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
        heroToneOf(pixels, SampleW, SampleH)
    }.getOrNull()
}

/** The tone of the hero's photo, measured off the main thread once per photo; [HeroPhotoTone.None] until then, and for no photo or a cut-out. */
@Composable
internal fun rememberHeroPhotoTone(imageUrl: String?): State<HeroPhotoTone> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val model = if (imageUrl.isNullOrBlank() || isCutoutPhoto(imageUrl)) null else rememberPhotoModel(imageUrl)
    return produceState(model?.let { toneCache[it.toString()] } ?: HeroPhotoTone.None, model) {
        value = model?.let { measureHeroToneCached(context, it) } ?: HeroPhotoTone.None
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
