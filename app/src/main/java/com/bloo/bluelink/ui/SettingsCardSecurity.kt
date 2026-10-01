@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.bloo.uicommon.ReorderColumn
import android.content.ClipData
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.setAiEnabled
import com.bloo.bluelink.data.setFontChoice
import com.bloo.bluelink.data.setShowSearch
import com.bloo.bluelink.data.setUnitSystem
import com.bloo.bluelink.data.unitSystem
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.composed
import com.bloo.bluelink.data.links
import kotlin.math.max
import com.bloo.bluelink.data.setChargerApiKey
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.semantics.selected
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.deleteCustomPalette
import com.bloo.bluelink.data.saveCustomPalette
import com.bloo.bluelink.data.setActiveCustomPaletteId
import com.bloo.bluelink.data.setAuroraBackground
import com.bloo.bluelink.data.setAuroraMotion
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setColorPalette
import com.bloo.bluelink.data.setDynamicColor
import com.bloo.bluelink.data.setHapticsEnabled
import com.bloo.bluelink.data.setLockTiming
import com.bloo.bluelink.data.setPebbleOutline
import com.bloo.bluelink.data.setThemeMode
import com.bloo.uicommon.rememberConfirmArm

/** The "Security" card: the app lock and the PIN that backs it up. */
@Composable
internal fun SecurityCardContent(
    state: UiState,
    vm: AppViewModel,
    canBio: Boolean,
    context: Context,
    appearance: SettingsStore.Appearance,
) {
    val locked = canBio && appearance.biometricLock
    val status = when {
        !canBio -> "No biometrics enrolled"
        locked -> "Locked · ${appearance.lockTiming.label}"
        else -> "Not locked"
    }
    var pinDialog by remember { mutableStateOf<String?>(null) }
    val pinSet = state.appPinSet
    SettingsCard("Security", AppIcons.Lock, vm, status = status) {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            SettingsGroup("App lock") {
                if (canBio) {
                    val timingOn = appearance.biometricLock
                    SettingsSegmentedRow(
                        label = "Ask for biometrics",
                        options = listOf(
                            SegmentOption("off", LockTiming.OFF.label, null),
                            SegmentOption("screen_off", LockTiming.SCREEN_OFF.label, null),
                            SegmentOption("immediate", LockTiming.IMMEDIATE.label, null),
                        ),
                        selectedKey = when {
                            !timingOn -> "off"
                            appearance.lockTiming == LockTiming.IMMEDIATE -> "immediate"
                            else -> "screen_off"
                        },
                        onSelect = { key ->

                            when (key) {
                                "off" -> {
                                    // Turning the lock OFF needs the same authentication
                                    // turning it on does, otherwise reaching Settings from an
                                    // already-unlocked app lets a single unauthenticated tap
                                    // permanently remove the lock on future cold launches --
                                    // turning momentary physical access into standing access
                                    // to unlocking the car. Failing to authenticate keeps the
                                    // lock on.
                                    val activity = context.findFragmentActivity()
                                    // The cold-start lock decision is (biometricLock && canBio)
                                    // OR a PIN being set (see AppViewModel's own doc) -- clearing
                                    // ONLY biometricLock here used to leave the app locking on
                                    // every launch anyway whenever a PIN was also set, reported
                                    // directly as "turn off locking, it still asks every time".
                                    // "App lock: Off" is this control's own promise that the app
                                    // stops asking at all, so it has to clear BOTH mechanisms --
                                    // the same biometric confirmation already required to get
                                    // here is treated as proof of identity everywhere else in
                                    // this screen (enabling/disabling the lock itself), so
                                    // reusing it to also drop the PIN isn't a weaker gate than
                                    // the PIN removal dialog's own "enter the current PIN" check,
                                    // just a different, already-trusted proof of the same thing.
                                    val pinAlsoSet = state.appPinSet
                                    if (activity == null) {
                                        // Fail closed -- keep the lock -- but say so,
                                        // rather than leaving the control looking stuck.
                                        vm.reportInfo("Couldn't verify it's you. The lock is still on.")
                                    } else {
                                        showBiometricPrompt(
                                            activity = activity,
                                            title = "Turn off app lock",
                                            subtitle = if (pinAlsoSet) {
                                                "Confirm to stop requiring it. This also removes your PIN."
                                            } else {
                                                "Confirm to stop requiring it"
                                            },
                                            onSuccess = {
                                                vm.setBiometricLock(false)
                                                if (pinAlsoSet) vm.removeAppPin()
                                            },
                                            onError = { },
                                        )
                                    }
                                }
                                else -> {
                                    val timing = if (key == "immediate") LockTiming.IMMEDIATE else LockTiming.SCREEN_OFF
                                    if (timingOn) {
                                        // Already locked: changing *when* it re-locks needs no
                                        // extra proof -- the user just proved who they are to
                                        // be in here, and tightening the timing is harmless.
                                        vm.setLockTiming(timing)
                                    } else {
                                        // Turning the lock ON proves who you are first, then
                                        // both arms it and sets when it re-locks.
                                        val activity = context.findFragmentActivity()
                                        if (activity == null) {
                                            vm.reportInfo("Couldn't verify it's you. The lock wasn't turned on.")
                                        } else {
                                            showBiometricPrompt(
                                                activity = activity,
                                                title = "Enable biometric lock",
                                                subtitle = "Confirm to require it on launch",
                                                onSuccess = {
                                                    vm.setBiometricLock(true)
                                                    vm.setLockTiming(timing)
                                                },
                                                onError = { },
                                            )
                                        }
                                    }
                                }
                            }
                        
                        },
                    )
                } else {
                    BodySmallText("No biometrics are enrolled on this device.")
                }
            }
            SettingsGroup("App PIN") {
                BodySmallText(
                    if (canBio) "A 4-8 digit PIN that works as a backup when biometrics aren't available."
                    else "This device has no biometrics, so the app unlocks with this PIN.",
                )
                ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                    MorphActionButton(
                        label = if (pinSet) "Change PIN" else "Set up PIN",
                        icon = if (pinSet) Icons.Filled.LockReset else AppIcons.Lock,
                        onClick = { pinDialog = "set" },
                    )
                    if (pinSet) {
                        SafeMorphTextButton("Remove", onClick = { pinDialog = "remove" }, emphasis = ButtonEmphasis.Destructive)
                    }
                }
            }
        }
        PinDialogs(mode = pinDialog, onDismiss = { pinDialog = null }, vm = vm, state = state, canBio = canBio)
    }
}
