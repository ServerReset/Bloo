package com.bloo.bluelink.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

/** The search bar itself (SearchPill) and its suggestions list (SearchSuggestions). */

/**
 * The pill: one Surface at the [width]/[height] [SearchLayer] animates it to, with its content
 * chosen by [form]. Caller-sized (not a width fraction) so circle-in-corner and bottom bar are the
 * same element and can morph.
 */
@Composable
internal fun SearchPill(
    query: String,
    focused: Boolean,
    form: SearchForm,
    width: Dp,
    height: Dp,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    onDrag: ((Dp, Dp) -> Unit)?,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    hazeState: HazeState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val expanded = focused || query.isNotEmpty()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Focus is driven by `focused` both ways: clearing it here releases the keyboard on dismiss,
    // which reacting to a blur event could not do safely (blur fires before the field is ready).
    LaunchedEffect(focused) {
        if (focused) {
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    val interaction = remember { MutableInteractionSource() }
    // No entrance scale: a graphicsLayer scale on this box re-rasterized the glass blur (and the
    // border) every frame of the spring, which read as jank. The container's own size/position
    // springs already carry the arrival.
    val latestDrag = androidx.compose.runtime.rememberUpdatedState(onDrag)
    val latestDragStart = androidx.compose.runtime.rememberUpdatedState(onDragStart)
    val latestDragEnd = androidx.compose.runtime.rememberUpdatedState(onDragEnd)
    Box(
        modifier.size(width, height),
    ) {
        // canBlur/pillShape are hoisted so the fill and the blur layer agree; the blur needs its
        // own explicit clip because Surface's clip applies after the caller's modifier.
        val pillShape = CircleShape
        val canBlur = hazeState != null && canBlurBackdrops()
        Surface(
            onClick = { if (!expanded) onFocusChange(true) },
            shape = pillShape,
            // glassTint (GlassChrome.kt): the shared neutral fill every glass surface uses.
            color = glassTint(blurred = canBlur),
            contentColor = scheme.onSurface,
            // No tonalElevation: its spot shadow re-rasterizes every frame under the entrance/press
            // graphicsLayer scale. The border carries the depth.
            tonalElevation = 0.dp,
            border = BorderStroke(
                if (expanded) 1.5.dp else 1.dp,
                // Static: this is composition scope, so multiplying in glowPulse would recompose
                // the whole pill and text field on every tick.
                Brush.verticalGradient(
                    listOf(
                        scheme.primary.copy(alpha = if (expanded) 0.65f else 0.4f),
                        scheme.primary.copy(alpha = 0.05f),
                    ),
                ),
            ),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxSize()
                // The search pill gets neither shadow nor glass rim: on a 40dp circle they read as
                // a smudge. glassEdge (GlassChrome.kt) is the dropShadow + glassRim pair with the
                // theme-aware shadow weight, so a change there reaches this pill too. shadow =
                // !expanded: a full-width shadow on the expanded bar darkens the whole backdrop.
                .glassEdge(pillShape, shadow = !expanded)
                // appHazeEffect clipped to pillShape explicitly: this chain runs before Surface's
                // own shape clip, so an unclipped blur would poke past the rounded outline.
                .then(if (canBlur && hazeState != null) Modifier.clip(pillShape).appGlassEffect(hazeState, pillShape) else Modifier)
                .then(
                    if (onDrag != null) {
                        // Callbacks read via rememberUpdatedState: pointerInput(key) keeps the
                        // installing composition's lambda, so direct reads see stale dock, position
                        // and form.
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { latestDragStart.value() },
                                onDragEnd = { latestDragEnd.value() },
                                onDragCancel = { latestDragEnd.value() },
                            ) { change, amount ->
                                change.consume()
                                with(density) { latestDrag.value?.invoke(amount.x.toDp(), amount.y.toDp()) }
                            }
                        }
                    } else Modifier,
                ),
        ) {
            AnimatedContent(
                targetState = form to expanded,
                transitionSpec = {
                    // Cross-fade only, fast, not delayed: the container's spring carries the
                    // motion, so a second scale or delay on the content would fight it.
                    fadeIn(tween(MotionFast)) togetherWith fadeOut(tween(90))
                },
                label = "searchContentMorph",
            ) { (shape, isOpen) ->
                when {
                    isOpen -> Row(
                        Modifier.fillMaxSize().padding(horizontal = GapSection),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(GapGroup))
                        Box(Modifier.weight(1f)) {
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                                cursorBrush = SolidColor(scheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                // Submitting puts the keyboard away so the answer panel above the
                                // bar is visible.
                                keyboardActions = KeyboardActions(onSearch = { onSubmit(); keyboard?.hide() }),
                                // No auto-collapse on blur: onFocusChanged fires isFocused = false
                                // as the field composes, before requestFocus lands, and would close
                                // the bar as it opens.
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) {
                                        Text(
                                            "Search settings, commands & data",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = scheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    inner()
                                },
                            )
                        }
                        MorphIconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onFocusChange(false) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = if (query.isNotEmpty()) "Clear" else "Close",
                            )
                        }
                    }
                    // Closed PILL (Settings): the icon plus the word, centred.
                    shape == SearchForm.PILL -> Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(GapGroup))
                        Text("Search", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    }
                    // Closed BUBBLE: the glyph alone; contentDescription is on the icon since there
                    // is no label.
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search",
                            // 18dp in a 40dp circle left an oversized empty ring.
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Example queries shown while the search bar is focused but empty, to show that search answers data
 * questions and runs commands, not just finds settings.
 */
@Composable
internal fun SearchSuggestions(state: UiState, compact: Boolean = false, onPick: (String) -> Unit) {
    // Plain Surface chips, not MorphButton, so click() is wired by hand.
    val haptics = LocalHaptics.current
    val carName = state.vehicles.firstOrNull()?.name
    // Short forms when room is short (keyboard up): a long hint would wrap and
    // push the next chip off the panel. Long forms teach the syntax.
    val examples = buildList {
        if (compact) {
            add("lock")
            add("unlock")
            add("start charging")
            add("climate")
            add("odometer")
            add("battery")
        } else {
            // Commands (actions)
            add("lock" + (carName?.let { " my $it" } ?: " my car"))
            add("start charging" + (carName?.let { " $it" } ?: ""))
            add("start climate")
            add("flash lights")
            // Data queries
            add("odometer" + (carName?.let { " for $it" } ?: ""))
            add("battery level")
            // Settings
            add("haptic feedback")
            if (state.vehicles.any { state.hasBattery(it) }) add("smart climate")
            add("text scale")
        }
    }
    Text(
        "Try commands, settings, or ask about your car",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        // Floats over the aurora with nothing opaque behind it; full-strength onSurface for
        // contrast.
        color = MaterialTheme.colorScheme.onSurface,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(GapRow), verticalArrangement = Arrangement.spacedBy(GapRow)) {
        // Same staggered pop as the search result cards (staggeredResultVisible).
        val examplesKey = examples.joinToString("|")
        examples.forEachIndexed { i, example ->
            PopVisible(visible = staggeredResultVisible(examplesKey, i)) {
                // Same MorphButton as every selector chip, with the search screen's tonal fill.
                val exampleSource = remember { MutableInteractionSource() }
                MorphButton(
                    onClick = { onPick(example) },
                    interactionSource = exampleSource,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = GapRow),
                    minHeight = 0.dp,
                    // Theme-weighted, not a bare dropShadow() (0.38-alpha black is a halo on light
                    // themes); same split as glassDropShadow (GlassChrome.kt).
                    modifier = Modifier.themedDropShadow(CircleShape, blurRadius = 8.dp, offsetY = 3.dp),
                    expressive = true,
                ) {
                    Text(
                        example,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
