package com.bloo.bluelink.ui

/**
 * Car-info pebble and owner links: InfoPebble, OwnerLinks -- extracted from Pebbles.kt to keep the
 * UI file focused.
 */

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.chargerLabel
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.degLabel
import com.bloo.bluelink.data.fmtMinutes
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.isPluggedOrCharging
import com.bloo.bluelink.data.lastServiceMiles
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.nextServiceMiles
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.serviceDue
import com.bloo.bluelink.data.serviceIntervalMiles
import com.bloo.bluelink.data.targetForCurrentPlug
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphActionButton
import com.bloo.uicommon.content

// --- Car info (status + service + links combined) -------------------------

@Composable
internal fun InfoPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    val metric = appearance.metricDistance
    val location = state.locations[v.vin]
    val odoInt = parseOdometerMiles(v.odometer)
    val plate = state.licensePlates[v.vin]
    val lastSvc = state.lastServiceMiles[v.vin]
    val interval = state.serviceIntervalMiles[v.vin]
    val nextDue = if (lastSvc != null && interval != null) nextServiceMiles(lastSvc, interval) else null
    val remaining = serviceDue(odoInt, lastSvc, interval)

    val ev = status?.evStatus
    val plugged = ev.isPluggedOrCharging

    // A null summary is omitted by Pebble, so the header simply carries no lock word until we
    // actually know -- rather than asserting a state as fact in visible text and to TalkBack.
    // Matches the lock control, which already handles unknown.
    val infoSummary = status?.doorLock?.let { if (it) "Locked" else "Unlocked" }
    val glance = LocalForceExpanded.current
    Pebble(v, "info", "Car info", AppIcons.Info, state, vm, modifier, summary = infoSummary) {
        PebbleStatusGate(status, state.refreshing) { status ->
            val hasBattery = state.hasBattery(v)
            PebbleSection("Status", AppIcons.Info) {
                status.engine?.let { StatusRow("Vehicle", if (it) "On" else "Off") }
                status.doorLock?.let { StatusRow("Doors", if (it) "Locked" else "Unlocked") }
                status.doorOpen?.openLabels()?.takeIf { it.isNotEmpty() }
                    ?.let { StatusRow("Doors open", it.joinToString(", ")) }
                status.windowOpen?.openLabels()?.takeIf { it.isNotEmpty() }
                    ?.let { StatusRow("Windows open", it.joinToString(", ")) }
                if (status.trunkOpen == true) StatusRow("Trunk", "Open")
                if (status.hoodOpen == true) StatusRow("Hood", "Open")
                if (status.acc == true) StatusRow("Accessory power", "On")
            }
            PebbleSection("Climate", AppIcons.Climate) {
                status.airCtrlOn?.let { StatusRow("Running", if (it) "On" else "Off") }
                if (status.defrost == true) StatusRow("Defrost", "On")
                status.airTemp?.let { t ->
                    t.value?.let { StatusRow("Setpoint", degLabel(it, appearance.useFahrenheit, t.unit)) }
                }
                // Comfort heaters (read-only; mirror/rear-window heat track defrost).
                status.steerWheelHeat?.takeIf { it != 0 }?.let { StatusRow("Steering wheel heat", "On") }
                status.sideMirrorHeat?.takeIf { it != 0 }?.let { StatusRow("Mirror heat", "On") }
                status.sideBackWindowHeat?.takeIf { it != 0 }?.let { StatusRow("Rear defroster", "On") }
            }
            PebbleSection(if (hasBattery) "Battery & range" else "Fuel & range", AppIcons.Bolt) {
                status.percentFor(hasBattery)?.let { StatusRow(if (hasBattery) "Charge" else "Fuel", "$it%") }
                status.rangeMiFor(hasBattery)?.let { StatusRow("Range", formatDistance(it, metric)) }
                status.battery?.level?.let { StatusRow("12V battery", "$it%") }
                // Only while plugged: what the charge is doing, in the same group as the charge itself.
                if (plugged) {
                    ev?.minutesToFull?.let { StatusRow("Time to full", fmtMinutes(it)) }
                    chargerLabel(ev?.batteryPlugin)?.let { StatusRow("Charger", it) }
                    ev?.targetForCurrentPlug()?.let { StatusRow("Charge limit", "$it%") }
                }
            }
            // Phone keeps the long form, which has the room for it.
            val place = location?.let {
                (if (glance) state.placeZips[v.vin] ?: state.placeNames[v.vin] else state.placeNames[v.vin]) ?: it.coordString()
            }
            val refreshed = rememberRelativeTime(state.fetchedAt(v))
            if (place != null || refreshed != null) {
                PebbleSection("Location", Icons.Filled.Place) {
                    // The address is long: it gets its own line under the label rather than the squeezed half of a row.
                    place?.let { StackedRow("Parked at", it) }
                    refreshed?.let { StatusRow("Last refreshed", it) }
                }
            }
        }

        // "Service & identity" (VIN/plate/odometer/service) and the owner-links block are
        // lookup/management surfaces with no at-a-glance value in a pinned/glance tile, and they're
        // what overflows it into a long scroll.
        if (glance) {
            odoInt?.let { StatusRow("Odometer", formatDistance(it, metric)) }
        } else {
            PebbleSection("Service & identity", Icons.Filled.Build) {
                SelectionContainer { StatusRow("VIN", v.vin, valueMono = true) }
                if (!plate.isNullOrBlank()) StatusRow("Licence plate", plate)
                odoInt?.let { StatusRow("Odometer", formatDistance(it, metric)) }
                lastSvc?.let { StatusRow("Last service at", formatDistance(it, metric)) }
                nextDue?.let {
                    val note = remaining?.let { r ->
                        if (r >= 0) " · ${formatDistance(r, metric)} to go" else " · overdue ${formatDistance(-r, metric)}"
                    } ?: ""
                    StatusRow("Next service due", "${formatDistance(it, metric)}$note")
                }
                if (lastSvc == null || interval == null) {
                    RevealText(
                        "Set last-service mileage and interval in Settings to track service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            PebbleSection("${v.brand.label} owners", Icons.Filled.Person, panel = false) {
                OwnerLinks(v, state, context)
            }
        }
    }
}

/**
 * Owner/assistance destinations as compact labelled buttons that flow 2+ per row where they fit.
 * Each says where it goes; the phone icon dials, others open links.
 */
@Composable
internal fun OwnerLinks(v: Vehicle, state: UiState, context: Context) {
    val links = v.brand.links

    @Composable
    fun group(title: String, content: @Composable () -> Unit) {
        // A quiet subheading: the group of links is already under "<brand> owners".
        RevealText(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExpressiveButtonRow(
            modifier = Modifier.fillMaxWidth(),
            spacing = GapRow,
            lineSpacing = GapRow,
            content = content,
        )
    }

    val isSamsung = remember { Build.MANUFACTURER.lowercase() == "samsung" }

    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
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
            // Features-on-Demand store (themes, lighting patterns…): ccNC-era head units only -
            // older Gen5W cars have nothing to buy. Honours the user's own confirmed generation
            // over the raw API guess.
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
        // Digital Key: Gen5W head units use DK1 (BLE/NFC dedicated app). Gen3+ and all Kia models
        // use DK2 (UWB via wallet). Kia has no gen field so isGen5W is always false for them.
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
