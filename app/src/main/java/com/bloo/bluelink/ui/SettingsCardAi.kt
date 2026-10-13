package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.Composable
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.setAiEnabled

/** The "AI" card: one switch, and what it does. */
@Composable
internal fun AiCardContent(state: UiState, vm: AppViewModel) {
    SettingsCard("AI", AppIcons.AutoAwesome, vm, status = if (state.aiEnabled) "On" else "Off") {
        ToggleRow(
            "On-device AI (Gemini Nano)",
            state.aiEnabled,
            description = if (state.aiEnabled) {
                "On: adds the AI summary pebble and lets you ask the search box plain questions. It all runs privately on your device."
            } else {
                "Off: no AI summary or questions. Turn on to use Gemini Nano on this device."
            },
        ) { vm.setAiEnabled(it) }
        // Only once the AI is on: the automatic run is an option OF it, and off until chosen.
        PopVisible(visible = state.aiEnabled) {
            ToggleRow(
                "Summarize when I open the app",
                state.aiAutoSummarize,
                description = if (state.aiAutoSummarize) {
                    "On: writes a fresh status summary for each car whenever the app opens or refreshes."
                } else {
                    "Off: summaries appear only when you ask for one."
                },
            ) { vm.setAiAutoSummarize(it) }
        }
    }
}

/**
 * The "App shortcuts" card: which actions each car offers from the launcher icon's long-press menu.
 */
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
