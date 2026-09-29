package com.bloo.bluelink.wear

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The whole watch UI: a vertical pager of cars, each car a vertical stack of pebbles.
 *
 * Deliberately tiny for the first pass -- swipe UP/DOWN to move between cars (a
 * VerticalPager, which is what the round watch face affords), and each car page scrolls
 * its pebbles. Two pebbles only: basic controls (lock) and climate. Everything is built
 * from [WearPebble] + [MorphButton], so adding a pebble later is one more entry in the
 * page, not a new screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearGarageScreen(
    repo: WearSnapshotRepository,
    modifier: Modifier = Modifier,
) {
    val vehicles by repo.vehicles.collectAsStateWithLifecycle(initialValue = emptyList())
    if (vehicles.isEmpty()) {
        WearMessage("Open Bloo on your phone to set up a car.", modifier)
        return
    }
    // One page per car; the watch has no wrap pager, so this is a plain 0..n-1 range.
    val pagerState = rememberPagerState(pageCount = { vehicles.size })
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        vehicles.getOrNull(page)?.let { v ->
            WearCarPage(repo, v)
        }
    }
}

@Composable
private fun WearCarPage(repo: WearSnapshotRepository, v: VehicleSnapshot) {
    val scope = rememberCoroutineScope()
    // A command in flight disables both pebbles' buttons, so a double-tap can't fire twice.
    val pending = remember(v.vin) { MutableStateFlow(false) }
    val isPending by pending.collectAsStateWithLifecycle()

    fun run(command: com.bloo.bluelink.data.CarCommand) {
        if (isPending) return
        pending.value = true
        scope.launch {
            runCatching { repo.send(command) }
            pending.value = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The car's name rides at the top of its own page -- it is the one thing that
        // changes as you swipe between cars, so it is the page's identity.
        WearMessage(v.name, Modifier.fillMaxWidth())

        WearPebble(title = "Controls", icon = Icons.Filled.DirectionsCar) {
            val locked = v.locked == true
            WearActionRow(
                label = if (locked) "Unlock" else "Lock",
                icon = if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                enabled = !isPending,
            ) { run(repo.lock(v.vin)) }
        }

        WearPebble(title = "Climate", icon = Icons.Filled.AcUnit) {
            val on = v.climateOn == true
            WearActionRow(
                label = if (on) "Climate off" else "Climate on",
                icon = Icons.Filled.AcUnit,
                enabled = !isPending,
            ) { run(repo.climate(v.vin)) }
        }
    }
}

@Composable
private fun WearMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
