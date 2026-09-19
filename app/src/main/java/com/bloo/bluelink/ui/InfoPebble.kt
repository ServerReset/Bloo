@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Car-info pebble and owner links: InfoPebble, OwnerLinks -- extracted from
 * Pebbles.kt to keep the UI file focused. The buttons themselves are the shared
 * MorphActionButton (Morph.kt); this file no longer declares a button of its own.
 */

import android.content.Context
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.targetForCurrentPlug
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.serviceDue
import com.bloo.bluelink.data.nextServiceMiles
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.isPluggedOrCharging


// --- Car info (status + service + links combined) -------------------------

@Composable
internal fun InfoPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    val metric = appearance.unitSystem == "metric"
    val location = state.locations[v.vin]
    val odoInt = parseOdometerMiles(v.odometer)
    val plate = state.licensePlates[v.vin]
    val lastSvc = state.lastServiceMiles[v.vin]
    val interval = state.serviceIntervalMiles[v.vin]
    val nextDue = if (lastSvc != null && interval != null) nextServiceMiles(lastSvc, interval) else null
    val remaining = serviceDue(odoInt, lastSvc, interval)

    val ev = status?.evStatus
    val plugged = ev.isPluggedOrCharging

    // Tri-state: null (unknown -- no status yet, or the car hasn't reported lock state) must
    // NOT read as "Unlocked". A null summary is omitted by Pebble, so the header simply carries
    // no lock word until we actually know -- rather than asserting a state as fact in visible
    // text and to TalkBack. Matches CoverMainTile / StateControl, which already handle unknown.
    val infoSummary = status?.doorLock?.let { if (it) "Locked" else "Unlocked" }
    val coverGlance = LocalForceExpanded.current
    // NOT alwaysExpandedInSimpleMode: that flag is for pebbles with a single setting
    // that reads better inline without an expand/collapse control (see its own doc).
    // This one renders ~15 info rows (below), so forcing it always open in simple
    // mode just removed the ability to collapse it.
    Pebble(v, "info", "Car info", Icons.Filled.Info, state, vm, dragHandle, summary = infoSummary) {
        // COVER SCREEN only: lead with a big lock-state hero. On the cover the info
        // tile drops its header (so the "Locked/Unlocked" summary is otherwise
        // buried as one row among ~15). A large icon + word makes it the glance
        // value. Phone is untouched (coverGlance = LocalForceExpanded, false there).
        // No cover hero: this pebble's summary is already "Locked"/"Unlocked" and CoverTile
        // renders it as the tile's headline. The lock state used to appear THREE times on one
        // tile -- as the summary, as this hero, and again as the "Doors" status row below.
        when {
            status == null && state.refreshing -> Text("Fetching live status…")
            status == null -> Text("No status yet.")
            else -> {
                SectionLabel("Status")
                status.engine?.let { StatusRow("Vehicle", if (it) "On" else "Off") }
                // Absent when the lock state is unknown, matching the engine row above --
                // "Unlocked" was being shown for a car that simply hadn't reported it.
                status.doorLock?.let { StatusRow("Doors", if (it) "Locked" else "Unlocked") }
                status.doorOpen?.openLabels()?.takeIf { it.isNotEmpty() }
                    ?.let { StatusRow("Doors open", it.joinToString(", ")) }
                status.windowOpen?.openLabels()?.takeIf { it.isNotEmpty() }
                    ?.let { StatusRow("Windows open", it.joinToString(", ")) }
                if (status.trunkOpen == true) StatusRow("Trunk", "Open")
                if (status.hoodOpen == true) StatusRow("Hood", "Open")
                if (status.acc == true) StatusRow("Accessory power", "On")
                // Absent when climate state is unknown (airCtrlOn null), like the engine/doorLock
                // rows above -- "Off" was being shown as fact for a car that never reported it.
                status.airCtrlOn?.let { StatusRow("Climate", if (it) "On" else "Off") }
                if (status.defrost == true) StatusRow("Defrost", "On")
                status.airTemp?.let { t ->
                    t.value?.let { StatusRow("Climate setpoint", degLabel(it, appearance.useFahrenheit, t.unit)) }
                }
                status.percentFor(state.hasBattery(v))?.let {
                    StatusRow(if (state.hasBattery(v)) "Charge" else "Fuel", "$it%")
                }
                status.rangeMiFor(state.hasBattery(v))?.let { StatusRow("Range", formatDistance(it, metric)) }
                status.battery?.batSoc?.let { StatusRow("12V battery", "$it%") }
                // Comfort heaters (read-only; mirror/rear-window heat track defrost).
                status.steerWheelHeat?.takeIf { it != 0 }?.let { StatusRow("Steering wheel heat", "On") }
                status.sideMirrorHeat?.takeIf { it != 0 }?.let { StatusRow("Mirror heat", "On") }
                status.sideBackWindowHeat?.takeIf { it != 0 }?.let { StatusRow("Rear defroster", "On") }
                // Resolved place name when geocoding's landed (same source the
                // Location pebble and the AI summary both use); raw coordinates
                // ONLY as the fallback until it does, never as the steady state --
                // this row used to show coordString() unconditionally, the one
                // place in the app that never even tried to resolve an address.
                //
                // On the cover, the COMPACT form (street + ZIP, not street + city) -- this
                // row's own value column is narrow enough that the long form reliably wrapped
                // onto two lines, a real reported "looks bad" bug. Phone keeps the long form,
                // which has the room for it.
                location?.let {
                    val place = if (coverGlance) state.placeZips[v.vin] ?: state.placeNames[v.vin] else state.placeNames[v.vin]
                    StatusRow("Location", place ?: it.coordString())
                }
                rememberRelativeTime(state.fetchedAt(v))?.let { StatusRow("Last refreshed", it) }

                if (plugged) {
                    SectionLabel("Charging")
                    ev?.minutesToFull
                        ?.let { StatusRow("Time to full", fmtMinutes(it)) }
                    chargerLabel(ev?.batteryPlugin)?.let { StatusRow("Charger", it) }
                    ev?.targetForCurrentPlug()?.let { StatusRow("Charge limit", "$it%") }
                }
            }
        }

        // "Service & identity" (VIN/plate/odometer/service) and the owner-links block
        // are lookup/management surfaces with no at-a-glance value on a ~1-inch cover
        // tile, and they're what overflows it into a long scroll. Show them only on
        // the phone (not coverGlance). Odometer stays visible on the cover as one
        // quick row since it's genuinely glanceable.
        if (coverGlance) {
            odoInt?.let { StatusRow("Odometer", formatDistance(it, metric)) }
        } else {
            SectionLabel("Service & identity")
            SelectionContainer { StatusRow("VIN", v.vin) }
            if (!plate.isNullOrBlank()) StatusRow("License plate", plate)
            odoInt?.let { StatusRow("Odometer", formatDistance(it, metric)) }
            lastSvc?.let { StatusRow("Last service at", formatDistance(it, metric)) }
            nextDue?.let {
                val note = remaining?.let { r ->
                    if (r >= 0) " · ${formatDistance(r, metric)} to go" else " · overdue ${formatDistance(-r, metric)}"
                } ?: ""
                StatusRow("Next service due", "${formatDistance(it, metric)}$note")
            }
            if (lastSvc == null || interval == null) {
                Text(
                    "Set last-service mileage and a service interval in Settings to track service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel("${v.brand.label} owners")
            OwnerLinks(v, state, context)
        }
    }
}

/**
 * Owner/assistance destinations as compact labelled buttons that flow 2+ per row
 * where they fit. Each says where it goes; the phone icon dials, others open
 * links. All destinations come from [BrandLinks] - the per-brand single source
 * of truth - so nothing here is defined twice.
 *
 * In-car payments (Hyundai Pay) and Plug & Charge are deliberately absent:
 * they live only inside the OEM app with no public web page or documented deep
 * link, so a button could only open an unrelated marketing page - better to
 * omit them than mislead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OwnerLinks(v: Vehicle, state: UiState, context: Context) {
    val links = v.brand.links

    // ExpressiveButtonRow, not FlowRow: it wraps the same way, but a FlowRow has no notion of a
    // shared budget, so a pressed link button grew for real and simply shoved its neighbours
    // along -- the reported "they expand but just push the other buttons away". As a group the
    // line's total is fixed and the neighbours give the width back.
    @Composable
    fun group(title: String, content: @Composable () -> Unit) {
        SectionLabel(title)
        ExpressiveButtonRow(
            modifier = Modifier.fillMaxWidth(),
            spacing = 8.dp,
            lineSpacing = 8.dp,
            content = content,
        )
    }

    val isSamsung = remember { Build.MANUFACTURER.lowercase() == "samsung" }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group("App & account") {
            MorphActionButton(
                label = "${links.appName} app",
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                onClick = { openApp(context, listOf(links.appPackage), links.playStoreUrl) },
            )
            MorphActionButton(
                label = "Owners site",
                icon = Icons.Filled.Person,
                onClick = { openUrl(context, links.ownersUrl) },
            )
            // Features-on-Demand store (themes, lighting patterns…): ccNC-era
            // head units only - older Gen5W cars have nothing to buy. Honours
            // the user's own confirmed generation over the raw API guess.
            if (state.supportsConnectedStoreEffective(v)) {
                MorphActionButton(
                    label = "Car store",
                    icon = Icons.Filled.Storefront,
                    onClick = { openUrl(context, links.storeUrl) },
                )
            }
        }
        group("Service") {
            MorphActionButton(
                label = "Schedule service",
                icon = Icons.Filled.Build,
                onClick = { openUrl(context, links.serviceScheduleUrl) },
            )
            MorphActionButton(
                label = links.dealerLabel,
                icon = Icons.Filled.Place,
                onClick = { openUrl(context, links.dealerUrl) },
            )
            MorphActionButton(
                label = "Manuals",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = { openUrl(context, links.manualsUrl) },
            )
            MorphActionButton(
                label = "Roadside",
                icon = Icons.Filled.Call,
                onClick = { dial(context, links.roadsidePhone) },
            )
        }
        // Digital Key: Gen5W head units use DK1 (BLE/NFC dedicated app).
        // Gen3+ and all Kia models use DK2 (UWB via wallet).
        // Kia has no gen field so isGen5W is always false for them. Honours
        // the user's own confirmed generation over the raw API guess.
        val isGen5W = state.isGen5WEffective(v)
        group("Digital Car Key") {
            if (isGen5W) {
                when (v.brand) {
                    Brand.HYUNDAI -> MorphActionButton(
                        label = "Digital Key",
                        icon = Icons.Filled.VpnKey,
                        onClick = {
                            openApp(
                                context,
                                listOf("com.hyundaiusa.hyundai.digitalcarkey"),
                                "https://play.google.com/store/apps/details?id=com.hyundaiusa.hyundai.digitalcarkey",
                            )
                        },
                    )
                    Brand.GENESIS -> MorphActionButton(
                        label = "Digital Key",
                        icon = Icons.Filled.VpnKey,
                        onClick = {
                            openApp(
                                context,
                                listOf("com.genesisusa.genesis.digitalcarkey"),
                                "https://play.google.com/store/apps/details?id=com.genesisusa.genesis.digitalcarkey",
                            )
                        },
                    )
                    Brand.KIA, Brand.HYUNDAI_CA, Brand.GENESIS_CA, Brand.KIA_CA, Brand.HYUNDAI_EU -> Unit
                }
            } else {
                if (isSamsung) {
                    MorphActionButton(
                        label = "Digital Key",
                        icon = Icons.Filled.CreditCard,
                        onClick = {
                            openApp(context, listOf("com.samsung.android.spay"), "https://www.samsung.com/us/samsung-wallet/")
                        },
                    )
                } else {
                    MorphActionButton(
                        label = "Digital Key",
                        icon = Icons.Filled.AccountBalanceWallet,
                        onClick = {
                            openApp(
                                context,
                                listOf("com.google.android.apps.walletnfcrel", "com.google.android.apps.wallet"),
                                "https://pay.google.com/",
                            )
                        },
                    )
                }
            }
        }
    }
}

// LinkButton used to live here: the same tonal pill, minus the rim, re-declared for this
// one pebble. It IS the app's standard action button and nothing about it was link-specific,
// so it moved to Morph.kt as MorphActionButton, beside the rest of the button family, and
// these owner-area destinations are now just its first callers.
