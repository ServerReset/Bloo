package com.bloo.wear.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WearAction
import com.bloo.wear.WatchUi
import com.bloo.wear.WearViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

private val VehicleSnapshot.hasBattery: Boolean get() = isEv || charging != null

@Composable
fun WatchApp(vm: WearViewModel) {
    val ui by vm.ui.collectAsState()
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            ui.vehicles.isEmpty() -> EmptyState(ui)
            else -> Garage(ui, vm)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Garage(ui: WatchUi, vm: WearViewModel) {
    val cars = ui.vehicles
    val initial = remember(cars, ui.selectedVin) {
        cars.indexOfFirst { it.vin == ui.selectedVin }.coerceAtLeast(0)
    }
    val carPager = rememberPagerState(initialPage = initial) { cars.size }

    // Persist the settled car so it's restored on next launch.
    LaunchedEffect(carPager, cars) {
        snapshotFlow { carPager.settledPage }.distinctUntilChanged().collect { page ->
            cars.getOrNull(page)?.let { vm.selectVin(it.vin) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = carPager, modifier = Modifier.fillMaxSize()) { page ->
            CarTiles(cars[page], ui, vm)
        }
        if (cars.size > 1) {
            Dots(
                count = cars.size,
                current = carPager.currentPage,
                horizontal = true,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarTiles(car: VehicleSnapshot, ui: WatchUi, vm: WearViewModel) {
    val tiles = remember(car.vin, car.hasBattery) {
        buildList {
            add("main")
            add("climate")
            if (car.hasBattery) add("charge")
            add("refresh")
        }
    }
    val tilePager = rememberPagerState(initialPage = 0) { tiles.size }
    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = tilePager, modifier = Modifier.fillMaxSize()) { i ->
            when (tiles[i]) {
                "main" -> MainTile(car, ui, vm)
                "climate" -> ClimateTile(car, ui, vm)
                "charge" -> ChargeTile(car, ui, vm)
                "refresh" -> RefreshTile(car, ui, vm)
            }
        }
        if (tiles.size > 1) {
            Dots(
                count = tiles.size,
                current = tilePager.currentPage,
                horizontal = false,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
            )
        }
    }
}

// ---- Tiles ---------------------------------------------------------------

@Composable
private fun MainTile(car: VehicleSnapshot, ui: WatchUi, vm: WearViewModel) {
    TileScaffold(title = car.name) {
        ChargeRing(percent = car.percent, isEv = car.isEv)
        Spacer(Modifier.height(4.dp))
        car.rangeMi?.let {
            Text(
                "$it mi",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        val locked = car.locked == true
        val key = "${car.vin}:${WearAction.TOGGLE_LOCK}"
        WatchAction(
            label = if (locked) "Locked" else "Unlocked",
            icon = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            active = locked,
            activeColor = MaterialTheme.colorScheme.primary,
            pending = key in ui.pending,
            onClick = { vm.send(car.vin, WearAction.TOGGLE_LOCK) },
        )
    }
}

@Composable
private fun ClimateTile(car: VehicleSnapshot, ui: WatchUi, vm: WearViewModel) {
    TileScaffold(title = car.name) {
        Icon(
            Icons.Filled.Thermostat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(4.dp))
        val on = car.climateOn == true
        Text(
            if (on) "Climate on" else "Climate off",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        val key = "${car.vin}:${WearAction.TOGGLE_CLIMATE}"
        WatchAction(
            label = if (on) "Stop" else "Start",
            icon = Icons.Filled.Thermostat,
            active = on,
            activeColor = MaterialTheme.colorScheme.tertiary,
            pending = key in ui.pending,
            onClick = { vm.send(car.vin, WearAction.TOGGLE_CLIMATE) },
        )
    }
}

@Composable
private fun ChargeTile(car: VehicleSnapshot, ui: WatchUi, vm: WearViewModel) {
    TileScaffold(title = car.name) {
        Icon(
            Icons.Filled.Bolt,
            contentDescription = null,
            tint = WearColors.chargeGreen,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(4.dp))
        val charging = car.charging == true
        Text(
            if (charging) "Charging" else "Not charging",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        car.percent?.let {
            Text(
                "$it%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        val key = "${car.vin}:${WearAction.TOGGLE_CHARGE}"
        WatchAction(
            label = if (charging) "Stop" else "Start",
            icon = Icons.Filled.Bolt,
            active = charging,
            activeColor = WearColors.chargeGreen,
            pending = key in ui.pending,
            onClick = { vm.send(car.vin, WearAction.TOGGLE_CHARGE) },
        )
    }
}

@Composable
private fun RefreshTile(car: VehicleSnapshot, ui: WatchUi, vm: WearViewModel) {
    TileScaffold(title = car.name) {
        Icon(
            if (ui.phoneConnected) Icons.Filled.PhoneAndroid else Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (ui.phoneConnected) "Via phone" else "Standalone",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        val key = "${car.vin}:refresh"
        WatchAction(
            label = "Refresh",
            icon = Icons.Filled.Refresh,
            active = false,
            activeColor = MaterialTheme.colorScheme.primary,
            pending = key in ui.pending,
            onClick = { vm.refresh(car.vin) },
        )
    }
}

// ---- Building blocks -----------------------------------------------------

/** Centered, round-screen-safe column with a small car-name header. */
@Composable
private fun TileScaffold(title: String, content: @Composable () -> Unit) {
    val round = LocalConfiguration.current.isScreenRound
    val pad = if (round) 26.dp else 14.dp
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = pad, vertical = if (round) 18.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** The app's pill-shaped primary action, scaled for a watch. */
@Composable
private fun WatchAction(
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
        shape = RoundedCornerShape(if (active) 28 else 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) activeColor else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = if (active) activeColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Charge/fuel ring with the percent in the middle, echoing the phone gauge. */
@Composable
private fun ChargeRing(percent: Int?, isEv: Boolean) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill = if (isEv) WearColors.chargeGreen else MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val frac = (percent ?: 0).coerceIn(0, 100) / 100f
            if (frac > 0f) {
                drawArc(
                    color = fill,
                    startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            percent?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Car (horizontal) / tile (vertical) position indicator. */
@Composable
private fun Dots(count: Int, current: Int, horizontal: Boolean, modifier: Modifier = Modifier) {
    val arrangement = Arrangement.spacedBy(4.dp)
    if (horizontal) {
        Row(modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
            repeat(count) { Dot(it == current) }
        }
    } else {
        Column(modifier, verticalArrangement = arrangement, horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(count) { Dot(it == current) }
        }
    }
}

@Composable
private fun Dot(selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(if (selected) 7.dp else 5.dp).clip(CircleShape).background(color))
}

@Composable
private fun EmptyState(ui: WatchUi) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.DirectionsCar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (!ui.loaded) "Loading…" else "Open Bloo on your phone to sync your cars",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
