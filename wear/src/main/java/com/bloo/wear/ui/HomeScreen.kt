package com.bloo.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bloo.wear.CarView
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel

@Composable
fun HomeScreen(vm: WearViewModel, ui: WearUi, onSettings: () -> Unit) {
    if (ui.cars.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No cars yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val pager = rememberPagerState { ui.cars.size }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            CarScreen(vm, ui, ui.cars[page], onSettings)
        }
        if (ui.cars.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(ui.cars.size) { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier
                            .size(if (on) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun CarScreen(vm: WearViewModel, ui: WearUi, car: CarView, onSettings: () -> Unit) {
    val state = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ListHeader { Text(car.name, textAlign = TextAlign.Center, maxLines = 1) } }

        item { ChargeCard(car) }

        item {
            ActionButton(
                label = if (car.locked == true) "Locked" else "Unlocked",
                icon = if (car.locked == true) Icons.Filled.Lock else Icons.Filled.LockOpen,
                active = car.locked == true,
                activeColor = MaterialTheme.colorScheme.primary,
                pending = "${car.vin}:doors" in ui.pending,
                onClick = { vm.toggleLock(car.vin) },
            )
        }

        item { ClimateControl(vm, ui, car) }

        if (car.hasBattery) {
            item {
                ActionButton(
                    label = if (car.charging == true) "Charging" else "Charge",
                    icon = Icons.Filled.Bolt,
                    active = car.charging == true,
                    activeColor = WearColors.chargeGreen,
                    pending = "${car.vin}:charge" in ui.pending,
                    onClick = { vm.toggleCharge(car.vin) },
                )
            }
        }

        item { DetailsCard(car) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = { vm.refreshStatus(car.vin) },
                    enabled = "${car.vin}:refresh" !in ui.pending,
                    modifier = Modifier.weight(1f),
                    label = { Text("Refresh", maxLines = 1) },
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                )
            }
        }
        item {
            FilledTonalButton(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Settings") },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun ChargeCard(car: CarView) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChargeRing(car.percent)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    car.rangeMi?.let { "$it mi" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (car.hasBattery) "Battery" else "Fuel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!car.hasLiveStatus) {
                    Text(
                        "synced",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClimateControl(vm: WearViewModel, ui: WearUi, car: CarView) {
    Column(Modifier.fillMaxWidth()) {
        ActionButton(
            label = if (car.climateOn == true) "Climate on" else "Climate",
            icon = Icons.Filled.Thermostat,
            active = car.climateOn == true,
            activeColor = MaterialTheme.colorScheme.tertiary,
            pending = "${car.vin}:climate" in ui.pending,
            onClick = { vm.toggleClimate(car.vin) },
        )
        if (car.climateOn != true) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { vm.setClimateTemp(-1) },
                    modifier = Modifier.size(40.dp),
                    label = { Text("–") },
                )
                Text(
                    "${ui.climateTempF}°F",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalButton(
                    onClick = { vm.setClimateTemp(1) },
                    modifier = Modifier.size(40.dp),
                    label = { Text("+") },
                )
            }
        }
    }
}

@Composable
private fun DetailsCard(car: CarView) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text("Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            StatusRow("Doors", if (car.doorsOpen.isEmpty()) "Closed" else car.doorsOpen.joinToString())
            StatusRow("Windows", if (car.windowsOpen.isEmpty()) "Closed" else car.windowsOpen.joinToString())
            if (car.tireWarning) StatusRow("Tires", "Check", valueColor = MaterialTheme.colorScheme.error)
            car.battery12v?.let { StatusRow("12V", "$it%") }
            car.pluggedIn?.let { StatusRow("Plug", if (it) "Plugged in" else "Unplugged") }
            car.odometer?.let { StatusRow("Odometer", it) }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    activeColor: Color,
    pending: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !pending,
        modifier = Modifier.fillMaxWidth(),
        colors = if (active) {
            ButtonDefaults.buttonColors(
                containerColor = activeColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        label = { Text(if (pending) "Sending…" else label, maxLines = 1) },
        icon = { Icon(icon, contentDescription = null) },
    )
}
