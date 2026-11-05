package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloo.bluelink.data.ChargeOrder
import com.bloo.bluelink.data.Vehicle
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
        runCatching { vm.canadaRepo(vm.brandOf(v)).chargeOrders(v, month.format(f), month.withDayOfMonth(month.lengthOfMonth()).format(f)) }
            .onSuccess { orders = it }
            .onFailure { error = it.message ?: "Bluelink wouldn't give the charging history for this car." }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Charging history", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Paid sessions this month (${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                val list = orders
                when {
                    error != null -> Text(error.orEmpty())
                    list == null -> CircularProgressIndicator()
                    list.isEmpty() -> Text("No paid charging sessions this month.")
                    else -> {
                        val kwh = list.sumOf { it.kwh ?: 0.0 }
                        val spent = list.sumOf { it.total ?: 0.0 }
                        val currency = list.firstNotNullOfOrNull { it.currency }
                        Text(
                            "${list.size} sessions · ${"%.1f".format(kwh)} kWh · ${"%.2f".format(spent)}${currency?.let { " $it" }.orEmpty()}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            list.forEach { ChargeOrderRow(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargeOrderRow(o: ChargeOrder) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(o.place ?: "Charging session", style = MaterialTheme.typography.titleSmall)
        val detail = listOfNotNull(o.started, o.address).joinToString(" · ")
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
        val cost = listOfNotNull(
            o.kwh?.let { "%.1f kWh".format(it) },
            o.total?.let { "%.2f${o.currency?.let { c -> " $c" }.orEmpty()}".format(it) },
        ).joinToString(" · ")
        if (cost.isNotBlank()) Text(cost, style = MaterialTheme.typography.bodySmall)
    }
}
