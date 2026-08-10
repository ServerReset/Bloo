package com.bloo.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Pin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bloo.bluelink.data.Brand
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel

/**
 * The watch's own sign-in screen, shown (as the `SignedOut` top-level state)
 * when there's no phone-synced session to inherit. It holds
 * brand/email/password/PIN purely as local Compose state until "Sign in" is
 * tapped, then hands them wholesale to [WearViewModel.login] -- this composable
 * does no validation of its own, it is only a form.
 *
 * Kia is special-cased: its sign-in is a one-time code that can't practically
 * be typed on a watch keyboard, so that branch swaps the input fields for an
 * [InfoCallout] and hides the "Sign in" CTA entirely (showing it would only
 * invite a tap that always fails with blank credentials, contradicting the
 * "set up on your phone" guidance).
 *
 * While [WearUi.busy] is true this renders only a spinner and returns early,
 * replacing the whole form rather than overlaying it.
 */
@Composable
fun LoginScreen(vm: WearViewModel, ui: WearUi) {
    var brand by remember { mutableStateOf(Brand.HYUNDAI) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    // There's no on-watch physical keyboard, so every "field" here is really a
    // button that launches the system remote/voice/handwriting text-entry UI;
    // rememberWearTextInput streams the entered text back into these locals via
    // each lambda rather than binding an inline TextField.
    val emailInput = rememberWearTextInput("Email") { email = it }
    val passwordInput = rememberWearTextInput("Password") { password = it }
    val pinInput = rememberWearTextInput("PIN") { pin = it }

    if (ui.busy) {
        BusySpinner("Signing in…")
        return
    }

    // RotaryScreenScaffold owns the shared list state and suppresses the inherited
    // AppScaffold clock, which overlapped the centered "Sign in to Bloo" header.
    RotaryScreenScaffold(
        contentPadding = PaddingValues(
            horizontal = roundSafeHorizontalPadding(),
            vertical = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            // Header fades + slides in from the top.
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 4 },
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ListHeader { Text("Sign in to Bloo", textAlign = TextAlign.Center) }
                        Text(
                            "Pick your brand and enter your details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Brand selector (defaults to HYUNDAI).
            item {
                MorphSegmented(
                    options = listOf(
                        WearSegmentOption(Brand.HYUNDAI.name, "Hyundai"),
                        WearSegmentOption(Brand.GENESIS.name, "Genesis"),
                        WearSegmentOption(Brand.KIA.name, "Kia"),
                    ),
                    selectedKey = brand.name,
                    onSelect = { key -> brand = Brand.valueOf(key) },
                )
            }

            if (brand == Brand.KIA) {
                item {
                    InfoCallout(
                        "Kia sign-in uses a one-time code. Sign in on your phone and it syncs to the watch.",
                    )
                }
            } else {
                // Each field button: tap to launch the IME, shows the current value.
                item { FieldRow("Email", email, Icons.Filled.Email, emailInput) }
                item {
                    FieldRow(
                        "Password",
                        if (password.isBlank()) "" else "••••••",
                        Icons.Filled.Lock,
                        passwordInput,
                        masked = true,
                    )
                }
                item {
                    FieldRow(
                        "PIN",
                        if (pin.isBlank()) "" else "••••",
                        Icons.Filled.Pin,
                        pinInput,
                        masked = true,
                    )
                }
            }

            // Primary CTA -- hidden for Kia (it has no fields to fill above, so
            // there's nothing this could meaningfully submit; see the comment on
            // the KIA branch of the function doc).
            if (brand != Brand.KIA) {
                item {
                    MorphButton(
                        label = "Sign in",
                        icon = Icons.AutoMirrored.Filled.Login,
                        // Filled-primary at rest: this is the screen's one true
                        // CTA, so it should read as the emphasized action rather
                        // than sit level with the field rows above it.
                        active = true,
                        activeColor = MaterialTheme.colorScheme.primary,
                        pending = false,
                        onClick = { vm.login(brand, email, password, pin) },
                    )
                }
            }

            // "Set up on phone" handoff -- offered for EVERY brand (and for Kia
            // it's the only practical option). Sends a credential-free request to
            // the phone, which either pushes its existing session down or prompts
            // the user to sign in there; this watch auto-advances the moment the
            // session lands (see WearViewModel.requestSetupOnPhone + the
            // WearAuthEvents collector). It is additive -- the on-watch fields
            // above stay fully usable with no phone.
            item {
                MorphButton(
                    label = if (ui.setupBusy) "Continue on phone…" else "Set up on phone",
                    icon = Icons.Filled.PhoneAndroid,
                    active = false,
                    activeColor = MaterialTheme.colorScheme.tertiary,
                    pending = ui.setupBusy,
                    onClick = { vm.requestSetupOnPhone() },
                )
            }

            // Error line -- the item is always present so it fades/expands in and
            // shrinks/fades out in place, instead of popping the list below it up
            // and down when the message appears/disappears.
            item {
                AnimatedVisibility(
                    visible = ui.message != null,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                ) {
                    Text(
                        ui.message ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Closing tip.
            item {
                Text(
                    "Tip: open Bloo on your phone to set up without typing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

/**
 * One field row: an icon + label, whose value taps open the system keyboard.
 * [masked] is for the password/PIN fields, whose visible value is a literal
 * bullet string ("••••••"). MorphButton's label is just a Text node, so without
 * this override TalkBack would read that out glyph-by-glyph ("bullet, bullet,
 * …") instead of announcing anything meaningful -- so a masked row merges its
 * descendants and supplies its own contentDescription.
 */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    masked: Boolean = false,
) {
    Box(
        if (masked) {
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = if (value.isBlank()) label else "$label, entered"
            }
        } else {
            Modifier
        },
    ) {
        MorphButton(
            label = value.ifBlank { label },
            secondaryLabel = if (value.isNotBlank()) label else null,
            icon = icon,
            active = false,
            activeColor = MaterialTheme.colorScheme.primary,
            pending = false,
            onClick = onClick,
        )
    }
}

/** A subtle, centered info callout -- for non-blocking instructions. */
@Composable
private fun InfoCallout(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}
