package com.bloo.bluelink.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
                Screen.Vehicles -> VehicleListScreen(state, vm)
                is Screen.Detail -> VehicleDetailPager(state, vm)
            }
        }
    }
}

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
        Text("Bloo", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
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
            if (loading) CircularProgressIndicator(Modifier.height(20.dp)) else Text("Sign in")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Credentials are sent directly to Hyundai's telematics servers and " +
                "stored only on this device.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleListScreen(state: UiState, vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("My Vehicles") },
            actions = {
                IconButton(onClick = { vm.loadVehicles() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
                OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Sign out")
                }
            },
        )
        if (state.loading && state.vehicles.isEmpty()) {
            Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        } else {
            LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.vehicles) { v ->
                    Card(onClick = { vm.openVehicle(v) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(v.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(v.model, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${if (v.isEv) "EV" else "ICE"} · VIN ${v.vin.takeLast(6)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleDetailPager(state: UiState, vm: AppViewModel) {
    val vehicles = state.vehicles
    if (vehicles.isEmpty()) return
    val initial = (state.screen as? Screen.Detail)?.index ?: 0
    val pagerState = rememberPagerState(initialPage = initial.coerceIn(0, vehicles.lastIndex)) { vehicles.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { vm.onPageSettled(it) }
    }

    val currentVehicle = vehicles[pagerState.currentPage]

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(currentVehicle.name) },
            navigationIcon = {
                IconButton(onClick = { vm.back() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { vm.refreshStatus(currentVehicle, forceRefresh = true) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh status")
                }
            },
        )

        if (vehicles.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                vehicles.indices.forEach { i ->
                    val selected = i == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (selected) 10.dp else 7.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            )
                    )
                }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val v = vehicles[page]
            val active = pagerState.settledPage == page
            if (active) {
                VehicleDetailContent(v, state, vm)
            } else {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        v.name,
                        Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleDetailContent(v: Vehicle, state: UiState, vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(v, state.status, state.loading)
        DiagnosticsCard(state.status)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CommandButton("Lock", Icons.Filled.Lock, Modifier.weight(1f), !state.loading) { vm.lock(v) }
            CommandButton("Unlock", Icons.Filled.LockOpen, Modifier.weight(1f), !state.loading) { vm.unlock(v) }
        }

        ClimateCard(v, state, vm)

        if (v.isEv) {
            ChargeLimitCard(v, state, vm)
        }

        LocationCard(state.location, !state.loading, onLocate = { vm.locate(v) })
    }
}

// --- Status ---------------------------------------------------------------

@Composable
private fun StatusCard(v: Vehicle, status: VehicleStatus?, loading: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when {
                status == null && loading -> Text("Fetching live status…")
                status == null -> Text("No status yet. Tap refresh to query the car.")
                else -> {
                    StatusRow("Doors locked", if (status.doorLock == true) "Yes" else "No")
                    status.doorOpen?.let { StatusRow("A door open", if (it.anyOpen) "Yes" else "No") }
                    status.trunkOpen?.let { StatusRow("Trunk", if (it) "Open" else "Closed") }
                    status.hoodOpen?.let { StatusRow("Hood", if (it) "Open" else "Closed") }
                    StatusRow("Climate", if (status.airCtrlOn == true) "On" else "Off")
                    status.engine?.let { StatusRow("Engine", if (it) "Running" else "Off") }
                    status.battery?.batSoc?.let { StatusRow("12V battery", "$it%") }
                    status.evStatus?.batteryStatus?.let { StatusRow("EV charge", "$it%") }
                    status.evStatus?.batteryCharge?.let { StatusRow("Charging", if (it) "Yes" else "No") }
                    val range = status.evStatus?.drvDistance?.firstOrNull()
                        ?.rangeByFuel?.totalAvailableRange?.value
                        ?: status.dte?.value
                    range?.let { StatusRow("Range", "${it.toInt()} mi") }
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
            if (tp.frontLeft != null) add("  Front left" to warn(tp.frontLeft))
            if (tp.frontRight != null) add("  Front right" to warn(tp.frontRight))
            if (tp.rearLeft != null) add("  Rear left" to warn(tp.rearLeft))
            if (tp.rearRight != null) add("  Rear right" to warn(tp.rearRight))
        }
        status.fuelLevel?.let { add("Fuel level" to "$it%") }
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
private fun ClimateCard(v: Vehicle, state: UiState, vm: AppViewModel) {
    var tempF by remember(v.vin) { mutableIntStateOf(72) }
    var duration by remember(v.vin) { mutableIntStateOf(10) }
    var defrost by remember(v.vin) { mutableStateOf(false) }
    var steeringHeat by remember(v.vin) { mutableStateOf(false) }
    var fl by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var fr by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rl by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rr by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }

    val cap = state.seatCapability
    val enabled = !state.loading

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
            if (state.status?.steerWheelHeat != null) {
                ToggleRow("Steering wheel heat", steeringHeat) { steeringHeat = it }
            }

            if (cap.any) {
                ToggleRow("Ventilated (cooled) seats", state.ventilatedSeats) { vm.setVentilatedSeats(v, it) }
                if (cap.frontLeft) SeatControl("Driver seat", fl, state.ventilatedSeats) { fl = it }
                if (cap.frontRight) SeatControl("Passenger seat", fr, state.ventilatedSeats) { fr = it }
                if (cap.rearLeft) SeatControl("Rear left seat", rl, state.ventilatedSeats) { rl = it }
                if (cap.rearRight) SeatControl("Rear right seat", rr, state.ventilatedSeats) { rr = it }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandButton("Start", Icons.Filled.AcUnit, Modifier.weight(1f), enabled) {
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
                }
                CommandButton("Stop", Icons.Filled.PowerSettingsNew, Modifier.weight(1f), enabled) {
                    vm.stopClimate(v)
                }
            }
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
private fun ChargeLimitCard(v: Vehicle, state: UiState, vm: AppViewModel) {
    val targets = state.status?.evStatus?.reservChargeInfos
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
            CommandButton("Set limits", Icons.Filled.Bolt, Modifier.fillMaxWidth(), !state.loading) {
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
