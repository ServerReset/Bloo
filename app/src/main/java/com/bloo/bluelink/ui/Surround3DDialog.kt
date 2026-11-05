package com.bloo.bluelink.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.svm3d.Backdrop
import com.bloo.bluelink.svm3d.Fisheye
import com.bloo.bluelink.svm3d.FloorFill
import com.bloo.bluelink.svm3d.ModelStore
import com.bloo.bluelink.svm3d.OnnxDepth
import com.bloo.bluelink.svm3d.OnnxInpaint
import com.bloo.bluelink.svm3d.OnnxVehicles
import com.bloo.bluelink.svm3d.SceneGlb
import com.bloo.bluelink.svm3d.SurroundBuilder
import com.bloo.bluelink.svm3d.SvmCalibration
import com.bloo.bluelink.svm3d.SvmShot
import com.bloo.bluelink.ui.car3d.CarBody
import com.bloo.bluelink.ui.car3d.CarPaint
import com.bloo.bluelink.ui.car3d.Surround3DView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** A built 3D scene: its GLB, and the cars the detector found in the photos. */
private class SurroundScene(val glb: ByteArray, val vehicles: List<com.bloo.bluelink.svm3d.Vehicle>)

/**
 * The 3D view of the car's newest surround-view set. The first time, the depth, detection and inpainting models are downloaded (about
 * the size shown, from their makers' GitHub pages), then the scene is built on the phone and kept. Read-only.
 */
@Composable
internal fun Surround3DDialog(v: Vehicle, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val newest = remember(v.vin) { SurroundHistory.load(context, v.vin).firstOrNull() }
    val models = remember(context) { ModelStore(File(context.filesDir, "models")) }
    val cacheBase = remember(v.vin, newest?.utcTime) { File(context.filesDir, "svm3d/v7_${newest?.utcTime ?: "latest"}") }
    var scene by remember(v.vin) { mutableStateOf<SurroundScene?>(null) }
    var busy by remember(v.vin) { mutableStateOf(false) }
    var stage by remember(v.vin) { mutableStateOf("") }
    var progress by remember(v.vin) { mutableFloatStateOf(0f) }
    var needsDownload by remember(v.vin) { mutableStateOf(false) }
    var error by remember(v.vin) { mutableStateOf<String?>(null) }
    var painted by remember(v.vin) { mutableStateOf(true) }
    var hasPainted by remember(v.vin) { mutableStateOf(false) }

    fun report(s: String, f: Float) = scope.launch(Dispatchers.Main) { stage = s; progress = f }

    // Loads the scene kept from an earlier build, if there is one; otherwise builds it, or asks first when the models are missing.
    fun start(downloadFirst: Boolean) {
        val shot = newest ?: return
        busy = true
        error = null
        scope.launch {
            val kept = withContext(Dispatchers.IO) { loadScene(cacheBase) }
            if (kept != null) {
                scene = kept
                busy = false
                return@launch
            }
            if (!models.ready && !downloadFirst) {
                needsDownload = true
                busy = false
                return@launch
            }
            needsDownload = false
            try {
                if (!models.ready) {
                    report("Downloading the models", 0f)
                    withContext(Dispatchers.IO) { models.download { f -> report("Downloading the models", f * 0.99f) } }
                }
                val built = withContext(Dispatchers.Default) {
                    buildScene(models, SvmShot.of(shot), cacheBase) { s, f -> report(s, f) }
                }
                scene = built
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message ?: "Couldn't build the 3D view."
            } catch (e: OutOfMemoryError) {
                error = "The phone ran out of memory building the 3D view."
            }
            busy = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("3D surroundings", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                val current = scene
                when {
                    newest == null -> SurroundNote("Ask the car for surround-view photos first, then come back here.")
                    needsDownload -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "The 3D view needs three models, about ${ModelStore.TOTAL_BYTES / 1_000_000} MB, downloaded once from their " +
                                "makers' GitHub pages. Best on Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = { start(downloadFirst = true) }) { Text("Download and build") }
                    }
                    busy -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(stage.ifBlank { "Building the 3D view" }, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    current != null -> {
                        Surround3DView(
                            envGlb = current.glb,
                            vehicles = current.vehicles,
                            painted = painted,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            body = CarBody.forModel(v.model),
                            paint = CarPaint.WHITE,
                            onHasPainted = { hasPainted = it },
                        )
                        if (hasPainted) {
                            OutlinedButton(onClick = { painted = !painted }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (painted) "Show the simple view" else "Show the painted view")
                            }
                        }
                    }
                    else -> Button(onClick = { start(downloadFirst = false) }) { Text("Build the 3D view") }
                }
                error?.let { SurroundNote(it, emphasised = true) }
            }
        }
    }
}

/** Builds the scene from one surround set: the cameras, the depth and cars, the floor and the far surroundings. Blocking; run it off the main thread. */
private fun buildScene(models: ModelStore, shot: SvmShot, cacheBase: File, report: (String, Float) -> Unit): SurroundScene {
    report("Reading the photos", 0f)
    val cams = mapOf("Front" to SvmCalibration.FRONT, "Rear" to SvmCalibration.REAR, "Left" to SvmCalibration.LEFT, "Right" to SvmCalibration.RIGHT)
    val frames = shot.views().mapNotNull { (name, bmp) ->
        val cam = cams[name] ?: return@mapNotNull null
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in px.indices) px[i] = px[i] and 0xFFFFFF
        Fisheye(cam, bmp.width, bmp.height, px)
    }
    if (frames.size < 4) error("These photos don't have all four camera views.")
    if (frames.any { it.width != SvmCalibration.FRAME_WIDTH || it.height != SvmCalibration.FRAME_HEIGHT }) {
        error("These photos are a different size from the ones the cameras were calibrated with.")
    }
    val depth = OnnxDepth(models.file(ModelStore.DEPTH))
    val detector = runCatching { OnnxVehicles(models.file(ModelStore.DETECTOR)) }.getOrNull()
    val scene = try {
        SurroundBuilder.run(frames, depth, detector) { stage, f -> report(stage, f * 0.8f) }
    } finally {
        depth.close()
        detector?.close()
    }
    var ground = scene.ground
    var backdrop: Backdrop? = null
    val lama = if (models.has(ModelStore.INPAINT)) runCatching { OnnxInpaint(models.file(ModelStore.INPAINT)) }.getOrNull() else null
    try {
        if (lama != null) {
            report("Painting in the floor the cameras couldn't see", 0.8f)
            runCatching { FloorFill.fill(scene, lama) { f -> report("Painting in the floor the cameras couldn't see", 0.8f + 0.1f * f) } }
                .onSuccess { ground = it }
        }
        report("Stitching the far surroundings", 0.9f)
        backdrop = runCatching { Backdrop.build(frames, scene.backdropBlocked, lama) { f -> report("Stitching the far surroundings", 0.9f + 0.08f * f) } }.getOrNull()
    } finally {
        lama?.close()
    }
    val n = scene.groundSize
    val argb = IntArray(n * n) { ground[it] or (0xFF shl 24) }
    val bmp = Bitmap.createBitmap(argb, n, n, Bitmap.Config.ARGB_8888)
    val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
    val backdropJpeg = backdrop?.let { bd ->
        val bb = Bitmap.createBitmap(IntArray(bd.rgb.size) { bd.rgb[it] or (0xFF shl 24) }, bd.width, bd.height, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().also { bb.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }
    val built = SurroundScene(SceneGlb.write(scene, jpeg, backdropImage = backdropJpeg), scene.vehicles)
    runCatching { saveScene(cacheBase, built) }
    return built
}

/** The scene kept from an earlier build of the same set, or null. */
private fun loadScene(base: File): SurroundScene? {
    val glb = File(base.path + ".glb")
    val txt = File(base.path + ".txt")
    if (!glb.exists() || !txt.exists()) return null
    val vehicles = txt.readLines().filter { it.isNotBlank() }.map { line ->
        val p = line.split(" ")
        com.bloo.bluelink.svm3d.Vehicle(p[0].toDouble(), p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5])
    }
    return SurroundScene(glb.readBytes(), vehicles)
}

private fun saveScene(base: File, scene: SurroundScene) {
    base.parentFile?.mkdirs()
    File(base.path + ".glb").writeBytes(scene.glb)
    File(base.path + ".txt").writeText(scene.vehicles.joinToString("\n") { "${it.x} ${it.y} ${it.heading} ${it.length} ${it.height} ${it.kind}" })
}
