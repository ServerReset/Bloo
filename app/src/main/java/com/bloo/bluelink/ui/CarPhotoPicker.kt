package com.bloo.bluelink.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update

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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && pickTarget != null) cropUri = uri else pickTarget = null
    }
    LaunchedEffect(request) {
        if (request != null) {
            pickTarget = request
            vm.clearPhotoPickRequest()
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    cropUri?.let { uri ->
        val target = pickTarget
        if (target != null) {
            CropScreen(
                vin = target,
                uriString = uri.toString(),
                onCancel = { cropUri = null; pickTarget = null },
                onSave = { path -> vm.setVehicleImage(target, path); cropUri = null; pickTarget = null },
            )
        }
    }
}
