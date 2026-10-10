package com.bloo.bluelink.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

// --- Photo crop editor ---

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
