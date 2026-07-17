package com.bloo.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Pin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bloo.bluelink.data.Brand
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(vm: WearViewModel, ui: WearUi) {
    var brand by remember { mutableStateOf(Brand.HYUNDAI) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    val emailInput = rememberWearTextInput("Email") { email = it }
    val passwordInput = rememberWearTextInput("Password") { password = it }
    val pinInput = rememberWearTextInput("PIN") { pin = it }

    if (ui.busy) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(10.dp))
                Text(
                    "Signing in…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val state = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { e ->
                scope.launch { state.scrollBy(e.verticalScrollPixels) }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        state = state,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Title fades in from top
        item {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 4 },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
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

        // Brand selector
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
                    "Kia sign-in uses a one-time code — sign in on your phone and it syncs to the watch.",
                )
            }
        } else {
            // Each field button: tap to launch IME, shows current value
            item { FieldRow("Email", email, Icons.Filled.Email, emailInput) }
            item { FieldRow("Password", if (password.isBlank()) "" else "••••••", Icons.Filled.Lock, passwordInput) }
            item { FieldRow("PIN", if (pin.isBlank()) "" else "••••", Icons.Filled.Pin, pinInput) }
        }

        // Sign-in button -- Kia has no fields to fill in above (see the
        // InfoCallout), so there's nothing this button could meaningfully do
        // for that brand; showing it invited a tap that always fails with
        // blank credentials, contradicting the "sign in on your phone" message.
        if (brand != Brand.KIA) {
            item {
                MorphButton(
                    label = "Sign in",
                    icon = Icons.Filled.Login,
                    active = false,
                    activeColor = MaterialTheme.colorScheme.primary,
                    pending = false,
                    onClick = { vm.login(brand, email, password, pin) },
                )
            }
        }

        // Error message
        ui.message?.let { msg ->
            item {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Tip at the bottom
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

/** A field row: icon + label above + value, taps open the keyboard. */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
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

/** A subtle, centered info callout — used for non-blocking instructions. */
@Composable
private fun InfoCallout(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}
