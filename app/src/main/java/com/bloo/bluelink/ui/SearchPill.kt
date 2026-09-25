@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import com.bloo.uicommon.dropShadow
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * The search bar itself (SearchPill) and its suggestions list (SearchSuggestions).
 * Split out of SettingsSearch.kt to separate these from SearchLayer, which hosts
 * and calls both.
 */

/**
 * The pill itself: one Surface at whatever [width]/[height] [SearchLayer] has
 * animated it to, with a glow behind it and its content chosen by [form].
 *
 * Sized by the caller rather than by a width FRACTION of its own parent, which
 * is what it used to do. A fraction cannot express "a circle in that corner"
 * and "a bar across the bottom" as the same element, and it is the sameness
 * that makes the screen-to-screen morph possible at all.
 */
@Composable
internal fun SearchPill(
    query: String,
    focused: Boolean,
    form: SearchForm,
    width: Dp,
    height: Dp,
    compact: Boolean,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onDrag: ((Dp, Dp) -> Unit)?,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val expanded = focused || query.isNotEmpty()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Focus is DRIVEN by `focused`, both ways. It used to only ever be
    // requested, never released: dismissing the bar -- scrim tap, close
    // button, back -- collapsed the pill and left the keyboard standing over
    // it, because nothing ever told the field to let go. Clearing focus here
    // is safe in a way that listening for blur is not (see the note on the
    // text field's modifier): this reacts to the state that OWNS the bar,
    // not to a transient focus event that arrives before the field is ready.
    LaunchedEffect(focused) {
        if (focused) {
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Springs in on first appearance -- and because SearchLayer keys the
    // animations on the layout mode, "first appearance" includes arriving on
    // the cover screen. The ball lands in its corner rather than sliding to it.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    // DampingRatioMediumBouncy (0.5) on BOTH of these compounded badly: they multiply into
    // the same scaleX/scaleY below, so a press landing anywhere near the entrance pop (or
    // just the two overshoots being visually close together on a small, frequently-tapped
    // control) read as noticeably more bounce than either spring alone would suggest --
    // reported as "overly bouncy," and this is the same lesson the pebble bounce work
    // already paid for: 0.5 reads as a lot on a real device, repeatedly, not occasionally.
    // Entrance keeps some spring (it plays once, arriving) but now on the literal shared
    // PebbleBounceDamping/Stiffness tokens rather than its own separately-tuned numbers --
    // an "arrival" pop is the same kind of event a pebble opening is, so it gets the exact
    // same spring, not a lookalike. Press feedback drops nearly all bounce (PebbleCloseDamping,
    // i.e. no overshoot) and stays on its own fast StiffnessHigh -- a frequent, repeated
    // micro-interaction is exactly where extra bounce stops feeling playful and starts
    // feeling like noise, and it no longer compounds with the entrance spring above it.
    val entrance by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.55f,
        animationSpec = lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness),
        label = "searchEntrance",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = lowPowerAwareSpring(dampingRatio = PebbleCloseDamping, stiffness = Spring.StiffnessHigh),
        label = "searchPress",
    )
    // No ambient glow. This used to carry a travelling-hotspot bloom that
    // swept the pill's rim and breathed continuously the entire time the
    // search element was on screen, plus a separate fade-and-shrink once it
    // went idle. Reported as bad-looking and distracting, and it earned
    // that: a permanent light show on a control that is visible almost all
    // the time competes with everything the user is actually looking at. The
    // pill's affordance is now just its filled shape and border below --
    // legible without needing to move to prove it's there.
    Box(
        modifier.size(width, height).graphicsLayer {
            val k = pressScale * entrance
            scaleX = k
            scaleY = k
        }
            // Publishes wherever this ended up -- dragged to an edge, docked in a camera band,
            // or spanning the screen as a bar -- so other floating chrome can avoid it. Bounds
            // only: this deliberately does NOT use floatingOverlay, because the pull shift and
            // refresh fade are for chrome the page owns, and this one is placed by the person
            // using it. Attached after .size so the rect is the pill's real one.
            .floatingElement(FloatingIds.Search),
    ) {
        // canBlur/pillShape hoisted above the Surface call so both the fill colour
        // and the blur layer chained onto its modifier agree on the same values --
        // see the modifier chain below for why this needed its own explicit clip
        // rather than relying on Surface's own (which applies AFTER this whole
        // caller-supplied modifier, too late to bound a blur added inside it).
        val pillShape = RoundedCornerShape(50)
        val canBlur = hazeState != null && canBlurBackdrops()
        Surface(
            onClick = { if (!expanded) onFocusChange(true) },
            shape = pillShape,
            // glassTint (GlassChrome.kt): the one shared neutral fill every other
            // glass surface in the app uses, blurred or not exactly like every one
            // of them -- no more hardcoded `blurred = false`, no one-off compact/
            // non-compact alpha split. The Settings-screen collapsed pill (form ==
            // PILL && !expanded) used to get its own opaque tonal container
            // (secondaryContainer/onSecondaryContainer, no border) instead of this
            // fill -- reported as an inconsistent, different-looking search control
            // on that one screen; fixed by dropping that override in favour of the
            // one shared fill everywhere, the same direction this goes further in.
            color = glassTint(blurred = canBlur),
            contentColor = scheme.onSurface,
            // No tonalElevation: it is a per-frame spot shadow baked into this Surface's
            // layer, and the pill's whole layer is scaled by the entrance/press graphicsLayer
            // above, so the shadow re-rasterized on every frame of the pop. The border below
            // carries the depth; the shadow was a second, redundant depth cue on a control
            // that is animated constantly.
            tonalElevation = 0.dp,
            border = BorderStroke(
                if (expanded) 1.5.dp else 1.dp,
                // Static. This is an argument to Surface, i.e. COMPOSITION
                // scope -- it used to multiply in glowPulse, which meant every
                // 33ms tick of the glow clock recomposed this whole composable
                // and the text field inside it, thirty times a second, for the
                // entire time the app was open. The moving light belongs in
                // the drawBehind gradients above, where a tick invalidates
                // draw and nothing else; the rim just needs to be lit.
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
                // The cover screen gets neither the drop shadow nor the glass
                // rim. Both are tuned for a 52dp pill or a full-width bar; on a
                // 40dp circle they are a soft dark halo and a bright outline
                // stacked on a shape barely wider than the two of them, which
                // is what made this read as a smudge rather than a button. The
                // border below plus the glow behind carry it there.
                //
                // glassEdge, not a hand-chained dropShadow + glassRim: those two lines
                // ARE glassEdge (GlassChrome.kt) -- it is literally defined as that pair,
                // in that order -- and spelling them out here meant this pill silently
                // opted OUT of the theme-aware shadow weight glassEdge grew when the
                // "black shadow behind floating elements" report was finally tracked down
                // (see glassDropShadow's own doc: bare dropShadow() is 0.38-alpha black
                // with no light/dark gate, which on a light theme is the smudge in the
                // screenshots). One call now, so the next change to what a floating edge
                // looks like reaches this pill too instead of stopping one file short.
                .then(if (compact) Modifier else Modifier.glassEdge(pillShape))
                // appHazeEffect, clipped to pillShape explicitly -- this whole
                // modifier chain runs BEFORE Surface's own internal shape-clip
                // (Surface appends that itself, after everything the caller
                // passes in), so a blur added here without its own clip would
                // render as a soft-edged rectangle poking past the pill's actual
                // rounded/stadium outline instead of stopping at it.
                .then(if (canBlur) Modifier.clip(pillShape).appHazeEffect(hazeState!!) else Modifier)
                .then(
                    if (onDrag != null) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                            ) { change, amount ->
                                change.consume()
                                with(density) { onDrag(amount.x.toDp(), amount.y.toDp()) }
                            }
                        }
                    } else Modifier,
                ),
        ) {
            AnimatedContent(
                targetState = form to expanded,
                transitionSpec = {
                    // Cross-fade only, fast, and NOT delayed.
                    //
                    // This used to scale the content in from 0.9 after a 90ms
                    // hold. Both were wrong for what is happening around it:
                    // the container is already springing to a new size, so a
                    // second scale on the content inside it is two different
                    // rates of growth fighting over the same pixels, and the
                    // delay meant the shape arrived somewhere before its
                    // contents admitted they were moving. The old content
                    // leaving quickly and the new one arriving over the top,
                    // while the shape carries the motion, is the whole effect.
                    fadeIn(tween(140)) togetherWith fadeOut(tween(90))
                },
                label = "searchContentMorph",
            ) { (shape, isOpen) ->
                when {
                    isOpen -> Row(
                        Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
                        Spacer(Modifier.width(if (compact) 6.dp else 10.dp))
                        Box(Modifier.weight(1f)) {
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                                cursorBrush = SolidColor(scheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                // Submitting puts the keyboard away. The answer
                                // to what you just asked appears in the panel
                                // directly above this bar, which is exactly
                                // where the keyboard was covering.
                                keyboardActions = KeyboardActions(onSearch = { onSubmit(); keyboard?.hide() }),
                                // No auto-collapse on blur: onFocusChanged fires
                                // with isFocused = false the instant this field
                                // composes, before the requestFocus above lands,
                                // and that false positive used to close the bar in
                                // the same beat it opened.
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) {
                                        Text(
                                            if (compact) "Search" else "Search settings, commands & data",
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
                        Spacer(Modifier.width(10.dp))
                        Text("Search", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    }
                    // Closed BUBBLE: the glyph alone. contentDescription is on
                    // the icon rather than the label, since there isn't one.
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search",
                            // 18dp inside a 40dp circle left a ring of empty
                            // surface wider than the glyph; the button read as
                            // a blob with something small in it.
                            modifier = Modifier.size(if (compact) 21.dp else 22.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Example queries shown while the search bar is focused but empty --
 * without these there's no way to discover that search answers data
 * questions ("what's my odometer") and runs commands ("lock my car"), not
 * just finds settings by name.
 */
@Composable
internal fun SearchSuggestions(state: UiState, compact: Boolean = false, onPick: (String) -> Unit) {
    // Plain Surface chips, not MorphButton -- so unlike most taps in this app they
    // don't get a click() automatically and needed it wired in by hand.
    val haptics = LocalHaptics.current
    val carName = state.vehicles.firstOrNull()?.name
    // Short forms when the room is short -- on a cover screen with the keyboard
    // up, "odometer for Ioniq 5" wraps to two lines and pushes the next chip
    // off the panel, so a hint about what you can ask costs you the ability to
    // see what else you can ask. The long forms teach the syntax; the short
    // ones just have to fit and still work when tapped.
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
        // Floating directly over the aurora/scrolling content behind it with
        // nothing opaque underneath -- onSurfaceVariant (a deliberately muted
        // secondary-text tone) read as low-contrast there. Full-strength
        // onSurface instead.
        color = MaterialTheme.colorScheme.onSurface,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Same staggered pop as the search RESULT cards (staggeredResultVisible), reused
        // as-is: this list is just as much a "search UI" element as the results below it,
        // and giving one a cascade while the other snaps in flat is exactly the kind of
        // per-surface inconsistency that was reported.
        val examplesKey = examples.joinToString("|")
        examples.forEachIndexed { i, example ->
            PopVisible(visible = staggeredResultVisible(examplesKey, i)) {
                // Same MorphButton every selector chip in the app uses, with
                // the search screen's tonal fill kept as its standard colours.
                val exampleSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = exampleSource,
                    enabled = true,
                ) {
                    MorphButton(
                        onClick = { onPick(example) },
                        interactionSource = exampleSource,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        minHeight = 0.dp,
                        // Theme-weighted, not a bare dropShadow(). These chips float
                        // over the aurora with nothing opaque behind them (see the
                        // heading's own comment just above), so they do want a real
                        // shadow -- but dropShadow's default colour is 0.38-alpha
                        // black, and on a light theme that is the "black halo behind
                        // a floating pill" this app has now been reported for from
                        // three different surfaces. Same split, and the same reasoning,
                        // as glassDropShadow (GlassChrome.kt): unchanged in dark, a
                        // soft contact shadow in light.
                        modifier = Modifier.dropShadow(
                            RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = if (appIsDarkTheme()) 0.38f else 0.12f),
                            blurRadius = 8.dp,
                            offsetY = 3.dp,
                        ),
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
}
