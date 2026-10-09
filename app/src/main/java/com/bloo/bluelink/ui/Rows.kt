package com.bloo.bluelink.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Crash-free crop: pinch-zoom + drag the picked image inside a 16:9 frame, then export the framed
 * region. Drawn via Canvas + Matrix so what you see is what is saved.
 */
@Composable
internal fun CropScreen(vin: String, uriString: String, onCancel: () -> Unit, onSave: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bmp by remember(uriString) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uriString) { mutableStateOf(false) }
    var scale by remember(uriString) { mutableFloatStateOf(1f) }
    var offset by remember(uriString) { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(uriString) {
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                val uri = uriString.toUri()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: return@runCatching null
                // Apply the photo's EXIF orientation.
                val orientation = context.contentResolver.openInputStream(uri)?.use {
                    androidx.exifinterface.media.ExifInterface(it).getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                    )
                } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                val m = android.graphics.Matrix()
                when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                }
                if (m.isIdentity) raw
                else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            }.getOrNull()
        }
        if (bmp == null) failed = true
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize().padding(GapSection), verticalArrangement = Arrangement.spacedBy(GapSection)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = bmp
                when {
                    image != null -> Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(StandardShape)
                            .onSizeChanged { frame = it }
                            .pointerInput(image) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offset += pan
                                }
                            },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val m = cropMatrix(image.width, image.height, size.width, size.height, scale, offset)
                            drawIntoCanvas { it.nativeCanvas.drawBitmap(image, m, null) }
                        }
                    }
                    failed -> Text("Couldn't load that image", color = Color.White)
                    else -> LoadingIndicator()
                }
            }
            ExpressiveButtonRow(spacing = GapGroup) {
                SafeMorphTextButton(
                    "Cancel",
                    onClick = onCancel,
                    emphasis = ButtonEmphasis.Deny,
                )
                val confirmSource = remember { MutableInteractionSource() }
                MorphButton(
                onClick = {
                    val image = bmp ?: return@MorphButton
                    val f = frame
                    scope.launch {
                        val path = withContext(Dispatchers.IO) {
                            runCatching {
                                val wpx = f.width.toFloat()
                                val hpx = f.height.toFloat()
                                val outScale = 1080f / wpx
                                val out = createBitmap(1080, (hpx * outScale).toInt(), Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(out)
                                val m = cropMatrix(image.width, image.height, wpx, hpx, scale, offset)
                                    .apply { postScale(outScale, outScale) }
                                canvas.drawBitmap(image, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                                val dir = java.io.File(context.filesDir, "cars").apply { mkdirs() }
                                // Preserve transparency: alpha sources save as PNG, others as JPEG.
                                val alpha = image.hasAlpha()
                                val ext = if (alpha) "png" else "jpg"
                                val file = java.io.File(dir, "car_${vin}_${System.currentTimeMillis()}.$ext")
                                file.outputStream().use {
                                    if (alpha) out.compress(Bitmap.CompressFormat.PNG, 100, it)
                                    else out.compress(Bitmap.CompressFormat.JPEG, 90, it)
                                }
                                file.absolutePath
                            }.getOrNull()
                        }
                        if (path != null) onSave(path) else onCancel()
                    }
                },
                enabled = bmp != null,
                interactionSource = confirmSource,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = GapRow),
                    expressive = true,
                fillOnPress = true,
                groupWeight = GroupWeightProportional,
            ) { Text("Use photo", style = ButtonLabelStyle, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// --- Settings -------------------------------------------------------------
// (The settings screen lives in SettingsScreen.kt's family.)

// --- Small reusable pieces ------------------------------------------------

@Composable
internal fun StatusRow(label: String, value: String, valueMono: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        // Top-align so a wrapped value leaves the label on the first line.
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            // Natural width, not weight(1f): a 50/50 split starved the value (the long side) into
            // wrapping. The value below is the row's one weighted child.
            style = MaterialTheme.typography.bodyMedium,
            // MutedContentAlpha reads as a secondary label; 0.92 in a forced-open context
            // (LocalForceExpanded) so the pair stays distinguishable.
            color = LocalContentColor.current.copy(
                alpha = if (LocalForceExpanded.current) 0.92f else MutedContentAlpha,
            ),
            // Capped defensively so a long label clips to one line instead of wrapping per
            // character.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(GapRow))
        // Right-aligning Box owns the label's leftover width: short values sit flush right, long
        // ones (coordinates, VIN, email) use the extra width. A filling Box gives textAlign=End
        // something to align against.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // AnimatedValue (uicommon). Colour pinned to onSurface: Pebble's Card would otherwise
            // give onSurfaceVariant, barely distinct from the dimmed label.
            val baseStyle = LocalTextStyle.current
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            // Memoized to avoid recreating TextStyle.copy() every recomposition.
            val valueStyle = remember(baseStyle, onSurfaceColor, valueMono) {
                baseStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = onSurfaceColor,
                    textAlign = TextAlign.End,
                    fontFamily = if (valueMono) FontFamily.Monospace else baseStyle.fontFamily,
                )
            }
            com.bloo.uicommon.AnimatedValue(
                value = value,
                style = valueStyle,
                maxLines = 2,
                reduceMotion = LocalReduceMotion.current,
            )
        }
    }
}

/** A small bold group heading used inside the Car-info pebble. */
@Composable
internal fun SectionLabel(text: String) {
    TitleSmallText(
        text,
        modifier = Modifier.padding(top = 2.dp),
        color = LocalContentColor.current.copy(alpha = 0.85f),
    )
}

@Composable
internal fun StepRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // bodySmall keeps slider labels compact.
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(GapRow))
        // Roll the value when it changes (e.g. dragging a slider).
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 })
            },
            label = "stepValue",
        ) { v -> Text(v, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = valueColor, maxLines = 1) }
    }
}

/**
 * The app's one toggle control for boolean settings: a custom pill track+thumb (spring-timed like
 * [MorphButton]) in place of a stock Material [Switch].
 */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    /**
     * The explanatory line under the switch. Owning it here gives one style and rhythm, and keeps
     * it outside the toggleable so TalkBack reports a single switch.
     */
    description: String? = null,
    onChange: (Boolean) -> Unit,
) {
    // No wrapper at all when there is no caption, so every existing call site keeps exactly the
    // layout it had -- a Column around a single fillMaxWidth Row measures the same, but "the same"
    // is not worth asserting across ~25 call sites for a branch that costs nothing.
    if (description == null) {
        ToggleRowControl(label, checked, onChange)
    } else {
        Column(Modifier.fillMaxWidth()) {
            ToggleRowControl(label, checked, onChange)
            SettingsCaption(description)
        }
    }
}

/**
 * The caption style shared by [ToggleRow]'s `description` and switchless settings rows. Bottom
 * padding: it belongs to the control it explains.
 */
@Composable
internal fun SettingsCaption(
    text: String,
    modifier: Modifier = Modifier,
    /**
     * The gap below. The default is the group gap, because a caption normally trails the control it
     * explains and what matters is the distance to the NEXT one. Pass a smaller one where the
     * caption instead LEADS its own control, so the two read as a pair.
     */
    bottomGap: Dp = GapGroup,
) {
    Text(
        text,
        modifier = modifier.padding(top = 2.dp, bottom = bottomGap),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A bare toggle (no row or label) for a card whose whole body is one setting; same track and
 * semantics as [ToggleRow].
 */
/**
 * The `toggleable` (for checked/Role.Switch semantics), no-ripple and toggle-haptics wrapper shared
 * by [InlineToggle] and [ToggleRowControl].
 */
@Composable
private fun Modifier.hapticToggleable(checked: Boolean, onChange: (Boolean) -> Unit): Modifier {
    val haptics = LocalHaptics.current
    // No blockPageSwipe: a sideways drag from a toggle swipes the page (a real tap is inside the
    // touch slop, so the toggle still wins).
    return toggleable(
        value = checked,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Switch,
    ) {
        val next = !checked
        if (next) haptics?.toggleOn() else haptics?.toggleOff()
        onChange(next)
    }
}

@Composable
internal fun InlineToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.hapticToggleable(checked, onChange)) {
        MorphToggleTrack(checked)
    }
}

@Composable
private fun ToggleRowControl(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // toggleable gives a Role.Switch + checked node; the track clears its own so TalkBack
            // sees one toggle.
            .hapticToggleable(checked, onChange)
            .padding(vertical = GapHairline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            // Cap at 2 lines; long labels wrap at spaces rather than growing the row.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(GapGroup))
        MorphToggleTrack(checked)
    }
}

/**
 * The matrix drawing an [imgW] x [imgH] image into a [wpx] x [hpx] frame as the crop editor shows
 * it: scaled to cover, zoomed by [scale], panned by [offset] but never past an edge. Preview and
 * saved bitmap share it.
 */
private fun cropMatrix(imgW: Int, imgH: Int, wpx: Float, hpx: Float, scale: Float, offset: androidx.compose.ui.geometry.Offset): android.graphics.Matrix {
    val s = max(wpx / imgW, hpx / imgH) * scale
    val maxX = ((imgW * s - wpx) / 2f).coerceAtLeast(0f)
    val maxY = ((imgH * s - hpx) / 2f).coerceAtLeast(0f)
    return android.graphics.Matrix().apply {
        postTranslate(-imgW / 2f, -imgH / 2f)
        postScale(s, s)
        postTranslate(wpx / 2f + offset.x.coerceIn(-maxX, maxX), hpx / 2f + offset.y.coerceIn(-maxY, maxY))
    }
}
