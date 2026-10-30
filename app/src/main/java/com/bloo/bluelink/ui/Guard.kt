package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.SecondaryTextStyle
import kotlin.math.max
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.MotionMedium
import com.bloo.uicommon.MotionLong
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapPage
import com.bloo.uicommon.GapBlock
import com.bloo.uicommon.GapHero
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.GroupWeightProportional
import com.bloo.uicommon.content
import com.bloo.uicommon.MorphButton

@Composable
internal fun LockBlurLayer(locked: Boolean, content: @Composable () -> Unit) {
    // Both directions SNAP, neither animates the radius.
    val lockBlur = remember { Animatable(0f) }
    LaunchedEffect(locked) { lockBlur.snapTo(if (locked) LOCK_BLUR_DP else 0f) }
    // The blur modifier is applied only while there IS one. Modifier.blur(0.dp) installs no
    // RenderEffect, but it still forces the whole app tree into its own graphicsLayer on every
    // frame -- for the entire life of the process, to serve a lock screen that is almost never up.
    Box(Modifier.fillMaxSize().then(if (lockBlur.value > 0f) Modifier.blur(lockBlur.value.dp) else Modifier)) {
        content()
    }
}

private const val LOCK_BLUR_DP = 22f

@Composable
internal fun LockAlphaOverlay(locked: Boolean, vm: AppViewModel, opaqueBackdrop: Boolean = false) {
    val lockAlpha = animateFloatAsState(
        targetValue = if (locked) 1f else 0f,
        animationSpec = tween(MotionLong),
        label = "lockAlpha",
    )
    // Composition only sees the on/off threshold; the per-frame fade is read in the layer block.
    val visible by remember { androidx.compose.runtime.derivedStateOf { lockAlpha.value > 0.01f } }
    if (visible) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = lockAlpha.value }) {
            LockOverlay(vm, opaqueBackdrop)
        }
    }
}

/**
 * The app lock, drawn as an overlay on top of the blurred app. The phone's own lock is the unlock
 * (fingerprint, face, PIN, pattern or password), so this screen is one card with one button. High-contrast
 * white-on-scrim text reads over any wallpaper of cars behind it; a floating back arrow returns to the login screen.
 */
@Composable
internal fun LockOverlay(vm: AppViewModel, opaqueBackdrop: Boolean = false) {
    val context = LocalContext.current
    val lockFg = MaterialTheme.colorScheme.onBackground
    val lockBg = MaterialTheme.colorScheme.background
    val haptics = LocalHaptics.current

    fun authenticateDevice() {
        context.findFragmentActivity()?.let { activity ->
            showBiometricPrompt(
                activity = activity,
                title = "Unlock Bloo",
                subtitle = "Confirm it's you to access your cars",
                onSuccess = { vm.unlocked() },
                onError = { },
            )
        }
    }
    LaunchedEffect(Unit) { authenticateDevice() }

    // The backdrop animates between fully opaque (the first frames of a cold start, before the
    // content underneath has settled -- see LockBlurLayer's own note) and the 45% scrim.
    val backdropAlpha by animateFloatAsState(
        targetValue = if (opaqueBackdrop) 1f else 0.30f,
        animationSpec = tween(MotionMedium),
        label = "lockBackdropAlpha",
    )
    Box(
        Modifier.fillMaxSize()
            // Darken the blur for legibility, and swallow taps to the app behind.
            .drawBehind { drawRect(lockBg.copy(alpha = backdropAlpha)) }
            .noRippleClickable {},
    ) {
        FloatingIcon(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = "Back to sign in",
            onClick = { haptics?.click(); vm.lockToLogin() },
            containerColor = lockFg.copy(alpha = 0.16f),
            contentColor = lockFg,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding(),
        )

        Column(
            Modifier.align(Alignment.Center).widthIn(max = 420.dp).padding(horizontal = GapHero, vertical = GapBlock),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The same liquid glass as the rest of the app, over the force-blurred app behind it: the blur
            // (LockBlurLayer) is what keeps data from leaking, the glass is what makes this one of us.
            GlassSurface(
                shape = ExtraLargeShape,
                modifier = Modifier.fillMaxWidth(),
                hazeState = LocalBackdropHaze.current,
            ) {
                Column(
                    Modifier.padding(horizontal = GapBlock, vertical = GapBlock),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LockMessage(Icons.Filled.Fingerprint, "Confirm it's you to reach your vehicles.", lockFg, GapPage)
                    Spacer(Modifier.height(28.dp))
                    val unlockSource = remember { MutableInteractionSource() }
                    MorphButton(
                        onClick = { authenticateDevice() },
                        modifier = Modifier.heightIn(min = ControlHeight),
                        interactionSource = unlockSource,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 18.dp),
                        expressive = true, fillOnPress = true, groupWeight = GroupWeightProportional,
                    ) {
                        // MorphButtonLabel, not a hand-rolled Icon+Spacer+Text -- the icon stays larger
                        // (24dp) than the standard 18dp, an intentional emphasis for the screen's one
                        // primary CTA.
                        MorphButtonLabel(Icons.Filled.Fingerprint, "Unlock", pending = false, iconSize = 24.dp)
                    }
                }
            }
        }
    }
}

/** The lock screen's icon, "Bloo is locked" and a line under it, in [fg]; [iconGap] sits between the icon and the title. */
@Composable
private fun LockMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, subtitle: String, fg: androidx.compose.ui.graphics.Color, iconGap: androidx.compose.ui.unit.Dp) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(72.dp), tint = fg)
    Spacer(Modifier.height(iconGap))
    RevealText("Bloo is locked", style = PrimaryTextStyle(), fontWeight = FontWeight.Bold, color = fg)
    Spacer(Modifier.height(GapHairline))
    RevealText(subtitle, style = SecondaryTextStyle(), color = fg.copy(alpha = 0.85f))
}
