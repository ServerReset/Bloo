package com.bloo.bluelink.wear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.CarCommand
import com.bloo.bluelink.data.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The whole watch UI: swipe left/right between cars, scroll up/down a car's pebbles.
 *
 * Built from Wear's own shells so it behaves like a watch app should -- [AppScaffold]
 * for the ambient/time chrome, [ScreenScaffold] for the round-screen padding and
 * position indicator, [ScalingLazyColumn] so the pebbles shrink toward the curved edges
 * and scroll with the crown. The two pebbles (Controls, Climate) share [WearPebble], so
 * adding a third is one more entry here and nowhere else.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearGarageScreen(
    repo: WearSnapshotRepository,
    modifier: Modifier = Modifier,
) {
    val vehicles by repo.vehicles.collectAsStateWithLifecycle(initialValue = emptyList())
    AppScaffold(modifier = modifier) {
        if (vehicles.isEmpty()) {
            ScreenScaffold(timeText = { TimeText() }) {
                WearMessage("Open Bloo on your phone to add a car.")
            }
            return@AppScaffold
        }
        // One page per car. The watch has no wrap pager, so this is a plain 0..n-1 range;
        // swiping left/right is the "switch cars" gesture.
        val pagerState = rememberPagerState(pageCount = { vehicles.size })
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            vehicles.getOrNull(page)?.let { v -> WearCarPage(repo, v) }
        }
    }
}

@Composable
private fun WearCarPage(repo: WearSnapshotRepository, v: VehicleSnapshot) {
    val scope = rememberCoroutineScope()
    // A command in flight disables both pebbles' buttons, so a double-tap can't fire twice.
    val pending = remember(v.vin) { MutableStateFlow(false) }
    val isPending by pending.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    fun run(command: CarCommand) {
        if (isPending) return
        pending.value = true
        scope.launch { runCatching { repo.send(command) } ; pending.value = false }
    }

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                // The car's name is the page's identity -- the one thing that changes as
                // you swipe between cars.
                WearMessage(v.name)
            }
            item {
                WearPebble(title = "Controls", icon = Icons.Filled.DirectionsCar) {
                    val locked = v.locked == true
                    WearActionRow(
                        label = if (locked) "Unlock" else "Lock",
                        icon = if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        enabled = !isPending,
                    ) { run(repo.lock(v.vin)) }
                }
            }
            item {
                WearPebble(title = "Climate", icon = Icons.Filled.AcUnit) {
                    val on = v.climateOn == true
                    WearActionRow(
                        label = if (on) "Climate off" else "Climate on",
                        icon = Icons.Filled.AcUnit,
                        enabled = !isPending,
                        active = on,
                    ) { run(repo.climate(v.vin)) }
                }
            }
        }
    }
}

@Composable
private fun WearMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
