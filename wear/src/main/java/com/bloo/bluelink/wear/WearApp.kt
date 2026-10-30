package com.bloo.bluelink.wear

import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.PageDots
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.material3.AppScaffold
import com.bloo.uicommon.RevealText

/**
 * The watch app: a sign-in code check, then a page per car, swiped left and right. Each car's page is [WearCarPage].
 *
 * Every command goes through [send], so the face, the details page, the tile and a notification all run the same path.
 */
@Composable
fun WearApp(
    repo: WearSnapshotRepository,
    modifier: Modifier = Modifier,
    /** An action chosen on the watch's Tile, to run as a tap on the face would. */
    tileRequest: WearMainActivity.TileRequest? = null,
    /** Called once that action has been run, so the activity stops holding it. */
    onTileRequestHandled: () -> Unit = {},
) {
    val vehicles by repo.vehicles.collectAsStateWithLifecycle(initialValue = emptyList())
    val connected by repo.connected.collectAsStateWithLifecycle(initialValue = false)
    val signInCode by WearCredentialSync.signInCode.collectAsStateWithLifecycle()
    // The watch's own device lock is the only gate in front of the app, so a command runs as it is tapped.
    val send: (() -> Unit) -> Unit = { command -> command() }

    AppScaffold(modifier = modifier) {
        when {
            signInCode != null -> SignInCode(signInCode.orEmpty())
            vehicles.isEmpty() -> WearMessageScreen(
                icon = if (connected) AppIcons.DirectionsCar else AppIcons.PhoneAndroid,
                title = if (connected) "No cars yet" else "Waiting for your phone",
                detail = if (connected) "Add a car in Bloo on your phone." else "Open Bloo on your phone and keep it near the watch.",
            )
            else -> Cars(repo, vehicles, CarPageActions(send, tileRequest, onTileRequestHandled))
        }
    }
}

/** The phone is signing this watch in: the code to match, and the way out (the ✕ beside it) if the phone's end is gone. */
@Composable
private fun SignInCode(code: String) {
    WearMessageScreen(icon = AppIcons.Watch, title = "Sign in from your phone", detail = "Check this code matches the phone.") {
        RevealText(
            code,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        WearActionRow("Cancel", AppIcons.Close) { WearCredentialSync.cancel() }
    }
}

/** One page per car, swiped up and down. Opens on the car last shown, which is the car the tile shows, so a tile button acts on its own car. */
@Composable
private fun Cars(
    repo: WearSnapshotRepository,
    vehicles: List<com.bloo.bluelink.data.VehicleSnapshot>,
    actions: CarPageActions,
) {
    val (send, tileRequest, onTileRequestHandled) = actions
    val context = LocalContext.current
    val startPage = remember {
        WearTileCar.saved(context)?.let { vin -> vehicles.indexOfFirst { it.vin == vin } }?.coerceAtLeast(0) ?: 0
    }
    // The cars wrap: swiping on from the last car brings up the first, and the other way round.
    val wrap = rememberWearWrap(realCount = vehicles.size, initialRealIndex = startPage)
    val pagerState = wrap.pager
    // The tile follows the car on screen: it shows this car, with this car's actions, as the face does.
    LaunchedEffect(pagerState.currentPage, vehicles) {
        vehicles.getOrNull(wrap.wrap.real(pagerState.currentPage))?.vin?.let { vin ->
            if (WearTileCar.save(context, vin)) WearSurfaces.refresh(context)
        }
    }
    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            vehicles.getOrNull(wrap.wrap.real(page))?.let { car ->
                WearCarPage(
                    repo = repo,
                    car = car,
                    // A tile action belongs to the car the tile showed, so only the page on screen takes it: the pager
                    // composes its neighbours, and they would run it too.
                    actions = CarPageActions(
                        send = send,
                        tileRequest = tileRequest?.takeIf { page == pagerState.currentPage },
                        onTileRequestHandled = onTileRequestHandled,
                    ),
                )
            }
        }
        if (vehicles.size > 1) PageDots(count = vehicles.size, current = wrap.wrap.real(pagerState.currentPage))
    }
}
