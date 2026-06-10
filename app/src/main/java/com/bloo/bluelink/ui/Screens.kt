@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package com.bloo.bluelink.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun BlooApp(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            scope.launch { snackbar.showSnackbar(it) }
            vm.clearMessage()
        }
    }

    // Edge-to-edge: a soft full-bleed gradient paints behind the transparent
    // status/navigation bars; screen content draws on top of it.
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surfaceContainerHigh,
                        scheme.surface,
                        scheme.surfaceContainerLow,
                    ),
                ),
            ),
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            // imePadding so the toast rises above the keyboard when it's open.
            SnackbarHost(snackbar, modifier = Modifier.imePadding()) { data ->
                // Themed, rounded, copyable "toast" — used for errors/notices.
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        Modifier.padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        SelectionContainer(Modifier.weight(1f)) {
                            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(data.visuals.message)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
            }
        },
    ) { padding ->
        // Adding an account shows the login form even while already signed in.
        val target = if (state.addingAccount) Screen.Login else state.screen
        AnimatedContent(
            targetState = target,
            modifier = Modifier.padding(padding),
            transitionSpec = {
                // Settings slides in from the right; returning slides back left.
                val sign = if (targetState == Screen.Settings) 1 else -1
                (slideInHorizontally { w -> sign * w } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> -sign * w } + fadeOut())
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Login -> LoginScreen(
                    loading = state.loading,
                    onLogin = vm::login,
                    onCancel = if (state.accounts.isNotEmpty()) ({ vm.cancelAddAccount() }) else null,
                )
                Screen.Locked -> LockScreen(vm)
                Screen.Empty -> EmptyScreen(vm)
                Screen.Garage -> GarageScreen(state, vm)
                Screen.Settings -> SettingsScreen(vm)
            }
        }
    }
    }

    if (state.showOnboarding && state.screen == Screen.Garage) {
        val canBio = remember { vm.canUseBiometrics() }
        OnboardingDialog(
            canBiometric = canBio,
            onOpenSettings = { vm.dismissOnboarding(openSettings = true) },
            onEnableBiometric = {
                context.findFragmentActivity()?.let { activity ->
                    showBiometricPrompt(
                        activity = activity,
                        title = "Enable fingerprint lock",
                        subtitle = "Confirm to require it when opening Bloo",
                        onSuccess = { vm.setBiometricLock(true) },
                        onError = { },
                    )
                }
            },
            onDismiss = { vm.dismissOnboarding(openSettings = false) },
        )
    }
}

/** First-run nudge: configure each car's features, add a photo, and lock the app. */
@Composable
private fun OnboardingDialog(
    canBiometric: Boolean,
    onOpenSettings: () -> Unit,
    onEnableBiometric: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
        title = { Text("Welcome to Bloo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Hyundai's API doesn't report everything about your car, so head to " +
                        "Settings to tell Bloo what each car has — its powertrain (gas, hybrid, " +
                        "PHEV or EV) and which seats and steering wheel can heat/cool. While " +
                        "you're there, you can add a photo of each car.",
                )
                if (canBiometric) {
                    FilledTonalButton(onClick = onEnableBiometric, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Lock Bloo with fingerprint")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onOpenSettings) { Text("Open settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

// --- Login ----------------------------------------------------------------

private val FieldShape = RoundedCornerShape(18.dp)

@Composable
private fun LoginScreen(
    loading: Boolean,
    onLogin: (String, String, String, Brand) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf(Brand.HYUNDAI) }
    val scheme = MaterialTheme.colorScheme

    if (onCancel != null) BackHandler { onCancel() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Gradient hero with the wordmark.
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary))),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "Bloo",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = scheme.onPrimary,
                )
                Text(
                    "A better Blue Link · US",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimary.copy(alpha = 0.85f),
                )
            }
        }

        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Brand", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Brand.entries.forEach { b ->
                    FilterChip(
                        selected = brand == b,
                        onClick = { brand = b },
                        label = { Text(b.label) },
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Blue Link email") },
                singleLine = true,
                shape = FieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                shape = FieldShape,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("Service PIN") },
                singleLine = true,
                shape = FieldShape,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onLogin(email, password, pin, brand) },
                enabled = !loading,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (loading) LoadingIndicator() else Text("Sign in")
            }
            if (onCancel != null) {
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
            Text(
                "Credentials are sent directly to ${brand.label}'s telematics servers and " +
                    "stored encrypted on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/** Minimum comfortable width for one car column before we add another. */
private const val MIN_CARD_DP = 380

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GarageScreen(state: UiState, vm: AppViewModel) {
    val vehicles = state.vehicles
    if (vehicles.isEmpty()) return
    val appearance by vm.appearance.collectAsState()

    val count = vehicles.size
    val cfg = LocalConfiguration.current
    val widthDp = cfg.screenWidthDp
    val heightDp = cfg.screenHeightDp
    val large = widthDp >= 600
    // Very small screens (e.g. flip-phone cover): tile layout, one section at a
    // time, swipe up/down between them; swipe left/right between cars.
    val compact = !large && heightDp < 520
    if (compact) {
        Box(Modifier.fillMaxSize()) {
            CompactGarage(state, vm)
            FloatingIcon(
                icon = Icons.Filled.Settings,
                description = "Settings",
                onClick = { vm.openSettings() },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        return
    }
    // How many full-height cards fit side by side; pages advance by this many.
    val perPage = (widthDp / MIN_CARD_DP).coerceIn(1, count)
    // Expanding to the dual-column view only makes sense on a wide screen.
    val canExpand = large && count > 1
    val singleLarge = large && count == 1
    // A car expanded by the user (multi-car), or the lone car on a big screen.
    val expandedByUser = state.expandedIndex?.takeIf { it in vehicles.indices && canExpand }
    val expandedIdx = if (singleLarge) 0 else expandedByUser

    BackHandler(enabled = expandedByUser != null) { vm.collapse() }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = expandedIdx != null,
            transitionSpec = {
                val spec = spring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)
                (fadeIn(spec) + scaleIn(spec, initialScale = 0.94f)) togetherWith
                    (fadeOut(spec) + scaleOut(spec, targetScale = 0.94f))
            },
            label = "expand",
        ) { isExpanded ->
            if (isExpanded) {
                // Full-screen car; swipe left/right to switch cars.
                val exPager = rememberPagerState(initialPage = (expandedIdx ?: 0).coerceIn(0, count - 1)) { count }
                LaunchedEffect(exPager) {
                    snapshotFlow { exPager.settledPage }.collect { vm.expand(it) }
                }
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(state = exPager, modifier = Modifier.fillMaxSize()) { page ->
                        ExpandedCar(vehicles[page], state, vm, flipped = appearance.columnsFlipped)
                    }
                    if (count > 1) {
                        PagerDots(exPager.currentPage, count, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                    }
                }
            } else {
                val pageCount = (count + perPage - 1) / perPage
                val pager = rememberPagerState(
                    initialPage = (state.currentIndex.coerceIn(0, count - 1)) / perPage,
                ) { pageCount }
                LaunchedEffect(pager, perPage) {
                    snapshotFlow { pager.settledPage }.collect { page ->
                        vm.selectIndex((page * perPage).coerceIn(0, count - 1))
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        val start = page * perPage
                        val end = minOf(start + perPage, count)
                        Row(Modifier.fillMaxSize()) {
                            for (i in start until end) {
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    VehicleDetailContent(
                                        vehicles[i], state, vm,
                                        onExpand = if (canExpand) ({ vm.expand(i) }) else null,
                                        reserveHeaderEnd = canExpand && i == end - 1,
                                    )
                                }
                            }
                            repeat(perPage - (end - start)) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    // Floating animated page indicator (no thin top bar).
                    if (pageCount > 1) {
                        PagerDots(pager.currentPage, pageCount, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                    }
                }
            }
        }
        // Floating overlay controls.
        if (expandedByUser != null) {
            FloatingIcon(
                icon = Icons.Filled.ArrowBack,
                description = "Back to all cars",
                onClick = { vm.collapse() },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        if (expandedIdx != null) {
            FloatingIcon(
                icon = Icons.Filled.SwapHoriz,
                description = "Flip columns",
                onClick = { vm.setColumnsFlipped(!appearance.columnsFlipped) },
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 52.dp),
            )
        }
        FloatingIcon(
            icon = Icons.Filled.Settings,
            description = "Settings",
            onClick = { vm.openSettings() },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** Cover-screen layout: swipe left/right for cars, up/down for section tiles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactGarage(state: UiState, vm: AppViewModel) {
    val vehicles = state.vehicles
    val count = vehicles.size
    val pager = rememberPagerState(initialPage = state.currentIndex.coerceIn(0, count - 1)) { count }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { vm.selectIndex(it) }
    }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        CompactCar(vehicles[page], state, vm)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactCar(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val sections = state.sectionsFor(v).filter {
        it !in listOf("summary", "controls") &&
            (it != "charge" || state.hasBattery(v)) &&
            !state.isPebbleHidden(v.vin, it)
    }
    val tiles = listOf("main") + sections
    // Infinite wrap-around: start in the middle of a huge virtual range and map
    // each virtual page back onto a real tile with modulo.
    val loop = tiles.size > 1
    val virtualCount = if (loop) tiles.size * 1000 else tiles.size
    val start = if (loop) virtualCount / 2 else 0
    val vPager = rememberPagerState(initialPage = start) { virtualCount }
    val current = ((vPager.currentPage % tiles.size) + tiles.size) % tiles.size

    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = vPager, modifier = Modifier.fillMaxSize()) { page ->
            val i = ((page % tiles.size) + tiles.size) % tiles.size
            // Pebbles inside cover-screen tiles are always open (no collapsing).
            CompositionLocalProvider(LocalForceExpanded provides true) {
                Box(Modifier.fillMaxSize().padding(10.dp)) {
                    when (val tile = tiles[i]) {
                        "main" -> CompactMainTile(v, state, vm)
                        else -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            when (tile) {
                                "climate" -> ClimatePebble(v, status, state.seatConfigFor(v), state, vm, Modifier)
                                "charge" -> ChargePebble(v, status, !state.loading, state, vm, Modifier)
                                "location" -> LocationPebble(v, state, vm, Modifier)
                                "info" -> InfoPebble(v, status, state, vm, Modifier)
                                "diagnostics" -> DiagnosticsPebble(v, status, state, vm, Modifier)
                            }
                        }
                    }
                }
            }
        }
        // Car-name chip, top-left, on every tile except the main one (which shows
        // the name itself).
        androidx.compose.animation.AnimatedVisibility(
            visible = current != 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.85f),
                shadowElevation = 2.dp,
                modifier = Modifier.padding(14.dp),
            ) {
                Text(
                    v.name,
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        // Vertical page dots on the right edge.
        if (tiles.size > 1) {
            VerticalPagerDots(
                current = current,
                count = tiles.size,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
            )
        }
    }
}

/** Vertical sibling of [PagerDots] for the cover-screen tile stack. */
@Composable
private fun VerticalPagerDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.7f),
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(count) { i ->
                val selected = i == current
                val h by animateDpAsState(if (selected) 20.dp else 7.dp, label = "vdotH")
                val color by androidx.compose.animation.animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    label = "vdotC",
                )
                Box(Modifier.width(7.dp).height(h).clip(CircleShape).background(color))
            }
        }
    }
}

/** The dense main tile: faded car photo behind name, gauge and key controls. */
@Composable
private fun CompactMainTile(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val img = state.imageUrls[v.vin]
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
        if (!img.isNullOrBlank()) {
            AsyncImage(
                model = if (img.startsWith("/")) java.io.File(img) else img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.22f),
            )
        } else {
            Box(
                Modifier.fillMaxSize().alpha(0.18f)
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary))),
            )
        }
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { vm.refreshStatus(v) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
            LastUpdatedLabel(v, state)
            ChargeFuelBar(status, state.hasBattery(v), state.hasFuel(v), state.drivingLabel(v))
            Spacer(Modifier.weight(1f))
            PrimaryActions(v, state, vm)
            Text(
                "Swipe up for more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** A small translucent circular icon button used as a floating overlay control. */
@Composable
private fun FloatingIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.85f),
        shadowElevation = 3.dp,
        modifier = modifier.padding(12.dp).size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
}

@Composable
private fun PagerDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.7f),
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { i ->
                val selected = i == current
                // The active page stretches into a pill; others are small dots.
                val w by animateDpAsState(if (selected) 20.dp else 7.dp, label = "dotW")
                val color by androidx.compose.animation.animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    label = "dotC",
                )
                Box(Modifier.height(7.dp).width(w).clip(CircleShape).background(color))
            }
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
    drivingLabel: String? = null,
    dragHandle: Modifier = Modifier,
    height: Dp = 150.dp,
) {
    val charging = hasBattery && status?.evStatus?.batteryCharge == true
    val corner by animateDpAsState(
        targetValue = if (charging) 40.dp else 24.dp,
        animationSpec = spring(
            dampingRatio = SoftDamping,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "heroCorner",
    )
    Card(modifier = Modifier.fillMaxWidth().then(dragHandle), shape = RoundedCornerShape(corner)) {
        Column(Modifier.padding(16.dp)) {
            HeroVisual(v, imageUrl, height)
            Spacer(Modifier.height(16.dp))
            ChargeFuelBar(status, hasBattery, hasFuel, drivingLabel)
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
        // A locally-cropped photo is an absolute path; a pasted one is a URL.
        val model: Any = if (imageUrl.startsWith("/")) java.io.File(imageUrl) else imageUrl
        AsyncImage(
            model = model,
            contentDescription = v.model,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(18.dp)),
        )
    }
}

@Composable
private fun ChargeFuelBar(status: VehicleStatus?, hasBattery: Boolean, hasFuel: Boolean, drivingLabel: String? = null) {
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
    // Charging time + type, shown in the badge slot (replacing parked/driving,
    // which is hidden while charging) so the pebble doesn't grow taller.
    val chargeMinutes = status?.evStatus?.remainTime2?.atc?.value?.toInt()?.takeIf { it > 0 }
    val chargeType = when (status?.evStatus?.batteryPlugin) {
        1 -> "DC"
        2 -> "AC"
        else -> null
    }

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            // Roll the headline number when it changes.
            RollingNumber(
                text = pct?.let { "$it%" } ?: "—",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            // Charging chip while charging, else the parked/driving badge — same slot.
            if (charging) {
                ChargingChip(chargeMinutes, chargeType)
                Spacer(Modifier.width(12.dp))
            } else drivingLabel?.let {
                DrivingBadge(it)
                Spacer(Modifier.width(12.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                RollingNumber(
                    text = range?.let { "${it.toInt()} mi" } ?: "—",
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
                dampingRatio = SoftDamping,
                stiffness = Spring.StiffnessLow,
            ),
            label = "chargeFill",
        )
        // Target-SOC marker: dot at the AC/DC limit, only when plugged in.
        val ev = status?.evStatus
        val plugged = ev?.batteryPlugin != null && ev.batteryPlugin != 0
        val targetPct = if (plugged) {
            when (ev?.batteryPlugin) {
                1 -> ev.reservChargeInfos?.level(0) // DC fast
                2 -> ev.reservChargeInfos?.level(1) // AC
                else -> null
            }
        } else {
            null
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            // Track + gradient fill (darker green on the left → current green right).
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp))
                    .background(ChargeGreen.copy(alpha = 0.18f)),
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(maxWidth * animatedFrac.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.horizontalGradient(listOf(ChargeGreenDark, ChargeGreen))),
            )
            if (targetPct != null) {
                val x = maxWidth * (targetPct.coerceIn(0, 100) / 100f)
                Box(
                    Modifier
                        .offset(x = x - 6.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                        .align(Alignment.CenterStart),
                )
            }
        }
    }
}

/** Compact green chip with bolt + time-to-full + charger type, for the badge slot. */
@Composable
private fun ChargingChip(minutes: Int?, type: String?) {
    val text = buildString {
        if (minutes != null) append(if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m")
        if (type != null) {
            if (isNotEmpty()) append(" · ")
            append(type)
        }
        if (isEmpty()) append("Charging")
    }
    Surface(color = ChargeGreen, contentColor = Color.White, shape = RoundedCornerShape(8.dp)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val ChargeGreen = Color(0xFF2EBD59)
private val ChargeGreenDark = Color(0xFF1B8A41)

/** Gentle spring damping — present, but not an aggressive overshoot (0.82). */
private const val SoftDamping = 0.82f

/**
 * When true (cover-screen tiles), pebbles render permanently open with no
 * collapse chevron or drag handle — collapsing a full-screen tile makes no sense.
 */
private val LocalForceExpanded = staticCompositionLocalOf { false }

/**
 * A number that rolls vertically when it changes, with a light directional
 * "motion blur" while the glyphs are in motion. The blur is RenderEffect-backed
 * (a no-op below API 31), so it costs nothing on the steady state.
 */
@Composable
private fun RollingNumber(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight,
    color: Color = Color.Unspecified,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith
                (slideOutVertically { -it / 2 } + fadeOut())
        },
        label = "roll",
    ) { t ->
        val blur by transition.animateDp(
            transitionSpec = { spring(stiffness = Spring.StiffnessMediumLow) },
            label = "rollBlur",
        ) { st -> if (st == EnterExitState.Visible) 0.dp else 7.dp }
        Text(t, modifier = Modifier.blur(blur), style = style, fontWeight = fontWeight, color = color)
    }
}

/** A coarse, self-ticking "x min ago" string for [millis] (null → null). */
@Composable
private fun rememberRelativeTime(millis: Long?): String? {
    if (millis == null) return null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(millis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val diff = (now - millis).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L} min ago"
        diff < 86_400_000L -> "${diff / 3_600_000L} hr ago"
        else -> "${diff / 86_400_000L} d ago"
    }
}

/** Small "Updated x ago" caption shown prominently under the car name. */
@Composable
private fun LastUpdatedLabel(v: Vehicle, state: UiState, modifier: Modifier = Modifier) {
    val rel = rememberRelativeTime(state.fetchedAt(v)) ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "Updated $rel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Drag-and-drop reordering --------------------------------------------

/**
 * A vertical list whose items can be reordered by long-pressing the supplied
 * [dragHandle] and dragging. Item heights are measured so variable-height rows
 * reorder correctly; the live order is committed via [onReorder] on drop.
 *
 * Designed to live inside an existing scroll container (it is a plain Column).
 */
@Composable
private fun <T> ReorderColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable (item: T, dragHandle: Modifier, isDragging: Boolean) -> Unit,
) {
    var order by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }

    // Sync with upstream changes only while not actively dragging.
    LaunchedEffect(items) { if (draggingKey == null) order = items }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        order.forEach { item ->
            val k = keyOf(item)
            // Identity key so Compose moves the existing node when the order
            // changes (instead of reusing nodes by slot, which looks janky).
            key(k) {
                val dragging = draggingKey == k
                val lift by animateFloatAsState(if (dragging) 1.03f else 1f, label = "lift")
                Box(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) offsetY else 0f
                            scaleX = lift
                            scaleY = lift
                        }
                        .onSizeChanged { heights[k] = it.height },
                ) {
                    val handle = Modifier.pointerInput(k) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingKey = k; offsetY = 0f },
                        onDragEnd = { draggingKey = null; offsetY = 0f; onReorder(order) },
                        onDragCancel = { draggingKey = null; offsetY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetY += dragAmount.y
                            val cur = order.indexOfFirst { keyOf(it) == k }
                            if (cur >= 0) {
                                if (offsetY > 0 && cur < order.lastIndex) {
                                    val nextH = heights[keyOf(order[cur + 1])] ?: 0
                                    if (nextH > 0 && offsetY > nextH / 2f) {
                                        order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                        offsetY -= nextH
                                    }
                                } else if (offsetY < 0 && cur > 0) {
                                    val prevH = heights[keyOf(order[cur - 1])] ?: 0
                                    if (prevH > 0 && -offsetY > prevH / 2f) {
                                        order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                        offsetY += prevH
                                    }
                                }
                            }
                        },
                    )
                    }
                    content(item, handle, dragging)
                }
            }
        }
    }
}

/**
 * A [Slider] whose thumb follows your finger continuously while dragging, then
 * springs (with a little bounce) to the nearest step when you let go.
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
    val scope = rememberCoroutineScope()
    var pos by remember { mutableFloatStateOf(value) }
    val settle = remember { Animatable(value) }

    // When idle, keep the thumb parked on the real (snapped) value.
    LaunchedEffect(value) { if (!dragging) settle.snapTo(value) }

    Box(contentAlignment = Alignment.Center) {
        // Step ticks — drawn under the slider so the thumb/active track cover the
        // passed ones, mimicking native ticks while drag stays continuous.
        if (steps > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(steps + 2) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                    )
                }
            }
        }
        Slider(
            value = if (dragging) pos else settle.value,
            onValueChange = {
                pos = it
                onValueChange(snapToStep(it, valueRange, steps))
            },
            onValueChangeFinished = {
                val target = snapToStep(pos, valueRange, steps)
                onValueChange(target)
                scope.launch {
                    settle.snapTo(pos)
                    settle.animateTo(target, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
                }
            },
            valueRange = valueRange,
            steps = 0, // continuous while dragging; we snap on release ourselves
            colors = colors,
            interactionSource = interaction,
        )
    }
}

private fun snapToStep(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    if (steps <= 0) return v.coerceIn(range.start, range.endInclusive)
    val inc = (range.endInclusive - range.start) / (steps + 1)
    val snapped = range.start + Math.round((v - range.start) / inc) * inc
    return snapped.coerceIn(range.start, range.endInclusive)
}

// --- Full detail ----------------------------------------------------------

/** Single-column car view (phones, and each column of the grid). */
@Composable
private fun VehicleDetailContent(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    onExpand: (() -> Unit)? = null,
    reserveHeaderEnd: Boolean = false,
) {
    Refreshable(v, state, vm) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CarHeaderRow(v, state, onExpand, reserveHeaderEnd)
            // summary (image+gauge) and controls are reorderable pebbles too.
            PebbleList(v, state, vm)
        }
    }
}

/** Wide expanded view: critical info in one column, pebbles in the other. */
@Composable
private fun ExpandedCar(v: Vehicle, state: UiState, vm: AppViewModel, flipped: Boolean) {
    val controls: @Composable ColumnScope.() -> Unit = {
        CarHeaderRow(v, state, onExpand = null, reserveEnd = false)
        CriticalContent(v, state, vm)
    }
    val pebbles: @Composable ColumnScope.() -> Unit = {
        PebbleList(v, state, vm, exclude = setOf("summary", "controls"))
    }
    Refreshable(v, state, vm) {
        // Animate the swap when the columns are flipped.
        AnimatedContent(
            targetState = flipped,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (slideInHorizontally { w -> dir * w / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> -dir * w / 4 } + fadeOut())
            },
            label = "flipColumns",
        ) { isFlipped ->
            Row(
                // Top padding clears the floating back / flip / settings buttons.
                Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = if (isFlipped) pebbles else controls,
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = if (isFlipped) controls else pebbles,
                )
            }
        }
    }
}

/** Wraps content with the pull-to-refresh gesture, offset and squiggly indicator. */
@Composable
private fun Refreshable(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    content: @Composable BoxScope.() -> Unit,
) {
    val ptrState = rememberPullToRefreshState()
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .pullToRefresh(isRefreshing = state.refreshing, state = ptrState, onRefresh = { vm.refreshStatus(v) }),
    ) {
        val maxShift = 72.dp
        val shift = if (state.refreshing) maxShift else (maxShift * ptrState.distanceFraction).coerceIn(0.dp, maxShift)
        Box(Modifier.fillMaxSize().offset { IntOffset(0, with(density) { shift.roundToPx() }) }) {
            content()
        }
        PullToRefreshDefaults.LoadingIndicator(
            state = ptrState,
            isRefreshing = state.refreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** Car name/model + a Driving/Parked badge, with an optional expand button. */
@Composable
private fun CarHeaderRow(v: Vehicle, state: UiState, onExpand: (() -> Unit)?, reserveEnd: Boolean) {
    Row(
        Modifier.fillMaxWidth().then(if (reserveEnd) Modifier.padding(end = 52.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(v.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "${v.model} · ${state.powertrainLabel(v)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LastUpdatedLabel(v, state, Modifier.padding(top = 2.dp))
        }
        if (onExpand != null) {
            IconButton(onClick = onExpand) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Expand to full screen")
            }
        }
    }
}

@Composable
private fun DrivingBadge(label: String) {
    val bg: Color
    val fg: Color
    when (label) {
        "Driving" -> { bg = MaterialTheme.colorScheme.primary; fg = MaterialTheme.colorScheme.onPrimary }
        "Running" -> { bg = ChargeGreen; fg = Color.White }
        else -> { bg = MaterialTheme.colorScheme.surfaceVariant; fg = MaterialTheme.colorScheme.onSurfaceVariant }
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp)) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Hero image + gauge, then the primary lock/charge controls (expanded view). */
@Composable
private fun CriticalContent(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    HeroHeader(v, status, state.imageUrls[v.vin], state.hasBattery(v), state.hasFuel(v), state.drivingLabel(v))
    PrimaryActions(v, state, vm)
}

/**
 * The lock/unlock control. Deliberately *not* styled like the other pebbles —
 * it's just the morphing Lock/Unlock button with its status on the left, with no
 * card, header or expand chevron. It can still be long-pressed and dragged to
 * reorder, like a pebble, even though it doesn't look like one.
 */
@Composable
private fun ControlsPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    Box(Modifier.fillMaxWidth().then(dragHandle).padding(horizontal = 4.dp)) {
        PrimaryActions(v, state, vm)
    }
}

/** The reorderable pebble stack for a car. */
@Composable
private fun PebbleList(v: Vehicle, state: UiState, vm: AppViewModel, exclude: Set<String> = emptySet()) {
    val status = state.statusFor(v)
    val seats = state.seatConfigFor(v)
    val enabled = !state.loading
    val sections = state.sectionsFor(v).filter {
        it !in exclude && !state.isPebbleHidden(v.vin, it)
    }
    ReorderColumn(
        items = sections,
        keyOf = { it },
        onReorder = { vm.setSectionOrder(v, it) },
    ) { section, dragHandle, _ ->
        when (section) {
            "summary" -> HeroHeader(
                v, status, state.imageUrls[v.vin], state.hasBattery(v), state.hasFuel(v),
                state.drivingLabel(v), dragHandle = dragHandle,
            )
            "controls" -> ControlsPebble(v, state, vm, dragHandle)
            "climate" -> ClimatePebble(v, status, seats, state, vm, dragHandle)
            // The "charge" slot is the powertrain's energy pebble: charging for an
            // EV/PHEV, a fuel readout for a gas/hybrid car (no charge UI at all).
            "charge" -> if (state.hasBattery(v)) {
                ChargePebble(v, status, enabled, state, vm, dragHandle)
            } else {
                FuelPebble(v, status, state, vm, dragHandle)
            }
            "location" -> LocationPebble(v, state, vm, dragHandle)
            "info" -> InfoPebble(v, status, state, vm, dragHandle)
            "diagnostics" -> DiagnosticsPebble(v, status, state, vm, dragHandle)
            else -> Spacer(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PrimaryActions(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    // Doors: locked is the calm/grey state; unlocked is highlighted + red text.
    StateControl(
        name = "Doors",
        isOn = status?.doorLock,
        stateOn = "Locked", stateOff = "Unlocked",
        turnOn = "Lock", turnOff = "Unlock",
        icon = Icons.Filled.Lock, pending = state.isPending(v.vin, "doors"),
        onActivate = { vm.lock(v) }, onDeactivate = { vm.unlock(v) },
        highlightWhenOff = true,
        offTextColor = MaterialTheme.colorScheme.error,
    )
}

/** The charge start/stop control (used in the charge pebble header). */
@Composable
private fun ChargeControl(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel) {
    val ev = status?.evStatus
    val plugIn = ev?.batteryPlugin
    val plugged = ev?.batteryCharge == true || plugIn == null || plugIn != 0
    StateControl(
        name = "Charging",
        isOn = ev?.batteryCharge,
        stateOn = "Charging", stateOff = "Idle",
        turnOn = "Start", turnOff = "Stop",
        icon = Icons.Filled.Bolt, pending = state.isPending(v.vin, "charge"),
        onActivate = { vm.startCharge(v) }, onDeactivate = { vm.stopCharge(v) },
        highlightColor = ChargeGreen,
        highlightContentColor = Color.White,
        enabled = plugged,
        disabledNote = "Not plugged in",
    )
}

/**
 * A chunky stateful control: shows the current state and a button offering the
 * *opposite* action. The button is always a clearly filled control that morphs
 * from a pill (calm) to a rounded square (highlighted).
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
    enabled: Boolean = true,
    disabledNote: String? = null,
    highlightWhenOff: Boolean = false,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    highlightContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    offTextColor: Color? = null,
) {
    // Which state is the "highlighted" (square, coloured) one.
    val highlighted = enabled && (if (highlightWhenOff) isOn == false else isOn == true)
    val corner by animateDpAsState(
        targetValue = if (highlighted) 18.dp else 34.dp,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "ctrlCorner",
    )
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val stateText = when {
                !enabled && disabledNote != null -> disabledNote
                pending -> "Sending…"
                isOn == true -> stateOn
                isOn == false -> stateOff
                else -> "Unknown"
            }
            val stateColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                isOn == false && offTextColor != null -> offTextColor
                highlighted -> highlightColor
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                stateText,
                style = MaterialTheme.typography.bodyMedium,
                color = stateColor,
                fontWeight = if (stateColor != MaterialTheme.colorScheme.onSurfaceVariant) FontWeight.Bold else FontWeight.Normal,
            )
        }
        val onClick = { if (isOn == true) onDeactivate() else onActivate() }
        val container = if (highlighted) highlightColor else MaterialTheme.colorScheme.surfaceContainerHighest
        val contentColor = if (highlighted) highlightContentColor else MaterialTheme.colorScheme.onSurface
        Button(
            onClick = onClick,
            enabled = enabled && !pending,
            shape = RoundedCornerShape(corner),
            colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = contentColor),
            modifier = Modifier.height(60.dp),
        ) {
            if (pending) {
                LoadingIndicator(Modifier.size(22.dp))
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isOn == true) turnOff else turnOn, fontWeight = FontWeight.SemiBold)
            }
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
    dragHandle: Modifier = Modifier,
    summary: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val forceExpanded = LocalForceExpanded.current
    val expanded = forceExpanded || state.isPebbleExpanded(v.vin, section)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "pebbleChevron",
    )
    // Collapsed = pill-soft corners; expanded morphs to a tighter rounded square.
    val corner by animateDpAsState(
        targetValue = if (expanded) PebbleCornerExpanded else PebbleCornerCollapsed,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "pebbleCorner",
    )
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        // animateContentSize gives a smooth, correctly-measured collapse (no
        // post-animation size jump) for both fixed- and variable-height bodies.
        Column(
            Modifier.animateContentSize(
                spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
            ),
        ) {
            // Header: tap anywhere to toggle, long-press to drag-reorder. The
            // action button and chevron handle their own clicks. Fixed min height
            // so every collapsed pebble lines up.
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (forceExpanded) Modifier else Modifier.clickable { vm.togglePebble(v, section) })
                    .then(dragHandle)
                    .heightIn(min = PebbleHeaderHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (summary != null) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (headerAction != null) {
                    headerAction()
                    Spacer(Modifier.width(4.dp))
                }
                if (!forceExpanded) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(rotation),
                    )
                }
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
        }
    }
}

/** Uniform collapsed-header height so every pebble lines up at the same size. */
private val PebbleHeaderHeight = 60.dp
private val PebbleCornerCollapsed = 32.dp
private val PebbleCornerExpanded = 20.dp

/**
 * A compact pill button used in a pebble header (charge/climate/location), shown
 * even when the pebble is collapsed, sitting just before the expand chevron.
 */
@Composable
private fun PebbleActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    pending: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !pending,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 42.dp),
    ) {
        if (pending) {
            LoadingIndicator(Modifier.size(18.dp))
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

// --- Car info (status + service + links combined) -------------------------

@Composable
private fun InfoPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val context = LocalContext.current
    val appearance by vm.appearance.collectAsState()
    val inApp = appearance.linksInApp
    val genesis = v.brandIndicator.equals("G", ignoreCase = true)
    val location = state.locations[v.vin]
    val odo = v.odometer?.trim()?.takeIf { it.isNotBlank() }
    val odoInt = odo?.replace(",", "")?.toDoubleOrNull()?.toInt()
    val plate = state.licensePlates[v.vin]
    val lastSvc = state.lastServiceMiles[v.vin]
    val interval = state.serviceIntervalMiles[v.vin]
    val nextDue = if (lastSvc != null && interval != null) lastSvc + interval else null
    val remaining = if (nextDue != null && odoInt != null) nextDue - odoInt else null

    val ev = status?.evStatus
    val plugged = (ev?.batteryPlugin ?: 0) != 0 || ev?.batteryCharge == true

    Pebble(v, "info", "Car info", Icons.Filled.Info, state, vm, dragHandle) {
        when {
            status == null && state.refreshing -> Text("Fetching live status…")
            status == null -> Text("No status yet. Pull down to refresh.")
            else -> {
                SectionLabel("Status")
                status.engine?.let { StatusRow("Vehicle", if (it) "On" else "Off") }
                StatusRow("Doors", if (status.doorLock == true) "Locked" else "Unlocked")
                status.doorOpen?.let { d ->
                    val open = listOfNotNull(
                        if (d.frontLeft == 1) "front-left" else null,
                        if (d.frontRight == 1) "front-right" else null,
                        if (d.backLeft == 1) "rear-left" else null,
                        if (d.backRight == 1) "rear-right" else null,
                    )
                    if (open.isNotEmpty()) StatusRow("Doors open", open.joinToString(", "))
                }
                status.windowOpen?.let { w ->
                    val open = listOfNotNull(
                        if (w.frontLeft == 1) "front-left" else null,
                        if (w.frontRight == 1) "front-right" else null,
                        if (w.backLeft == 1) "rear-left" else null,
                        if (w.backRight == 1) "rear-right" else null,
                    )
                    if (open.isNotEmpty()) StatusRow("Windows open", open.joinToString(", "))
                }
                if (status.trunkOpen == true) StatusRow("Trunk", "Open")
                if (status.hoodOpen == true) StatusRow("Hood", "Open")
                if (status.acc == true) StatusRow("Accessory power", "On")
                StatusRow("Climate", if (status.airCtrlOn == true) "On" else "Off")
                if (status.defrost == true) StatusRow("Defrost", "On")
                status.battery?.batSoc?.let { StatusRow("12V battery", "$it%") }
                location?.let { StatusRow("Coordinates", String.format("%.5f, %.5f", it.latitude, it.longitude)) }
                rememberRelativeTime(state.fetchedAt(v))?.let { StatusRow("Last refreshed", it) }

                if (plugged) {
                    SectionLabel("Charging")
                    ev?.remainTime2?.atc?.value?.toInt()?.takeIf { it > 0 }
                        ?.let { StatusRow("Time to full", fmtMinutes(it)) }
                    chargerLabel(ev?.batteryPlugin)?.let { StatusRow("Charger", it) }
                    val tgt = when (ev?.batteryPlugin) {
                        1 -> ev.reservChargeInfos?.level(0)
                        2 -> ev.reservChargeInfos?.level(1)
                        else -> null
                    }
                    tgt?.let { StatusRow("Charge limit", "$it%") }
                }
            }
        }

        SectionLabel("Service & identity")
        SelectionContainer { StatusRow("VIN", v.vin) }
        if (!plate.isNullOrBlank()) StatusRow("License plate", plate)
        odo?.let { StatusRow("Odometer", "$it mi") }
        lastSvc?.let { StatusRow("Last service at", "$it mi") }
        nextDue?.let {
            val note = remaining?.let { r -> if (r >= 0) " · $r mi to go" else " · overdue ${-r} mi" } ?: ""
            StatusRow("Next service due", "$it mi$note")
        }
        if (lastSvc == null || interval == null) {
            Text(
                "Set last-service mileage and a service interval in Settings to track service.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel(if (genesis) "Genesis links" else "Hyundai links")
        if (genesis) {
            LinkRow("Open Genesis app") {
                openApp(context, listOf("com.stationdm.genesis"),
                    "https://play.google.com/store/apps/details?id=com.stationdm.genesis", inApp)
            }
            LinkRow("Genesis owners site") { openUrl(context, "https://owners.genesis.com", inApp) }
            LinkRow("Schedule service / retailer") { openUrl(context, "https://www.genesis.com/us/en/find-a-retailer.html", inApp) }
            LinkRow("Manuals & guides") { openUrl(context, "https://www.genesis.com/us/en/owners.html", inApp) }
            LinkRow("Roadside assistance") { dial(context, "8443409741") }
        } else {
            LinkRow("Open Bluelink app") {
                openApp(context, listOf("com.stationdm.bluelink"),
                    "https://play.google.com/store/apps/details?id=com.stationdm.bluelink", inApp)
            }
            LinkRow("MyHyundai owners site") { openUrl(context, "https://owners.hyundaiusa.com", inApp) }
            LinkRow("Schedule service / dealer") { openUrl(context, "https://www.hyundaiusa.com/us/en/dealer-locator", inApp) }
            LinkRow("Manuals & guides") { openUrl(context, "https://www.hyundaiusa.com/us/en/owner-resources", inApp) }
            LinkRow("Roadside assistance") { dial(context, "8002437766") }
        }
    }
}

// --- Diagnostics ----------------------------------------------------------

private data class DiagRow(val label: String, val value: String, val indent: Boolean = false)

@Composable
private fun DiagnosticsPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
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
        status?.seatHeaterVentState?.let { s ->
            val seats = listOfNotNull(
                s.flSeatHeatState?.takeIf { it != 0 }?.let { "Driver" },
                s.frSeatHeatState?.takeIf { it != 0 }?.let { "Passenger" },
                s.rlSeatHeatState?.takeIf { it != 0 }?.let { "Rear-left" },
                s.rrSeatHeatState?.takeIf { it != 0 }?.let { "Rear-right" },
            )
            if (seats.isNotEmpty()) add(DiagRow("Seat heat/vent active", seats.joinToString(", ")))
        }
        status?.evStatus?.pluggedInLabel?.let { add(DiagRow("Plug", it)) }
        status?.evStatus?.remainTime2?.atc?.value?.let { add(DiagRow("Time to full", "${it.toInt()} min")) }
    }
    Pebble(
        v, "diagnostics", "Diagnostics", Icons.Filled.ErrorOutline, state, vm, dragHandle,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
    dragHandle: Modifier,
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

    val climateOn = status?.airCtrlOn == true
    val startClimate = {
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
    }

    Pebble(
        v, "climate", "Climate", Icons.Filled.AcUnit, state, vm, dragHandle,
        summary = if (climateOn) "On" else "Off",
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        headerAction = {
            PebbleActionButton(
                label = if (climateOn) "Stop" else "Start",
                icon = Icons.Filled.AcUnit,
                onClick = { if (climateOn) vm.stopClimate(v) else startClimate() },
                pending = pending,
                container = if (climateOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (climateOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        },
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
        if (seats.steeringWheel) {
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

/**
 * Charge pebble: collapsed shows just the charge start/stop control; expand to
 * set limits and see charging info. Long-press to drag-reorder.
 */
@Composable
private fun ChargePebble(v: Vehicle, status: VehicleStatus?, enabled: Boolean, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val targets = status?.evStatus?.reservChargeInfos
    var ac by remember(v.vin) { mutableIntStateOf(targets?.level(1) ?: 80) }
    var dc by remember(v.vin) { mutableIntStateOf(targets?.level(0) ?: 80) }
    val ev = status?.evStatus
    val charging = ev?.batteryCharge == true
    val plugged = (ev?.batteryPlugin != null && ev.batteryPlugin != 0) || charging
    val pending = state.isPending(v.vin, "charge")
    val mins = ev?.remainTime2?.atc?.value?.toInt()?.takeIf { it > 0 }
    val summary = when {
        charging -> "Charging" + (mins?.let { " · ${fmtMinutes(it)} to full" } ?: "")
        plugged -> "Plugged in · idle"
        else -> "Not plugged in"
    }

    Pebble(
        v, "charge", "Charge", Icons.Filled.Bolt, state, vm, dragHandle,
        summary = summary,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        headerAction = {
            PebbleActionButton(
                label = if (charging) "Stop" else "Start",
                icon = Icons.Filled.Bolt,
                onClick = { if (charging) vm.stopCharge(v) else vm.startCharge(v) },
                enabled = plugged,
                pending = pending,
                container = if (charging) ChargeGreen else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (charging) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        },
    ) {
        if (plugged) {
            mins?.let { StatusRow("Time to full", fmtMinutes(it)) }
            chargerLabel(ev?.batteryPlugin)?.let { StatusRow("Charger", it) }
        }
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

/**
 * The energy pebble for a gas/hybrid car: fuel level + range, no charge UI at
 * all. Occupies the same "charge" slot so order/collapse state carry over.
 */
@Composable
private fun FuelPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val fuelPct = status?.fuelLevel
    val range = status?.dte?.value?.toInt()
    val summary = when {
        fuelPct != null && range != null -> "$fuelPct% · $range mi"
        fuelPct != null -> "$fuelPct%"
        range != null -> "$range mi"
        else -> "—"
    }
    Pebble(
        v, "charge", "Fuel", Icons.Filled.LocalGasStation, state, vm, dragHandle,
        summary = summary,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        when {
            status == null && state.refreshing -> Text("Fetching live status…")
            status == null -> Text("No status yet. Pull down to refresh.")
            else -> {
                fuelPct?.let { StatusRow("Fuel level", "$it%") }
                range?.let { StatusRow("Range (distance to empty)", "$it mi") }
                if (fuelPct == null && range == null) Text("No fuel data reported.")
            }
        }
    }
}

private fun chargerLabel(plugin: Int?): String? = when (plugin) {
    1 -> "DC fast"
    2 -> "AC (level 2)"
    else -> null
}

private fun fmtMinutes(min: Int) = if (min >= 60) "${min / 60}h ${min % 60}m" else "$min min"

// --- Location -------------------------------------------------------------

@Composable
private fun LocationPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val context = LocalContext.current
    val location = state.locations[v.vin]
    val place = state.placeNames[v.vin]
    val locating = state.isPending(v.vin, "locate")
    // Show the place name (or a hint) in the header so it's visible even collapsed.
    val summary = place ?: if (location != null) "Located" else "Not located yet"
    Pebble(
        v, "location", "Location", Icons.Filled.LocationOn, state, vm, dragHandle, summary = summary,
        headerAction = {
            PebbleActionButton(
                label = if (locating) "…" else "Locate",
                icon = Icons.Filled.LocationOn,
                onClick = { vm.locate(v) },
                enabled = !locating,
                pending = locating,
            )
        },
    ) {
        if (location == null) {
            Text("Tap Locate to query the car's current position.")
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
            CommandButton("Open in maps", Icons.Filled.Map, Modifier.fillMaxWidth(), true) {
                val uri = Uri.parse(
                    "geo:${loc.latitude},${loc.longitude}" +
                        "?q=${loc.latitude},${loc.longitude}(My car)"
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

/**
 * A small map with a pin at the car's position. Uses a key-free OSM static-map
 * image (loaded with Coil) rather than a WebView, which painted blank.
 */
@Composable
private fun CarMap(location: GeoLocation, modifier: Modifier = Modifier) {
    val lat = location.latitude
    val lon = location.longitude
    val url = "https://staticmap.openstreetmap.de/staticmap.php" +
        "?center=$lat,$lon&zoom=15&size=640x360&maptype=mapnik&markers=$lat,$lon,red-pushpin"
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = url,
            contentDescription = "Map",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.0f),
        )
    }
}

// --- Service & links ------------------------------------------------------


@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(
            Icons.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openUrl(context: Context, url: String, inApp: Boolean) {
    val uri = Uri.parse(url)
    val external = { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
    if (inApp) {
        runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
            .onFailure { runCatching { external() } }
    } else {
        runCatching { external() }
    }
}

private fun openApp(context: Context, packages: List<String>, fallbackUrl: String, inApp: Boolean) {
    for (p in packages) {
        context.packageManager.getLaunchIntentForPackage(p)?.let {
            runCatching { context.startActivity(it) }.onSuccess { return }
        }
    }
    openUrl(context, fallbackUrl, inApp)
}

private fun dial(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

// --- Photo crop -----------------------------------------------------------

/**
 * Lightweight, crash-free crop: pinch-zoom + drag the picked image inside a 16:9
 * frame, then export the framed region to a file. Drawn via a Canvas + Matrix so
 * what you see is what gets saved.
 */
@Composable
private fun CropScreen(vin: String, uriString: String, onCancel: () -> Unit, onSave: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bmp by remember(uriString) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uriString) { mutableStateOf(false) }
    var scale by remember(uriString) { mutableFloatStateOf(1f) }
    var offset by remember(uriString) { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(uriString) {
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: return@runCatching null
                // Apply the photo's EXIF orientation (camera photos are often rotated).
                val orientation = context.contentResolver.openInputStream(uri)?.use {
                    androidx.exifinterface.media.ExifInterface(it).getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                    )
                } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                val m = android.graphics.Matrix()
                when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                }
                if (m.isIdentity) raw
                else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            }.getOrNull()
        }
        if (bmp == null) failed = true
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Pinch and drag to frame your photo", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = bmp
                when {
                    image != null -> Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(18.dp))
                            .onSizeChanged { frame = it }
                            .pointerInput(image) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offset += pan
                                }
                            },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val wpx = size.width
                            val hpx = size.height
                            val cover = max(wpx / image.width, hpx / image.height)
                            val s = cover * scale
                            val maxX = ((image.width * s - wpx) / 2f).coerceAtLeast(0f)
                            val maxY = ((image.height * s - hpx) / 2f).coerceAtLeast(0f)
                            val cx = offset.x.coerceIn(-maxX, maxX)
                            val cy = offset.y.coerceIn(-maxY, maxY)
                            val m = android.graphics.Matrix().apply {
                                postTranslate(-image.width / 2f, -image.height / 2f)
                                postScale(s, s)
                                postTranslate(wpx / 2f + cx, hpx / 2f + cy)
                            }
                            drawIntoCanvas { it.nativeCanvas.drawBitmap(image, m, null) }
                        }
                    }
                    failed -> Text("Couldn't load that image", color = Color.White)
                    else -> LoadingIndicator()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        val image = bmp ?: return@Button
                        val f = frame
                        scope.launch {
                            val path = withContext(Dispatchers.IO) {
                                runCatching {
                                    val wpx = f.width.toFloat()
                                    val hpx = f.height.toFloat()
                                    val cover = max(wpx / image.width, hpx / image.height)
                                    val s = cover * scale
                                    val maxX = ((image.width * s - wpx) / 2f).coerceAtLeast(0f)
                                    val maxY = ((image.height * s - hpx) / 2f).coerceAtLeast(0f)
                                    val cx = offset.x.coerceIn(-maxX, maxX)
                                    val cy = offset.y.coerceIn(-maxY, maxY)
                                    val outScale = 1080f / wpx
                                    val out = Bitmap.createBitmap(1080, (hpx * outScale).toInt(), Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(out)
                                    val m = android.graphics.Matrix().apply {
                                        postTranslate(-image.width / 2f, -image.height / 2f)
                                        postScale(s, s)
                                        postTranslate(wpx / 2f + cx, hpx / 2f + cy)
                                        postScale(outScale, outScale)
                                    }
                                    canvas.drawBitmap(image, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                                    val dir = java.io.File(context.filesDir, "cars").apply { mkdirs() }
                                    val file = java.io.File(dir, "car_${vin}_${System.currentTimeMillis()}.jpg")
                                    file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                                    file.absolutePath
                                }.getOrNull()
                            }
                            if (path != null) onSave(path) else onCancel()
                        }
                    },
                    enabled = bmp != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Use photo") }
            }
        }
    }
}

// --- Settings -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val appearance by vm.appearance.collectAsState()
    val notif by vm.notifications.collectAsState()
    val state by vm.state.collectAsState()
    val logs by vm.logs.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val canBio = remember { vm.canUseBiometrics() }

    // System back returns to the garage, not out of the app.
    BackHandler { vm.closeSettings() }

    var pickTarget by remember { mutableStateOf<String?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    // System photo picker (crash-free), then our own Compose crop step.
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && pickTarget != null) cropUri = uri
    }

  Box(Modifier.fillMaxSize()) {
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
            // Accounts (one per brand; Hyundai + Genesis can both be signed in).
            SettingsCard("Accounts") {
                if (state.accounts.isEmpty()) {
                    Text("Not signed in")
                }
                state.accounts.forEachIndexed { i, creds ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Text(creds.brand.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    StatusRow("Email", creds.email)
                    SecretRow("Password", creds.password)
                    var pin by remember(creds.brand, creds.pin) { mutableStateOf(creds.pin) }
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Service PIN") },
                        singleLine = true,
                        shape = FieldShape,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { vm.updatePin(creds.brand, pin) },
                            enabled = pin.isNotBlank() && pin != creds.pin,
                        ) { Text("Update PIN") }
                        TextButton(
                            onClick = { vm.logout(creds.brand) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Sign out") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { vm.beginAddAccount() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add another account")
                }
                Text(
                    "If commands fail with a locked PIN, fix the Service PIN above — too " +
                        "many wrong-PIN attempts lock it for a few minutes server-side.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
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

            // Cars: drag to reorder, tap a car to expand its setup + photo. With a
            // single car there's nothing to order, so it's just shown expanded.
            if (state.vehicles.isNotEmpty()) {
                var expandedCar by remember { mutableStateOf<String?>(null) }
                val single = state.vehicles.size == 1
                val pick: (String) -> Unit = { vin ->
                    pickTarget = vin
                    photoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                SettingsCard(if (single) "Car" else "Cars") {
                    if (single) {
                        val v = state.vehicles[0]
                        CarSettingsCard(
                            v = v, state = state, vm = vm,
                            expanded = true, dragging = false, dragHandle = Modifier,
                            collapsible = false, showHandle = false,
                            onToggle = {}, onPickPhoto = { pick(v.vin) },
                        )
                    } else {
                        Text(
                            "Long-press a car to drag it into a new order. Tap one to set its " +
                                "powertrain, seats, steering wheel and photo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        ReorderColumn(
                            items = state.vehicles,
                            keyOf = { it.vin },
                            onReorder = { vm.reorderVehicles(it) },
                            spacing = 8.dp,
                        ) { v, dragHandle, dragging ->
                            CarSettingsCard(
                                v = v, state = state, vm = vm,
                                expanded = expandedCar == v.vin, dragging = dragging, dragHandle = dragHandle,
                                onToggle = { expandedCar = if (expandedCar == v.vin) null else v.vin },
                                onPickPhoto = { pick(v.vin) },
                            )
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
                Spacer(Modifier.height(8.dp))
                StepRow("Vibrancy", "${(appearance.vibrancy * 100).roundToInt()}%")
                Slider(
                    value = appearance.vibrancy,
                    onValueChange = { vm.setVibrancy((it * 20).roundToInt() / 20f) },
                    valueRange = 0.5f..1.6f,
                )
            }

            // Display scale
            SettingsCard("Display") {
                StepRow("Text & layout scale", "${(appearance.uiScale * 100).roundToInt()}%")
                Slider(
                    value = appearance.uiScale,
                    onValueChange = { vm.setUiScale((it * 20).roundToInt() / 20f) },
                    valueRange = 0.85f..1.3f,
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

            // Links
            SettingsCard("Links") {
                ToggleRow("Open links in app", appearance.linksInApp) { vm.setLinksInApp(it) }
                Text(
                    "On uses an in-app browser tab; off opens your default browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Notifications
            SettingsCard("Notifications") {
                ToggleRow("Service due alerts", notif.service) { vm.setNotifyService(it) }
                ToggleRow("Door-left-open alerts", notif.doorOpen) { vm.setNotifyDoor(it) }
                if (notif.doorOpen) {
                    var minutes by remember(notif.doorOpenMinutes) { mutableStateOf(notif.doorOpenMinutes.toString()) }
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = {
                            minutes = it.filter(Char::isDigit)
                            minutes.toIntOrNull()?.takeIf { m -> m in 1..120 }?.let(vm::setDoorOpenMinutes)
                        },
                        label = { Text("Door-open minutes") },
                        singleLine = true,
                        shape = FieldShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                Text(
                    "Background checks run roughly every 30 minutes, so door alerts may " +
                        "arrive a little after your set time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    cropUri?.let { uri ->
        val target = pickTarget
        if (target != null) {
            CropScreen(
                vin = target,
                uriString = uri.toString(),
                onCancel = { cropUri = null; pickTarget = null },
                onSave = { path -> vm.setVehicleImage(target, path); cropUri = null; pickTarget = null },
            )
        }
    }
  }
}

/** One reorderable car entry in Settings; tap to expand its setup + photo. */
@Composable
private fun CarSettingsCard(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    expanded: Boolean,
    dragging: Boolean,
    dragHandle: Modifier,
    onToggle: () -> Unit,
    onPickPhoto: () -> Unit,
    collapsible: Boolean = true,
    showHandle: Boolean = true,
) {
    val seats = state.seatConfigs[v.vin] ?: SeatConfig()
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .then(if (collapsible) Modifier.clickable { onToggle() } else Modifier)
                    .then(dragHandle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showHandle) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(v.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${v.model} · ${state.powertrainLabel(v)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (collapsible) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Powertrain", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val current = state.powertrainOf(v)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Powertrain.entries.forEach { pt ->
                            FilterChip(
                                selected = current == pt,
                                onClick = { vm.setPowertrain(v, pt) },
                                label = { Text(pt.name) },
                            )
                        }
                    }

                    Text("Features", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "The remote climate command controls four seat positions. Enable " +
                            "heating and/or cooling for the seats your car actually has.",
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
                    ToggleRow("Heated steering wheel", seats.steeringWheel) { vm.setSeatFlag(v, "sw", it) }

                    Text("Photo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val storedImage = state.imageUrls[v.vin]
                    if (storedImage != null && storedImage.startsWith("/")) {
                        Text(
                            "Custom photo set",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        OutlinedTextField(
                            value = storedImage ?: "",
                            onValueChange = { vm.setVehicleImage(v.vin, it) },
                            label = { Text("Image URL (blank = gradient)") },
                            singleLine = true,
                            shape = FieldShape,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onPickPhoto) { Text("Choose photo") }
                        if (state.imageUrls[v.vin] != null) {
                            TextButton(onClick = { vm.setVehicleImage(v.vin, "") }) { Text("Clear") }
                        }
                    }

                    Text("Identity & service", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    SelectionContainer { StatusRow("VIN", v.vin) }
                    OutlinedTextField(
                        value = state.licensePlates[v.vin] ?: "",
                        onValueChange = { vm.setLicensePlate(v.vin, it) },
                        label = { Text("License plate") },
                        singleLine = true,
                        shape = FieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.lastServiceMiles[v.vin]?.toString() ?: "",
                            onValueChange = { vm.setLastServiceMiles(v.vin, it.filter(Char::isDigit).toIntOrNull()) },
                            label = { Text("Last service (mi)") },
                            singleLine = true,
                            shape = FieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.serviceIntervalMiles[v.vin]?.toString() ?: "",
                            onValueChange = { vm.setServiceIntervalMiles(v.vin, it.filter(Char::isDigit).toIntOrNull()) },
                            label = { Text("Interval (mi)") },
                            singleLine = true,
                            shape = FieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Text("Sections shown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val labels = mapOf(
                        "climate" to "Climate",
                        "charge" to "Charge limits",
                        "location" to "Location",
                        "info" to "Car info",
                        "diagnostics" to "Diagnostics",
                    )
                    com.bloo.bluelink.data.HIDEABLE_SECTIONS.forEach { sec ->
                        ToggleRow(labels[sec] ?: sec, !state.isPebbleHidden(v.vin, sec)) { show ->
                            vm.setSectionHidden(v, sec, !show)
                        }
                    }
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

/** A small bold group heading used inside the Car-info pebble. */
@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(2.dp))
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StepRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        // Roll the value when it changes (e.g. dragging a slider).
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 })
            },
            label = "stepValue",
        ) { v -> Text(v, fontWeight = FontWeight.Medium) }
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
