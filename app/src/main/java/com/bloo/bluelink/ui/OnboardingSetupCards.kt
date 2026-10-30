package com.bloo.bluelink.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.content

// --- The reusable onboarding setup cards (used by the setup deck and Settings) ---

/**
 * One onboarding Setup card: icon, title and body on a solid surface (not the busy Aurora
 * backdrop).
 */
@Composable
internal fun OnboardingSetupCard(
    icon: ImageVector,
    title: String,
    body: String,
    done: Boolean,
    /**
     * Required to leave this card: a "Required" chip until [done], so the user knows why Next is
     * disabled rather than just finding it greyed out.
     */
    required: Boolean = false,
    /**
     * The action that gets it done. Shown only until [done]: a finished item says so and gets out
     * of the way.
     */
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            // GapSection, not 14.dp: this is the app's one inner-glass padding for a panel inside a
            // card (SettingsPrimitives names onboarding as a user of it), and a literal here meant
            // this card's inset did not match the alerts and seat panels beside it in the same deck.
            .onboardingPanel(GapSection)
            .animateContentSize(),
        // GapRow, not GapGroup: every other stack of rows inside a card in this app -- SettingsGroup,
        // the seat list -- spaces at GapRow.
        verticalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        IconLeadRow(
            if (done) AppIcons.CheckCircle else icon,
            tint = if (done) scheme.primary else scheme.onSurfaceVariant,
            title = title,
            subtitle = body,
            trailing = {
                when {
                    done -> StatusChip("On", scheme.primary)
                    required -> StatusChip("Required", scheme.tertiary)
                }
            },
        )
        if (!done) content()
    }
}

/**
 * A glassy inner panel: a translucent pane with the shared frosted rim, used for the setup and tip
 * sub-cards so they layer as glass over the card's own glass rather than reading as flat outline.
 */
@Composable
private fun Modifier.onboardingPanel(padding: Dp = GapGroup): Modifier = innerGlassPanel(padding)
