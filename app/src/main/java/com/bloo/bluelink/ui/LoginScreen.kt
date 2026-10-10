package com.bloo.bluelink.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.max
import kotlinx.coroutines.flow.first

/**
 * Sign-in form for every brand (US, Canada, Europe) on one screen. Field state is local until
 * [onLogin] fires, so switching brands keeps typed values. Brand copy cross-fades via
 * [AnimatedContent].
 */
@Composable
internal fun LoginScreen(
    loading: Boolean,
    onLogin: (String, String, String, Brand) -> Unit,
    onCancel: (() -> Unit)? = null,
    /**
     * Offers the update flow on the logged-out screen too, since the normal surface lives in
     * Settings behind sign-in.
     */
    onCheckForUpdates: () -> Unit = {},
    updateChecking: Boolean = false,
    /**
     * Set once a check finds a newer build; the button then reads "Update available" and opens the
     * release page.
     */
    updateAvailableUrl: String? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    // Region picks which brands the picker offers (Canadian ones use a different backend,
    // CanadaApi); switching resets `brand`.
    var region by remember { mutableStateOf("US") }
    var brand by remember { mutableStateOf(Brand.HYUNDAI) }
    val scheme = MaterialTheme.colorScheme
    // LocalWindowInfo, not LocalConfiguration: containerSize is the app's window, correct in
    // multi-window.
    val shortScreen = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() < 520.dp
    }
    val context = LocalContext.current

    // Brand-specific copy
    val brandSubtitle = when (brand) {
        Brand.HYUNDAI -> "A better Bluelink · US"
        Brand.GENESIS -> "A better Genesis · US"
        Brand.KIA     -> "A better Kia Connect · US"
        Brand.HYUNDAI_CA -> "A better Bluelink · Canada"
        Brand.GENESIS_CA -> "A better Genesis Connect · Canada"
        Brand.KIA_CA -> "A better Kia Connect · Canada"
        Brand.HYUNDAI_EU -> "A better Bluelink · Europe"
    }
    val emailLabel = when (brand) {
        Brand.HYUNDAI, Brand.HYUNDAI_CA, Brand.HYUNDAI_EU -> "Bluelink email"
        Brand.GENESIS, Brand.GENESIS_CA -> "Genesis account email"
        Brand.KIA, Brand.KIA_CA -> "Kia Connect email"
    }

    // Animate the form in from below on first composition.
    var formVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { formVisible = true }

    if (onCancel != null) BackHandler { onCancel() }

    // Backs the StatusBarScrim call below with a REAL backdrop blur of the Aurora background --
    // same pattern GarageScreen.kt uses for its own two pagers. See StatusBarScrim's own doc for
    // why plain Modifier.blur never worked here.
    val hazeState = remember { HazeState() }
    Box(Modifier.fillMaxSize()) {
        // Same gate as LoadingScreen.
        if (LocalAppearance.current.auroraBackground) AuroraBackground(Modifier.matchParentSize().hazeSource(hazeState))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero: the app tile and wordmark, with the brand line beneath crossfading as it
            // changes.
            Row(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .statusBarsPadding()
                    .padding(horizontal = GapBlock)
                    .padding(top = if (shortScreen) 16.dp else 48.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GapGroup),
            ) {
                Box(
                    Modifier
                        .size(if (shortScreen) 52.dp else 68.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.DirectionsCar,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(if (shortScreen) 28.dp else 36.dp),
                    )
                }
                Column {
                    Text(
                        "Bloo",
                        style = if (shortScreen) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = scheme.onSurface,
                    )
                    AnimatedContent(
                        targetState = brandSubtitle,
                        transitionSpec = {
                            (fadeIn(tween(MotionMedium)) + slideInVertically(tween(MotionMedium)) { it / 3 }) togetherWith
                                (fadeOut(tween(MotionShort)) + slideOutVertically(tween(MotionShort)) { -it / 3 })
                        },
                        label = "loginSubtitle",
                    ) { subtitle ->
                        Text(subtitle, style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
                    }
                }
            }

            // The form, on one liquid-glass card, slides up on first composition.
            AnimatedVisibility(
                visible = formVisible,
                enter = slideInVertically(tween(MotionLong, easing = LinearOutSlowInEasing)) { it / 3 } +
                    fadeIn(tween(MotionLong)),
            ) {
                GlassSurface(
                    shape = ExtraLargeShape,
                    hazeState = hazeState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .padding(horizontal = GapSection)
                        .padding(bottom = GapBlock),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(GapPage),
                        verticalArrangement = Arrangement.spacedBy(GapGroup),
                    ) {
                        // Where the car lives, then which account: two selectors, one thought.
                        LabelText("Where is your account?")
                        MorphSegmented(
                            options = listOf(
                                SegmentOption("US", "United States", null),
                                SegmentOption("CA", "Canada", null),
                                SegmentOption("EU", "Europe", null),
                            ),
                            selectedKey = region,
                            onSelect = { key ->
                                region = key
                                brand = Brand.brandsForRegion(key).first()
                            },
                        )
                        val brandOptions = Brand.brandsForRegion(region)
                        MorphSegmented(
                            options = brandOptions.map { b -> SegmentOption(b.name, Brand.shortLabel(b), null) },
                            selectedKey = brand.name,
                            onSelect = { key -> brand = Brand.valueOf(key) },
                        )

                        BlooTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(emailLabel) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        BlooTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = { RevealToggle(showPassword, "password") { showPassword = !showPassword } },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        AnimatedVisibility(
                            visible = brand.requiresPin,
                            enter = expandEnterSized(Alignment.Bottom),
                            exit = expandExitSized(Alignment.Bottom),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                                PinField(
                                    value = pin,
                                    onValueChange = { pin = it },
                                    label = if (brand.pinRequiredToSignIn) "Service PIN" else "Service PIN (optional)",
                                    digitsOnly = false,
                                    revealed = showPin,
                                    colors = OutlinedTextFieldDefaults.colors(),
                                    trailingIcon = { RevealToggle(showPin, "service PIN") { showPin = !showPin } },
                                )
                                if (!brand.pinRequiredToSignIn) {
                                    BodySmallText("Only needed to run remote commands. Leave it blank if you never set one in the Hyundai app; you can add it later in Settings.")
                                }
                            }
                        }

                        SafeMorphTextButton(
                            text = "Sign in to ${brand.label}",
                            onClick = { onLogin(email, password, pin, brand) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading,
                            pending = loading,
                            emphasis = ButtonEmphasis.Confirm,
                        )

                        // Secondary actions share one row, the app's standard button group.
                        ActionRow {
                            SafeMorphTextButton(
                                text = "Forgot password?",
                                onClick = {
                                    val forgotUrl = when (brand) {
                                        Brand.HYUNDAI -> "https://owners.hyundaiusa.com/us/en/forgot-password"
                                        Brand.GENESIS -> "https://owners.genesis.com/us/en/forgot-password.html"
                                        Brand.KIA     -> "https://owners.kia.com/us/en/kia-owner-portal.html"
                                        Brand.HYUNDAI_CA -> "https://www.hyundaicanada.com/en/owners-section"
                                        Brand.GENESIS_CA -> "https://www.genesis.com/ca/en/support/contact-us.html"
                                        Brand.KIA_CA -> "https://www.kia.ca/en/owners"
                                        Brand.HYUNDAI_EU -> "https://www.hyundai.com/eu/en/owners.html"
                                    }
                                    context.startActivity(Intent(Intent.ACTION_VIEW, forgotUrl.toUri()))
                                },
                            )
                            if (onCancel != null) {
                                SafeMorphTextButton(text = "Cancel", onClick = onCancel, emphasis = ButtonEmphasis.Deny)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                            Icon(AppIcons.Lock, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            AnimatedContent(
                                targetState = brand.label,
                                transitionSpec = { fadeIn(tween(MotionShort)) togetherWith fadeOut(tween(MotionShort)) },
                                label = "privacyNote",
                            ) { label ->
                                Text(
                                    "Sent only to $label's servers; stored encrypted on this device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Update affordance, available WITHOUT signing in. Reads "Update available"
                        // and opens the release page once a check finds a build; otherwise it
                        // checks (or re-checks) on tap.
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            SafeMorphTextButton(
                                text = when {
                                    updateChecking -> "Checking…"
                                    updateAvailableUrl != null -> "Update available"
                                    else -> "Check for updates"
                                },
                                onClick = {
                                    val url = updateAvailableUrl
                                    if (url != null) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                    } else {
                                        onCheckForUpdates()
                                    }
                                },
                                icon = if (updateAvailableUrl != null) {
                                    Icons.AutoMirrored.Filled.OpenInNew
                                } else {
                                    Icons.Filled.SystemUpdate
                                },
                                enabled = !updateChecking,
                                pending = updateChecking,
                                contentColor = scheme.onSurfaceVariant,
                            )
                    }
                    }
                }
            }
        }
        // The hero wordmark has no top inset padding (deliberate), so it draws under the status
        // bar: same scrim as every other call site.
        StatusBarScrim(hazeState = hazeState)
    }
}
