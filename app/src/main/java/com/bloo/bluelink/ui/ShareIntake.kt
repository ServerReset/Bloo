package com.bloo.bluelink.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.settingsBackupProblem
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.MotionMedium
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MotionShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update

// Things shared INTO Bloo from another app's share sheet: a picture (set as a car's photo, through the same crop step as
// Settings' "Change photo") or a settings file (imported, after a confirm, through the same import as Settings' "Restore").
// MainActivity hands the share to [offerSharedFile]; [ShareIntakeHost] shows the steps.

private val JsonTypes = setOf("application/json", "text/json")

/** Takes a share that arrived with [mime] and starts the step it needs. Anything else is refused, with a reason. */
fun AppViewModel.offerSharedFile(uri: Uri, mime: String?) {
    val type = mime.orEmpty().lowercase()
    when {
        type.startsWith("image/") -> _state.update { it.copy(sharedImageUri = uri.toString(), sharedImageVin = null) }
        type in JsonTypes -> viewModelScope.launch {
            // Checked now, not at import: a file that is not a Bloo backup is said so at once, and never offered for import.
            val json = withContext(Dispatchers.IO) {
                runCatching { getApplication<android.app.Application>().contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }
                    .getOrNull()
            }
            val problem = if (json == null) "Couldn't read that file." else settingsStore.settingsBackupProblem(json)
            if (problem != null) {
                AppLog.log("⚠ Shared settings file refused: $problem")
                _state.update { it.copy(message = problem, messageType = ToastKind.ERROR) }
                return@launch
            }
            _state.update { it.copy(sharedSettingsUri = uri.toString()) }
        }
        else -> _state.update {
            it.copy(message = "Bloo can take a photo or a settings file (.json).", messageType = ToastKind.ERROR)
        }
    }
}

/** Says why a share was refused or dropped, in the app's own message toast. */
internal fun AppViewModel.showMessage(text: String) = _state.update { it.copy(message = text, messageType = ToastKind.ERROR) }

/** Picks the car a shared picture is for; the crop step then runs for it. */
internal fun AppViewModel.chooseSharedImageCar(vin: String) = _state.update { it.copy(sharedImageVin = vin) }

internal fun AppViewModel.endSharedImage() = _state.update { it.copy(sharedImageUri = null, sharedImageVin = null) }

internal fun AppViewModel.endSharedSettings() = _state.update { it.copy(sharedSettingsUri = null) }

/**
 * The share steps, drawn over the app: a picture asks which car it is for (straight to the crop when there is only one), then
 * crops and saves it; a settings file asks to be confirmed, then imports.
 */
@Composable
internal fun ShareIntakeHost(vm: AppViewModel, stateHolder: State<UiState>) {
    val context = LocalContext.current
    val imageUri by remember { derivedStateOf { stateHolder.value.sharedImageUri } }
    val imageVin by remember { derivedStateOf { stateHolder.value.sharedImageVin } }
    val settingsUri by remember { derivedStateOf { stateHolder.value.sharedSettingsUri } }
    val vehicles by remember { derivedStateOf { stateHolder.value.vehicles } }

    // Acted on once per share, not on every recomposition: a picture with no car to put it on says so, one car takes it.
    LaunchedEffect(imageUri, imageVin, vehicles) {
        val pending = imageUri ?: return@LaunchedEffect
        when {
            vehicles.isEmpty() -> {
                vm.endSharedImage()
                vm.showMessage("Sign in to a car first, then share the photo again.")
            }
            imageVin == null && vehicles.size == 1 -> vm.chooseSharedImageCar(vehicles.first().vin)
        }
    }

    val chosen = imageVin
    val uri = imageUri
    if (uri != null && chosen == null && vehicles.size > 1) {
        SharedCarChooser(
            vehicles = vehicles,
            onPick = { vm.chooseSharedImageCar(it.vin) },
            onDismiss = { vm.endSharedImage() },
        )
    }

    val crop = if (uri != null && chosen != null) chosen to uri else null
    AnimatedVisibility(visible = crop != null, enter = fadeIn(tween(MotionMedium)), exit = fadeOut(tween(MotionShort))) {
        if (crop != null) {
            CropScreen(
                vin = crop.first,
                uriString = crop.second,
                onCancel = { vm.endSharedImage() },
                onSave = { path ->
                    vm.setVehicleImage(crop.first, path)
                    vm.endSharedImage()
                },
            )
        }
    }

    val settings = settingsUri
    if (settings != null) {
        GlassAlertDialog(
            onDismissRequest = { vm.endSharedSettings() },
            icon = AppIcons.Settings,
            title = "Import these settings?",
            text = {
                BodyMediumText(
                    "This replaces the matching settings on this phone with the ones in the file. Your car sign-in is never in a settings file, so it stays as it is.",
                )
            },
            buttons = {
                ConfirmCancelButtons(
                    "Import",
                    onConfirm = {
                        vm.endSharedSettings()
                        vm.importSettings(context, Uri.parse(settings))
                    },
                    onCancel = { vm.endSharedSettings() },
                    confirmEmphasis = ButtonEmphasis.Tonal,
                )
            },
        )
    }
}

/**
 * The cars a shared picture can go to: one row each, with the car's name and its model. The VIN stays off the screen;
 * nothing here needs it.
 */
@Composable
private fun SharedCarChooser(vehicles: List<Vehicle>, onPick: (Vehicle) -> Unit, onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = AppIcons.DirectionsCar,
        title = "Use this photo for",
        text = {
            SecondaryText("Pick the car this photo is for. You can change it later in that car's settings.")
            vehicles.forEach { v ->
                ListButton(
                    title = v.name.ifBlank { v.model },
                    subtitle = v.model.ifBlank { null },
                    icon = AppIcons.DirectionsCar,
                    onClick = { onPick(v) },
                )
            }
        },
        buttons = { DialogButton("Cancel", onDismiss) },
    )
}
