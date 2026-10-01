package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Credentials
import com.bloo.uicommon.rememberConfirmArm

/**
 * The "Accounts" card: one panel per signed-in account (each brand has its own sign-in, and
 * Hyundai and Genesis can both be signed in), then the way to add another. Each panel carries
 * exactly what you can do to that account -- see the password, fix the service PIN, sign out --
 * and nothing about the others.
 */
@Composable
internal fun AccountsCardContent(state: UiState, vm: AppViewModel) {
    val accounts = state.accounts
    val status = when (accounts.size) {
        0 -> "No accounts"
        1 -> "1 account"
        else -> "${accounts.size} accounts"
    }
    SettingsCard("Accounts", Icons.Filled.Person, vm, status = status) {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            if (accounts.isEmpty()) {
                BodyMediumText("Not signed in", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            accounts.forEach { AccountPanel(it, vm) }
            SafeMorphTextButton(
                text = if (accounts.isEmpty()) "Sign in" else "Add another account",
                onClick = { vm.beginAddAccount() },
                icon = Icons.Filled.PersonAdd,
                emphasis = if (accounts.isEmpty()) ButtonEmphasis.Primary else ButtonEmphasis.Tonal,
            )
            if (accounts.any { it.brand.requiresPin }) {
                BodySmallText("Wrong-PIN attempts lock the service PIN for a few minutes. Fix it here if commands fail.")
            }
        }
    }
}

/** One signed-in account: who it is, its password and service PIN, and signing it out. */
@Composable
private fun AccountPanel(creds: Credentials, vm: AppViewModel) {
    val signOut = rememberConfirmArm()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(StandardShape)
            .background(glassTint(blurred = false))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        IconLeadRow(
            Icons.Filled.Person,
            tint = MaterialTheme.colorScheme.primary,
            title = creds.brand.label,
            subtitle = creds.email,
        )
        SecretRow("Password", creds.password)
        // Kia US has no service PIN; its commands are session-keyed.
        var pin by remember(creds.brand, creds.pin) { mutableStateOf(creds.pin) }
        if (creds.brand.requiresPin) {
            BlooTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("Service PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
            if (creds.brand.requiresPin && pin.isNotBlank() && pin != creds.pin) {
                SafeMorphTextButton("Update PIN", onClick = { vm.updatePin(creds.brand, pin) })
            }
            SafeMorphTextButton(
                text = if (signOut.armed) "Tap again to confirm" else "Sign out",
                onClick = { if (signOut.armed) vm.logout(creds.brand) else signOut.arm() },
                emphasis = ButtonEmphasis.Destructive,
            )
        }
    }
}
