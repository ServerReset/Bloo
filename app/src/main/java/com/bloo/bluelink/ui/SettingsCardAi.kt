@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.setAiEnabled

/** The "AI" card: one switch, and what it does. */
@Composable
internal fun AiCardContent(state: UiState, advanced: Boolean, vm: AppViewModel) {
    SettingsCard("AI", AppIcons.AutoAwesome, vm, status = if (state.aiEnabled) "On" else "Off") {
        ToggleRow(
            "On-device AI (Gemini Nano)",
            state.aiEnabled,
            description = "Adds an AI summary pebble and lets you ask the search box plain questions. Everything runs privately on your device.",
        ) { vm.setAiEnabled(it) }
    }
}

/** The "App shortcuts" card: which actions each car offers from the launcher icon's long-press menu. */
@Composable
internal fun AppShortcutsCardContent(state: UiState, vm: AppViewModel) {
    SettingsCard("App shortcuts", AppIcons.Bolt, vm) {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            BodySmallText("Quick actions on the launcher icon. Pick what each car offers.")
            state.vehicles.forEach { v ->
                SettingsGroup(v.name) {
                    com.bloo.bluelink.Shortcuts.ACTIONS.forEach { cmd ->
                        ToggleRow(
                            com.bloo.bluelink.Shortcuts.actionLabel(cmd),
                            state.isShortcutEnabled(v.vin, cmd),
                        ) { vm.setShortcutEnabled(v.vin, cmd, it) }
                    }
                }
            }
        }
    }
}
