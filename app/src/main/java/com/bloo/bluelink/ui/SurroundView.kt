package com.bloo.bluelink.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloo.bluelink.data.SurroundCapture
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.launch

/**
 * The car's surround-view photos: the most recent set Bluelink stored, and a request for a new set. The car takes the photos some time
 * after it is asked, so "Refresh" reads whatever Bluelink has stored by then. Read-only apart from the request.
 */
@Composable
internal fun SurroundViewDialog(vm: AppViewModel, v: Vehicle, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // What is already on the phone shows at once; a refresh adds whatever the car has sent since.
    var captures by remember(v.vin) { mutableStateOf<List<SurroundCapture>?>(SurroundHistory.load(context, v.vin)) }
    var note by remember(v.vin) { mutableStateOf<String?>(null) }
    var busy by remember(v.vin) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repo = vm.repoFor(v)

    fun refresh() {
        busy = true
        scope.launch {
            runCatching { repo.lastSurroundView(v) }
                .onSuccess {
                    SurroundHistory.save(context, v.vin, it)
                    captures = SurroundHistory.load(context, v.vin)
                    note = if (captures.isNullOrEmpty()) "No photos stored for this car yet." else null
                }
                .onFailure { note = it.message ?: "Bluelink wouldn't give the photos." }
            busy = false
        }
    }

    LaunchedEffect(v.vin) { refresh() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Surround view", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Photos the car took around itself. Asking takes a little while; Refresh reads what Bluelink has stored.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching { repo.requestSurroundView(v) }
                                    .onSuccess { note = "Asked the car for new photos. Tap Refresh in a minute or two." }
                                    .onFailure { note = it.message ?: "Bluelink didn't take the request." }
                                busy = false
                            }
                        },
                    ) { Text("Ask the car for photos") }
                    OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
                if (busy) CircularProgressIndicator(Modifier.padding(8.dp))
                val list = captures.orEmpty()
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    list.forEach { c ->
                        val bitmap = remember(c.jpeg) { BitmapFactory.decodeByteArray(c.jpeg, 0, c.jpeg.size)?.asImageBitmap() }
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(c.utcTime ?: "Photos", style = MaterialTheme.typography.titleSmall)
                                OutlinedButton(onClick = {
                                    val where = saveSurroundPhoto(context, surroundPhotoName(c.utcTime), c.jpeg)
                                    note = if (where != null) "Saved to $where" else "Couldn't save that photo."
                                }) { Text("Save") }
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = "Surround-view photo",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
