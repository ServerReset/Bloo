@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package com.bloo.bluelink.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.R
import coil.compose.AsyncImage
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BlooApp(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let {
            scope.launch { snackbar.showSnackbar(it) }
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                // Themed, rounded "toast" — used for errors/notices.
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (state.screen) {
                Screen.Login -> LoginScreen(state.loading, vm::login)
                Screen.Locked -> LockScreen(vm)
                Screen.Empty -> EmptyScreen(vm)
                Screen.Garage -> GarageScreen(state, vm)
                Screen.Settings -> SettingsScreen(vm)
            }
        }
    }

    if (state.showOnboarding && state.screen == Screen.Garage) {
        OnboardingDialog(
            onOpenSettings = { vm.dismissOnboarding(openSettings = true) },
            onDismiss = { vm.dismissOnboarding(openSettings = false) },
        )
    }
}

/** First-run nudge: configure each car's features and (optionally) add a photo. */
@Composable
private fun OnboardingDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
        title = { Text("Set up your cars") },
        text = {
            Text(
                "Hyundai's API doesn't report everything about your car, so head to " +
                    "Settings to tell Bloo what each car has — its powertrain (gas, hybrid, " +
                    "PHEV or EV) and which seats can heat/cool. While you're there, you can " +
                    "add a photo of your car to replace the default gradient.",
            )
        },
        confirmButton = { Button(onClick = onOpenSettings) { Text("Open settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

// --- Login ----------------------------------------------------------------

@Composable
private fun LoginScreen(loading: Boolean, onLogin: (String, String, String, Brand) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf(Brand.HYUNDAI) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Bloo", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
        Text(
            "A better Blue Link · US",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))

        Text("Brand", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Brand.entries.forEach { b ->
                FilterChip(
                    selected = brand == b,
                    onClick = { brand = b },
                    label = { Text(b.label) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Blue Link email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("Service PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onLogin(email, password, pin, brand) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) LoadingIndicator() else Text("Sign in")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Credentials are sent directly to ${brand.label}'s telematics servers and " +
                "stored encrypted on this device.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// --- Lock -----------------------------------------------------------------

@Composable
private fun LockScreen(vm: AppViewModel) {
    val context = LocalContext.current
    fun authenticate() {
        context.findFragmentActivity()?.let { activity ->
            showBiometricPrompt(
                activity = activity,
                title = "Unlock Bloo",
                subtitle = "Confirm it's you to access your vehicles",
                onSuccess = { vm.unlocked() },
                onError = { },
            )
        }
    }
    LaunchedEffect(Unit) { authenticate() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("Bloo is locked", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { authenticate() }) { Text("Unlock") }
    }
}

// --- Empty ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Bloo") },
            actions = {
                IconButton(onClick = { vm.openSettings() }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
                IconButton(onClick = { vm.loadGarage() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
            },
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No enrolled vehicles found on this account.")
        }
    }
}

// --- Garage (main) --------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GarageScreen(state: UiState, vm: AppViewModel) {
    val vehicles = state.vehicles
    if (vehicles.isEmpty()) return

    val widthDp = LocalConfiguration.current.screenWidthDp
    val large = widthDp >= 600
    // A single car on a large screen always fills the screen (no card view —
    // there's nothing to switch between).
    val singleLarge = large && vehicles.size == 1
    val current = state.currentIndex.coerceIn(0, vehicles.lastIndex)
    val expanded = if (singleLarge) 0 else state.expandedIndex?.takeIf { it in vehicles.indices }
    // "Garage" title only when showing multiple collapsed cars.
    val isGrid = large && !singleLarge && expanded == null && vehicles.size > 1
    val actionTarget = vehicles[(expanded ?: current).coerceIn(0, vehicles.lastIndex)]

    // On large screens, back collapses an expanded car to the grid.
    BackHandler(enabled = expanded != null && !singleLarge) { vm.collapse() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (isGrid) {
                    Text("Garage")
                } else {
                    Column {
                        Text(actionTarget.name, fontWeight = FontWeight.Bold)
                        Text(
                            "${actionTarget.model} · ${state.powertrainLabel(actionTarget)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            navigationIcon = {
                if (expanded != null && !singleLarge) {
                    IconButton(onClick = { vm.collapse() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back to garage")
                    }
                }
            },
            actions = {
                IconButton(onClick = { vm.openSettings() }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            },
        )

        if (singleLarge) {
            Box(Modifier.weight(1f)) { DualColumn(vehicles[0], state, vm) }
        } else if (large) {
            AnimatedContent(
                targetState = expanded,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val spec = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    (fadeIn(spec) + scaleIn(spec, initialScale = 0.92f)) togetherWith
                        (fadeOut(spec) + scaleOut(spec, targetScale = 0.92f))
                },
                label = "expandCollapse",
            ) { exp ->
                if (exp != null && exp in vehicles.indices) {
                    LargeExpandedPager(vehicles, exp, state, vm)
                } else {
                    LargeCollapsedRow(vehicles, state, vm)
                }
            }
        } else {
            // Phones / cover screens: an endless swipe carousel.
            val count = vehicles.size
            val loop = count > 1
            val pageCount = if (loop) Int.MAX_VALUE else 1
            val initial = remember(count) {
                if (!loop) 0 else {
                    val mid = Int.MAX_VALUE / 2
                    mid - (mid % count) + current
                }
            }
            val pager = rememberPagerState(initialPage = initial) { pageCount }
            LaunchedEffect(pager, count) {
                snapshotFlow { pager.settledPage }.collect { page ->
                    vm.selectIndex(if (loop) page.mod(count) else page)
                }
            }
            Column(Modifier.fillMaxSize()) {
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    val v = vehicles[if (loop) page.mod(count) else page]
                    VehicleDetailContent(v, state, vm)
                }
                if (loop) PagerDots(pager.currentPage.mod(count), count)
            }
        }
    }
}

/** Large + collapsed: every car's full data, in vertical feeds shown side by side. */
@Composable
private fun LargeCollapsedRow(vehicles: List<Vehicle>, state: UiState, vm: AppViewModel) {
    Row(
        Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        vehicles.forEachIndexed { i, v ->
            Column(Modifier.width(400.dp).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${v.model} · ${state.powertrainLabel(v)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.expand(i) }) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = "Expand")
                    }
                }
                Box(Modifier.weight(1f)) { VehicleDetailContent(v, state, vm) }
            }
        }
    }
}

/** Large + expanded: one car filling the screen; swipe left/right to switch. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LargeExpandedPager(vehicles: List<Vehicle>, startIndex: Int, state: UiState, vm: AppViewModel) {
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, vehicles.lastIndex)) { vehicles.size }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { vm.expand(it) }
    }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        DualColumn(vehicles[page], state, vm)
    }
}

/** Wide layout: controls on the left, diagnostics on the right. */
@Composable
private fun DualColumn(v: Vehicle, state: UiState, vm: AppViewModel) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1.4f).fillMaxHeight()) {
            VehicleDetailContent(v, state, vm, showDiagnostics = false)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DiagnosticsPebble(v, state.statusFor(v), state, vm)
        }
    }
}

@Composable
private fun PagerDots(current: Int, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            val selected = i == current
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (selected) 10.dp else 7.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    )
            )
        }
    }
}

// --- Hero header + charge/fuel bar ---------------------------------------

@Composable
private fun HeroHeader(
    v: Vehicle,
    status: VehicleStatus?,
    imageUrl: String?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    height: Dp = 150.dp,
) {
    val charging = hasBattery && status?.evStatus?.batteryCharge == true
    val corner by animateDpAsState(
        targetValue = if (charging) 40.dp else 24.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "heroCorner",
    )
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(corner)) {
        Column(Modifier.padding(16.dp)) {
            HeroVisual(v, imageUrl, height)
            Spacer(Modifier.height(16.dp))
            ChargeFuelBar(status, hasBattery, hasFuel)
        }
    }
}

/** Default = a clean brand gradient. If the user set a photo, show that instead. */
@Composable
private fun HeroVisual(v: Vehicle, imageUrl: String?, height: Dp) {
    if (imageUrl.isNullOrBlank()) {
        val scheme = MaterialTheme.colorScheme
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(scheme.primary, scheme.tertiary, scheme.secondary),
                    )
                ),
        )
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = v.model,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(18.dp)),
        )
    }
}

@Composable
private fun ChargeFuelBar(status: VehicleStatus?, hasBattery: Boolean, hasFuel: Boolean) {
    // Primary metric: battery if the car has one, else fuel. Plug-in hybrids show
    // both — battery as the headline and fuel as a secondary line.
    val battPct = status?.evStatus?.batteryStatus
    val fuelPct = status?.fuelLevel
    val pct = if (hasBattery) battPct else fuelPct
    val frac = ((pct ?: 0).coerceIn(0, 100)) / 100f
    val battRange = status?.evStatus?.drvDistance?.firstOrNull()
        ?.rangeByFuel?.totalAvailableRange?.value
    val range = (if (hasBattery) battRange else null) ?: status?.dte?.value
    val charging = hasBattery && status?.evStatus?.batteryCharge == true

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            // Roll the headline number when it changes.
            AnimatedContent(
                targetState = pct,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 2 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 2 })
                },
                label = "pctRoll",
            ) { p ->
                Text(
                    p?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    range?.let { "${it.toInt()} mi" } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        charging && pct != null -> "Charging · $pct%"
                        charging -> "Charging"
                        hasBattery -> "Battery · range"
                        else -> "Fuel · range"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (charging) ChargeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (charging) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        // Plug-in hybrid: surface the fuel tank too.
        if (hasBattery && hasFuel && fuelPct != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalGasStation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Fuel $fuelPct%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Expressive motion: the fill springs in with a gentle overshoot.
        val animatedFrac by animateFloatAsState(
            targetValue = frac,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            label = "chargeFill",
        )
        // Target-SOC marker: dot at the AC/DC limit for the active plug type.
        val ev = status?.evStatus
        val targetPct = if (charging && ev != null) {
            when (ev.batteryPlugin) {
                1 -> ev.reservChargeInfos?.level(0) // DC fast
                2 -> ev.reservChargeInfos?.level(1) // AC
                else -> null
            }
        } else {
            null
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            LinearProgressIndicator(
                progress = { animatedFrac },
                color = ChargeGreen,
                trackColor = ChargeGreen.copy(alpha = 0.22f),
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)),
            )
            if (targetPct != null) {
                val x = maxWidth * (targetPct.coerceIn(0, 100) / 100f)
                Box(
                    Modifier
                        .offset(x = x - 9.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface)
                        .align(Alignment.CenterStart),
                )
            }
        }
        if (charging) {
            val minutes = status?.evStatus?.remainTime2?.atc?.value?.toInt()
            val plug = when (status?.evStatus?.batteryPlugin) {
                1 -> "DC fast"
                2 -> "AC"
                else -> null
            }
            val parts = buildList {
                if (minutes != null && minutes > 0) {
                    add(if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m to full" else "$minutes min to full")
                }
                plug?.let { add("$it charger") }
            }
            if (parts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChargeGreen,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private val ChargeGreen = Color(0xFF2EBD59)

/**
 * A [Slider] whose thumb springs to its target with a gentle bounce when the
 * value changes by tap or snap, while tracking the finger directly during an
 * active drag (so dragging still feels immediate).
 */
@Composable
private fun AnimatedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    colors: androidx.compose.material3.SliderColors = SliderDefaults.colors(),
) {
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val animated by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "sliderValue",
    )
    Slider(
        value = if (dragging) value else animated,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = colors,
        interactionSource = interaction,
    )
}

// --- Full detail ----------------------------------------------------------

@Composable
private fun VehicleDetailContent(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    showDiagnostics: Boolean = true,
) {
    val status = state.statusFor(v)
    val seats = state.seatConfigFor(v)
    val enabled = !state.loading

    val ptrState = rememberPullToRefreshState()
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = state.refreshing,
                state = ptrState,
                onRefresh = { vm.refreshStatus(v) },
            ),
    ) {
        // Content slides down as you pull / while refreshing, then springs back.
        val maxShift = 72.dp
        val shift = if (state.refreshing) maxShift else (maxShift * ptrState.distanceFraction).coerceIn(0.dp, maxShift)
        Column(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, with(density) { shift.roundToPx() }) }
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Fixed top: car image + gauge, then the primary actions.
            HeroHeader(v, status, state.imageUrls[v.vin], state.hasBattery(v), state.hasFuel(v))
            PrimaryActions(v, state, vm)
            // Reorderable sections (pebbles) — order + open/closed set per car.
            state.sectionsFor(v).forEach { section ->
                when (section) {
                    "climate" -> ClimatePebble(v, status, seats, state, vm)
                    "charge" -> if (state.hasBattery(v)) ChargePebble(v, status, enabled, state, vm)
                    "location" -> LocationPebble(v, state, vm)
                    "information" -> InformationPebble(v, status, state, vm)
                    "diagnostics" -> if (showDiagnostics) DiagnosticsPebble(v, status, state, vm)
                }
            }
        }
        // Expressive squiggly refresh indicator.
        PullToRefreshDefaults.LoadingIndicator(
            state = ptrState,
            isRefreshing = state.refreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun PrimaryActions(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StateControl(
            name = "Doors",
            isOn = status?.doorLock,
            stateOn = "Locked", stateOff = "Unlocked",
            turnOn = "Lock", turnOff = "Unlock",
            icon = Icons.Filled.Lock, pending = state.isPending(v.vin, "doors"),
            onActivate = { vm.lock(v) }, onDeactivate = { vm.unlock(v) },
        )
        if (state.hasBattery(v)) {
            StateControl(
                name = "Charging",
                isOn = status?.evStatus?.batteryCharge,
                stateOn = "Charging", stateOff = "Idle",
                turnOn = "Start", turnOff = "Stop",
                icon = Icons.Filled.Bolt, pending = state.isPending(v.vin, "charge"),
                onActivate = { vm.startCharge(v) }, onDeactivate = { vm.stopCharge(v) },
            )
        }
    }
}

/**
 * A chunky stateful control: shows the current On/Off state and a button
 * offering the *opposite* action. The button morphs from a pill (deactivated)
 * to a rounded square (activated).
 */
@Composable
private fun StateControl(
    name: String,
    isOn: Boolean?,
    stateOn: String,
    stateOff: String,
    turnOn: String,
    turnOff: String,
    icon: ImageVector,
    pending: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
) {
    val active = isOn == true
    val corner by animateDpAsState(
        targetValue = if (active) 18.dp else 32.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ctrlCorner",
    )
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                when {
                    pending -> "Sending…"
                    isOn == true -> stateOn
                    isOn == false -> stateOff
                    else -> "Unknown"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) ChargeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
        }
        val onClick = { if (active) onDeactivate() else onActivate() }
        val shape = RoundedCornerShape(corner)
        val content: @Composable RowScope.() -> Unit = {
            if (pending) {
                LoadingIndicator(Modifier.size(22.dp))
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (active) turnOff else turnOn, fontWeight = FontWeight.SemiBold)
            }
        }
        if (active) {
            Button(onClick = onClick, enabled = !pending, shape = shape, modifier = Modifier.height(60.dp), content = content)
        } else {
            FilledTonalButton(onClick = onClick, enabled = !pending, shape = shape, modifier = Modifier.height(60.dp), content = content)
        }
    }
}

// --- Pebble (expandable, reorderable section) -----------------------------

/**
 * A collapsible "pebble" — a titled section that springs open/closed with a
 * playful bounce. Open/closed state lives in the ViewModel (per car + section),
 * and the section order is user-configurable in Settings.
 */
@Composable
private fun Pebble(
    v: Vehicle,
    section: String,
    title: String,
    icon: ImageVector,
    state: UiState,
    vm: AppViewModel,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expanded = state.isPebbleExpanded(v.vin, section)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pebbleChevron",
    )
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.togglePebble(v, section) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotation),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                ) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
            ) {
                Column(
                    Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
        }
    }
}

// --- Information ----------------------------------------------------------

@Composable
private fun InformationPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel) {
    val location = state.locations[v.vin]
    Pebble(v, "information", "Information", Icons.Filled.Info, state, vm) {
        when {
            status == null && state.refreshing -> Text("Fetching live status…")
            status == null -> Text("No status yet. Pull down to refresh.")
            else -> {
                StatusRow("Doors locked", if (status.doorLock == true) "Yes" else "No")
                status.doorOpen?.let { StatusRow("A door open", if (it.anyOpen) "Yes" else "No") }
                status.trunkOpen?.let { StatusRow("Trunk", if (it) "Open" else "Closed") }
                status.hoodOpen?.let { StatusRow("Hood", if (it) "Open" else "Closed") }
                StatusRow("Climate", if (status.airCtrlOn == true) "On" else "Off")
                status.engine?.let { StatusRow("Engine", if (it) "Running" else "Off") }
                status.battery?.batSoc?.let { StatusRow("12V battery", "$it%") }
                v.odometer?.takeIf { it.isNotBlank() }?.let { StatusRow("Odometer", "$it mi") }
                status.dateTime?.let { StatusRow("Updated", it) }
            }
        }
        location?.let {
            StatusRow("Coordinates", String.format("%.5f, %.5f", it.latitude, it.longitude))
        }
    }
}

// --- Diagnostics ----------------------------------------------------------

private data class DiagRow(val label: String, val value: String, val indent: Boolean = false)

@Composable
private fun DiagnosticsPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel) {
    val rows = buildList {
        status?.tirePressureLamp?.let { tp ->
            add(DiagRow("Tire pressure", if (tp.hasWarning) "Warning" else "OK"))
            tp.frontLeft?.let { add(DiagRow("Front left", warn(it), indent = true)) }
            tp.frontRight?.let { add(DiagRow("Front right", warn(it), indent = true)) }
            tp.rearLeft?.let { add(DiagRow("Rear left", warn(it), indent = true)) }
            tp.rearRight?.let { add(DiagRow("Rear right", warn(it), indent = true)) }
        }
        status?.battery?.let { b ->
            b.batSoc?.let { soc ->
                add(DiagRow("12V battery", "$soc%" + (b.health?.let { " · $it" } ?: "")))
            }
        }
        status?.evStatus?.batteryStatus?.let { add(DiagRow("Drive battery", "$it%")) }
        status?.fuelLevel?.let { add(DiagRow("Fuel level", "$it%")) }
        status?.lowFuelLight?.let { add(DiagRow("Low fuel", yesNo(it))) }
        status?.washerFluidStatus?.let { add(DiagRow("Washer fluid", if (it) "Low" else "OK")) }
        status?.breakOilStatus?.let { add(DiagRow("Brake fluid", if (it) "Check" else "OK")) }
        status?.smartKeyBatteryWarning?.let { add(DiagRow("Key fob battery", if (it) "Low" else "OK")) }
        status?.steerWheelHeat?.let { add(DiagRow("Steering wheel heat", onOff(it))) }
        status?.sideBackWindowHeat?.let { add(DiagRow("Rear defroster", onOff(it))) }
        status?.sideMirrorHeat?.let { add(DiagRow("Mirror heat", onOff(it))) }
        status?.evStatus?.pluggedInLabel?.let { add(DiagRow("Plug", it)) }
        status?.evStatus?.remainTime2?.atc?.value?.let { add(DiagRow("Time to full", "${it.toInt()} min")) }
    }
    Pebble(
        v, "diagnostics", "Diagnostics", Icons.Filled.ErrorOutline, state, vm,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        if (rows.isEmpty()) {
            Text("No diagnostics yet. Pull down to refresh.")
        }
        rows.forEach { row ->
            if (row.indent) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        row.label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(row.value, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                StatusRow(row.label, row.value)
            }
        }
    }
}

private fun warn(v: Int) = if (v == 0) "OK" else "Warning"
private fun yesNo(v: Boolean) = if (v) "Yes" else "No"
private fun onOff(v: Int) = if (v == 0) "Off" else "On"

// --- Climate --------------------------------------------------------------

@Composable
private fun ClimatePebble(
    v: Vehicle,
    status: VehicleStatus?,
    seats: SeatConfig,
    state: UiState,
    vm: AppViewModel,
) {
    val pending = state.isPending(v.vin, "climate")
    var tempF by remember(v.vin) { mutableIntStateOf(72) }
    var duration by remember(v.vin) { mutableIntStateOf(10) }
    var defrost by remember(v.vin) { mutableStateOf(false) }
    var steeringHeat by remember(v.vin) { mutableStateOf(false) }
    var driver by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var passenger by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rearLeft by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rearRight by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }

    Pebble(
        v, "climate", "Climate", Icons.Filled.AcUnit, state, vm,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        StepRow("Temperature", "$tempF°F")
        AnimatedSlider(
            value = tempF.toFloat(),
            onValueChange = { tempF = it.roundToInt() },
            valueRange = 62f..82f,
            steps = 19,
        )

        StepRow("Run time", "$duration min")
        AnimatedSlider(
            value = duration.toFloat(),
            onValueChange = { duration = it.roundToInt() },
            valueRange = 1f..10f,
            steps = 8,
        )

        ToggleRow("Defrost", defrost) { defrost = it }
        if (status?.steerWheelHeat != null) {
            ToggleRow("Steering wheel heat", steeringHeat) { steeringHeat = it }
        }

        // Seats are shown only for functions the car actually has (set per car
        // in Settings, since the API exposes no reliable capability flags). The
        // remote command addresses four positions only.
        if (seats.any) {
            Text("Seats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Slide left to cool, right to heat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (seats.driverHeat || seats.driverCool) {
                SeatControl("Driver seat", driver, seats.driverCool, seats.driverHeat) { driver = it }
            }
            if (seats.passHeat || seats.passCool) {
                SeatControl("Passenger seat", passenger, seats.passCool, seats.passHeat) { passenger = it }
            }
            if (seats.rearLeftHeat || seats.rearLeftCool) {
                SeatControl("Rear left seat", rearLeft, seats.rearLeftCool, seats.rearLeftHeat) { rearLeft = it }
            }
            if (seats.rearRightHeat || seats.rearRightCool) {
                SeatControl("Rear right seat", rearRight, seats.rearRightCool, seats.rearRightHeat) { rearRight = it }
            }
        }

        // Combined start/stop: morphs pill <-> rounded square, shows On/Off.
        StateControl(
            name = "Climate",
            isOn = status?.airCtrlOn,
            stateOn = "On", stateOff = "Off",
            turnOn = "Start", turnOff = "Stop",
            icon = Icons.Filled.AcUnit, pending = pending,
            onActivate = {
                vm.startClimate(
                    v,
                    ClimateRequest(
                        tempF = tempF,
                        defrost = defrost,
                        durationMinutes = duration,
                        steeringWheelHeat = steeringHeat,
                        seatFrontLeft = driver,
                        seatFrontRight = passenger,
                        seatRearLeft = rearLeft,
                        seatRearRight = rearRight,
                    ),
                )
            },
            onDeactivate = { vm.stopClimate(v) },
        )
    }
}

private val SeatCool = Color(0xFF2E78FF)
private val SeatHeat = Color(0xFFE5484D)

@Composable
private fun SeatControl(
    label: String,
    level: SeatLevel,
    canCool: Boolean,
    canHeat: Boolean,
    onChange: (SeatLevel) -> Unit,
) {
    val range = SeatLevel.rangeFor(canCool, canHeat)
    if (range.size <= 1) return
    val index = range.indexOf(level).let { if (it < 0) range.indexOf(SeatLevel.OFF) else it }
    val current = range[index]
    val tint = when {
        current.isCool -> SeatCool
        current.isHeat -> SeatHeat
        else -> MaterialTheme.colorScheme.primary
    }
    Column {
        StepRow(label, current.label)
        AnimatedSlider(
            value = index.toFloat(),
            onValueChange = { onChange(range[it.roundToInt().coerceIn(0, range.lastIndex)]) },
            valueRange = 0f..range.lastIndex.toFloat(),
            steps = (range.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
        )
    }
}

// --- Charge limits --------------------------------------------------------

@Composable
private fun ChargePebble(v: Vehicle, status: VehicleStatus?, enabled: Boolean, state: UiState, vm: AppViewModel) {
    val targets = status?.evStatus?.reservChargeInfos
    var ac by remember(v.vin) { mutableIntStateOf(targets?.level(1) ?: 80) }
    var dc by remember(v.vin) { mutableIntStateOf(targets?.level(0) ?: 80) }

    Pebble(
        v, "charge", "Charge limits", Icons.Filled.Bolt, state, vm,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        StepRow("AC (home) target", "$ac%")
        AnimatedSlider(
            value = ac.toFloat(),
            onValueChange = { ac = (it / 10f).roundToInt() * 10 },
            valueRange = 50f..100f,
            steps = 4,
        )
        StepRow("DC (fast) target", "$dc%")
        AnimatedSlider(
            value = dc.toFloat(),
            onValueChange = { dc = (it / 10f).roundToInt() * 10 },
            valueRange = 50f..100f,
            steps = 4,
        )
        CommandButton("Set limits", Icons.Filled.Bolt, Modifier.fillMaxWidth(), enabled) {
            vm.setChargeLimits(v, ac, dc)
        }
    }
}

// --- Location -------------------------------------------------------------

@Composable
private fun LocationPebble(v: Vehicle, state: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    val location = state.locations[v.vin]
    val place = state.placeNames[v.vin]
    val locating = state.isPending(v.vin, "locate")
    Pebble(v, "location", "Location", Icons.Filled.LocationOn, state, vm) {
        when {
            place != null -> Text(place, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            location != null -> Text("Location updated")
            else -> Text("Tap Locate to query the car's current position.")
        }
        location?.let { loc ->
            CarMap(
                loc,
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            StatusRow("Coordinates", String.format("%.5f, %.5f", loc.latitude, loc.longitude))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CommandButton(
                label = if (locating) "Locating…" else "Locate",
                icon = Icons.Filled.LocationOn,
                modifier = Modifier.weight(1f),
                enabled = !locating,
            ) { vm.locate(v) }
            if (location != null) {
                CommandButton("Map", Icons.Filled.Map, Modifier.weight(1f), true) {
                    val uri = Uri.parse(
                        "geo:${location.latitude},${location.longitude}" +
                            "?q=${location.latitude},${location.longitude}(My car)"
                    )
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A small embedded OpenStreetMap with a pin at the car's position. Uses the
 * key-free OSM embed page in a WebView (no Maps API key / Play Services needed).
 * Keyed on the coordinates so a fresh map loads when the location changes.
 */
@Composable
private fun CarMap(location: GeoLocation, modifier: Modifier = Modifier) {
    val span = 0.008
    val lat = location.latitude
    val lon = location.longitude
    val url = "https://www.openstreetmap.org/export/embed.html" +
        "?bbox=${lon - span}%2C${lat - span}%2C${lon + span}%2C${lat + span}" +
        "&layer=mapnik&marker=$lat%2C$lon"
    key(lat, lon) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    loadUrl(url)
                }
            },
        )
    }
}

// --- Settings -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val appearance by vm.appearance.collectAsState()
    val state by vm.state.collectAsState()
    val logs by vm.logs.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val canBio = remember { vm.canUseBiometrics() }

    // System back returns to the garage, not out of the app.
    BackHandler { vm.closeSettings() }

    var pickTarget by remember { mutableStateOf<String?>(null) }
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val target = pickTarget
        if (result.isSuccessful && target != null) {
            result.uriContent?.let { vm.setVehicleImage(target, it.toString()) }
        }
        pickTarget = null
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = { vm.closeSettings() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Account
            SettingsCard("Account") {
                val creds = state.credentials
                if (creds != null) {
                    StatusRow("Email", creds.email)
                    SecretRow("Service PIN", creds.pin)
                    SecretRow("Password", creds.password)
                } else {
                    Text("Not signed in")
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.logout() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text("Sign out") }
            }

            // Security
            SettingsCard("Security") {
                if (canBio) {
                    ToggleRow("Require fingerprint to open", appearance.biometricLock) { enable ->
                        if (enable) {
                            context.findFragmentActivity()?.let { activity ->
                                showBiometricPrompt(
                                    activity = activity,
                                    title = "Enable fingerprint lock",
                                    subtitle = "Confirm to require it on launch",
                                    onSuccess = { vm.setBiometricLock(true) },
                                    onError = { },
                                )
                            }
                        } else {
                            vm.setBiometricLock(false)
                        }
                    }
                } else {
                    Text("No fingerprint/biometric is enrolled on this device.")
                }
            }

            // Vehicle order
            if (state.vehicles.size > 1) {
                SettingsCard("Vehicle order") {
                    state.vehicles.forEachIndexed { index, v ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(v.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { vm.moveVehicle(v.vin, up = true) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = { vm.moveVehicle(v.vin, up = false) },
                                enabled = index < state.vehicles.lastIndex,
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                            }
                        }
                    }
                }
            }

            // Car photos
            if (state.vehicles.isNotEmpty()) {
                SettingsCard("Car photos") {
                    Text(
                        "Leave blank for the default gradient, or paste an image URL.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    state.vehicles.forEach { v ->
                        OutlinedTextField(
                            value = state.imageUrls[v.vin] ?: "",
                            onValueChange = { vm.setVehicleImage(v.vin, it) },
                            label = { Text(v.name) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                pickTarget = v.vin
                                cropLauncher.launch(
                                    CropImageContractOptions(
                                        uri = null,
                                        cropImageOptions = CropImageOptions(
                                            imageSourceIncludeGallery = true,
                                            imageSourceIncludeCamera = false,
                                            fixAspectRatio = true,
                                            aspectRatioX = 16,
                                            aspectRatioY = 9,
                                        ),
                                    ),
                                )
                            }) { Text("Choose & crop photo") }
                            if (state.imageUrls[v.vin] != null) {
                                TextButton(onClick = { vm.setVehicleImage(v.vin, "") }) { Text("Clear") }
                            }
                        }
                    }
                }
            }

            // Per-car setup (powertrain + which seat functions exist)
            state.vehicles.forEach { v ->
                val seats = state.seatConfigs[v.vin] ?: com.bloo.bluelink.data.SeatConfig()
                SettingsCard("${v.name} setup") {
                    Text("Powertrain", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val current = state.powertrains[v.vin]
                        ?: if (v.isEv) com.bloo.bluelink.data.Powertrain.EV else com.bloo.bluelink.data.Powertrain.GAS
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.bloo.bluelink.data.Powertrain.entries.forEach { pt ->
                            FilterChip(
                                selected = current == pt,
                                onClick = { vm.setPowertrain(v, pt) },
                                label = { Text(pt.name) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Seats this car has", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "The remote climate command controls four positions. Enable heating " +
                            "and/or cooling for each seat your car actually has.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SeatConfigRow("Driver", seats.driverHeat, seats.driverCool,
                        { vm.setSeatFlag(v, "dh", it) }, { vm.setSeatFlag(v, "dc", it) })
                    SeatConfigRow("Front passenger", seats.passHeat, seats.passCool,
                        { vm.setSeatFlag(v, "ph", it) }, { vm.setSeatFlag(v, "pc", it) })
                    SeatConfigRow("Rear left", seats.rearLeftHeat, seats.rearLeftCool,
                        { vm.setSeatFlag(v, "rlh", it) }, { vm.setSeatFlag(v, "rlc", it) })
                    SeatConfigRow("Rear right", seats.rearRightHeat, seats.rearRightCool,
                        { vm.setSeatFlag(v, "rrh", it) }, { vm.setSeatFlag(v, "rrc", it) })

                    Spacer(Modifier.height(8.dp))
                    Text("Section order", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val sections = state.sectionOrders[v.vin] ?: com.bloo.bluelink.data.DEFAULT_SECTIONS
                    val sectionLabels = mapOf(
                        "climate" to "Climate",
                        "charge" to "Charge limits",
                        "location" to "Location",
                        "information" to "Information",
                        "diagnostics" to "Diagnostics",
                    )
                    sections.forEachIndexed { index, id ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                sectionLabels[id] ?: id,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            IconButton(onClick = { vm.moveSection(v, id, up = true) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = { vm.moveSection(v, id, up = false) },
                                enabled = index < sections.lastIndex,
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                            }
                        }
                    }
                }
            }

            // Theme
            SettingsCard("Theme") {
                val labels = mapOf(
                    ThemeMode.SYSTEM to "Follow system",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark",
                    ThemeMode.AMOLED to "AMOLED (pure black)",
                )
                ThemeMode.entries.forEach { mode ->
                    ChoiceRow(labels.getValue(mode), appearance.themeMode == mode) { vm.setThemeMode(mode) }
                }
            }

            // Color
            SettingsCard("Color") {
                ToggleRow("Dynamic color (Material You)", appearance.dynamicColor) { vm.setDynamicColor(it) }
                Text(
                    "Uses your wallpaper palette on Android 12+. Turn off for Bloo's " +
                        "built-in expressive colors.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Font
            SettingsCard("Font") {
                val labels = mapOf(
                    FontChoice.SYSTEM to "System default",
                    FontChoice.ATKINSON to "Atkinson Hyperlegible",
                    FontChoice.GOOGLE_SANS to "Google Sans",
                )
                FontChoice.entries.forEach { choice ->
                    ChoiceRow(labels.getValue(choice), appearance.fontChoice == choice) { vm.setFontChoice(choice) }
                }
            }

            // Logs
            SettingsCard("Logs") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Activity log",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                    }) { Text("Copy") }
                    TextButton(onClick = { vm.clearLogs() }) { Text("Clear") }
                }
                SelectionContainer {
                    Text(
                        text = logs.joinToString("\n").ifBlank { "No activity yet." },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SecretRow(label: String, value: String) {
    var show by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (show) value else "•".repeat(value.length.coerceIn(4, 10)),
            fontWeight = FontWeight.Medium,
        )
        TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

// --- Small reusable pieces ------------------------------------------------

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StepRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** One seat's heat + cool capability toggles, shown as two compact filter chips. */
@Composable
private fun SeatConfigRow(
    label: String,
    heat: Boolean,
    cool: Boolean,
    onHeat: (Boolean) -> Unit,
    onCool: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FilterChip(selected = heat, onClick = { onHeat(!heat) }, label = { Text("Heat") })
        Spacer(Modifier.width(8.dp))
        FilterChip(selected = cool, onClick = { onCool(!cool) }, label = { Text("Cool") })
    }
}

@Composable
private fun CommandButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
