package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.bloo.bluelink.data.ChargeOrder
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.formatKwh
import com.bloo.bluelink.data.formatMoney
import com.bloo.bluelink.suspendRunCatching
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.RevealText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The paid charging sessions of this month for [v], from Hyundai's in-app charging. Read-only. Shows the sessions, the energy and the
 * total for the month. The month is the phone's current one; earlier months are not offered yet.
 */
@Composable
internal fun ChargeHistoryDialog(vm: AppViewModel, v: Vehicle, onDismiss: () -> Unit) {
    var orders by remember(v.vin) { mutableStateOf<List<ChargeOrder>?>(null) }
    var error by remember(v.vin) { mutableStateOf<String?>(null) }
    val month = remember { LocalDate.now().withDayOfMonth(1) }
    LaunchedEffect(v.vin) {
        val f = DateTimeFormatter.ofPattern("yyyyMMdd")
        suspendRunCatching { vm.canadaRepo(vm.brandOf(v)).chargeOrders(v, month.format(f), month.withDayOfMonth(month.lengthOfMonth()).format(f)) }
            .onSuccess { orders = it }
            .onFailure { error = it.message ?: "Bluelink wouldn't give the charging history for this car." }
    }
    FullScreenDialog(title = "Charging history", onDismiss = onDismiss) {
        SheetNote("Paid sessions this month (${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}).")
        SheetLoad(orders, error, empty = "No paid charging sessions this month.") { list ->
            val kwh = list.sumOf { it.kwh ?: 0.0 }
            val spent = list.sumOf { it.total ?: 0.0 }
            val currency = list.firstNotNullOfOrNull { it.currency }
            RevealText(
                "${list.size} session${if (list.size == 1) "" else "s"} · ${formatKwh(kwh)} · ${formatMoney(spent, currency)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = GapRow),
            )
            SheetList { list.forEach { ChargeOrderRow(it) } }
        }
    }
}

@Composable
private fun ChargeOrderRow(o: ChargeOrder) {
    SheetRow(
        title = o.place ?: "Charging session",
        lines = listOf(
            listOfNotNull(o.started, o.address).joinToString(" · "),
            listOfNotNull(o.kwh?.let(::formatKwh), o.total?.let { formatMoney(it, o.currency) }).joinToString(" · "),
        ),
    )
}
