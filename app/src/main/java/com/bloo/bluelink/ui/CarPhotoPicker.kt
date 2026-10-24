package com.bloo.bluelink.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.update
import com.bloo.uicommon.MotionShort
import com.bloo.uicommon.MotionMedium

/** Ask for the photo picker for one car, from anywhere: Settings, search, or a plain-language request. */
fun AppViewModel.requestPhotoPick(vin: String) = _state.update { it.copy(photoPickRequest = vin) }

internal fun AppViewModel.clearPhotoPickRequest() = _state.update { it.copy(photoPickRequest = null) }

/**
 * The one place a car photo is chosen and cropped, mounted at the app root so it works whichever page
 * asked: the system photo picker (crash-free), then our own Compose crop step, then the saved photo.
 * Settings' "Change photo", the search control and a natural-language request all just call
 * [requestPhotoPick].
 */
@Composable
internal fun CarPhotoPickerHost(vm: AppViewModel, request: String?) {
    var pickTarget by remember { mutableStateOf<String?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    // The crop screen is shown by this flag, not by cropUri itself, so it can fade out with its content
    // intact: the picked image and target are cleared only after the exit has played.
    var showCrop by remember { mutableStateOf(false) }
    // A document picker, so every image type is offered (AVIF and JPEG XL included); the system photo picker lists only some.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && pickTarget != null) { cropUri = uri; showCrop = true } else pickTarget = null
    }
    LaunchedEffect(request) {
        if (request != null) {
            pickTarget = request
            vm.clearPhotoPickRequest()
            launcher.launch(arrayOf("image/*"))
        }
    }
    LaunchedEffect(showCrop) {
        if (!showCrop && cropUri != null) {
            kotlinx.coroutines.delay((MotionShort + 100).toLong())
            cropUri = null
            pickTarget = null
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = showCrop,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(MotionMedium)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(MotionShort)),
    ) {
        val uri = cropUri
        val target = pickTarget
        if (uri != null && target != null) {
            CropScreen(
                vin = target,
                uriString = uri.toString(),
                onCancel = { showCrop = false },
                onSave = { path -> vm.setVehicleImage(target, path); showCrop = false },
            )
        }
    }
}
