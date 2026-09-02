@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.graphics.Bitmap
import android.os.Build
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import com.bloo.uicommon.ReorderColumn

/**
 * Lightweight, crash-free crop: pinch-zoom + drag the picked image inside a 16:9
 * frame, then export the framed region to a file. Drawn via a Canvas + Matrix so
 * what you see is what gets saved.
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
                val uri = Uri.parse(uriString)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: return@runCatching null
                // Apply the photo's EXIF orientation (camera photos are often rotated).
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
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = bmp
                when {
                    image != null -> Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(18.dp))
                            .onSizeChanged { frame = it }
                            .pointerInput(image) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offset += pan
                                }
                            },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val wpx = size.width
                            val hpx = size.height
                            val cover = max(wpx / image.width, hpx / image.height)
                            val s = cover * scale
                            val maxX = ((image.width * s - wpx) / 2f).coerceAtLeast(0f)
                            val maxY = ((image.height * s - hpx) / 2f).coerceAtLeast(0f)
                            val cx = offset.x.coerceIn(-maxX, maxX)
                            val cy = offset.y.coerceIn(-maxY, maxY)
                            val m = android.graphics.Matrix().apply {
                                postTranslate(-image.width / 2f, -image.height / 2f)
                                postScale(s, s)
                                postTranslate(wpx / 2f + cx, hpx / 2f + cy)
                            }
                            drawIntoCanvas { it.nativeCanvas.drawBitmap(image, m, null) }
                        }
                    }
                    failed -> Text("Couldn't load that image", color = Color.White)
                    else -> LoadingIndicator()
                }
            }
            ExpressiveButtonRow(spacing = 12.dp) {
                val cancelSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = cancelSource,
                    enabled = true,
                ) {
                    MorphTextButton(
                        "Cancel",
                        onClick = onCancel,
                        interactionSource = cancelSource,
                    )
                }
                val confirmSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = confirmSource,
                    enabled = true,
                ) {
                    MorphButton(
                    onClick = {
                        val image = bmp ?: return@MorphButton
                        val f = frame
                        scope.launch {
                            val path = withContext(Dispatchers.IO) {
                                runCatching {
                                    val wpx = f.width.toFloat()
                                    val hpx = f.height.toFloat()
                                    val cover = max(wpx / image.width, hpx / image.height)
                                    val s = cover * scale
                                    val maxX = ((image.width * s - wpx) / 2f).coerceAtLeast(0f)
                                    val maxY = ((image.height * s - hpx) / 2f).coerceAtLeast(0f)
                                    val cx = offset.x.coerceIn(-maxX, maxX)
                                    val cy = offset.y.coerceIn(-maxY, maxY)
                                    val outScale = 1080f / wpx
                                    val out = Bitmap.createBitmap(1080, (hpx * outScale).toInt(), Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(out)
                                    val m = android.graphics.Matrix().apply {
                                        postTranslate(-image.width / 2f, -image.height / 2f)
                                        postScale(s, s)
                                        postTranslate(wpx / 2f + cx, hpx / 2f + cy)
                                        postScale(outScale, outScale)
                                    }
                                    canvas.drawBitmap(image, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                                    val dir = java.io.File(context.filesDir, "cars").apply { mkdirs() }
                                    // Preserve transparency: alpha sources are saved as PNG (so the
                                    // background stays see-through and renders seamlessly), others JPEG.
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
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) { Text("Use photo", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
// --- Settings -------------------------------------------------------------
// (The settings screen is owned by SettingsScreen.kt's family: SettingsScreen,
// SettingsCards, SettingsIndex, SettingsSearch, SettingsWidgets.)


// --- Small reusable pieces ------------------------------------------------

@Composable
internal fun StatusRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        // Top-align so that if the value wraps to a 2nd line (a long value at a
        // large display/font size), the label stays anchored to the first line
        // rather than floating to the vertical center of a now-taller row.
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            // MutedContentAlpha (0.7) on the phone, where LocalContentColor is usually
            // full onSurface and 0.7 reads as a deliberately secondary label. On the
            // cover every pebble's default container is surfaceVariant, whose paired
            // content tone is ALREADY the dimmer onSurfaceVariant role -- StatusRow is
            // the single most-reused row in the app (Diagnostics, Trips, Charge,
            // Weather, ...), so this one compounding was the largest contributor to
            // "flip mode is a contrast nightmare". 0.92 on the cover, unchanged
            // elsewhere: same fix CoverTile's own subtitle already got.
            color = LocalContentColor.current.copy(
                alpha = if (LocalForceExpanded.current) 0.92f else MutedContentAlpha,
            ),
            // Without a cap, at a large display size the value cell (below) used to
            // take its full intrinsic width first, starving this weighted label into
            // a sliver — and a single-word label ("Coordinates", "Email", "VIN")
            // with no room to break at a space then wrapped CHARACTER-by-character
            // ("Coordin/ates"). One line + ellipsis keeps the label intact; giving
            // the value its own weight (below) stops it from crushing the label in
            // the first place. Mirrors the correctly-built SyncInfoRow.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        // Right-aligning Box that OWNS half the row (weight(1f), which fills its
        // allocation) with the value end-aligned inside it. This keeps the classic
        // label-left / value-right column: a SHORT value ("Off", "90%", "Locked")
        // sits flush to the row's right edge, while a LONG value (coordinates, VIN,
        // email) is bounded to this half and wraps to a 2nd line instead of crushing
        // the label into character-by-character wrapping. (An earlier version put
        // weight(1f, fill = false) directly on the value; because AnimatedValue's
        // leaf text hugs its content, fill=false made a short value measure to its
        // intrinsic width and pack just past row-center — floating in the middle
        // with dead space to its right, since textAlign=End has no room to act in a
        // content-width box. The filling Box gives End something to align against.)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // Was a hand-rolled AnimatedContent + WiggleText -- uicommon's shared
            // AnimatedValue already implements this (used elsewhere in this file
            // and now watch's ChargeRing). Colour pinned to full-strength onSurface
            // rather than inherited -- Pebble's Card sets its content color from
            // containerColor (usually surfaceVariant), so an uncoloured value here
            // rendered at onSurfaceVariant strength, barely distinguishable from the
            // dimmed label right next to it despite being the important half.
            val baseStyle = LocalTextStyle.current
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            // Memoized to avoid recreating the TextStyle.copy() on every recomposition.
            val valueStyle = remember(baseStyle, onSurfaceColor) {
                baseStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = onSurfaceColor,
                    textAlign = TextAlign.End,
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
    Text(
        text,
        modifier = Modifier.padding(top = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = LocalContentColor.current.copy(alpha = 0.85f),
    )
}

@Composable
internal fun StepRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Cap at 2 lines so a long label ("Text & layout scale") wraps cleanly at
        // spaces instead of the value (short: "130%") crushing it mid-word at a
        // large font size.
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        // Roll the value when it changes (e.g. dragging a slider).
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 })
            },
            label = "stepValue",
        ) { v -> Text(v, fontWeight = FontWeight.Medium, color = valueColor, maxLines = 1) }
    }
}

/**
 * The app's one toggle control for boolean settings. Ground-up redesign away
 * from a plain label next to a stock Material [Switch] -- a custom pill
 * track+thumb (spring-timed like [MorphButton]) stands in for the Switch so
 * this shares that pill-morph vocabulary too instead of being the one
 * default-Material holdout in an otherwise fully custom UI.
 *
 * An earlier version of this also washed the whole row toward the primary
 * color and rounded its corners when checked, matching how MorphButton fills
 * solid on activation -- dropped after feedback that stacked next to a
 * card's own background it read as a second nested box rather than a
 * highlight, especially over the AI toggle's already-boxed row.
 */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    /**
     * The explanatory line under the switch, if the setting needs one.
     *
     * A parameter and not a Text the caller writes itself: Settings had eleven of those,
     * hand-written at their call sites, and they had drifted -- some carried a 10dp bottom
     * padding, some none, and the gap above them was whatever the previous control happened
     * to leave. Owning the caption here means one style and one rhythm for all of them, and
     * it stays outside the toggleable below so TalkBack still reports the row as a single
     * switch rather than a switch followed by a paragraph.
     */
    description: String? = null,
    onChange: (Boolean) -> Unit,
) {
    // No wrapper at all when there is no caption, so every existing call site keeps exactly
    // the layout it had -- a Column around a single fillMaxWidth Row measures the same, but
    // "the same" is not worth asserting across ~25 call sites for a branch that costs nothing.
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
 * The caption style shared by [ToggleRow]'s own `description` and by the settings rows that
 * need one without a switch attached. Bottom padding, not top: it belongs to the control it
 * explains, and the gap that matters is the one before the NEXT control.
 */
@Composable
internal fun SettingsCaption(
    text: String,
    modifier: Modifier = Modifier,
    /**
     * The gap below. The default is the group gap, because a caption normally trails the
     * control it explains and what matters is the distance to the NEXT one. Pass a smaller one
     * where the caption instead LEADS its own control, so the two read as a pair.
     */
    bottomGap: Dp = SettingsGapGroup,
) {
    Text(
        text,
        modifier = modifier.padding(top = 2.dp, bottom = bottomGap),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A bare toggle, with no row and no label around it -- what a card puts on its own title row
 * when the whole card IS one setting.
 *
 * The same MorphToggleTrack every ToggleRow draws, with the same toggleable semantics, so an
 * inline card and an expanded one are the same control in two places rather than two controls
 * that happen to look alike.
 */
@Composable
internal fun InlineToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    Box(
        Modifier.toggleable(
            value = checked,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Switch,
        ) {
            val next = !checked
            if (next) haptics?.toggleOn() else haptics?.toggleOff()
            onChange(next)
        },
    ) {
        MorphToggleTrack(checked)
    }
}

@Composable
private fun ToggleRowControl(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            // toggleable (not clickable) gives this its own Role.Switch + checked
            // semantics node -- the track below clears its own (identical) node
            // so TalkBack sees ONE correctly-announced toggle for the row
            // instead of two adjacent focus stops (a generic "double tap to
            // activate" for the row, then the real on/off announcement for the
            // track a swipe later).
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
            ) {
                val next = !checked
                if (next) haptics?.toggleOn() else haptics?.toggleOff()
                onChange(next)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            // Cap at 2 lines: the toggle track is fixed-width so it can't be pushed
            // off, but a long setting label at a large font size should wrap at
            // spaces to two lines rather than growing the row indefinitely.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        MorphToggleTrack(checked)
    }
}

/**
 * A pill track + circular thumb, spring-timed like [MorphButton] instead of
 * the stock Material [Switch]. Purely visual -- [ToggleRow]'s own toggleable()
 * modifier owns the real click target and semantics, so this clears its own.
 */
@Composable
internal fun MorphToggleTrack(checked: Boolean) {
    val trackColor by androidx.compose.animation.animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else buttonContainer(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "toggleTrackBg",
    )
    val thumbColor by androidx.compose.animation.animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "toggleThumbFg",
    )
    val trackWidth = 44.dp
    val trackHeight = 26.dp
    val inset = 3.dp
    val thumbSize by animateDpAsState(
        if (checked) 20.dp else 16.dp,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
        label = "toggleThumbSize",
    )
    val thumbOffset by animateDpAsState(
        if (checked) trackWidth - thumbSize - inset else inset,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
        label = "toggleThumbOffset",
    )
    Box(
        Modifier
            .size(trackWidth, trackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)), RoundedCornerShape(50))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

/** One seat's heat + cool capability toggles, shown as two compact filter chips. */
@Composable
internal fun SeatConfigRow(
    label: String,
    heat: Boolean,
    cool: Boolean,
    onHeat: (Boolean) -> Unit,
    onCool: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            // The two chips are fixed-width; cap the label so a long seat name at a
            // large font size wraps at spaces rather than being crushed mid-word.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        // A group, so pressing Heat takes width from Cool rather than shoving it -- the pair is
        // exactly the "several buttons in one space" case, and it was a plain Row.
        ExpressiveButtonRow(spacing = 8.dp) {
            MorphChip(selected = heat, onClick = { onHeat(!heat) }, label = "Heat")
            MorphChip(selected = cool, onClick = { onCool(!cool) }, label = "Cool")
        }
    }
}

@Composable
internal fun CommandButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    MorphButton(
        onClick = onClick,
        enabled = enabled,
        // 64dp, not ButtonTargetHeight: this is the app's LARGE command button (Open in maps,
        // the pebble command rows), deliberately taller than a settings button. Its type and
        // glyph come from the shared tokens even so, so it stays a size variant of the one
        // button rather than a second look -- ButtonLabelStyle is already what it was hand-
        // writing as titleMedium, and the glyph matches the icon-only token for the same
        // reason that one exists: at this size an 18dp icon reads as a speck.
        modifier = modifier.height(64.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonIconOnlySize))
        Spacer(Modifier.width(8.dp))
        Text(label, style = ButtonLabelStyle, fontWeight = FontWeight.SemiBold)
    }
}

/** A label/value row inside the sync status block: muted label on the left,
 *  emphasised value on the right (monospaced for the File ID so it reads as a
 *  code to compare across devices). */
@Composable
internal fun SyncInfoRow(
    label: String,
    value: String,
    valueMono: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            fontFamily = if (valueMono) androidx.compose.ui.text.font.FontFamily.Monospace else null,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The synced-devices registry shown in the "Backup & sync" card: a
 * drag-to-reorder list (same gesture as the car-order list in Settings) where
 * the TOP device is the primary — the source of truth other devices adopt.
 * Dragging a device to the top makes it primary. Each row shows a drag handle, a
 * device icon (★ on the primary), its name (with a "This device" marker for
 * self + a rename affordance), model, and how long ago it last synced. Renders
 * nothing until the first sync populates the registry.
 */
@Composable
internal fun SyncDevicesSection(state: UiState, vm: AppViewModel) {
    val devices = state.syncDevices
    if (devices.isEmpty()) return
    var renaming by remember { mutableStateOf(false) }

    // Order the list so the primary is on top (that's the invariant the drag
    // gesture maintains); everyone else falls in by most-recently-seen. Dragging
    // a device to the top sets it primary, after which this same sort keeps it
    // there — so the visual order and the "primary" concept stay in lockstep.
    val ordered = remember(devices, state.syncPrimaryId) {
        devices.sortedWith(
            compareByDescending<com.bloo.bluelink.data.SyncMerge.SyncDevice> { it.id == state.syncPrimaryId }
                .thenByDescending { it.lastSeenMs },
        )
    }

    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Devices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Synced devices",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(2.dp))
    Text(
        "Drag to reorder. The top device is primary, the source of truth the others follow.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))

    ReorderColumn(
        items = ordered,
        keyOf = { it.id },
        // Dropped in a new order → the new TOP device becomes primary. setPrimaryDevice
        // persists it + triggers a sync so every device converges on the choice.
        onReorder = { reordered -> reordered.firstOrNull()?.let { vm.setPrimaryDevice(it.id) } },
        spacing = 8.dp,
    ) { device, dragHandle, dragging ->
        SyncDeviceRow(
            device = device,
            isSelf = device.id == state.thisDeviceId,
            isPrimary = device.id == state.syncPrimaryId,
            dragging = dragging,
            dragHandle = dragHandle,
            onRename = { renaming = true },
        )
    }

    // Advisory: if a peer hasn't checked in for a while but this device just
    // synced, it likely drifted onto a DIFFERENT Drive file (a device can't see
    // another's file directly — the File ID at the top is the real cross-check).
    val now = System.currentTimeMillis()
    val stalePeer = devices.any { it.id != state.thisDeviceId && it.lastSeenMs > 0 && now - it.lastSeenMs > STALE_DEVICE_MS }
    if (stalePeer) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "A device hasn't synced in a while. If it's still in use, check its File ID matches the one under Diagnostics. Otherwise it's on a different file. Reconnect it via Change Drive file → Open from Drive.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (renaming) {
        var draft by remember { mutableStateOf(state.syncDeviceName) }
        val scheme = MaterialTheme.colorScheme
        // Standardized on the shared GlassAlertDialog shell (was the legacy
        // BlooDialog, now removed). Stacked full-width buttons.
        GlassAlertDialog(
            onDismissRequest = { renaming = false },
            icon = Icons.Filled.Smartphone,
            title = "Rename this device",
            text = {
                Text(
                    "Shown in the devices list on all your synced devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                // Styled to match every other text field in the app (18dp FieldShape,
                // borderless surface fill) rather than a default outlined box, which
                // looked generic against the frosted dialog.
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Device name") },
                    placeholder = { Text(Build.MODEL ?: "This device") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = borderlessFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            buttons = {
                val saveRenameSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = saveRenameSource,
                    enabled = draft.isNotBlank(),
                ) {
                    MorphButton(
                        onClick = {
                            if (draft.isNotBlank()) vm.renameThisDevice(draft)
                            renaming = false
                        },
                        active = true,
                        interactionSource = saveRenameSource,
                        enabled = draft.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                }
                val cancelRenameSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = cancelRenameSource,
                    enabled = true,
                ) {
                    MorphTextButton(
                        "Cancel",
                        onClick = { renaming = false },
                        interactionSource = cancelRenameSource,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
        )
    }
}

/** One row in the drag-to-reorder [SyncDevicesSection]: a frosted card with a
 *  drag handle, a device icon (★ when primary), the device name (+ a "This
 *  device" chip and a rename button for self), model, and last-seen. Styled to
 *  match the card language of the rest of Settings; lifts slightly while dragged. */
@Composable
internal fun SyncDeviceRow(
    device: com.bloo.bluelink.data.SyncMerge.SyncDevice,
    isSelf: Boolean,
    isPrimary: Boolean,
    dragging: Boolean,
    dragHandle: Modifier,
    onRename: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val container =
        if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        // Shared default, not its own 0.9 -- see glassContainerAlpha's own doc
        // for why every frosted surface takes the one value now.
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = glassContainerAlpha())
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .then(if (dragging) Modifier.dropShadow(shape, blurRadius = 14.dp, offsetY = 4.dp) else Modifier)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag handle — the grab affordance, same idiom as the car-order list.
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragHandle.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            if (isPrimary) Icons.Filled.Star else Icons.Filled.Smartphone,
            contentDescription = if (isPrimary) "Primary device" else null,
            tint = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.name.ifBlank { "Unnamed device" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPrimary || isSelf) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSelf) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "This device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val seen = com.bloo.bluelink.data.relativeLabel(device.lastSeenMs)
            val sub = buildString {
                if (isPrimary) append("Primary")
                val model = device.model.takeIf { it.isNotBlank() }
                if (isPrimary && model != null) append(" · ")
                if (model != null) append(model)
                if (seen.isNotBlank()) { if (isNotEmpty()) append(" · "); append(seen) }
            }
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelf) {
            MorphIconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename this device", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * The Google Drive sync setup dialog, shared between onboarding and the
 * Settings "Backup & sync" card so both look and behave identically (they
 * used to be two separately hand-rolled dialogs -- one BlooDialog, one plain
 * AlertDialog with an awkward confirmButton/dismissButton split -- that had
 * drifted out of sync with each other). Two tappable choice cards instead of
 * three same-weight text buttons, so "start fresh" vs. "join an existing
 * sync" reads as an actual decision rather than an arbitrary button order.
 */
@Composable
internal fun DriveSyncSetupDialog(
    onDismissRequest: () -> Unit,
    onSaveToDrive: () -> Unit,
    onOpenFromDrive: () -> Unit,
    // True when this device has synced before / knows about other devices. In
    // that case "Save to Drive" would create a SEPARATE new file (Google Drive
    // allows duplicate names) — the exact trap that leaves two devices on two
    // files that never converge — so it's gated behind a warning + confirm, and
    // "Open from Drive" (join the existing file) is emphasized as the right path.
    hasExistingSync: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // Local warning step: first tap of "Save to Drive" while already synced flips
    // this on and swaps the row for a warning + explicit "Create anyway"; the
    // recommended action is to join the existing file instead.
    var warnNewFile by remember { mutableStateOf(false) }
    GlassAlertDialog(
        onDismissRequest = onDismissRequest,
        icon = Icons.Filled.Cloud,
        title = "Google Drive sync",
        text = {
            Text(
                "Keep your settings in sync across devices with one file in Google Drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            // Join first — it's the correct choice when another device already set
            // sync up, and making it the emphasized (active) card steers people away
            // from accidentally creating a second file.
            DriveSyncChoiceRow(
                icon = Icons.Filled.FileOpen,
                title = "Open from Drive",
                subtitle = "Join the file another device already set up, they'll share settings.",
                emphasized = hasExistingSync,
                onClick = onOpenFromDrive,
            )
            if (warnNewFile) {
                // The trap, spelled out, with the safe alternative one tap away.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.errorContainer.copy(alpha = 0.5f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "This creates a NEW, separate file: your devices would end up on different files and stop sharing settings. Only do this to start over.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onErrorContainer,
                    )
                    MorphButton(
                        onClick = onSaveToDrive,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) { Text("Create a new file anyway", color = scheme.error) }
                }
            } else {
                DriveSyncChoiceRow(
                    icon = Icons.Filled.CreateNewFolder,
                    title = "Save to Drive",
                    subtitle = "Start fresh: create a new file with this device's settings.",
                    onClick = { if (hasExistingSync) warnNewFile = true else onSaveToDrive() },
                )
            }
        },
        buttons = {
            MorphTextButton("Cancel", onClick = onDismissRequest, modifier = Modifier.fillMaxWidth())
        },
    )
}

@Composable
internal fun DriveSyncChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    // Highlights this choice as the recommended one (filled/active MorphButton).
    emphasized: Boolean = false,
) {
    // The app's standard button component (MorphButton), not a bespoke
    // Surface row -- so this dialog's actions look and feel like every other
    // button in the app instead of a one-off.
    MorphButton(
        onClick = onClick,
        active = emphasized,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
