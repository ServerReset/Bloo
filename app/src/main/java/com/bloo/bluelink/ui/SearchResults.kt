@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/** Search results surface: the ranked settings/car-data result list, its stagger
 *  timing constant, and the per-result pop-in helper. Peeled out of
 *  SettingsSearch.kt into its own file. */

import android.app.StatusBarManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.degValue
import com.bloo.bluelink.data.TileCommandRunner
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.displayChargeLimit
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.degLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import com.bloo.uicommon.ReorderColumn
import com.bloo.uicommon.LocalReorderActive
import com.bloo.uicommon.coldStartIntroPlayed
import com.bloo.uicommon.animatePlacement

internal const val SEARCH_RESULT_STAGGER_MS = 35L

@Composable
internal fun staggeredResultVisible(resetKey: Any, index: Int): Boolean {
    var visible by remember(resetKey) { mutableStateOf(false) }
    LaunchedEffect(resetKey) {
        delay(index * SEARCH_RESULT_STAGGER_MS)
        visible = true
    }
    return visible
}

/**
 * Live search over both app settings and per-car data/fields. Tokenises the
 * query (dropping filler words like "for"/"the"), so "odometer for xyz" finds
 * the odometer of the car named xyz, and "plate" lists every car's plate.
 */
@Composable
internal fun SettingsSearchResults(
    query: String,
    submittedQuery: String,
    vm: AppViewModel,
    state: UiState,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
    /** Show at most this many, best first. See the call site: with a keyboard
     *  up there is no room for a long list, and ranking is what makes taking
     *  the top few the right answer rather than an arbitrary one. */
    limit: Int = Int.MAX_VALUE,
) {
    val tokens = query.lowercase().split(RxSearchTokens)
        .filter { it.isNotBlank() && it !in SearchStopwords }
    // Same source the main Settings screen uses for its own Security card gate.
    val canBio = remember { vm.canUseBiometrics() }

    val entries = ArrayList<SearchEntry>()
    fun add(title: String, keywords: String, content: @Composable () -> Unit) {
        entries.add(SearchEntry(title, "$title $keywords".lowercase(), content))
    }

    // --- App-wide settings ---
    // The dynamic half of the index: every plain toggle in ToggleSettings
    // renders itself here with no per-toggle code -- see that list's own doc
    // comment for what does and doesn't fit this shape.
    ToggleSettings.forEach { spec ->
        if (!spec.visible(state)) return@forEach
        add(spec.title, spec.keywords) {
            ToggleRow(spec.label, spec.checked(appearance, notif, state)) { spec.onToggle(vm, it) }
        }
    }
    // Two of Security's own controls, missing from here entirely -- "fingerprint"
    // and "lock" are exactly the words someone would type for this. Reproduces
    // the real card's logic verbatim (down to the same confirm-to-disable
    // biometric prompt, not a bare toggle) rather than a simplified stand-in,
    // since a security control is the one place a search shortcut skipping a
    // step the real row enforces would be a genuine regression, not just a
    // visual inconsistency.
    if (canBio) {
        val bioContext = LocalContext.current
        add("Require fingerprint to open", "biometric lock security app unlock") {
            SettingsSegmentedRow(
                label = "Require fingerprint to open",
                options = listOf(
                    SegmentOption("off", "Off", null),
                    SegmentOption("on", "On", null),
                ),
                selectedKey = if (appearance.biometricLock) "on" else "off",
                onSelect = { key ->
                    if (key == "on") {
                        bioContext.findFragmentActivity()?.let { activity ->
                            showBiometricPrompt(
                                activity = activity,
                                title = "Enable fingerprint lock",
                                subtitle = "Confirm to require it on launch",
                                onSuccess = { vm.setBiometricLock(true) },
                                onError = { },
                            )
                        }
                    } else {
                        val activity = bioContext.findFragmentActivity()
                        if (activity == null) {
                            vm.reportInfo("Couldn't verify it's you. The lock is still on.")
                        } else {
                            showBiometricPrompt(
                                activity = activity,
                                title = "Disable fingerprint lock",
                                subtitle = "Confirm to stop requiring it",
                                onSuccess = { vm.setBiometricLock(false) },
                                onError = { },
                            )
                        }
                    }
                },
            )
        }
        if (appearance.biometricLock) {
            add("Lock timing", "lock the app grace period timeout re-lock security") {
                SettingsSegmentedRow(
                    label = "Lock the app",
                    options = LockTiming.entries.map { t -> SegmentOption(t.name, t.label, null) },
                    selectedKey = appearance.lockTiming.name,
                    onSelect = { key -> runCatching { vm.setLockTiming(LockTiming.valueOf(key)) } },
                )
            }
        }
    }
    add("Text & layout scale", "display size zoom bigger") {
        var uiScaleDraft by remember(appearance.uiScale) { mutableFloatStateOf(appearance.uiScale) }
        StepRow("Scale", "${(uiScaleDraft * 100).roundToInt()}%")
        AnimatedSlider(
            value = uiScaleDraft,
            onValueChange = { uiScaleDraft = it },
            valueRange = 0.8f..1.3f,
            steps = 4,
            onValueSettled = { uiScaleDraft = (it * 10).roundToInt() / 10f; vm.setUiScaleSoon(uiScaleDraft) },
        )
    }
    add("Colour vibrancy", "color saturation vivid material you monochrome best buy tv") {
        // Deferred-commit, same as the main Appearance card's slider — see there.
        VibrancySlider(appearance, vm)
    }
    add("Search on the car screen", "search bubble car screen cover home garage ask command") {
        ToggleRow("Search on the car screen", appearance.showSearch) { vm.setShowSearch(it) }
    }
    // Unlike every other entry here, this ONE still needs a slice of the
    // Display card's own cross-navigation -- search is reachable from the
    // garage screen too, not just from inside Settings, so toggling this on
    // from a search result was a real, easy-to-hit way to trip the exact
    // "kicked out instead of moved to the right place" bug the Display card's
    // own toggle was fixed for: the preference flipped with no visible
    // navigation, and only the NEXT time Settings was reached did it turn up
    // somewhere unexpected. Turning ON always follows it there now, safe to
    // call from any screen: closeSettings(landOnSettingsPage = true) is a
    // harmless no-op navigation if already on the garage, and the pager's own
    // authoritative landing effect (Screens.kt) snaps onto the new Settings
    // slot regardless of whether this composition is fresh or already
    // mounted.
    //
    // Turning OFF used to be treated as a plain preference change, on the
    // theory that the pager's own drift-correction (LaunchedEffect(totalBlocks)
    // in GarageScreen) would "land back on a car gracefully once the slot
    // disappears" -- that's exactly the bug: reached from a search result
    // while genuinely parked on the embedded slot, nothing ever calls
    // openSettings(), so that drift-correction effect finds state.screen
    // still == Screen.Garage and snaps the pager to whatever car currentIndex
    // resolves to instead of navigating anywhere -- the "turning this off
    // takes you back to the first car, not the real Settings screen" bug.
    // The main Settings card's own copy of this toggle (see ToggleRow above
    // in this same file) already gets this right by checking `embedded`; this
    // one has no such parameter, so it checks state.onSettingsPageSlot
    // instead -- true exactly when the pager is currently settled on the
    // embedded slot, the same signal GarageScreen itself uses.
    add("Settings as a swipeable page", "gear button pager swipe car screen navigation") {
        ToggleRow("Settings as a swipeable page", appearance.settingsAsPage) { turningOn ->
            vm.setSettingsAsPage(turningOn)
            if (turningOn) {
                vm.closeSettings(landOnSettingsPage = true)
            } else if (state.onSettingsPageSlot) {
                vm.openSettings()
            }
        }
    }
    add("Units", "unit system metric imperial temperature distance speed miles km") {
        SettingsSegmentedRow(
            label = "Units",
            options = listOf(
                SegmentOption("imperial", "Imperial", null),
                SegmentOption("metric", "Metric", null),
            ),
            selectedKey = appearance.unitSystem,
            onSelect = { vm.setUnitSystem(it) },
        )
    }
    add("Font", "typeface atkinson hyperlegible google sans accessibility low vision") {
        val labels = mapOf(
            FontChoice.SYSTEM to "System default",
            FontChoice.ATKINSON to "Atkinson Hyperlegible",
            FontChoice.GOOGLE_SANS to "Google Sans",
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FontChoice.entries.forEach { choice ->
                ChoiceRow(labels.getValue(choice), appearance.fontChoice == choice) { vm.setFontChoice(choice) }
            }
        }
    }
    add("Display mode", "theme light dark amoled system appearance") {
        SettingsSegmentedRow(
            label = "Appearance",
            options = listOf(
                SegmentOption(ThemeMode.SYSTEM.name, "System", null),
                SegmentOption(ThemeMode.SYSTEM_AMOLED.name, "+AMOLED", null),
                SegmentOption(ThemeMode.LIGHT.name, "Light", null),
                SegmentOption(ThemeMode.DARK.name, "Dark", null),
                SegmentOption(ThemeMode.AMOLED.name, "AMOLED", null),
            ),
            selectedKey = appearance.themeMode.name,
            onSelect = { vm.setThemeMode(ThemeMode.valueOf(it)) },
        )
    }
    // --- Per-car ---
    state.vehicles.forEach { v ->
        val st = state.statusFor(v)
        val plate = state.licensePlates[v.vin] ?: ""
        add("License plate · ${v.name}", "plate licence registration ${v.name} $plate") {
            OutlinedTextField(
                value = plate,
                onValueChange = { vm.setLicensePlate(v.vin, it) },
                label = { Text("License plate") },
                singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
            )
        }
        parseOdometerMiles(v.odometer)?.let { odoInt ->
            add("Odometer · ${v.name}", "odometer mileage miles ${v.name}") { StatusRow("Odometer", formatDistance(odoInt, appearance.unitSystem == "metric")) }
        }
        add("VIN · ${v.name}", "vin identification ${v.name} ${v.vin}") {
            SelectionContainer { StatusRow("VIN", v.vin) }
        }
        // VehicleStatus.rangeMiFor -- already imported, and its body was copied here
        // character-for-character (battery-range-else-null ?: dte, then toInt). One source of
        // truth for "what range do we show for this powertrain".
        st?.rangeMiFor(state.hasBattery(v))?.let { r ->
            add("Range · ${v.name}", "range distance dte empty ${v.name}") { StatusRow("Range", formatDistance(r, appearance.unitSystem == "metric")) }
        }
        if (state.hasBattery(v)) {
            st?.evStatus?.batteryStatus?.let { b ->
                add("Battery · ${v.name}", "battery charge soc percent ${v.name}") { StatusRow("Battery", "$b%") }
            }
            // Current-plug target if plugged in, else the configured AC home limit --
            // now the shared EvStatus.displayChargeLimit(), this call site's own fallback
            // generalized so every surface agrees rather than re-deriving it.
            val limit = st?.evStatus?.displayChargeLimit()
            limit?.let { l -> add("Charge limit · ${v.name}", "charge limit target ${v.name}") { StatusRow("Charge limit", "$l%") } }
        } else {
            st?.fuelLevel?.let { f ->
                add("Fuel · ${v.name}", "fuel gas tank percent ${v.name}") { StatusRow("Fuel", "$f%") }
            }
        }
        rememberRelativeTime(state.fetchedAt(v))?.let { rel ->
            add("Last refreshed · ${v.name}", "updated refreshed time ${v.name}") { StatusRow("Last refreshed", rel) }
        }
        (state.placeNames[v.vin] ?: state.locations[v.vin]?.coordString(4))?.let { loc ->
            add("Location · ${v.name}", "location where place gps ${v.name}") { StatusRow("Location", loc) }
        }
        add("Powertrain · ${v.name}", "powertrain ev gas hybrid phev ${v.name}") {
            PowertrainPicker(current = state.powertrainOf(v)) { pt -> vm.setPowertrain(v, pt) }
        }
        // Same gate CarSettingsCard's own group uses -- nothing to confirm for
        // a vehicle where this picker would have no effect either way.
        if (v.platformOverridable) {
            add("Head-unit generation · ${v.name}", "gen5w ccnc platform generation trips ${v.name}") {
                PlatformPicker(current = state.platformOf(v)) { pt -> vm.setPlatform(v, pt) }
            }
        }
        add("Last service · ${v.name}", "service maintenance mileage ${v.name}") {
            MilesField(state.lastServiceMiles[v.vin], "Last service (mi)", Modifier.fillMaxWidth()) {
                vm.setLastServiceMiles(v.vin, it)
            }
        }
        // The interval, which had no search entry while "Last service" above did. The two
        // are only meaningful TOGETHER -- the service pebble's whole output is
        // `last + interval` -- so search let you set one half of a sum and hid the other,
        // leaving a "next due" figure that could not be corrected from here. Both fields
        // sit side by side in the per-car section; only the index had one of them.
        add("Service interval · ${v.name}", "service interval maintenance mileage due ${v.name}") {
            MilesField(state.serviceIntervalMiles[v.vin], "Interval (mi)", Modifier.fillMaxWidth()) {
                vm.setServiceIntervalMiles(v.vin, it)
            }
        }
        // The dynamic half of the per-car index: every seat heat/cool flag,
        // the steering wheel, and every hideable dashboard section for THIS
        // car, from VehicleToggleSettings -- no per-car, per-toggle code.
        VehicleToggleSettings.forEach { spec ->
            if (!spec.visible(v, state)) return@forEach
            add(spec.title(v), spec.keywords(v)) {
                ToggleRow(spec.label, spec.checked(v, state)) { spec.onToggle(vm, v, it) }
            }
        }
    }

    // Matches render FIRST (top of this composable's output), the AI answer
    // LAST -- this composable is placed above the floating search bar, so the
    // resulting stack top-to-bottom is [suggested results] [AI tile]
    // [search bar], matching the requested reading order bottom-up.
    // Ranked, not filtered. The fuzzy pass is a FALLBACK, only reached when the
    // strict one found nothing -- so a real match is never outranked by a
    // one-typo guess, and the cost of scanning every word of every entry is
    // only paid on a query that was going to show "no matches" otherwise.
    val results = if (tokens.isEmpty()) {
        entries
    } else {
        val strict = entries.mapNotNull { e -> searchScore(tokens, e, fuzzy = false)?.let { e to it } }
        val scored = strict.ifEmpty {
            entries.mapNotNull { e -> searchScore(tokens, e, fuzzy = true)?.let { e to it } }
        }
        scored.sortedByDescending { it.second }.map { it.first }
    }.let { if (it.size > limit) it.take(limit) else it }
    // Floating above busy/aurora content needs real separation -- a plain
    // default Card blends into whatever's behind it. Elevated container +
    // actual shadow (not just tonal elevation) so results clearly pop.
    val resultCardShape = RoundedCornerShape(16.dp)
    val resultCardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    // These float over busy/aurora content the same way the search bar and
    // "Try asking" panel above them do, but were left on plain tonal-
    // elevation Cards -- the one inconsistency in an otherwise unified
    // floating-chrome look within this exact panel.
    val resultCardModifier = Modifier.fillMaxWidth().dropShadow(resultCardShape, blurRadius = 10.dp, offsetY = 3.dp).frostedRim(resultCardShape)
    if (results.isEmpty()) {
        Card(resultCardModifier, shape = resultCardShape, colors = resultCardColors) {
            Text(
                "No matches for \"$query\"",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        // Restarts the stagger whenever the actual SET of results changes -- not on every
        // keystroke, which would re-pop a list that hasn't actually moved just because the
        // user is still typing the same word. Titles joined is cheap and exactly captures
        // "did the ranked list change," which is the only thing that should trigger this.
        val resultsKey = results.joinToString("|") { it.title }
        results.forEachIndexed { i, e ->
            PopVisible(visible = staggeredResultVisible(resultsKey, i)) {
                Card(resultCardModifier, shape = resultCardShape, colors = resultCardColors) {
                    Row(Modifier.padding(16.dp)) {
                        // A small icon badge per result, the same "leading circle" language
                        // the update pebble and settings hero stats use -- these cards used
                        // to open straight on bold text with nothing to distinguish a
                        // toggle-able setting from an informational readout at a glance.
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            e.content()
                        }
                    }
                }
            }
        }
    }

    // A recognised command ("lock my Ioniq", "start smart climate", "stop
    // charging") actually runs -- reuses TileCommandRunner, the same
    // execution path the Quick Settings tiles use, so this isn't a separate,
    // untested way of sending vehicle commands. If the query doesn't name a
    // specific car, this falls back to a single car (unambiguous) or asks
    // the user to be more specific (multiple cars, none named).
    //
    // Gated on submittedQuery, NOT the live query -- this actually sends a
    // command to the car, so it must only run once the user has deliberately
    // submitted (Enter/search key, or a suggestion tap), never mid-typing off
    // a debounce timer. Typing "lock my car" used to run the lock the moment
    // the debounce elapsed, whether or not that's what the user meant to do.
    val metricUnits = appearance.unitSystem == "metric"
    val command = remember(submittedQuery, metricUnits) {
        if (submittedQuery.isBlank()) null else parseVehicleCommand(submittedQuery, metricUnits)
    }
    if (command != null) {
        val ctx = LocalContext.current
        // Whole-word, longest-match car resolution -- NOT a bare substring test.
        // A plain `name in query` lets "Ioniq" match inside "lock my Ioniq 5",
        // so a command meant for the "Ioniq 5" would be sent to the "Ioniq"
        // (list-order-first). Instead require the name to appear as a bounded
        // token sequence, and when several names match prefer the longest. If
        // several still match at that longest length the query is genuinely
        // ambiguous, so refuse to dispatch and ask which car (targetVehicle
        // stays null → the "Which car?" branch below).
        val q = submittedQuery.lowercase()
        val nameMatches = state.vehicles.filter { v ->
            v.name.isNotBlank() &&
                Regex("\\b" + Regex.escape(v.name.lowercase()) + "\\b").containsMatchIn(q)
        }
        val longestMatchLen = nameMatches.maxOfOrNull { it.name.length }
        val namedVehicle = nameMatches.filter { it.name.length == longestMatchLen }.singleOrNull()
        // Only fall back to "the one car" when NO name matched at all; if a name
        // matched but was ambiguous, do not silently pick a car.
        val targetVehicle = namedVehicle ?: if (nameMatches.isEmpty()) state.vehicles.singleOrNull() else null
        var actionResult by remember(submittedQuery) { mutableStateOf<String?>(null) }
        var actionRunning by remember(submittedQuery) { mutableStateOf(false) }
        LaunchedEffect(submittedQuery) {
            if (targetVehicle != null) {
                actionRunning = true
                val result = runCatching { TileCommandRunner.run(ctx, targetVehicle.vin, command.cmd, command.climateTarget) }.getOrNull()
                actionResult = result?.message ?: "Command failed"
                actionRunning = false
                vm.refreshStatus(targetVehicle)
            }
        }
        Card(
            resultCardModifier,
            shape = resultCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Action", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    when {
                        targetVehicle == null -> {
                            val example = state.vehicles.firstOrNull()?.name ?: "car"
                            "Which car? Mention its name, e.g. \"${command.label} my $example\"."
                        }
                        actionRunning -> "${command.label} ${targetVehicle.name}…"
                        actionResult != null -> actionResult ?: "${command.label} ${targetVehicle.name}"
                        else -> "${command.label} ${targetVehicle.name}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    // Free-form command, via the AI, when the deterministic parser did not
    // recognise the phrasing. Data questions already go through askAi below --
    // this is the other half: making the car DO something described in words
    // the parser has no pattern for.
    //
    // It asks before it acts, and that is deliberate rather than timid. The
    // parser runs its commands immediately because a pattern it matched is a
    // phrasing someone wrote down on purpose; a model's reading of an
    // unanticipated sentence is a guess, and the cost of a wrong guess here is
    // a car unlocked on a street somewhere. One tap is a small price for the
    // difference between "the app did what I said" and "the app did what a
    // model thought I said". aiResolveCommand has already thrown out anything
    // that is not a real action on a real car of yours, so what this offers is
    // always executable -- the question is only whether it is what you meant.
    if (command == null && state.aiEnabled && submittedQuery.isNotBlank()) {
        val ctx = LocalContext.current
        var proposal by remember(submittedQuery) { mutableStateOf<Pair<String, String>?>(null) }
        var thinking by remember(submittedQuery) { mutableStateOf(true) }
        var ran by remember(submittedQuery) { mutableStateOf<String?>(null) }
        var running by remember(submittedQuery) { mutableStateOf(false) }
        LaunchedEffect(submittedQuery) {
            proposal = vm.aiResolveCommand(submittedQuery)
            thinking = false
        }
        val p = proposal
        if (p != null) {
            val car = state.vehicles.firstOrNull { it.vin == p.second }
            Card(
                resultCardModifier,
                shape = resultCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Did you mean?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        ran ?: "${aiCommandLabel(p.first)} ${car?.name ?: "your car"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (ran == null && car != null) {
                        val scope = rememberCoroutineScope()
                        // MorphTextButton, not a bare Button: this was the one plain
                        // Material button left in the app, so it was the only standard
                        // button that neither morphed on press nor fired the click
                        // haptic every other button gives. Its label also flips
                        // "Run it" -> "Working…", which is exactly the content-width
                        // spring MorphButton exists to animate.
                        //
                        // primary/onPrimary passed explicitly because they are what
                        // Material's Button defaulted to here. MorphTextButton's own
                        // default is the calmer buttonContainer(), and this is the
                        // card's primary action -- the conversion should change the
                        // FEEL, not quietly demote the emphasis.
                        val runSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = runSource,
                            enabled = !running,
                        ) {
                            MorphTextButton(
                                text = if (running) "Working…" else "Run it",
                                interactionSource = runSource,
                                onClick = {
                                    running = true
                                    scope.launch {
                                        val r = runCatching {
                                            TileCommandRunner.run(ctx, car.vin, p.first, "default")
                                        }.getOrNull()
                                        ran = r?.message ?: "Command failed"
                                        running = false
                                        vm.refreshStatus(car)
                                    }
                                },
                                enabled = !running,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }

    // On-device AI reply (when enabled): answer the question in natural
    // language -- a fallback/complement for questions with no structured
    // match above, or a plain-language gloss when there is one.
    //
    // Gated on submittedQuery, not the live query -- this fires a real AI
    // request (network/compute cost, and it used to visibly show "Thinking…"
    // while the user was still mid-word), so it must wait for a deliberate
    // submit rather than firing on every keystroke's debounce.
    if (state.aiEnabled) {
        LaunchedEffect(submittedQuery) {
            if (submittedQuery.isNotBlank()) {
                vm.askAi(submittedQuery)
            } else {
                vm.clearAiReply()
            }
        }
        val thinking = "search" in state.aiBusy
        val reply = state.aiSearchReply
        AnimatedVisibility(
            visible = thinking || reply != null,
            enter = collapseEnter(Alignment.Bottom),
            exit = collapseExit(Alignment.Bottom),
        ) {
            Card(
                resultCardModifier,
                shape = resultCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI answer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    if (reply != null) {
                        Text(reply, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
