@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import kotlinx.coroutines.flow.first
import kotlin.math.max
import androidx.core.net.toUri

/**
 * Sign-in form supporting every brand (US Hyundai/Genesis/Kia plus the three
 * Canada brands) from one screen. All fields
 * (email/password/pin/brand) are local `mutableStateOf` -- nothing is
 * persisted until [onLogin] fires, so switching brands mid-entry doesn't
 * lose the typed email/password. Selecting a brand via [MorphSegmented]
 * only changes copy/labels/validation shape shown here; brand-specific
 * strings (subtitle, email label, forgot-password URL, sign-in button
 * label) are recomputed from `brand` on every recomposition and each swap
 * cross-fades via [AnimatedContent] rather than snapping instantly.
 * The PIN field is only shown for brands that need one (`brand.requiresPin`
 * -- every brand except Kia US); Kia and Canada instead get a one-time-
 * passcode dialog elsewhere ([KiaOtpDialog]/[CanadaOtpDialog]) after
 * submitting -- Canada still shows the PIN field first since its commands
 * are PIN-gated even though sign-in itself goes through OTP. `formVisible`
 * flips true one
 * frame after first composition purely to trigger the initial slide-up-and-
 * fade-in entrance animation.
 */
@Composable
internal fun LoginScreen(
    loading: Boolean,
    onLogin: (String, String, String, Brand) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    // Region gates which 3 brands the segmented picker below offers, rather
    // than cramming all 6 US+Canada entries into one row -- Hyundai/Genesis/
    // Kia Canada run on a completely different backend (see CanadaApi) with
    // its own sign-in shape, so switching region also resets `brand` to that
    // region's first entry.
    var region by remember { mutableStateOf("US") }
    var brand by remember { mutableStateOf(Brand.HYUNDAI) }
    val scheme = MaterialTheme.colorScheme
    // LocalWindowInfo, not LocalConfiguration: the latter reports the whole SCREEN, which is
    // wrong in split-screen/multi-window (the app is shorter than the screen then, so the
    // "short screen" branch fired for a tall window and vice versa). containerSize is the
    // window the app is actually drawing into.
    val shortScreen = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() < 520.dp
    }
    val heroHeight = if (shortScreen) 96.dp else 160.dp
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

    // Backs the StatusBarScrim call below with a REAL backdrop blur of the Aurora
    // background -- same pattern GarageScreen.kt uses for its own two pagers. See
    // StatusBarScrim's own doc for why plain Modifier.blur never worked here.
    val hazeState = remember { HazeState() }
    Box(Modifier.fillMaxSize()) {
        // Same gate as LoadingScreen -- see its comment.
        if (LocalAppearance.current.auroraBackground) AuroraBackground(Modifier.matchParentSize().hazeSource(hazeState))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Wordmark hero — subtitle crossfades when the brand changes.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        "Bloo",
                        style = if (shortScreen) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    AnimatedContent(
                        targetState = brandSubtitle,
                        transitionSpec = {
                            (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 3 }) togetherWith
                                (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 3 })
                        },
                        label = "loginSubtitle",
                    ) { subtitle ->
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Form slides up from below on first composition.
            AnimatedVisibility(
                visible = formVisible,
                enter = slideInVertically(tween(420, easing = LinearOutSlowInEasing)) { it / 3 } +
                    fadeIn(tween(380)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(GapGroup),
                ) {
                    val fieldColors = borderlessFieldColors()

                    Text(
                        "Region",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                    MorphSegmented(
                        options = listOf(
                            SegmentOption("US", "United States", null),
                            SegmentOption("CA", "Canada", null),
                            SegmentOption("EU", "Europe", null),
                        ),
                        selectedKey = region,
                        onSelect = { key ->
                            region = key
                            // Reset to the region's first (only, for EU) brand,
                            // since each region's backend/sign-in shape differs.
                            brand = Brand.brandsForRegion(key).first()
                        },
                    )

                    Text(
                        "Sign in with",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                    // Brand.brandsForRegion is the single source for this list --
                    // a hand-written copy had silently stopped at the three US brands.
                    val brandOptions = Brand.brandsForRegion(region)
                    MorphSegmented(
                        options = brandOptions.map { b ->
                            SegmentOption(b.name, Brand.shortLabel(b), null)
                        },
                        selectedKey = brand.name,
                        onSelect = { key -> brand = Brand.valueOf(key) },
                    )

                    // Email field — label and placeholder animate with brand. Same
                    // fadeIn/fadeOut durations (220/160) as the sign-in button's own
                    // label and the privacy note below -- all three are driven by the
                    // same brand-selection change, so they should settle together.
                    AnimatedContent(
                        targetState = emailLabel,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                        },
                        label = "emailLabel",
                    ) { label ->
                        MutedText(label)
                    }
                    BlooTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text(emailLabel) },
                        singleLine = true,
                        colors = fieldColors,
                        leadingIcon = { Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    MutedText("Password")
                    BlooTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Password") },
                        singleLine = true,
                        colors = fieldColors,
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            MorphIconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // PIN — every brand except Kia US (Kia's own commands need
                    // no PIN at all; Canada still needs one for CanadaApi.pinAuth
                    // even though its sign-in also goes through OTP).
                    AnimatedVisibility(
                        visible = brand.requiresPin,
                        enter = expandEnterSized(Alignment.Bottom),
                        exit = expandExitSized(Alignment.Bottom),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
                            MutedText(if (brand.pinRequiredToSignIn) "Service PIN" else "Service PIN (optional)")
                            if (!brand.pinRequiredToSignIn) {
                                BodySmallText("Only needed to run remote commands. Leave it blank if you never set one in the Hyundai app; you can add it later in Settings.")
                            }
                            BlooTextField(
                                value = pin,
                                onValueChange = { pin = it },
                                placeholder = { Text("Service PIN") },
                                singleLine = true,
                                colors = fieldColors,
                                trailingIcon = {
                                    MorphIconButton(onClick = { showPin = !showPin }) {
                                        Icon(
                                            if (showPin) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (showPin) "Hide service PIN" else "Show service PIN",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Sign in CTA — label reflects the chosen brand.
                    SafeMorphTextButton(
                        text = "Sign in to ${brand.label}",
                        onClick = { onLogin(email, password, pin, brand) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        pending = loading,
                        emphasis = ButtonEmphasis.Primary,
                    )

                    if (onCancel != null) {
                        SafeMorphTextButton(
                            text = "Cancel",
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Forgot password — MorphTextButton that routes to the right brand portal.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                            contentColor = scheme.onSurfaceVariant,
                        )
                    }

                    AnimatedContent(
                        targetState = brand.label,
                        // Same duration as this form's other two brand-driven crossfades
                        // (the email label and the sign-in button label) -- see there.
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                        label = "privacyNote",
                    ) { label ->
                        Text(
                            "Sent only to $label's servers; stored encrypted on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // Nothing above scrolls under a system inset the way the garage/settings
        // scrollers do, but the hero wordmark's own Column has no top inset padding
        // (deliberate -- see its own layout above), so it draws right up under the
        // status bar just like they do. Same scrim, same cover-screen exclusion as
        // every other call site (StatusBarScrim itself excludes multi-window).
        if (!isCompactCoverScreen()) StatusBarScrim(hazeState = hazeState)
    }
}

/**
 * Kia sign-in verification: pick where the one-time code goes (email/text),
 * then enter it. Shown over the login form while a Kia OTP challenge is open.
 */
@Composable
internal fun KiaOtpDialog(otp: KiaOtpUi, loading: Boolean, vm: AppViewModel) {
    var code by remember(otp.sentTo) { mutableStateOf("") }
    // Standardized on the shared GlassAlertDialog shell (frosted card, 28dp
    // corners, stacked full-width buttons) instead of a raw M3 AlertDialog.
    GlassAlertDialog(
        onDismissRequest = { if (!loading) vm.kiaCancelOtp() },
        icon = Icons.Filled.Lock,
        title = if (otp.sentTo == null) "Verify it's you" else "Enter your code",
        text = {
            if (otp.sentTo == null) {
                Text("Kia needs a one-time code. Where should it go?")
                if (otp.challenge.hasEmail) {
                    SafeMorphTextButton(
                        text = "Email" + (otp.challenge.email?.let { " · $it" } ?: ""),
                        onClick = { vm.kiaSendOtp("EMAIL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                    )
                }
                if (otp.challenge.hasSms) {
                    SafeMorphTextButton(
                        text = "Text message" + (otp.challenge.sms?.let { " · $it" } ?: ""),
                        onClick = { vm.kiaSendOtp("SMS") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                    )
                }
            } else {
                Text(
                    if (otp.sentTo == "SMS") "We texted you a one-time code."
                    else "We emailed you a one-time code.",
                )
                OtpCodeField(code) { code = it }
            }
        },
        buttons = {
            // Verify shown only once a code's been sent; Cancel always. Stacked
            // full-width (primary on top) per the shell's convention.
            if (otp.sentTo != null) {
                SafeMorphTextButton(
                    text = "Verify",
                    onClick = { vm.kiaVerifyOtp(code) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading && code.isNotBlank(),
                    pending = loading,
                )
            }
            SafeMorphTextButton(
                "Cancel",
                vm::kiaCancelOtp,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            )
        },
    )
}

/**
 * Canada sign-in verification: unlike [KiaOtpDialog] there's no destination to
 * pick (email only), and the code is already sent by the time this shows
 * (see AppViewModel.loginCanada), so it goes straight to code entry.
 */
@Composable
internal fun CanadaOtpDialog(otp: CanadaOtpUi, loading: Boolean, vm: AppViewModel) {
    var code by remember(otp.challenge) { mutableStateOf("") }
    GlassAlertDialog(
        onDismissRequest = { if (!loading) vm.canadaCancelOtp() },
        icon = Icons.Filled.Lock,
        title = "Enter your code",
        text = {
            Text(
                "We emailed a one-time code" +
                    (otp.challenge.email?.let { " to $it" } ?: "") + " to verify this sign-in.",
            )
            OtpCodeField(code) { code = it }
        },
        buttons = {
            SafeMorphTextButton(
                text = "Verify",
                onClick = { vm.canadaVerifyOtp(code) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && code.isNotBlank(),
                pending = loading,
            )
            SafeMorphTextButton(
                "Cancel",
                vm::canadaCancelOtp,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            )
        },
    )
}

/**
 * The one-time-code entry field shared by [KiaOtpDialog] and [CanadaOtpDialog]. Both
 * hoist their own `code` state (the Verify button reads it), so this takes the value
 * and its setter rather than owning the buffer -- everything else (the "Code" label,
 * single line, number keyboard, [FieldShape] and full width) is identical.
 */
@Composable
internal fun OtpCodeField(code: String, onCodeChange: (String) -> Unit) {
    BlooTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text("Code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
