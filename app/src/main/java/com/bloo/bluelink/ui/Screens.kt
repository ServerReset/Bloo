@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package com.bloo.bluelink.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.R
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.SeatCapability
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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
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
}

// --- Login ----------------------------------------------------------------

@Composable
private fun LoginScreen(loading: Boolean, onLogin: (String, String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Bloo", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Hyundai Blue Link (US)", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

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
            onClick = { onLogin(email, password, pin) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) LoadingIndicator() else Text("Sign in")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Credentials are sent directly to Hyundai's telematics servers and " +
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

    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val expanded = state.expandedIndex?.takeIf { it in vehicles.indices }
    val showGrid = wide && expanded == null
    val actionTarget = vehicles[(expanded ?: state.currentIndex).coerceIn(0, vehicles.lastIndex)]

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (showGrid) "Garage" else actionTarget.name) },
            navigationIcon = {
                if (wide && expanded != null) {
                    IconButton(onClick = { vm.collapse() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back to garage")
                    }
                }
            },
            actions = {
                if (state.refreshing) {
                    ContainedLoadingIndicator(Modifier.size(36.dp).padding(end = 4.dp))
                }
                if (!showGrid) {
                    IconButton(onClick = { vm.refreshStatus(actionTarget) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh status")
                    }
                }
                IconButton(onClick = { vm.openSettings() }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            },
        )

        when {
            showGrid -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(340.dp),
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(vehicles) { i, v ->
                        VehicleSummaryCard(v, state.statusFor(v)) { vm.expand(i) }
                    }
                }
            }

            wide && expanded != null -> {
                VehicleDetailContent(vehicles[expanded], state, vm)
            }

            else -> {
                val pager = rememberPagerState(
                    initialPage = state.currentIndex.coerceIn(0, vehicles.lastIndex),
                ) { vehicles.size }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect { vm.selectIndex(it) }
                }
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                        VehicleDetailContent(vehicles[page], state, vm)
                    }
                    if (vehicles.size > 1) PagerDots(pager.currentPage, vehicles.size)
                }
            }
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
private fun HeroHeader(v: Vehicle, status: VehicleStatus?) {
    // Shape morph: corners spring outward while the car is charging.
    val charging = v.isEv && status?.evStatus?.batteryCharge == true
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
            Image(
                painter = painterResource(R.drawable.ic_car_hero),
                contentDescription = v.model,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(130.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(v.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${if (v.isEv) "Electric" else "Gas"} · ${v.model}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            ChargeFuelBar(v, status)
        }
    }
}

@Composable
private fun ChargeFuelBar(v: Vehicle, status: VehicleStatus?) {
    val pct = if (v.isEv) status?.evStatus?.batteryStatus else status?.fuelLevel
    val frac = ((pct ?: 0).coerceIn(0, 100)) / 100f
    val range = status?.evStatus?.drvDistance?.firstOrNull()
        ?.rangeByFuel?.totalAvailableRange?.value
        ?: status?.dte?.value
    val charging = v.isEv && status?.evStatus?.batteryCharge == true

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                pct?.let { "$it%" } ?: "—",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    range?.let { "${it.toInt()} mi" } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        charging -> "Charging"
                        v.isEv -> "Battery · range"
                        else -> "Fuel · range"
                    },
                    style = MaterialTheme.typography.labelMedium,
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
        LinearProgressIndicator(
            progress = { animatedFrac },
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp)),
        )
    }
}

// --- Summary card (wide layout) ------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleSummaryCard(v: Vehicle, status: VehicleStatus?, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Image(
                painter = painterResource(R.drawable.ic_car_hero),
                contentDescription = v.model,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(v.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ChargeFuelBar(v, status)
            Spacer(Modifier.height(8.dp))
            Text(
                "Doors ${if (status?.doorLock == true) "locked" else "unlocked"} · tap to open",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Full detail ----------------------------------------------------------

@Composable
private fun VehicleDetailContent(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val cap = state.seatCapabilityFor(v)
    val ventilated = state.ventilated[v.vin] ?: false
    val enabled = !state.loading

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeroHeader(v, status)

        // Expressive connected primary actions.
        ButtonGroup(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { vm.lock(v) }, enabled = enabled) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Text("  Lock")
            }
            Button(onClick = { vm.unlock(v) }, enabled = enabled) {
                Icon(Icons.Filled.LockOpen, contentDescription = null)
                Text("  Unlock")
            }
        }

        // Expressive floating toolbar of contextual actions.
        HorizontalFloatingToolbar(expanded = true, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { vm.refreshStatus(v) }, enabled = enabled) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = { vm.locate(v) }, enabled = enabled) {
                Icon(Icons.Filled.LocationOn, contentDescription = "Locate")
            }
            IconButton(onClick = { vm.stopClimate(v) }, enabled = enabled) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Stop climate")
            }
        }

        StatusCard(v, status, state.refreshing)
        DiagnosticsCard(status)
        ClimateCard(v, status, cap, ventilated, enabled, vm)
        if (v.isEv) ChargeLimitCard(v, status, enabled, vm)
        LocationCard(state.locations[v.vin], enabled) { vm.locate(v) }
    }
}

// --- Status ---------------------------------------------------------------

@Composable
private fun StatusCard(v: Vehicle, status: VehicleStatus?, refreshing: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when {
                status == null && refreshing -> Text("Fetching live status…")
                status == null -> Text("No status yet. Tap refresh to query the car.")
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
        }
    }
}

// --- Diagnostics ----------------------------------------------------------

@Composable
private fun DiagnosticsCard(status: VehicleStatus?) {
    if (status == null) return
    val rows = buildList {
        status.tirePressureLamp?.let { tp ->
            add("Tire pressure" to if (tp.hasWarning) "Warning" else "OK")
            tp.frontLeft?.let { add("  Front left" to warn(it)) }
            tp.frontRight?.let { add("  Front right" to warn(it)) }
            tp.rearLeft?.let { add("  Rear left" to warn(it)) }
            tp.rearRight?.let { add("  Rear right" to warn(it)) }
        }
        status.lowFuelLight?.let { add("Low fuel" to yesNo(it)) }
        status.washerFluidStatus?.let { add("Washer fluid" to if (it) "Low" else "OK") }
        status.breakOilStatus?.let { add("Brake fluid" to if (it) "Check" else "OK") }
        status.smartKeyBatteryWarning?.let { add("Key fob battery" to if (it) "Low" else "OK") }
        status.steerWheelHeat?.let { add("Steering wheel heat" to onOff(it)) }
        status.sideBackWindowHeat?.let { add("Rear defroster" to onOff(it)) }
        status.sideMirrorHeat?.let { add("Mirror heat" to onOff(it)) }
        status.evStatus?.pluggedInLabel?.let { add("Plug" to it) }
        status.evStatus?.remainTime2?.atc?.value?.let { add("Time to full" to "${it.toInt()} min") }
    }
    if (rows.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            rows.forEach { (label, value) -> StatusRow(label, value) }
        }
    }
}

private fun warn(v: Int) = if (v == 0) "OK" else "Warning"
private fun yesNo(v: Boolean) = if (v) "Yes" else "No"
private fun onOff(v: Int) = if (v == 0) "Off" else "On"

// --- Climate --------------------------------------------------------------

@Composable
private fun ClimateCard(
    v: Vehicle,
    status: VehicleStatus?,
    cap: SeatCapability,
    ventilated: Boolean,
    enabled: Boolean,
    vm: AppViewModel,
) {
    var tempF by remember(v.vin) { mutableIntStateOf(72) }
    var duration by remember(v.vin) { mutableIntStateOf(10) }
    var defrost by remember(v.vin) { mutableStateOf(false) }
    var steeringHeat by remember(v.vin) { mutableStateOf(false) }
    var fl by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var fr by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rl by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rr by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Climate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            StepRow("Temperature", "$tempF°F")
            Slider(
                value = tempF.toFloat(),
                onValueChange = { tempF = it.roundToInt() },
                valueRange = 62f..82f,
                steps = 19,
            )

            StepRow("Run time", "$duration min")
            Slider(
                value = duration.toFloat(),
                onValueChange = { duration = it.roundToInt() },
                valueRange = 1f..10f,
                steps = 8,
            )

            ToggleRow("Defrost", defrost) { defrost = it }
            if (status?.steerWheelHeat != null) {
                ToggleRow("Steering wheel heat", steeringHeat) { steeringHeat = it }
            }

            if (cap.any) {
                ToggleRow("Ventilated (cooled) seats", ventilated) { vm.setVentilatedSeats(v, it) }
                if (cap.frontLeft) SeatControl("Driver seat", fl, ventilated) { fl = it }
                if (cap.frontRight) SeatControl("Passenger seat", fr, ventilated) { fr = it }
                if (cap.rearLeft) SeatControl("Rear left seat", rl, ventilated) { rl = it }
                if (cap.rearRight) SeatControl("Rear right seat", rr, ventilated) { rr = it }
            }

            // Expressive split button: leading = Start, trailing = Stop.
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(
                        onClick = {
                            vm.startClimate(
                                v,
                                ClimateRequest(
                                    tempF = tempF,
                                    defrost = defrost,
                                    durationMinutes = duration,
                                    steeringWheelHeat = steeringHeat,
                                    seatFrontLeft = fl,
                                    seatFrontRight = fr,
                                    seatRearLeft = rl,
                                    seatRearRight = rr,
                                ),
                            )
                        },
                        enabled = enabled,
                    ) {
                        Icon(Icons.Filled.AcUnit, contentDescription = null)
                        Text("  Start climate")
                    }
                },
                trailingButton = {
                    SplitButtonDefaults.TrailingButton(
                        checked = false,
                        onCheckedChange = { vm.stopClimate(v) },
                        enabled = enabled,
                    ) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Stop")
                    }
                },
            )
        }
    }
}

@Composable
private fun SeatControl(label: String, level: SeatLevel, ventilated: Boolean, onChange: (SeatLevel) -> Unit) {
    val range = if (ventilated) SeatLevel.ventilatedRange else SeatLevel.heatOnlyRange
    val index = range.indexOf(level).let { if (it < 0) range.indexOf(SeatLevel.OFF) else it }
    Column {
        StepRow(label, range[index].label)
        Slider(
            value = index.toFloat(),
            onValueChange = { onChange(range[it.roundToInt().coerceIn(0, range.lastIndex)]) },
            valueRange = 0f..range.lastIndex.toFloat(),
            steps = (range.size - 2).coerceAtLeast(0),
        )
    }
}

// --- Charge limits --------------------------------------------------------

@Composable
private fun ChargeLimitCard(v: Vehicle, status: VehicleStatus?, enabled: Boolean, vm: AppViewModel) {
    val targets = status?.evStatus?.reservChargeInfos
    var ac by remember(v.vin) { mutableIntStateOf(targets?.level(1) ?: 80) }
    var dc by remember(v.vin) { mutableIntStateOf(targets?.level(0) ?: 80) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Charge limits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            StepRow("AC (home) target", "$ac%")
            Slider(
                value = ac.toFloat(),
                onValueChange = { ac = (it / 10f).roundToInt() * 10 },
                valueRange = 50f..100f,
                steps = 4,
            )
            StepRow("DC (fast) target", "$dc%")
            Slider(
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
}

// --- Location -------------------------------------------------------------

@Composable
private fun LocationCard(location: GeoLocation?, enabled: Boolean, onLocate: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (location != null) {
                StatusRow("Latitude", String.format("%.5f", location.latitude))
                StatusRow("Longitude", String.format("%.5f", location.longitude))
            } else {
                Text("Tap Locate to query the car's current position.")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandButton("Locate", Icons.Filled.LocationOn, Modifier.weight(1f), enabled, onLocate)
                if (location != null) {
                    OutlinedButton(
                        onClick = {
                            val uri = Uri.parse(
                                "geo:${location.latitude},${location.longitude}" +
                                    "?q=${location.latitude},${location.longitude}(My car)"
                            )
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Open in Maps") }
                }
            }
        }
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
                    FontChoice.PRODUCT_SANS to "Product Sans style (Poppins)",
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

@Composable
private fun CommandButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, contentDescription = label)
        Text("  $label")
    }
}
