package com.bloo.wear.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ListHeader { Text("Sign in to Bloo", textAlign = TextAlign.Center) } }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BrandChip("Hyundai", brand == Brand.HYUNDAI, Modifier.weight(1f)) { brand = Brand.HYUNDAI }
                BrandChip("Genesis", brand == Brand.GENESIS, Modifier.weight(1f)) { brand = Brand.GENESIS }
                BrandChip("Kia", brand == Brand.KIA, Modifier.weight(1f)) { brand = Brand.KIA }
            }
        }

        if (brand == Brand.KIA) {
            item {
                Text(
                    "Kia sign-in uses a one-time code — sign in on your phone and it syncs to the watch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            item { FieldButton("Email", email, emailInput) }
            item { FieldButton("Password", if (password.isBlank()) "" else "••••••", passwordInput) }
            item { FieldButton("PIN", if (pin.isBlank()) "" else "••••", pinInput) }
            item {
                Button(
                    onClick = { vm.login(brand, email, password, pin) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Sign in") },
                )
            }
        }

        ui.message?.let { msg ->
            item {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
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

@Composable
private fun BrandChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
    )
}

@Composable
private fun FieldButton(label: String, value: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(value.ifBlank { label }, maxLines = 1) },
        secondaryLabel = if (value.isNotBlank()) ({ Text(label, maxLines = 1) }) else null,
    )
}
