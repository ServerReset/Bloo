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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.R
import coil.compose.AsyncImage
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.targetForCurrentPlug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun BlooApp(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val appearance by vm.appearance.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // One haptics engine for the whole app; its enabled flag tracks the setting.
    val haptics = remember { Haptics(context.applicationContext) }
    haptics.enabled = appearance.hapticsEnabled

    // While a command is in flight (or the garage is loading), loop a soft
    // left-to-right sweep so progress is felt until it completes.
    val busy = state.loading || state.pending.isNotEmpty()
    LaunchedEffect(busy) {
        while (busy) {
            haptics.loadingSweep()
            delay(560)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            scope.launch { snackbar.showSnackbar(it) }
            vm.clearMessage()
        }
    }

    CompositionLocalProvider(LocalHaptics provides haptics) {
    // Edge-to-edge: a soft full-bleed gradient paints behind the transparent
    // status/navigation bars; screen content draws on top of it.
    val scheme = MaterialTheme.colorScheme
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
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
            transitionSpec = {
                // Settings slides in from the right; returning slides back left.
                val sign = if (targetState == Screen.Settings) 1 else -1
                (slideInHorizontally { w -> sign * w } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> -sign * w } + fadeOut())
            },
            label = "screen",
        ) { screen ->
            // The garage draws full-bleed (content scrolls behind the bars and
            // handles its own insets); other screens stay inset by the Scaffold.
            when (screen) {
                Screen.Login -> Box(Modifier.padding(padding)) {
                    LoginScreen(
                        loading = state.loading,
                        onLogin = vm::login,
                        onCancel = if (state.accounts.isNotEmpty()) ({ vm.cancelAddAccount() }) else null,
                    )
                }
                Screen.Locked -> Box(Modifier.padding(padding)) { LockScreen(vm) }
                Screen.Empty -> Box(Modifier.padding(padding)) { EmptyScreen(vm) }
                Screen.Onboarding -> OnboardingScreen(vm)
                Screen.Garage -> GarageScreen(state, vm)
                Screen.Settings -> Box(Modifier.padding(padding)) { SettingsScreen(vm) }
            }
        }
    }
        // Fade-to-black scrim over the status bar so content stays legible as it
        // scrolls underneath.
        Box(
            Modifier
                .fillMaxWidth()
                .height(topInset + 22.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                    ),
                ),
        )
    }
    }

}

// --- Onboarding (first run) -----------------------------------------------

/**
 * First-run welcome. Celebrates the sign-in with on-screen fireworks (plus sound
 * and haptics) and explains how Bloo works, then funnels the user into Settings
 * — the only way forward — so they configure each car before reaching the app.
 */
@Composable
private fun OnboardingScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    val canBio = remember { vm.canUseBiometrics() }

    // Congratulations: fire the works once on entry.
    LaunchedEffect(Unit) {
        Fireworks.playSound(context)
        haptics?.fireworks()
    }
    // There's no way back — you must set up first.
    BackHandler {}

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.surfaceContainerHigh, scheme.surface))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("🎉", style = MaterialTheme.typography.displayMedium)
            Text(
                "You're in!",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Welcome to Bloo. A quick tour before you drive off:",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
            )
            OnboardingPoint("🚗", "Your cars", "Each car is a screen you swipe between. Pull down to refresh its live status.")
            OnboardingPoint("🧩", "Pebbles", "Tap a pebble to expand it; long-press to drag and reorder. Lock, charge, climate and more live here.")
            OnboardingPoint("⚙️", "Tell Bloo about each car", "Hyundai's API doesn't report a car's powertrain, seats or heated wheel — set those in Settings so the right controls appear. Add a photo while you're there.")
            OnboardingPoint("⚡", "Quick access", "Add Quick Settings tiles and long-press app-icon shortcuts for one-tap lock / unlock / climate.")

            if (canBio) {
                FilledTonalButton(
                    onClick = {
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Lock Bloo with fingerprint")
                }
            }

            Spacer(Modifier.height(8.dp))
            MorphButton(
                onClick = { vm.startSetup() },
                modifier = Modifier.fillMaxWidth(),
                restCorner = 30.dp,
                pressedCorner = 14.dp,
                contentPadding = PaddingValues(vertical = 18.dp),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Set up your cars", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Takes a minute — you'll land in the app right after.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }
        // Fireworks burst over everything.
        FireworksOverlay(Modifier.fillMaxSize())
    }
}

@Composable
private fun OnboardingPoint(emoji: String, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private class Burst(val x: Float, val y: Float, val start: Float, val life: Float, val hue: Float, val count: Int, val maxR: Float)

/** A short, lightweight particle-burst fireworks animation drawn on a Canvas. */
@Composable
private fun FireworksOverlay(modifier: Modifier = Modifier) {
    val bursts = remember {
        val r = kotlin.random.Random(System.nanoTime())
        List(7) {
            Burst(
                x = r.nextFloat() * 0.8f + 0.1f,
                y = r.nextFloat() * 0.5f + 0.12f,
                start = r.nextFloat() * 0.55f,
                life = r.nextFloat() * 0.25f + 0.35f,
                hue = r.nextFloat() * 360f,
                count = 18 + r.nextInt(14),
                maxR = r.nextFloat() * 0.12f + 0.14f,
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2600)) }
    Canvas(modifier) {
        bursts.forEach { b ->
            val local = ((t.value - b.start) / b.life)
            if (local <= 0f || local >= 1f) return@forEach
            val cx = b.x * size.width
            val cy = b.y * size.height
            val r = local * b.maxR * size.height
            val alpha = (1f - local).coerceIn(0f, 1f)
            val color = Color.hsv(b.hue, 0.85f, 1f).copy(alpha = alpha)
            for (k in 0 until b.count) {
                val ang = (k.toFloat() / b.count) * (2f * Math.PI.toFloat())
                val px = cx + kotlin.math.cos(ang) * r
                val py = cy + kotlin.math.sin(ang) * r + local * local * size.height * 0.06f
                drawCircle(color, radius = 5f * alpha + 1.5f, center = Offset(px, py))
            }
        }
    }
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
                    MorphChip(selected = brand == b, onClick = { brand = b }, label = b.label)
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
            MorphButton(
                onClick = { onLogin(email, password, pin, brand) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !loading,
                restCorner = 28.dp,
                pressedCorner = 14.dp,
            ) {
                if (loading) LoadingIndicator() else Text("Sign in", fontWeight = FontWeight.SemiBold)
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
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("Bloo is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Confirm it's you to reach your vehicles.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        // Expressive morphing button: a big pill that squishes into a rounded
        // square as you press it, springing back on release.
        MorphButton(
            onClick = { authenticate() },
            restCorner = 36.dp,
            pressedCorner = 16.dp,
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 18.dp),
        ) {
            Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("Unlock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
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

    // Slot-machine settle haptic when a refresh lands and the numbers roll.
    val haptics = LocalHaptics.current
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.refreshing) {
        if (wasRefreshing && !state.refreshing) haptics?.slotSettle()
        wasRefreshing = state.refreshing
    }

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
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding(),
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
                        PagerDots(exPager.currentPage, count, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp))
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
                        PagerDots(pager.currentPage, pageCount, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp))
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
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding(),
            )
        }
        if (expandedIdx != null) {
            FloatingIcon(
                icon = Icons.Filled.SwapHoriz,
                description = "Flip columns",
                onClick = { vm.setColumnsFlipped(!appearance.columnsFlipped) },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 52.dp),
            )
        }
        FloatingIcon(
            icon = Icons.Filled.Settings,
            description = "Settings",
            onClick = { vm.openSettings() },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding(),
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
        // A transparent PNG renders edge-to-edge with no opaque box, so it blends
        // seamlessly into the pebble (fit, not crop, so the whole subject shows).
        val transparent = imageUrl.endsWith(".png", ignoreCase = true)
        AsyncImage(
            model = model,
            contentDescription = v.model,
            contentScale = if (transparent) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(if (transparent) Modifier else Modifier.clip(RoundedCornerShape(18.dp))),
        )
    }
}

@Composable
private fun ChargeFuelBar(status: VehicleStatus?, hasBattery: Boolean, hasFuel: Boolean, drivingLabel: String? = null) {
    // Primary metric: battery if the car has one, else fuel. Plug-in hybrids show
    // both — battery as the headline and fuel as a secondary line.
    val fuelPct = status?.fuelLevel
    val pct = status?.percentFor(hasBattery)
    val frac = ((pct ?: 0).coerceIn(0, 100)) / 100f
    val range = status?.rangeMiFor(hasBattery)
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
                    text = range?.let { "$it mi" } ?: "—",
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
        val targetPct = status?.evStatus?.targetForCurrentPlug()
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
 * Animates an item gliding to its new placement when siblings reorder around it,
 * instead of snapping. Used for the non-dragged pebbles so they slide out of the
 * way smoothly. The dragged item is offset manually and must not use this.
 */
private fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(IntOffset.Zero) }
    var anim by remember { mutableStateOf<Animatable<IntOffset, *>?>(null) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val a = anim ?: Animatable(target, IntOffset.VectorConverter).also { anim = it }
            if (a.targetValue != target) {
                scope.launch { a.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
            }
            a.value - target
        }
}


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
                        // Non-dragged items glide to their new slot; the dragged
                        // one is positioned manually via graphicsLayer below.
                        .then(if (dragging) Modifier else Modifier.animatePlacement())
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
    val haptics = LocalHaptics.current
    var pos by remember { mutableFloatStateOf(value) }
    var lastSnapped by remember { mutableFloatStateOf(value) }
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
                val snapped = snapToStep(it, valueRange, steps)
                // A crisp tick each time the finger crosses a step.
                if (snapped != lastSnapped) {
                    haptics?.tick()
                    lastSnapped = snapped
                }
                onValueChange(snapped)
            },
            onValueChangeFinished = {
                val target = snapToStep(pos, valueRange, steps)
                lastSnapped = target
                onValueChange(target)
                haptics?.click()
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
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Refreshable(v, state, vm) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Inset spacers (not padding) so content scrolls *behind* the bars.
            Spacer(Modifier.height(topInset + 8.dp))
            CarHeaderRow(v, state, onExpand, reserveHeaderEnd)
            // summary (image+gauge) and controls are reorderable pebbles too.
            PebbleList(v, state, vm)
            Spacer(Modifier.height(bottomInset + 16.dp))
        }
    }
}

/** Wide expanded view: critical info in one column, pebbles in the other. */
@Composable
private fun ExpandedCar(v: Vehicle, state: UiState, vm: AppViewModel, flipped: Boolean) {
    val hotspot = state.hotspotFor(v.vin)
        ?.takeIf { it in state.sectionsFor(v) && !state.isPebbleHidden(v.vin, it) }
    val controls: @Composable ColumnScope.() -> Unit = {
        CarHeaderRow(v, state, onExpand = null, reserveEnd = false)
        CriticalContent(v, state, vm)
        HotspotSlot(v, hotspot, state, vm)
    }
    val pebbles: @Composable ColumnScope.() -> Unit = {
        PebbleList(v, state, vm, exclude = setOfNotNull("summary", "controls", hotspot))
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
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Row(
                // Top padding clears the floating back / flip / settings buttons.
                Modifier.fillMaxSize().padding(
                    start = 16.dp, end = 16.dp,
                    top = topInset + 56.dp, bottom = bottomInset + 16.dp,
                ),
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

/** A friendly label for a pebble/section id. */
private fun sectionLabel(section: String): String = when (section) {
    "charge" -> "Charge / fuel"
    "climate" -> "Climate"
    "location" -> "Location"
    "info" -> "Car info"
    "diagnostics" -> "Diagnostics"
    "controls" -> "Lock / unlock"
    else -> section.replaceFirstChar { it.uppercase() }
}

/**
 * The dual-column "hot spot": a fixed slot under the car-info column. When a
 * pebble is pinned here it renders non-collapsible (always open); otherwise it's
 * a chooser to pin one. Pinning moves the pebble out of the scrolling list.
 */
@Composable
private fun HotspotSlot(v: Vehicle, hotspot: String?, state: UiState, vm: AppViewModel) {
    if (hotspot != null) {
        Box(Modifier.fillMaxWidth()) {
            // Forced-open + no drag handle: the hot spot pebble can't be collapsed.
            CompositionLocalProvider(LocalForceExpanded provides true) {
                SinglePebble(hotspot, v, state, vm, Modifier)
            }
            IconButton(
                onClick = { vm.setHotspot(v, null) },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Unpin from hot spot")
            }
        }
    } else {
        var menu by remember { mutableStateOf(false) }
        val options = state.sectionsFor(v).filter {
            it !in setOf("summary", "controls") && !state.isPebbleHidden(v.vin, it)
        }
        Box {
            OutlinedCard(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Pin a pebble here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                options.forEach { sec ->
                    DropdownMenuItem(
                        text = { Text(sectionLabel(sec)) },
                        onClick = { vm.setHotspot(v, sec); menu = false },
                    )
                }
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
    val haptics = LocalHaptics.current
    Box(
        Modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = state.refreshing,
                state = ptrState,
                onRefresh = { haptics?.diceRoll(); vm.refreshStatus(v) },
            ),
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
    val base = state.sectionsFor(v).filter {
        it !in exclude && !state.isPebbleHidden(v.vin, it)
    }
    // The optional AI summary pebble leads the stack when enabled.
    val sections = if (state.aiEnabled && "ai" !in exclude) listOf("ai") + base else base
    ReorderColumn(
        items = sections,
        keyOf = { it },
        onReorder = { vm.setSectionOrder(v, it) },
    ) { section, dragHandle, _ ->
        SinglePebble(section, v, state, vm, dragHandle)
    }
}

/** Renders one pebble by section name (used by the list and the hot spot). */
@Composable
private fun SinglePebble(section: String, v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val status = state.statusFor(v)
    val seats = state.seatConfigFor(v)
    val enabled = !state.loading
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
        "ai" -> AiPebble(v, state, vm, dragHandle)
        else -> Spacer(Modifier.fillMaxWidth())
    }
}

/** Optional on-device Gemini Nano summary of the car's last-refreshed status. */
@Composable
private fun AiPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val busy = v.vin in state.aiBusy
    val summary = state.aiSummaries[v.vin]
    Pebble(
        v, "ai", "AI summary", Icons.Filled.AutoAwesome, state, vm, dragHandle,
        summary = "On-device Gemini Nano",
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        headerAction = {
            PebbleActionButton(
                label = if (busy) "…" else "Summarize",
                icon = Icons.Filled.AutoAwesome,
                onClick = { vm.summarizeCar(v) },
                pending = busy,
            )
        },
    ) {
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "Summarize this car's last-refreshed status, generated privately on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Reflects the last refresh — tap Summarize to update.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/**
 * A Material 3 Expressive button whose shape springs from a soft pill to a
 * tighter rounded-square while pressed, then bounces back on release. Shared
 * infrastructure for any "morphing" button in the app.
 */
@Composable
private fun MorphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    restCorner: Dp = 28.dp,
    pressedCorner: Dp = 12.dp,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHaptics.current
    val corner by animateDpAsState(
        targetValue = if (pressed) pressedCorner else restCorner,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "morphCorner",
    )
    Button(
        onClick = { haptics?.click(); onClick() },
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * A unified selectable chip: a **pill** when unselected, morphing smoothly into a
 * filled **rounded box** when selected. Replaces ad-hoc FilterChips so selection
 * feels the same everywhere.
 */
@Composable
private fun MorphChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val corner by animateDpAsState(
        targetValue = if (selected) 12.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "chipCorner",
    )
    val container by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "chipBg",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = { haptics?.tick(); onClick() },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Pill when calm, rounded box when active — and it squishes further on press.
    val corner by animateDpAsState(
        targetValue = when {
            pressed -> 10.dp
            highlighted -> 18.dp
            else -> 34.dp
        },
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
        val haptics = LocalHaptics.current
        val onClick = { haptics?.heavy(); if (isOn == true) onDeactivate() else onActivate() }
        val container = if (highlighted) highlightColor else MaterialTheme.colorScheme.surfaceContainerHighest
        val contentColor = if (highlighted) highlightContentColor else MaterialTheme.colorScheme.onSurface
        Button(
            onClick = onClick,
            enabled = enabled && !pending,
            shape = RoundedCornerShape(corner),
            interactionSource = interaction,
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
    val haptics = LocalHaptics.current
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
                    .then(
                        if (forceExpanded) Modifier
                        else Modifier.clickable {
                            if (expanded) haptics?.tick() else haptics?.click()
                            vm.togglePebble(v, section)
                        },
                    )
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
    // Press-morphing is the standard for togglable controls.
    MorphButton(
        onClick = onClick,
        enabled = enabled && !pending,
        containerColor = container,
        contentColor = contentColor,
        restCorner = 20.dp,
        pressedCorner = 10.dp,
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
    val genesis = v.brand == Brand.GENESIS
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

    val infoSummary = if (status?.doorLock == true) "Locked" else "Unlocked"
    Pebble(v, "info", "Car info", Icons.Filled.Info, state, vm, dragHandle, summary = infoSummary) {
        when {
            status == null && state.refreshing -> Text("Fetching live status…")
            status == null -> Text("No status yet. Pull down to refresh.")
            else -> {
                SectionLabel("Status")
                status.engine?.let { StatusRow("Vehicle", if (it) "On" else "Off") }
                StatusRow("Doors", if (status.doorLock == true) "Locked" else "Unlocked")
                status.doorOpen?.openLabels()?.takeIf { it.isNotEmpty() }
                    ?.let { StatusRow("Doors open", it.joinToString(", ")) }
                status.windowOpen?.openLabels()?.takeIf { it.isNotEmpty() }
                    ?.let { StatusRow("Windows open", it.joinToString(", ")) }
                if (status.trunkOpen == true) StatusRow("Trunk", "Open")
                if (status.hoodOpen == true) StatusRow("Hood", "Open")
                if (status.acc == true) StatusRow("Accessory power", "On")
                StatusRow("Climate", if (status.airCtrlOn == true) "On" else "Off")
                if (status.defrost == true) StatusRow("Defrost", "On")
                status.battery?.batSoc?.let { StatusRow("12V battery", "$it%") }
                location?.let { StatusRow("Coordinates", it.coordString()) }
                rememberRelativeTime(state.fetchedAt(v))?.let { StatusRow("Last refreshed", it) }

                if (plugged) {
                    SectionLabel("Charging")
                    ev?.remainTime2?.atc?.value?.toInt()?.takeIf { it > 0 }
                        ?.let { StatusRow("Time to full", fmtMinutes(it)) }
                    chargerLabel(ev?.batteryPlugin)?.let { StatusRow("Charger", it) }
                    ev?.targetForCurrentPlug()?.let { StatusRow("Charge limit", "$it%") }
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

        SectionLabel(if (genesis) "Genesis owners" else "Hyundai owners")
        OwnerLinks(genesis, context, inApp)
    }
}

/**
 * Owner/assistance destinations as compact labelled buttons that flow 2+ per row
 * where they fit. Each says where it goes; phone icons dial, others open links.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnerLinks(genesis: Boolean, context: Context, inApp: Boolean) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (genesis) {
            LinkButton("Genesis app", Icons.Filled.OpenInNew) {
                openApp(context, listOf("com.stationdm.genesis"),
                    "https://play.google.com/store/apps/details?id=com.stationdm.genesis", inApp)
            }
            LinkButton("Owners site", Icons.Filled.OpenInNew) { openUrl(context, "https://owners.genesis.com", inApp) }
            LinkButton("Find a retailer", Icons.Filled.OpenInNew) { openUrl(context, "https://www.genesis.com/us/en/find-a-retailer.html", inApp) }
            LinkButton("Manuals", Icons.Filled.OpenInNew) { openUrl(context, "https://www.genesis.com/us/en/owners.html", inApp) }
            LinkButton("Roadside", Icons.Filled.Call) { dial(context, "8443409741") }
            LinkButton("Call collision", Icons.Filled.Call) { dial(context, "8443409741") }
            LinkButton("Collision guide", Icons.Filled.OpenInNew) { openUrl(context, "https://www.genesis.com/us/en/owners.html", inApp) }
        } else {
            LinkButton("Bluelink app", Icons.Filled.OpenInNew) {
                openApp(context, listOf("com.stationdm.bluelink"),
                    "https://play.google.com/store/apps/details?id=com.stationdm.bluelink", inApp)
            }
            LinkButton("Owners site", Icons.Filled.OpenInNew) { openUrl(context, "https://owners.hyundaiusa.com", inApp) }
            LinkButton("Find a dealer", Icons.Filled.OpenInNew) { openUrl(context, "https://www.hyundaiusa.com/us/en/dealer-locator", inApp) }
            LinkButton("Manuals", Icons.Filled.OpenInNew) { openUrl(context, "https://www.hyundaiusa.com/us/en/owner-resources", inApp) }
            LinkButton("Roadside", Icons.Filled.Call) { dial(context, "8002437766") }
            LinkButton("Call collision", Icons.Filled.Call) { dial(context, "8002437766") }
            LinkButton("Collision guide", Icons.Filled.OpenInNew) { openUrl(context, "https://www.hyundaiusa.com/us/en/owner-resources", inApp) }
        }
    }
}

/** A compact owner-area destination button (sized to its label, not full width). */
@Composable
private fun LinkButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    FilledTonalButton(
        onClick = { haptics?.click(); onClick() },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
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
    val diagSummary = if (rows.isEmpty()) "No data" else "${rows.count { !it.indent }} checks"
    Pebble(
        v, "diagnostics", "Diagnostics", Icons.Filled.ErrorOutline, state, vm, dragHandle,
        summary = diagSummary,
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
    // Track freshly-fetched targets (the initial remember is keyed only on VIN,
    // so a later refresh wouldn't otherwise move the sliders).
    LaunchedEffect(targets?.level(1)) { targets?.level(1)?.let { ac = it } }
    LaunchedEffect(targets?.level(0)) { targets?.level(0)?.let { dc = it } }
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
            StatusRow("Coordinates", loc.coordString())
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
                                    // Preserve transparency: alpha sources are saved as PNG (so the
                                    // background stays see-through and renders seamlessly), others JPEG.
                                    val alpha = image.hasAlpha()
                                    val ext = if (alpha) "png" else "jpg"
                                    val file = java.io.File(dir, "car_${vin}_${System.currentTimeMillis()}.$ext")
                                    file.outputStream().use {
                                        if (alpha) out.compress(Bitmap.CompressFormat.PNG, 100, it)
                                        else out.compress(Bitmap.CompressFormat.JPEG, 90, it)
                                    }
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
            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search settings & car data") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                    }
                },
                singleLine = true,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
          if (query.isNotBlank()) {
            SettingsSearchResults(query, vm, state, appearance, notif)
          } else {
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

            // Sounds & vibration
            SettingsCard("Sounds & vibration") {
                ToggleRow("Haptic feedback", appearance.hapticsEnabled) { vm.setHapticsEnabled(it) }
                Text(
                    "Crisp, distinct vibrations across the app — slider notches, a dice-roll on " +
                        "pull-to-refresh, and a slot-machine settle when fresh data lands. Intensity " +
                        "follows your system setting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // On-device AI — only when the device supports Gemini Nano.
            if (state.aiSupported) {
                SettingsCard("AI") {
                    ToggleRow("On-device AI (Gemini Nano)", state.aiEnabled) { vm.setAiEnabled(it) }
                    Text(
                        "Adds an AI summary pebble to each car and lets you ask the search " +
                            "box questions like \"what's the odometer of Daisy\". Everything runs " +
                            "privately on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Quick Settings tiles
            SettingsCard("Quick tiles") {
                Text(
                    "Assign each Quick Settings tile to a car and action, then add the tiles " +
                        "from your notification shade's edit screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToggleRow("Run command in background", state.tileBackground) { vm.setTileBackground(it) }
                Text(
                    if (state.tileBackground) "Tiles fire the command directly (no app open)."
                    else "Tiles open Bloo and run the command there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                for (i in 0 until com.bloo.bluelink.data.TILE_COUNT) {
                    TileAssignRow(i, state, vm)
                }
            }

            // App-icon shortcuts (long-press the launcher icon)
            SettingsCard("App shortcuts") {
                Text(
                    "Pick which long-press app-icon shortcuts appear. Launchers usually show " +
                        "only the first 4–5.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.vehicles.forEach { v ->
                    Spacer(Modifier.height(4.dp))
                    Text(v.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    com.bloo.bluelink.Shortcuts.ACTIONS.forEach { cmd ->
                        ToggleRow(
                            com.bloo.bluelink.Shortcuts.actionLabel(cmd),
                            state.isShortcutEnabled(v.vin, cmd),
                        ) { vm.setShortcutEnabled(v.vin, cmd, it) }
                    }
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
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsGroup("Powertrain") {
                        val current = state.powertrainOf(v)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Powertrain.entries.forEach { pt ->
                                MorphChip(selected = current == pt, onClick = { vm.setPowertrain(v, pt) }, label = pt.name)
                            }
                        }
                    }

                    SettingsGroup("Climate features") {
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
                    }

                    SettingsGroup("Photo") {
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
                    }

                    SettingsGroup("Identity & service") {
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
                    }

                    SettingsGroup("Sections shown") {
                        val labels = mapOf(
                            "climate" to "Climate",
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
}

/** A titled, boxed sub-group inside the per-car settings card, for hierarchy. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

private val SearchStopwords = setOf(
    "for", "the", "of", "show", "me", "what", "whats", "is", "a", "an", "to",
    "car", "cars", "my", "s", "setting", "settings", "get", "in",
)

private class SearchEntry(val title: String, val haystack: String, val content: @Composable () -> Unit)

/**
 * Live search over both app settings and per-car data/fields. Tokenises the
 * query (dropping filler words like "for"/"the"), so "odometer for xyz" finds
 * the odometer of the car named xyz, and "plate" lists every car's plate.
 */
@Composable
private fun SettingsSearchResults(
    query: String,
    vm: AppViewModel,
    state: UiState,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
) {
    val tokens = query.lowercase().split(Regex("[^a-z0-9%]+"))
        .filter { it.isNotBlank() && it !in SearchStopwords }

    val entries = ArrayList<SearchEntry>()
    fun add(title: String, keywords: String, content: @Composable () -> Unit) {
        entries.add(SearchEntry(title, "$title $keywords".lowercase(), content))
    }

    // --- App-wide settings ---
    add("Haptic feedback", "vibration vibrate buzz sound") {
        ToggleRow("Haptic feedback", appearance.hapticsEnabled) { vm.setHapticsEnabled(it) }
    }
    add("Text & layout scale", "display size zoom bigger") {
        StepRow("Scale", "${(appearance.uiScale * 100).roundToInt()}%")
        Slider(
            value = appearance.uiScale,
            onValueChange = { vm.setUiScale((it * 20).roundToInt() / 20f) },
            valueRange = 0.85f..1.3f,
        )
    }
    add("Colour vibrancy", "color saturation vivid material you") {
        StepRow("Vibrancy", "${(appearance.vibrancy * 100).roundToInt()}%")
        Slider(
            value = appearance.vibrancy,
            onValueChange = { vm.setVibrancy((it * 20).roundToInt() / 20f) },
            valueRange = 0.5f..1.6f,
        )
    }
    add("Open links in app", "browser tab links") {
        ToggleRow("Open links in app", appearance.linksInApp) { vm.setLinksInApp(it) }
    }
    add("Service due alerts", "notification reminder service") {
        ToggleRow("Service due alerts", notif.service) { vm.setNotifyService(it) }
    }
    add("Door-left-open alerts", "notification door open") {
        ToggleRow("Door-left-open alerts", notif.doorOpen) { vm.setNotifyDoor(it) }
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
        v.odometer?.trim()?.takeIf { it.isNotBlank() }?.let { odo ->
            add("Odometer · ${v.name}", "odometer mileage miles ${v.name}") { StatusRow("Odometer", "$odo mi") }
        }
        add("VIN · ${v.name}", "vin identification ${v.name} ${v.vin}") {
            SelectionContainer { StatusRow("VIN", v.vin) }
        }
        val battRange = st?.evStatus?.drvDistance?.firstOrNull()?.rangeByFuel?.totalAvailableRange?.value
        ((if (state.hasBattery(v)) battRange else null) ?: st?.dte?.value)?.toInt()?.let { r ->
            add("Range · ${v.name}", "range distance dte empty ${v.name}") { StatusRow("Range", "$r mi") }
        }
        if (state.hasBattery(v)) {
            st?.evStatus?.batteryStatus?.let { b ->
                add("Battery · ${v.name}", "battery charge soc percent ${v.name}") { StatusRow("Battery", "$b%") }
            }
            // Current-plug target if plugged in, else the configured AC home limit.
            val limit = st?.evStatus?.targetForCurrentPlug() ?: st?.evStatus?.reservChargeInfos?.level(1)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Powertrain.entries.forEach { pt ->
                    MorphChip(selected = state.powertrainOf(v) == pt, onClick = { vm.setPowertrain(v, pt) }, label = pt.name)
                }
            }
        }
        add("Last service · ${v.name}", "service maintenance mileage ${v.name}") {
            OutlinedTextField(
                value = state.lastServiceMiles[v.vin]?.toString() ?: "",
                onValueChange = { vm.setLastServiceMiles(v.vin, it.filter(Char::isDigit).toIntOrNull()) },
                label = { Text("Last service (mi)") },
                singleLine = true, shape = FieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // On-device AI reply (when enabled): answer the question in natural language,
    // while the structured data below still surfaces the exact values.
    if (state.aiEnabled) {
        LaunchedEffect(query) {
            if (query.isNotBlank()) {
                delay(450)
                vm.askAi(query)
            } else {
                vm.clearAiReply()
            }
        }
        val thinking = "search" in state.aiBusy
        val reply = state.aiSearchReply
        if (thinking || reply != null) {
            Card(
                Modifier.fillMaxWidth(),
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

    val results = if (tokens.isEmpty()) entries else entries.filter { e -> tokens.all { it in e.haystack } }
    if (results.isEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Text(
                "No matches for “$query”",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    results.forEach { e ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                e.content()
            }
        }
    }
}

private val TileActions = listOf("lock" to "Lock", "unlock" to "Unlock", "climate" to "Climate", "charge" to "Charge")

/** One Quick Settings tile's car + action assignment, via two dropdowns. */
@Composable
private fun TileAssignRow(index: Int, state: UiState, vm: AppViewModel) {
    val cfg = state.tileConfigs.getOrNull(index)
    val car = cfg?.let { c -> state.vehicles.firstOrNull { it.vin == c.first } }
    val action = cfg?.second
    var carMenu by remember { mutableStateOf(false) }
    var actMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Tile ${index + 1}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { carMenu = true }) { Text(car?.name?.take(10) ?: "Car") }
            DropdownMenu(expanded = carMenu, onDismissRequest = { carMenu = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = {
                    vm.setTileAssignment(index, null, null); carMenu = false
                })
                state.vehicles.forEach { v ->
                    DropdownMenuItem(text = { Text(v.name) }, onClick = {
                        vm.setTileAssignment(index, v.vin, action ?: "lock"); carMenu = false
                    })
                }
            }
        }
        Box {
            OutlinedButton(onClick = { actMenu = true }, enabled = car != null) {
                Text(TileActions.firstOrNull { it.first == action }?.second ?: "Action")
            }
            DropdownMenu(expanded = actMenu, onDismissRequest = { actMenu = false }) {
                TileActions.forEach { (cmd, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        car?.let { vm.setTileAssignment(index, it.vin, cmd) }; actMenu = false
                    })
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
    val haptics = LocalHaptics.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = { if (it) haptics?.toggleOn() else haptics?.toggleOff(); onChange(it) },
        )
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
        MorphChip(selected = heat, onClick = { onHeat(!heat) }, label = "Heat")
        Spacer(Modifier.width(8.dp))
        MorphChip(selected = cool, onClick = { onCool(!cool) }, label = "Cool")
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
    val haptics = LocalHaptics.current
    Button(
        onClick = { haptics?.heavy(); onClick() },
        enabled = enabled,
        modifier = modifier.height(64.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
