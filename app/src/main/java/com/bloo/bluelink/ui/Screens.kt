@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.FlowRowScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import coil.request.ImageRequest
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.LockTiming
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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.tan

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
    // left-to-right sweep so progress is felt until it completes. The effect is
    // keyed on `busy`, so it cancels as soon as work finishes.
    val busy = state.loading || state.pending.isNotEmpty()
    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        while (true) {
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
    // The cover-screen (flip-phone) layout draws its own tiles edge-to-edge and
    // needs no status-bar scrim; only the regular garage scroll wants one.
    val cfg = LocalConfiguration.current
    val compactCover = state.screen == Screen.Garage &&
        cfg.screenWidthDp < 600 && cfg.screenHeightDp < 520
    // Biometric lock overlay: blur the whole app behind it and fade the blur
    // away once unlocked.
    val lockBlur by animateDpAsState(
        targetValue = if (state.locked) 22.dp else 0.dp,
        animationSpec = tween(durationMillis = 450),
        label = "lockBlur",
    )
    val lockAlpha by animateFloatAsState(
        targetValue = if (state.locked) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "lockAlpha",
    )
    // A blurred snapshot of the whole app, recorded each frame, that floating
    // elements sample for a frosted-glass background.
    val backdrop = rememberGraphicsLayer()
    val blurPx = with(LocalDensity.current) { 24.dp.toPx() }
    backdrop.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Decal)
    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .blur(lockBlur)
            .drawWithContent {
                backdrop.record { this@drawWithContent.drawContent() }
                drawContent()
            }
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
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
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
                // Lock is an overlay (see LockOverlay), not a full screen.
                Screen.Locked -> Box(Modifier.fillMaxSize())
                Screen.Empty -> Box(Modifier.padding(padding)) { EmptyScreen(vm) }
                Screen.Onboarding -> OnboardingScreen(vm)
                Screen.Garage -> GarageScreen(state, vm)
                Screen.Settings -> SettingsScreen(vm)
            }
        }
    }
        // Fade-to-black scrim over the status bar so content stays legible as it
        // scrolls underneath. Skipped on the cover screen, which is self-contained.
        if (!compactCover) {
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
        // Biometric lock overlay, drawn over the blurred app; fades out on unlock.
        if (lockAlpha > 0.01f) {
            Box(Modifier.fillMaxSize().alpha(lockAlpha)) {
                LockOverlay(vm)
            }
        }
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

            // Ask for notifications here, on a tap — not silently on first launch.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var notifGranted by remember {
                    mutableStateOf(com.bloo.bluelink.data.Notifications.hasPermission(context))
                }
                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted -> notifGranted = granted }
                FilledTonalButton(
                    onClick = { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                    enabled = !notifGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (notifGranted) "Notifications enabled" else "Enable notifications")
                }
            }

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
    val cfg = LocalConfiguration.current
    // Shrink the hero on short cover screens; cap form width on tablets.
    val shortScreen = cfg.screenHeightDp < 520
    val heroHeight = if (shortScreen) 120.dp else 220.dp

    if (onCancel != null) BackHandler { onCancel() }

    Box(Modifier.fillMaxSize()) {
    AuroraBackground(Modifier.matchParentSize())
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Wordmark hero over the animated aurora (transparent hero background).
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "Bloo",
                    style = if (shortScreen) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge,
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

        Column(
            Modifier.widthIn(max = 480.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
}

/**
 * A softly-blurred, slowly-drifting "aurora" of colour blobs — the animated login
 * backdrop. Three blobs ease back and forth on different periods.
 */
@Composable
private fun AuroraBackground(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "aurora")
    val p1 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "p1")
    val p2 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "p2")
    val p3 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse), label = "p3")
    fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f
    Box(
        modifier
            .blur(90.dp)
            .drawBehind {
                drawRect(scheme.surface)
                fun blob(c: Color, fx: Float, fy: Float, r: Float) =
                    drawCircle(c, radius = size.minDimension * r, center = Offset(size.width * fx, size.height * fy))
                blob(scheme.primary.copy(alpha = 0.55f), mix(0.15f, 0.7f, p1), mix(0.2f, 0.45f, p2), 0.6f)
                blob(scheme.tertiary.copy(alpha = 0.5f), mix(0.85f, 0.35f, p2), mix(0.75f, 0.5f, p3), 0.55f)
                blob(scheme.secondary.copy(alpha = 0.5f), mix(0.5f, 0.4f, p3), mix(0.35f, 0.95f, p1), 0.55f)
            },
    )
}

// --- Lock -----------------------------------------------------------------

/**
 * The biometric lock, drawn as an overlay on top of the blurred app. High-contrast
 * white-on-scrim text reads over any wallpaper of cars behind it; a floating back
 * arrow returns to the login screen. Centered + width-capped so it sits well on
 * phones, flip-phone cover screens and tablets alike.
 */
@Composable
private fun LockOverlay(vm: AppViewModel) {
    val context = LocalContext.current
    val cfg = LocalConfiguration.current
    val compact = cfg.screenHeightDp < 440
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

    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            // Darken the blur for legibility, and swallow taps to the app behind.
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = noRipple, indication = null) {},
    ) {
        // Floating back arrow -> login.
        Surface(
            onClick = { vm.lockToLogin() },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.16f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to login")
            }
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 44.dp else 72.dp),
                tint = Color.White,
            )
            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
            Text(
                "Bloo is locked",
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Confirm it's you to reach your vehicles.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
            // White pill for maximum contrast over the dimmed blur.
            MorphButton(
                onClick = { authenticate() },
                modifier = Modifier.height(if (compact) 56.dp else ControlHeight),
                containerColor = Color.White,
                contentColor = Color.Black,
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 18.dp),
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Unlock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
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
private const val MIN_CARD_DP = 320

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GarageScreen(state: UiState, vm: AppViewModel) {
    val vehicles = state.vehicles
    if (vehicles.isEmpty()) return
    val appearance by vm.appearance.collectAsState()

    // Settle haptic when a refresh lands.
    val haptics = LocalHaptics.current
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.refreshing) {
        if (wasRefreshing && !state.refreshing) haptics?.slotSettle()
        wasRefreshing = state.refreshing
    }
    // Fade the page indicator out while refreshing (and during the settle), so the
    // squiggly indicator has the stage to itself; fade it back in when done.
    val dotsAlpha by animateFloatAsState(
        targetValue = if (state.refreshing) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "dotsFade",
    )

    val count = vehicles.size
    val cfg = LocalConfiguration.current
    val widthDp = cfg.screenWidthDp
    val heightDp = cfg.screenHeightDp
    val large = widthDp >= 600
    // Very small screens (e.g. flip-phone cover): tile layout, one section at a
    // time, swipe up/down between them; swipe left/right between cars.
    val compact = !large && heightDp < 520
    if (compact) {
        // The cover screen hosts Settings (and refresh) inside the main tile, so
        // there is no floating overlay here.
        CompactGarage(state, vm)
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
                        PagerDots(exPager.currentPage, count, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp).alpha(dotsAlpha))
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
                        PagerDots(pager.currentPage, pageCount, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp).alpha(dotsAlpha))
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
            (it != "ai" || state.aiEnabled) &&
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

    val carIndex = state.vehicles.indexOf(v).coerceAtLeast(0)
    val carCount = state.vehicles.size

    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = vPager, modifier = Modifier.fillMaxSize()) { page ->
            val i = ((page % tiles.size) + tiles.size) % tiles.size
            // Cover-screen tiles are always open (no collapsing) and fill the
            // screen height (scrolling internally only if taller).
            CompositionLocalProvider(
                LocalForceExpanded provides true,
                LocalPebbleFillHeight provides true,
            ) {
                // Extra end inset clears the vertical page-dots rail on the right.
                Box(
                    Modifier.fillMaxSize().padding(
                        start = 10.dp, top = 10.dp, bottom = 10.dp,
                        end = if (tiles.size > 1) 22.dp else 10.dp,
                    ),
                ) {
                    when (val tile = tiles[i]) {
                        "main" -> CompactMainTile(v, state, vm)
                        "climate" -> ClimatePebble(v, status, state.seatConfigFor(v), state, vm, Modifier)
                        "charge" -> ChargePebble(v, status, !state.loading, state, vm, Modifier)
                        "location" -> LocationPebble(v, state, vm, Modifier)
                        "info" -> InfoPebble(v, status, state, vm, Modifier)
                        "diagnostics" -> DiagnosticsPebble(v, status, state, vm, Modifier)
                        "ai" -> AiPebble(v, state, vm, Modifier)
                    }
                }
            }
        }
        // Car-switching dots, always at top-center (every tile, including main).
        if (carCount > 1) {
            PagerDots(
                current = carIndex,
                count = carCount,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp),
            )
        }
        // Vertical page dots on the right edge — show which pebble tile is visible.
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
    val carIndex = state.vehicles.indexOf(v).coerceAtLeast(0)
    val carCount = state.vehicles.size
    // A themed Surface establishes the correct content colour for ALL text inside
    // (otherwise text on the cover screen falls back to the default black).
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
    ) {
        Box(Modifier.fillMaxSize()) {
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
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The car-switching dots live at top-center of the screen
                    // (see CompactCar), not here.
                    Text(
                        v.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.refreshStatus(v) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = scheme.onSurface)
                    }
                    IconButton(onClick = { vm.openSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = scheme.onSurface)
                    }
                }
                // Centre the live-status + lock group so the tile reads as one
                // balanced block instead of top-clustered with a big gap below.
                Spacer(Modifier.weight(1f))
                LastUpdatedLabel(v, state)
                ChargeFuelBar(status, state.hasBattery(v), state.hasFuel(v), state.drivingLabel(v))
                Spacer(Modifier.height(6.dp))
                PrimaryActions(v, state, vm)
                Spacer(Modifier.weight(1f))
                Text(
                    "Swipe up for more",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
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
        // Frosted glass: real backdrop blur with a faint tint on top.
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        modifier = modifier.padding(12.dp).backdropBlur(CircleShape).size(44.dp),
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

    // The state line under the range: charging (with time/type) replaces it while
    // charging, then driving/parked, then a plain battery/fuel descriptor.
    val statusLine = when {
        charging -> buildString {
            append("Charging")
            chargeMinutes?.let { append(" · ${fmtMinutes(it)}") }
            chargeType?.let { append(" · $it") }
        }
        drivingLabel != null -> drivingLabel
        else -> if (hasBattery) "Battery" else "Fuel"
    }
    val statusColor = when {
        charging -> ChargeGreen
        drivingLabel == "Driving" || drivingLabel == "Running" -> MaterialTheme.colorScheme.primary
        else -> LocalContentColor.current.copy(alpha = 0.7f)
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
            Column(horizontalAlignment = Alignment.End) {
                RollingNumber(
                    text = range?.let { "$it mi" } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    statusLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = if (charging || drivingLabel == "Driving") FontWeight.Bold else FontWeight.Normal,
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
 * When true (cover-screen tiles), a pebble stretches to fill the available height
 * and scrolls internally if its content is taller — so each tile fills the screen.
 */
private val LocalPebbleFillHeight = staticCompositionLocalOf { false }

/** A headline number that cross-fades when it changes. */
@Composable
private fun RollingNumber(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight,
    color: Color = Color.Unspecified,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "num",
    ) { t -> Text(t, style = style, fontWeight = fontWeight, color = color) }
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
    // Optional cross-target drag hooks: [onDragMove] reports the live finger
    // position (window coords) of the dragged item; [onDragRelease] is called on
    // drop and, if it returns true, the drop was handled elsewhere (e.g. pinned
    // to the hot spot) so the normal reorder is skipped.
    onDragMove: ((key: Any, windowPointer: Offset) -> Unit)? = null,
    onDragRelease: ((key: Any) -> Boolean)? = null,
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
                    val handleCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val handle = Modifier
                        .onGloballyPositioned { handleCoords.value = it }
                        .pointerInput(k) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingKey = k; offsetY = 0f },
                        onDragEnd = {
                            val handled = onDragRelease?.invoke(k) ?: false
                            draggingKey = null; offsetY = 0f
                            if (!handled) onReorder(order)
                        },
                        onDragCancel = { onDragRelease?.invoke(k); draggingKey = null; offsetY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetY += dragAmount.y
                            handleCoords.value?.takeIf { it.isAttached }?.let {
                                onDragMove?.invoke(k, it.localToWindow(change.position))
                            }
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
 * A clean, fully custom slider: a rounded track with an accent fill, subtle step
 * ticks, and a circular thumb that springs to the nearest step. Drawn entirely on
 * a Canvas (no Material Slider) so its look is consistent and theme-driven.
 */
@Composable
private fun AnimatedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    // The thumb position is driven continuously so it tracks the finger exactly
    // while dragging (the underlying Slider runs with steps = 0). On release we
    // spring it to the nearest step, giving a small bounce as it settles.
    val anim = remember { Animatable(value) }
    var dragging by remember { mutableStateOf(false) }
    var prevStep by remember { mutableFloatStateOf(snapToStep(value, valueRange, steps)) }

    // Follow external value changes when the user isn't interacting.
    LaunchedEffect(value) {
        if (!dragging && !anim.isRunning && anim.value != value) anim.snapTo(value)
    }

    // Geometry of the fully custom track + thumb. Drawing it ourselves (thumb
    // included, so the stock thumb is zero-sized) keeps the rounded ends clean,
    // lets the thumb sit exactly on its tick, and insets the end ticks from the
    // track caps so they're not crammed against the edge.
    val trackThickness = 14.dp
    val thumbW = 6.dp
    val thumbH = 44.dp
    val gap = 6.dp
    val dotR = 2.5.dp
    // How far the thumb travel and the tick row are inset from the track caps.
    val edgePad = 14.dp

    val inactiveColor = scheme.surfaceContainerHighest
    val dotOnActive = scheme.onPrimary.copy(alpha = 0.7f)
    val dotOnInactive = scheme.onSurfaceVariant.copy(alpha = 0.5f)

    Slider(
        value = anim.value,
        onValueChange = { v ->
            dragging = true
            scope.launch { anim.snapTo(v) }
            // Haptic tick each time the finger crosses into a new step.
            val s = snapToStep(v, valueRange, steps)
            if (steps > 0 && s != prevStep) {
                haptics?.tick()
                prevStep = s
            }
            // Report the snapped value so the readout label shows real steps.
            onValueChange(s)
        },
        onValueChangeFinished = {
            dragging = false
            val target = snapToStep(anim.value, valueRange, steps)
            prevStep = target
            haptics?.click()
            onValueChange(target)
            // Bounce-settle the thumb onto the nearest step.
            scope.launch {
                anim.animateTo(
                    target,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
                )
            }
        },
        valueRange = valueRange,
        steps = 0,
        interactionSource = interactionSource,
        // The real thumb is invisible — we draw it ourselves in the track so the
        // thumb, track and ticks all share one inset coordinate space.
        thumb = { Box(Modifier.size(0.dp)) },
        track = { state ->
            val span2 = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
            val frac2 = ((state.value - valueRange.start) / span2).coerceIn(0f, 1f)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(thumbH),
            ) {
                val halfThumb = thumbW.toPx() / 2f
                val gapPx = gap.toPx()
                val padPx = edgePad.toPx()
                // Thumb + ticks travel within the inset band; the track bar itself
                // still spans the full width with rounded caps at the very edges.
                val travel = (size.width - 2 * padPx).coerceAtLeast(0f)
                val thumbX = padPx + travel * frac2
                val cy = size.height / 2f
                val th = trackThickness.toPx()
                val top = cy - th / 2f
                val radius = androidx.compose.ui.geometry.CornerRadius(th / 2f)
                val cut = halfThumb + gapPx

                // Inactive segment (right of the thumb, up to the right cap).
                val inStart = (thumbX + cut).coerceAtMost(size.width)
                if (inStart < size.width) {
                    drawRoundRect(
                        inactiveColor,
                        topLeft = Offset(inStart, top),
                        size = androidx.compose.ui.geometry.Size(size.width - inStart, th),
                        cornerRadius = radius,
                    )
                }
                // Active segment (left cap up to the thumb).
                val acEnd = (thumbX - cut).coerceAtLeast(0f)
                if (acEnd > 0f) {
                    drawRoundRect(
                        accent,
                        topLeft = Offset(0f, top),
                        size = androidx.compose.ui.geometry.Size(acEnd, th),
                        cornerRadius = radius,
                    )
                }
                // Tick dots — evenly spaced across the inset band, skipping any
                // that fall under the thumb.
                if (steps > 0) {
                    val n = steps + 2
                    val rPx = dotR.toPx()
                    for (i in 0 until n) {
                        val tf = i.toFloat() / (n - 1)
                        val x = padPx + travel * tf
                        if (kotlin.math.abs(x - thumbX) < cut) continue
                        drawCircle(
                            if (x <= thumbX) dotOnActive else dotOnInactive,
                            rPx,
                            Offset(x, cy),
                        )
                    }
                }
                // The thumb — a tall rounded bar centered on its value.
                val twPx = thumbW.toPx()
                drawRoundRect(
                    accent,
                    topLeft = Offset(thumbX - twPx / 2f, 0f),
                    size = androidx.compose.ui.geometry.Size(twPx, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(twPx / 2f),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun snapToStep(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    if (steps <= 0) return v.coerceIn(range.start, range.endInclusive)
    val inc = (range.endInclusive - range.start) / (steps + 1)
    val snapped = range.start + Math.round((v - range.start) / inc) * inc
    return snapped.coerceIn(range.start, range.endInclusive)
}

/**
 * Softly fades the top/bottom [length] of a vertically scrolling area instead of
 * hard-clipping it at the bounds. The fade only appears on an edge that has more
 * content past it, and eases in as you scroll toward it.
 */
private fun Modifier.fadingEdges(scroll: ScrollState, length: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val lenPx = length.toPx()
        val topAlpha = (scroll.value / lenPx).coerceIn(0f, 1f)
        val botAlpha = ((scroll.maxValue - scroll.value) / lenPx).coerceIn(0f, 1f)
        if (topAlpha > 0.001f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = lenPx,
                ),
                blendMode = BlendMode.DstIn,
                alpha = topAlpha,
            )
        }
        if (botAlpha > 0.001f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - lenPx,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
                alpha = botAlpha,
            )
        }
    }

// --- Backdrop blur (frosted glass behind floating elements) ---------------

/** The captured, blurred snapshot of the app, sampled by floating elements. */
private val LocalBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }

/**
 * Shared state for dragging a pebble onto (or off) the dual-column hot spot. The
 * dragged section and the live finger position (window coords) are tracked here,
 * and the hot-spot slot publishes its window bounds so we can tell when a drag is
 * hovering it.
 */
private class HotSeatDrag {
    var section by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var slotTopLeft by mutableStateOf(Offset.Zero)
    var slotSize by mutableStateOf(IntSize.Zero)
    val overSlot: Boolean
        get() = section != null && slotSize.width > 0 &&
            pointer.x in slotTopLeft.x..(slotTopLeft.x + slotSize.width) &&
            pointer.y in slotTopLeft.y..(slotTopLeft.y + slotSize.height)
}

private val LocalHotSeatDrag = staticCompositionLocalOf<HotSeatDrag?> { null }

/**
 * Frosted-glass background: draws the blurred [LocalBackdrop] snapshot of the app,
 * aligned to this element's on-screen position and clipped to [shape]. Falls back
 * to nothing (callers layer their own translucent tint on top) if no backdrop is
 * available.
 */
private fun Modifier.backdropBlur(shape: Shape): Modifier = composed {
    val layer = LocalBackdrop.current ?: return@composed this.clip(shape)
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.localToWindow(Offset.Zero) }
        .clip(shape)
        .drawBehind {
            translate(left = -origin.x, top = -origin.y) { drawLayer(layer) }
        }
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
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Show the floating name pill once the car name has scrolled out of view.
    val nameHidden by remember {
        derivedStateOf { scroll.value > with(density) { (topInset + 56.dp).toPx() } }
    }
    Refreshable(v, state, vm) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
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
        androidx.compose.animation.AnimatedVisibility(
            visible = nameHidden,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        ) {
            // Tap to jump back to the top (the main car-info pebble).
            Surface(
                onClick = { scope.launch { scroll.animateScrollTo(0) } },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 3.dp,
                modifier = Modifier.backdropBlur(CircleShape),
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(v.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Wide expanded view: critical info in one column, pebbles in the other. */
@Composable
private fun ExpandedCar(v: Vehicle, state: UiState, vm: AppViewModel, flipped: Boolean) {
    val hotspot = state.hotspotFor(v.vin)
        ?.takeIf { it in state.sectionsFor(v) && !state.isPebbleHidden(v.vin, it) }
    val hotDrag = remember { HotSeatDrag() }
    val controls: @Composable ColumnScope.() -> Unit = {
        CarHeaderRow(v, state, onExpand = null, reserveEnd = false)
        CriticalContent(v, state, vm)
        HotspotSlot(v, hotspot, state, vm)
    }
    val pebbles: @Composable ColumnScope.() -> Unit = {
        PebbleList(v, state, vm, exclude = setOfNotNull("summary", "controls", hotspot))
    }
    CompositionLocalProvider(LocalHotSeatDrag provides hotDrag) {
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
            val leftCol = if (isFlipped) pebbles else controls
            val rightCol = if (isFlipped) controls else pebbles
            // Inset spacers (not padding) so content scrolls *behind* the bars;
            // the leading spacer also clears the floating overlay buttons.
            val lead: @Composable ColumnScope.() -> Unit = { Spacer(Modifier.height(topInset + 52.dp)) }
            val trail: @Composable ColumnScope.() -> Unit = { Spacer(Modifier.height(bottomInset + 16.dp)) }
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { lead(); leftCol(); trail() }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { lead(); rightCol(); trail() }
            }
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
        // A small header row (not an overlay) so the Unpin control never covers
        // the pinned pebble's own header/actions.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Long-press + drag this row to unpin (or tap the button).
            val unpinHaptics = LocalHaptics.current
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(hotspot) {
                        var dragged = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragged = 0f; unpinHaptics?.tick() },
                            onDrag = { change, amt -> change.consume(); dragged += kotlin.math.abs(amt.x) + kotlin.math.abs(amt.y) },
                            onDragEnd = { if (dragged > 48f) vm.setHotspot(v, null) },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Pinned here — drag to unpin",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.setHotspot(v, null) }) { Text("Unpin") }
            }
            // Forced-open + no drag handle: the hot spot pebble can't be collapsed.
            CompositionLocalProvider(LocalForceExpanded provides true) {
                SinglePebble(hotspot, v, state, vm, Modifier)
            }
        }
    } else {
        var menu by remember { mutableStateOf(false) }
        val options = state.sectionsFor(v).filter {
            it !in setOf("summary", "controls") && !state.isPebbleHidden(v.vin, it)
        }
        val hotDrag = LocalHotSeatDrag.current
        val hovered = hotDrag?.overSlot == true
        val border = if (hovered) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
        Box(
            Modifier.onGloballyPositioned {
                hotDrag?.let { d -> d.slotTopLeft = it.localToWindow(Offset.Zero); d.slotSize = it.size }
            },
        ) {
            OutlinedCard(
                onClick = { menu = true },
                modifier = Modifier.fillMaxWidth(),
                border = border,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (hovered) "Drop to pin here" else "Drag a pebble here (or tap)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hovered) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
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
            Text(
                v.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${v.model} · ${state.powertrainLabel(v)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LastUpdatedLabel(v, state, Modifier.padding(top = 2.dp))
        }
        if (onExpand != null) {
            // A proper floating chip (was a hard-to-see bare icon).
            FloatingIcon(Icons.Filled.Fullscreen, "Expand to full screen", onExpand)
        }
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
    Surface(
        modifier = Modifier.fillMaxWidth().then(dragHandle).height(ControlHeight),
        shape = RoundedCornerShape(PebbleCornerCollapsed),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            PrimaryActions(v, state, vm)
        }
    }
}

/** The reorderable pebble stack for a car. */
@Composable
private fun PebbleList(v: Vehicle, state: UiState, vm: AppViewModel, exclude: Set<String> = emptySet()) {
    val sections = state.sectionsFor(v).filter {
        it !in exclude &&
            !state.isPebbleHidden(v.vin, it) &&
            (it != "ai" || state.aiEnabled)
    }
    val hotDrag = LocalHotSeatDrag.current
    ReorderColumn(
        items = sections,
        keyOf = { it },
        onReorder = { newVisible ->
            // Merge the reordered visible items back into the full section order so
            // excluded ones (the pinned hot-spot, summary, controls, hidden) keep
            // their slots instead of being dropped.
            val visibleSet = sections.toSet()
            val full = (state.sectionsFor(v) + com.bloo.bluelink.data.DEFAULT_SECTIONS).distinct()
            val queue = ArrayDeque(newVisible)
            val merged = full.map { s ->
                if (s in visibleSet && queue.isNotEmpty()) queue.removeFirst() else s
            }
            vm.setSectionOrder(v, merged)
        },
        // In the dual-column view, dragging a pebble onto the hot-spot slot pins it.
        onDragMove = hotDrag?.let { d ->
            { key, pointer -> d.section = key as String; d.pointer = pointer }
        },
        onDragRelease = hotDrag?.let { d ->
            { key ->
                val pin = d.overSlot
                d.section = null
                if (pin) { vm.setHotspot(v, key as String); true } else false
            }
        },
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
                label = "Summarize",
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
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        }
        Text(
            "Reflects the last refresh — tap Summarize to update.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PrimaryActions(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    // Doors sit inside a collapsed-pebble shaped card so the lock/unlock pill has
    // a "holding" container like every other section.
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PebbleCornerCollapsed),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // Extra start inset lines the "Locked/Unlocked" text up with the other
        // pebble titles; a small end inset nudges the button toward the edge.
        Box(Modifier.padding(start = 26.dp, end = 8.dp)) {
            StateControl(
                name = "",
                isOn = status?.doorLock,
                stateOn = "Locked", stateOff = "Unlocked",
                turnOn = "Lock", turnOff = "Unlock",
                icon = Icons.Filled.Lock, pending = state.isPending(v.vin, "doors"),
                onActivate = { vm.lock(v) }, onDeactivate = { vm.unlock(v) },
                highlightWhenOff = true,
                offTextColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The one button style used across the whole app. It rests as a **pill** and
 * becomes a **rounded rectangle** only while [active] (an on/toggled state) — or
 * momentarily while pressed. When [active], it fills with [activeContainerColor].
 * Its width springs (with a little overshoot) whenever the content width changes,
 * e.g. the label flips Start -> Stop.
 */
@Composable
private fun MorphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    activeContainerColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHaptics.current
    // 50% = a true pill; a lower percent = a rounded rectangle.
    val pct by animateFloatAsState(
        targetValue = if (active || pressed) 28f else 50f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "morphCorner",
    )
    val bg by androidx.compose.animation.animateColorAsState(
        if (active) activeContainerColor else containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "morphBg",
    )
    Button(
        onClick = { haptics?.click(); onClick() },
        modifier = modifier.animateContentSize(
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        ),
        enabled = enabled,
        shape = RoundedCornerShape(percent = pct.roundToInt()),
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = if (active) activeContentColor else contentColor,
        ),
        border = if (active) null else border,
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * Standard leading slot for a [MorphButton]: shows the [icon], or a same-sized
 * spinner while [pending], so the button width never changes just from loading.
 */
@Composable
private fun MorphButtonLabel(
    icon: ImageVector,
    label: String,
    pending: Boolean,
    iconSize: Dp = 18.dp,
    spinning: Boolean = false,
) {
    if (pending) {
        LoadingIndicator(Modifier.size(iconSize))
    } else {
        val transition = rememberInfiniteTransition(label = "iconSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 2200, easing = LinearEasing)),
            label = "iconAngle",
        )
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize).rotate(if (spinning) angle else 0f),
        )
    }
    Spacer(Modifier.width(8.dp))
    Text(label, fontWeight = FontWeight.SemiBold)
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
    // Which state is the "highlighted" (on) one.
    val highlighted = enabled && (if (highlightWhenOff) isOn == false else isOn == true)
    Row(
        Modifier.fillMaxWidth().height(ControlHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Fill the button's height so the status reads as one tall control.
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            if (name.isNotBlank()) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            val stateText = when {
                !enabled && disabledNote != null -> disabledNote
                pending -> "Sending…"
                isOn == true -> stateOn
                isOn == false -> stateOff
                else -> "Unknown"
            }
            val stateColor = when {
                !enabled -> LocalContentColor.current.copy(alpha = 0.7f)
                isOn == false && offTextColor != null -> offTextColor
                highlighted -> highlightColor
                else -> LocalContentColor.current.copy(alpha = 0.7f)
            }
            // With no title, the state itself is the headline (fills the height).
            Text(
                stateText,
                style = if (name.isBlank()) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                color = stateColor,
                fontWeight = FontWeight.Bold,
            )
        }
        val haptics = LocalHaptics.current
        // Pill when off, rounded rectangle + highlight colour when on — same as
        // the climate/charge controls.
        MorphButton(
            onClick = { haptics?.heavy(); if (isOn == true) onDeactivate() else onActivate() },
            enabled = enabled && !pending,
            active = highlighted,
            activeContainerColor = highlightColor,
            activeContentColor = highlightContentColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            // Same pill height as the pebble header actions (the row stays
            // ControlHeight tall, so the button is vertically centred in it).
            modifier = Modifier.heightIn(min = 50.dp),
        ) {
            MorphButtonLabel(icon, if (isOn == true) turnOff else turnOn, pending, iconSize = 22.dp)
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
    val fillHeight = LocalPebbleFillHeight.current
    Card(
        Modifier.fillMaxWidth().then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        // animateContentSize gives a smooth, correctly-measured collapse (no
        // post-animation size jump). Cover-screen tiles fill instead.
        Column(
            if (fillHeight) {
                Modifier.fillMaxHeight()
            } else {
                Modifier.animateContentSize(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow))
            },
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
                    .padding(horizontal = 16.dp, vertical = 6.dp),
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
                            color = LocalContentColor.current.copy(alpha = 0.7f),
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
                val bodyScroll = rememberScrollState()
                val bodyMod = if (fillHeight) {
                    Modifier.weight(1f).fadingEdges(bodyScroll).verticalScroll(bodyScroll)
                } else {
                    Modifier
                }
                Column(
                    bodyMod.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
        }
    }
}

/** Shared control height: a collapsed pebble matches the lock/unlock button. */
private val ControlHeight = 76.dp

/** Uniform collapsed-header height so every pebble lines up at the same size. */
private val PebbleHeaderHeight = ControlHeight
private val PebbleCornerCollapsed = 38.dp
private val PebbleCornerExpanded = 20.dp

/**
 * A pebble-header action button (climate/charge/locate/AI), shown even when the
 * pebble is collapsed. A pill that becomes a rounded rectangle (in
 * [activeContainer]) when [active] — e.g. climate on, charging. The width is
 * stable while loading and springs when the label flips Start <-> Stop.
 */
@Composable
private fun PebbleActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    pending: Boolean = false,
    active: Boolean = false,
    spinning: Boolean = false,
    activeContainer: Color = MaterialTheme.colorScheme.primary,
    activeContent: Color = MaterialTheme.colorScheme.onPrimary,
) {
    MorphButton(
        onClick = onClick,
        enabled = enabled && !pending,
        active = active,
        activeContainerColor = activeContainer,
        activeContentColor = activeContent,
        // A faint outline keeps the inactive pill's shape visible on any pebble
        // background (e.g. the location pebble's neutral surface).
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        modifier = Modifier.heightIn(min = 50.dp),
    ) {
        MorphButtonLabel(icon, label, pending, spinning = spinning)
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
    // Manufacturer-specific destinations, defined once and grouped below.
    val appPkg = if (genesis) "com.stationdm.genesis" else "com.stationdm.bluelink"
    val appName = if (genesis) "Genesis app" else "Bluelink app"
    val ownersUrl = if (genesis) "https://owners.genesis.com" else "https://owners.hyundaiusa.com"
    val dealerLabel = if (genesis) "Find a retailer" else "Find a dealer"
    val dealerUrl = if (genesis) "https://www.genesis.com/us/en/find-a-retailer.html"
        else "https://www.hyundaiusa.com/us/en/dealer-locator"
    val manualsUrl = if (genesis) "https://www.genesis.com/us/en/owners.html"
        else "https://www.hyundaiusa.com/us/en/owner-resources"
    val roadside = if (genesis) "8443409741" else "8002437766"

    @Composable
    fun group(title: String, content: @Composable FlowRowScope.() -> Unit) {
        SectionLabel(title)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group("App & account") {
            LinkButton(appName, Icons.Filled.OpenInNew) {
                openApp(context, listOf(appPkg), "https://play.google.com/store/apps/details?id=$appPkg", inApp)
            }
            LinkButton("Owners site", Icons.Filled.OpenInNew) { openUrl(context, ownersUrl, inApp) }
        }
        group("Service") {
            LinkButton(dealerLabel, Icons.Filled.OpenInNew) { openUrl(context, dealerUrl, inApp) }
            LinkButton("Manuals", Icons.Filled.OpenInNew) { openUrl(context, manualsUrl, inApp) }
        }
        group("Assistance") {
            LinkButton("Roadside", Icons.Filled.Call) { dial(context, roadside) }
            LinkButton("Call collision", Icons.Filled.Call) { dial(context, roadside) }
            LinkButton("Collision guide", Icons.Filled.OpenInNew) { openUrl(context, manualsUrl, inApp) }
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
    // Surface a warning affordance if any diagnostic reports a problem.
    val hasWarning = (status?.tirePressureLamp?.hasWarning == true) ||
        status?.lowFuelLight == true || status?.washerFluidStatus == true ||
        status?.breakOilStatus == true || status?.smartKeyBatteryWarning == true
    val diagSummary = if (rows.isEmpty()) "No data" else "${rows.count { !it.indent }} checks"
    Pebble(
        v, "diagnostics", "Diagnostics", Icons.Filled.ErrorOutline, state, vm, dragHandle,
        summary = diagSummary,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        headerAction = if (hasWarning) ({
            // Red warning chip; tapping it just expands the pebble like the arrow.
            FilledTonalButton(
                onClick = { vm.togglePebble(v, "diagnostics") },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 42.dp),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = "Has warnings", modifier = Modifier.size(18.dp))
            }
        }) else null,
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
                active = climateOn,
                spinning = climateOn,
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
                color = LocalContentColor.current.copy(alpha = 0.7f),
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
    // Deeper colour the stronger the setting; smoothly cross-fades as you slide
    // through neutral between cooling (blues) and heating (reds).
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = seatTint(current),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "seatTint",
    )
    Column {
        StepRow(label, current.label)
        AnimatedSlider(
            value = index.toFloat(),
            onValueChange = { onChange(range[it.roundToInt().coerceIn(0, range.lastIndex)]) },
            valueRange = 0f..range.lastIndex.toFloat(),
            steps = (range.size - 2).coerceAtLeast(0),
            accent = tint,
        )
    }
}

/** Seat colour by intensity: light->dark blue for cool, light->dark red for heat. */
@Composable
private fun seatTint(level: SeatLevel): Color = when {
    level.isCool -> androidx.compose.ui.graphics.lerp(
        Color(0xFF82B1FF), Color(0xFF1A45C0), ((level.apiValue - 3) / 2f).coerceIn(0f, 1f),
    )
    level.isHeat -> androidx.compose.ui.graphics.lerp(
        Color(0xFFFF8A80), Color(0xFFC62828), ((level.apiValue - 6) / 2f).coerceIn(0f, 1f),
    )
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// --- Charge limits --------------------------------------------------------

/**
 * Charge pebble: collapsed shows just the charge start/stop control; expand to
 * set limits and see charging info. Long-press to drag-reorder.
 */
@Composable
private fun ChargeEta(mins: Int) {
    Text(
        "~${fmtMinutes(mins)} to full",
        style = MaterialTheme.typography.labelSmall,
        color = LocalContentColor.current.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
    )
}

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
                active = charging,
                activeContainer = ChargeGreen,
                activeContent = Color.White,
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
        if (charging && mins != null) ChargeEta(mins)
        StepRow("DC (fast) target", "$dc%")
        AnimatedSlider(
            value = dc.toFloat(),
            onValueChange = { dc = (it / 10f).roundToInt() * 10 },
            valueRange = 50f..100f,
            steps = 4,
        )
        if (charging && mins != null) ChargeEta(mins)
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
                label = "Locate",
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
 * A small slippy map centred on the car, assembled from key-free OpenStreetMap
 * raw tiles (tile.openstreetmap.org). We compute the tiles needed to fill the box
 * with the car at the centre, draw each at its pixel offset, then drop a pin in
 * the middle. This avoids the flaky static-map render services that painted blank.
 */
@Composable
private fun CarMap(location: GeoLocation, modifier: Modifier = Modifier) {
    val zoom = 15
    val context = LocalContext.current
    val pinColor = MaterialTheme.colorScheme.error
    BoxWithConstraints(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val density = LocalDensity.current
        val tilePx = 256f
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val span = 1 shl zoom
        val latRad = Math.toRadians(location.latitude)
        val xTileF = (location.longitude + 180.0) / 360.0 * span
        val yTileF = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * span
        // World-pixel of the box's top-left so the car lands dead-centre.
        val originX = (xTileF * tilePx - wPx / 2f).toFloat()
        val originY = (yTileF * tilePx - hPx / 2f).toFloat()
        val firstX = floor(originX / tilePx).toInt()
        val firstY = floor(originY / tilePx).toInt()
        val lastX = floor((originX + wPx) / tilePx).toInt()
        val lastY = floor((originY + hPx) / tilePx).toInt()
        val tileDp = with(density) { tilePx.toDp() }
        for (tx in firstX..lastX) {
            for (ty in firstY..lastY) {
                if (ty < 0 || ty >= span) continue
                val wrappedX = ((tx % span) + span) % span
                val offX = tx * tilePx - originX
                val offY = ty * tilePx - originY
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("https://tile.openstreetmap.org/$zoom/$wrappedX/$ty.png")
                        .setHeader("User-Agent", "Bloo Bluelink companion app")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(tileDp)
                        .offset(x = with(density) { offX.toDp() }, y = with(density) { offY.toDp() }),
                )
            }
        }
        // A pin whose tip points at the centred car position.
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = "Car location",
            tint = pinColor,
            modifier = Modifier.align(Alignment.Center).size(40.dp).offset(y = (-20).dp),
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
    val settingsScroll = rememberScrollState()
    val settingsScope = rememberCoroutineScope()

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

  val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(settingsScroll)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Content scrolls behind the status bar; clear the floating pills.
            Spacer(Modifier.height(topInset + 56.dp))
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
          // Drop any stale AI answer once the search box is cleared.
          LaunchedEffect(query.isBlank()) { if (query.isBlank()) vm.clearAiReply() }
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
                    if (appearance.biometricLock) {
                        Spacer(Modifier.height(4.dp))
                        Text("Lock the app", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LockTiming.entries.forEach { t ->
                                MorphChip(
                                    selected = appearance.lockTiming == t,
                                    onClick = { vm.setLockTiming(t) },
                                    label = t.label,
                                )
                            }
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
                AnimatedSlider(
                    value = appearance.vibrancy,
                    onValueChange = { vm.setVibrancy((it * 20).roundToInt() / 20f) },
                    valueRange = 0.5f..1.6f,
                )
            }

            // Display scale
            SettingsCard("Display") {
                StepRow("Text & layout scale", "${(appearance.uiScale * 100).roundToInt()}%")
                AnimatedSlider(
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
                            "box plain questions like \"what's the odometer\". Everything runs " +
                            "privately on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.aiEnabled) {
                        ToggleRow("Summarize automatically", state.aiAuto) { vm.setAiAuto(it) }
                        Text(
                            if (state.aiAuto) {
                                "Summaries refresh on their own when you open a car, refresh its " +
                                    "status, or send a command. You can still tap Summarize anytime."
                            } else {
                                "Summaries only run when you tap Summarize on a car."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                val logScroll = rememberScrollState()
                SelectionContainer {
                    Text(
                        text = logs.joinToString("\n").ifBlank { "No activity yet." },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .fadingEdges(logScroll)
                            .verticalScroll(logScroll),
                    )
                }
            }
            Spacer(Modifier.height(bottomInset + 16.dp))
          }
        }
        // Floating back-arrow + "Settings" label pills over the content.
        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingIcon(Icons.Filled.ArrowBack, "Back to the app", { vm.closeSettings() })
            Surface(
                onClick = { settingsScope.launch { settingsScroll.animateScrollTo(0) } },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 3.dp,
            ) {
                Text(
                    "Settings",
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        // First-run coach mark pointing at the back arrow.
        if (state.showSettingsCoach) {
            Surface(
                onClick = { vm.dismissSettingsCoach() },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 60.dp, end = 12.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "That arrow takes you into the app when you're done here. Tap to dismiss.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
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
                            "charge" to "Charge / fuel",
                            "climate" to "Climate",
                            "location" to "Location",
                            "info" to "Car info",
                            "diagnostics" to "Diagnostics",
                            "ai" to "AI summary",
                        )
                        com.bloo.bluelink.data.HIDEABLE_SECTIONS
                            // The AI toggle only matters when AI is enabled for this device.
                            .filter { it != "ai" || state.aiEnabled }
                            .forEach { sec ->
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
        AnimatedSlider(
            value = appearance.uiScale,
            onValueChange = { vm.setUiScale((it * 20).roundToInt() / 20f) },
            valueRange = 0.85f..1.3f,
        )
    }
    add("Colour vibrancy", "color saturation vivid material you") {
        StepRow("Vibrancy", "${(appearance.vibrancy * 100).roundToInt()}%")
        AnimatedSlider(
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

private val TileActions = listOf("doors" to "Lock / unlock", "climate" to "Climate", "open" to "Open")

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
                        vm.setTileAssignment(index, v.vin, action ?: "doors"); carMenu = false
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
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.7f),
        )
        Text(value, fontWeight = FontWeight.Medium)
    }
}

/** A small bold group heading used inside the Car-info pebble. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = LocalContentColor.current.copy(alpha = 0.85f),
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
    MorphButton(
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
