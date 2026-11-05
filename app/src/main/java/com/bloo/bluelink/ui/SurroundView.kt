package com.bloo.bluelink.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloo.bluelink.data.SurroundCapture
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.launch

/** A status or error panel: the one look the surround-view screens use for their messages. */
@Composable
internal fun SurroundNote(text: String, emphasised: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (emphasised) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasised) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

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
    var noteIsError by remember(v.vin) { mutableStateOf(false) }
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
                    noteIsError = false
                    note = if (captures.isNullOrEmpty()) "No photos stored for this car yet." else null
                }
                .onFailure {
                    noteIsError = true
                    note = it.message ?: "Bluelink wouldn't give the photos."
                }
            busy = false
        }
    }

    LaunchedEffect(v.vin) { refresh() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Surround view", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Photos the car took of its surroundings. Asking takes a little while; Refresh reads what Bluelink has stored.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching { repo.requestSurroundView(v) }
                                    .onSuccess {
                                        noteIsError = false
                                        note = "Asked the car for new photos. Tap Refresh in a minute or two."
                                    }
                                    .onFailure {
                                        // A refusal from Bluelink means this car cannot take surround-view photos: hide the feature for it.
                                        val raw = it.message ?: "Bluelink didn't take the request."
                                        val refused = raw.contains("HTTP 4") || raw.contains("HTTP 5") || raw.contains("errorMessage")
                                        if (refused) vm.setSurroundRefused(v, true)
                                        noteIsError = true
                                        note = if (refused) {
                                            "This car doesn't take surround-view photos, so Bloo hides the feature for it. Details: $raw"
                                        } else {
                                            raw
                                        }
                                    }
                                busy = false
                            }
                        },
                    ) { Text("Ask the car for photos") }
                    OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                note?.let { SurroundNote(it, emphasised = noteIsError) }
                val list = captures.orEmpty()
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (list.isEmpty() && !busy) {
                        item { SurroundNote("No photos on this phone yet. Ask the car for photos, then tap Refresh in a minute or two.") }
                    }
                    items(list) { c ->
                        SurroundCard(c) {
                            val where = saveSurroundPhoto(context, surroundPhotoName(c.utcTime), c.jpeg)
                            noteIsError = where == null
                            note = if (where != null) "Saved to $where" else "Couldn't save that photo."
                        }
                    }
                }
            }
        }
    }
}

/** One stored set of photos: when it was taken, the photo, and a Save button. */
@Composable
private fun SurroundCard(c: SurroundCapture, onSave: () -> Unit) {
    val bitmap = remember(c.jpeg) { BitmapFactory.decodeByteArray(c.jpeg, 0, c.jpeg.size)?.asImageBitmap() }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.utcTime ?: "Photos", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = onSave) { Text("Save") }
            }
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Surround-view photo",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}
