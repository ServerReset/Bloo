@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.PinCrypto
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setPlatform
import com.bloo.bluelink.data.setPowertrain
import com.bloo.bluelink.data.setSeatFlag
import com.bloo.bluelink.data.setSyncUri
import com.bloo.bluelink.data.syncUri

/**
 * Step 1: a short welcome + feature highlights. See [OnboardingTipListPage]'s own doc for why
 * this shares that shape (including its no-per-item-entrance-animation rule) with the two
 * closing pages -- this page is where that rule was originally learned the hard way.
 */
@Composable
internal fun OnboardingIntroPage() {
    OnboardingTipListPage(
        emoji = "👋",
        title = "Welcome to Bloo",
        titleStyle = MaterialTheme.typography.displaySmall,
        subtitle = "Lock, climate, charge and more for your Hyundai, Genesis, or Kia. Let's set up your car.",
        tips = listOf(
            Triple(AppIcons.Bolt, "Live status", "Battery, fuel, and lock state at a glance"),
            Triple(Icons.Filled.Thermostat, "Remote climate", "Warm it up or cool it down before you get in"),
            Triple(Icons.Filled.SwapHoriz, "Multiple cars", "Swipe between every car on your account"),
        ),
    )
}

/**
 * Step 2: get the app set up to actually work. Two things are REQUIRED before Next unlocks,
 * because the app is genuinely worse without them and "I'll do it later" reliably means never:
 *
 *  - **Notifications**, so charge/alerts/updates can reach the user at all (API 33+).
 *  - **A lock** -- biometrics when the device has them, otherwise a PIN. Without one of these
 *    the app can never lock itself, so the lock card SWAPS to whichever the device supports
 *    instead of showing both and letting the user skip the one that actually matters.
 *
 * Drive/manual sync stays optional: a restored backup can still skip the per-car setup screens
 * later in this flow for any car it already configured (see [buildOnboardingSteps]'
 * `preConfiguredVins`).
 */
@Composable
internal fun OnboardingSetupPage(
    vm: AppViewModel,
    state: UiState,
    context: android.content.Context,
    canBio: Boolean,
    biometricLock: Boolean,
    notifGranted: Boolean,
    onNotifResult: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        "Quick setup",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        color = scheme.onSurface,
    )
    BodyMediumText(
        "Two quick things and you're in -- let Bloo reach you, and lock the app.",
        color = scheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(GapHairline))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> onNotifResult(granted) }
        OnboardingSetupCard(
            icon = Icons.Filled.Notifications,
            title = "Notifications",
            body = "Charge status, car alerts and app updates need this.",
            done = notifGranted,
            required = true,
        ) {
            MorphActionButton(
                label = if (notifGranted) "Notifications on" else "Turn on notifications",
                icon = if (notifGranted) AppIcons.CheckCircle else Icons.Filled.Notifications,
                onClick = { if (!notifGranted) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth(),
                active = notifGranted,
            )
        }
    }

    // ONE lock card, swapped to whatever this device can actually authenticate with.
    if (canBio) {
        val bioEnabled = biometricLock
        OnboardingSetupCard(
            icon = Icons.Filled.Fingerprint,
            title = "Biometric lock",
            body = "Require your biometrics to open Bloo.",
            done = bioEnabled,
            required = true,
        ) {
            MorphActionButton(
                label = if (bioEnabled) "Biometric lock on" else "Turn on biometric lock",
                icon = if (bioEnabled) AppIcons.CheckCircle else Icons.Filled.Fingerprint,
                onClick = {
                    if (!bioEnabled) {
                        context.findFragmentActivity()?.let { activity ->
                            showBiometricPrompt(
                                activity = activity,
                                title = "Enable biometric lock",
                                subtitle = "Confirm to require it when opening Bloo",
                                onSuccess = { vm.setBiometricLock(true) },
                                onError = {},
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                active = bioEnabled,
            )
        }
    } else {
        OnboardingSetupCard(
            icon = AppIcons.Lock,
            title = "PIN lock",
            body = "This device has no biometrics, so a 4-8 digit PIN locks the app.",
            done = state.appPinSet,
            required = true,
        ) {
            OnboardingPinForm(
                existing = state.appPinSet,
                onSet = { pin -> vm.setAppPin(pin) },
            )
        }
    }

    // --- Sync across devices (Google Drive or a plain file) ---
    var showDriveDialog by remember { mutableStateOf(false) }
    val driveSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.setSyncUri(it) } }
    val driveOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.importSettingsAndSync(context, it) } }
    if (showDriveDialog) {
        DriveSyncSetupDialog(
            onDismissRequest = { showDriveDialog = false },
            onSaveToDrive = { showDriveDialog = false; driveSaveLauncher.launch("bloo_settings.json") },
            onOpenFromDrive = { showDriveDialog = false; driveOpenLauncher.launch(arrayOf("application/json")) },
        )
    }
    val syncEnabled = state.syncUri != null
    OnboardingSetupCard(
        icon = Icons.Filled.CloudSync,
        title = "Sync across devices",
        body = if (syncEnabled) {
            "Your settings and car photos back up to Google Drive automatically."
        } else {
            "Join an existing backup to restore your car photos and setup, or start fresh."
        },
        done = syncEnabled,
    ) {
        // AnimatedContent, not a bare if/else -- this used to snap straight
        // from the "Set up Drive sync" button to the "enabled" row the instant
        // the dialog finished, the one un-animated content swap left in a step
        // whose sibling cards (notifications, biometric) at least keep the
        // same MorphButton in place and only recolor it.
        AnimatedContent(
            targetState = syncEnabled,
            // Explicit, not the implicit default -- every other AnimatedContent
            // in this file specifies its own transitionSpec; this one didn't,
            // which meant a real height difference between the two states (the
            // MorphButton's Material3 minimum touch target vs. the plain
            // "enabled" row) snapped instantly under the fade instead of
            // animating, a small but visible pop right when Drive sync
            // finishes setting up.
            transitionSpec = {
                (fadeIn(tween(180)) togetherWith fadeOut(tween(180)))
                    .using(SizeTransform(clip = false))
            },
            label = "onboardingSyncDone",
        ) { enabled: Boolean ->
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Drive sync enabled", fontWeight = FontWeight.SemiBold, color = scheme.primary)
                }
            } else {
                MorphActionButton(
                    label = "Set up Drive sync",
                    icon = Icons.Filled.Cloud,
                    onClick = { showDriveDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One card in the onboarding Setup step: icon + title + body on a solid
 *  surface -- not directly on the animated Aurora background, which made
 *  plain text here hard to read against a busy, colourful, moving backdrop
 *  -- with [content] (a MorphButton or a "done" status row) below. [done]
 *  tints the icon chip to the primary color as a lightweight "this one's
 *  handled" cue, matching the checkmark treatment MorphButton itself already
 *  uses for its own active state. */
/**
 * The create-a-PIN mini form used by onboarding (and, in a slimmer re-use,
 * the building block of the Settings set/change/remove dialogs): two
 * matching 4-8 digit fields, a haptic'd Save only once valid. [existing]
 * true just swaps the call to "Replace PIN" semantics -- the caller handles
 * what that means; this form only ever validates and reports a valid new
 * PIN.
 */
@Composable
internal fun OnboardingPinForm(
    existing: Boolean,
    onSet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val valid = pin.length in PinCrypto.PIN_MIN_DIGITS..PinCrypto.PIN_MAX_DIGITS &&
        pin == confirm
    val sanitize: (String) -> String = { it.take(PinCrypto.PIN_MAX_DIGITS).filter { ch -> ch.isDigit() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GapRow)) {
        BlooTextField(
            value = pin,
            onValueChange = { pin = sanitize(it); attempted = false },
            placeholder = { Text("4–8 digit PIN") },
            singleLine = true,
            colors = borderlessFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = attempted && pin.isNotEmpty() && pin.length < PinCrypto.PIN_MIN_DIGITS,
            modifier = Modifier.fillMaxWidth(),
        )
        BlooTextField(
            value = confirm,
            onValueChange = { confirm = sanitize(it); attempted = false },
            placeholder = { Text("Confirm PIN") },
            singleLine = true,
            colors = borderlessFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = attempted && confirm.isNotEmpty() && pin != confirm,
            modifier = Modifier.fillMaxWidth(),
        )
        if (attempted && (pin.length < PinCrypto.PIN_MIN_DIGITS || pin != confirm)) {
            Text(
                "PINs must be 4-8 digits and match.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
            )
        }
        val pinLabel: String = if (existing) "Replace PIN" else "Save PIN"
        MorphActionButton(
            label = pinLabel,
            icon = AppIcons.Lock,
            onClick = {
                if (valid) {
                    haptics?.click()
                    onSet(pin)
                    pin = ""
                    confirm = ""
                } else {
                    attempted = true
                    haptics?.tick()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.isNotEmpty() && confirm.isNotEmpty(),
        )
    }
}

@Composable
internal fun OnboardingSetupCard(
    icon: ImageVector,
    title: String,
    body: String,
    done: Boolean,
    /** Required to leave this step: shows a "Required" chip until [done], so the user
     *  knows why Next is disabled rather than just finding it greyed out. */
    required: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = LargeShape,
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding16(), verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(
                    if (done) AppIcons.CheckCircle else icon,
                    tint = if (done) scheme.onPrimaryContainer else scheme.primary,
                    containerColor = if (done) scheme.primaryContainer else scheme.surfaceContainerHighest,
                    iconSize = 20.dp,
                )
                Column(Modifier.weight(1f)) {
                    TitleSmallText(title, color = scheme.onSurface)
                    MutedText(body)
                }
                if (required && !done) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = scheme.tertiaryContainer,
                    ) {
                        Text(
                            "Required",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            content()
        }
    }
}

/** One step per car: powertrain, seats, and steering-wheel heat together on a
 *  single dedicated screen -- reuses the exact same persisted-flag wiring as
 *  [CarFeatureWizard]'s per-feature pages, just consolidated into one page
 *  per vehicle instead of three. */
/**
 * A single tinted "tip" card: a rounded [surfaceContainerHigh] surface holding a
 * primary-tinted icon beside a bold title and a muted one-line body. The onboarding
 * intro and crash-course pages each render a list of these; the card chrome was
 * copied verbatim between them, so it lives here and each page just maps its own
 * `Triple(icon, title, body)` list onto it.
 */
@Composable
internal fun OnboardingTipCard(icon: ImageVector, title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = StandardShape,
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The same leading-circle icon badge the search results list, the update pebble,
        // and the settings hero stats all already use -- a bare tinted icon here was the
        // one place left still doing it differently for no reason tied to this screen.
        IconLeadRow(
            icon,
            tint = scheme.primary,
            title = title,
            subtitle = body,
            badgeSize = 28.dp,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/**
 * The three-line header every setup-wizard page opens with: a primary-coloured
 * eyebrow, a large title, and a supporting paragraph. Emitted as bare siblings (NOT
 * wrapped in a Column) because the callers place them as direct children of a Column
 * with its own `Arrangement.spacedBy`, which spaces the header lines and the gap to
 * the page content below -- an inner Column would collapse that spacing. The title is
 * pinned to `onSurface` (== `onBackground` in every scheme this app produces), so all
 * four pages render pixel-identically to how they did when hand-rolled.
 */
@Composable
internal fun WizardPageHeader(eyebrow: String, title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = scheme.primary, fontWeight = FontWeight.Bold)
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = scheme.onSurface)
    BodyMediumText(body, color = scheme.onSurfaceVariant)
}

@Composable
internal fun OnboardingCarPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    state: UiState,
    sc: com.bloo.bluelink.data.SeatConfig,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    WizardPageHeader(
        "Set up",
        vehicle.name,
        "Set powertrain and features once so the right controls appear.",
    )

    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Text("Powertrain", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
        val currentPt = state.powertrainOf(vehicle)
        PowertrainPicker(current = currentPt) { pt -> vm.setPowertrain(vehicle, pt) }
    }

    // Only Hyundai/Genesis US vehicles have a real head-unit generation to
    // confirm -- see platformOverridable's own doc.
    if (vehicle.platformOverridable) {
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            Text("Head-unit generation", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
            PlatformPicker(current = state.platformOf(vehicle)) { pt -> vm.setPlatform(vehicle, pt) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Text("Seats", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(StandardShape)
                .background(scheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = GapHairline),
        ) {
            SeatPositions.forEachIndexed { i: Int, pos: SeatPosition ->
                if (i > 0) SectionDivider(alpha = 0.35f)
                // The shared SeatConfigRow (Rows.kt) -- the exact row, with the exact
                // signature, that the per-car Settings card renders. The wizard used to keep
                // its own copy (WizardSeatRow + WizardToggleChip): same label + Heat/Cool
                // pair, but on half-height bespoke pills instead of MorphChip, and its "Cool"
                // chip carried a ❄️ the Settings one never had. One row, two places.
                SeatConfigRow(
                    pos.label,
                    pos.heat(sc),
                    pos.cool(sc),
                    onHeat = { enabled: Boolean -> vm.setSeatFlag(vehicle, pos.heatKey, enabled) },
                    onCool = { enabled: Boolean -> vm.setSeatFlag(vehicle, pos.coolKey, enabled) },
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Text("Extras", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
        // ToggleRow -- the app's one boolean-setting control, and the same row the
        // per-car Settings card shows this exact flag on ("Heated steering wheel").
        // This was a bespoke checkmark-pill MorphButton: a THIRD treatment of one
        // boolean, next to Settings' ToggleRow and the wizard's own steering page
        // (which hand-rolled a switch row of its own, see WizardSteeringPage). Same
        // label as Settings too, so the wizard doesn't teach a name the app then
        // stops using.
        ToggleRow("Heated steering wheel", sc.steeringWheel) { vm.setSeatFlag(vehicle, "sw", it) }
    }
}

/**
 * The shared shape all three intro/closing pages ([OnboardingIntroPage],
 * [OnboardingCrashCoursePage], [OnboardingFeaturesPage]) turned out to want: an emoji, a big
 * title, a supporting line, then a list of tip cards. Extracted after finding it hand-written
 * three times over -- the pages differ only in their copy, [titleStyle] (the intro page's
 * welcome is a size up from the other two), and their tip list, never in shape, so a future
 * fourth page (or a copy edit to any existing one) has exactly one place to change.
 *
 * Deliberately no per-item entrance animation on the tip list, matching what
 * [OnboardingIntroPage] itself already settled on: [AnimatedContent]'s own slide/fade in
 * [OnboardingScreen] already animates the whole page in, and this exact page family already
 * tried layering a second per-card entrance on top of that once (see intro's own history) and
 * found it fought with the page slide, reading as jittery rather than smooth. The two closing
 * pages get the same plain, instant stack intro already uses -- not a separate animation
 * choice per page that happens to agree today.
 */
@Composable
internal fun OnboardingTipListPage(
    emoji: String,
    title: String,
    subtitle: String,
    tips: List<Triple<ImageVector, String, String>>,
    titleStyle: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    val scheme = MaterialTheme.colorScheme
    Text(emoji, style = MaterialTheme.typography.displayMedium)
    Spacer(Modifier.height(GapHairline))
    Text(title, style = titleStyle, fontWeight = FontWeight.Black, color = scheme.onSurface)
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
    Spacer(Modifier.height(GapHairline))
    tips.forEach { (icon, cardTitle, body) ->
        OnboardingTipCard(icon, cardTitle, body)
    }
}

/** Second-to-last step: a quick tip list covering the app's core gestures. Followed by
 *  [OnboardingFeaturesPage], the actual final step. */
@Composable
internal fun OnboardingCrashCoursePage() {
    OnboardingTipListPage(
        emoji = "🎉",
        title = "You're all set",
        subtitle = "A few things that make Bloo quick to use:",
        tips = listOf(
            Triple(Icons.Filled.SwapHoriz, "Swipe between cars", "Swipe left or right on any pebble's top row, or anywhere on the hero card, to change cars, even when a pebble is open"),
            Triple(Icons.Filled.DragHandle, "Tap to expand, hold to reorder", "Tap any pebble for details, or hold and drag to rearrange them"),
            Triple(Icons.Filled.Refresh, "Hold to refresh", "Press and hold the refresh control to pull the latest status from your car"),
            Triple(AppIcons.Settings, "Tune it anytime", "Powertrain, seats, and lock settings all live in Settings if things change"),
        ),
    )
}

/**
 * The actual final step -- the one screen shown right before "Enter Bloo" hands off to the
 * garage, so it is the one place every new user is guaranteed to see these highlighted at
 * least once, unlike a feature that only shows itself to someone who happens to open
 * Settings. Distinct from [OnboardingCrashCoursePage] just before it: that page is about
 * *how to use the screen you're about to land on* (gestures); this one is about
 * *things the app can do that aren't obvious from looking at it* (AutoLock, live charging,
 * natural-language search). On-device AI is the one entry gated on
 * [UiState.aiSupported] -- the others work on every device, but advertising a feature this
 * phone's own hardware can't run would be a promise the app can't keep.
 */
@Composable
internal fun OnboardingFeaturesPage(state: UiState) {
    val tips = buildList<Triple<ImageVector, String, String>> {
        add(Triple(AppIcons.Lock, "AutoLock", "Locks your car when you walk away -- enable it per car in Settings"))
        add(Triple(AppIcons.Bolt, "Live charging updates", "Watch an EV's charge progress from your lock screen while plugged in"))
        add(Triple(AppIcons.Search, "Just ask", "Search \"lock my car\" or \"start climate at 70\" to run it from the bar"))
        if (state.aiSupported) {
            add(Triple(AppIcons.AutoAwesome, "On-device AI summaries", "Plain-language status summaries, generated on your phone"))
        }
    }
    OnboardingTipListPage(
        emoji = "✨",
        title = "A few more things Bloo can do",
        subtitle = "Worth knowing about, whenever you're ready for them:",
        tips = tips,
    )
}
