package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import com.bloo.bluelink.data.SurroundCapture
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.suspendRunCatching
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.ProgressBar
import com.bloo.uicommon.RevealText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A status or error panel: the one look the surround-view screens use for their messages. */
@Composable
internal fun SurroundNote(text: String, emphasised: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.tintedPanel(if (emphasised) scheme.errorContainer.copy(alpha = 0.6f) else scheme.surfaceVariant.copy(alpha = 0.5f))) {
        BodySmallText(text, color = if (emphasised) scheme.onErrorContainer else scheme.onSurfaceVariant)
    }
}

/**
 * The car's surround-view photos: the most recent set Bluelink stored, and a request for a new set. The car takes the photos some time
 * after it is asked, so "Refresh" reads whatever Bluelink has stored by then. Read-only apart from the request.
 */
@Composable
internal fun SurroundViewDialog(vm: AppViewModel, v: Vehicle, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // What is already on the phone shows first (read off the main thread: it is several photos); a refresh adds whatever the car has
    // sent since.
    var captures by remember(v.vin) { mutableStateOf<List<SurroundCapture>?>(null) }
    var note by remember(v.vin) { mutableStateOf<String?>(null) }
    var noteIsError by remember(v.vin) { mutableStateOf(false) }
    var busy by remember(v.vin) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repo = vm.repoFor(v)

    fun refresh() {
        busy = true
        scope.launch {
            suspendRunCatching { repo.lastSurroundView(v) }
                .onSuccess {
                    captures = withContext(Dispatchers.IO) {
                        SurroundHistory.save(context, v.vin, it)
                        SurroundHistory.load(context, v.vin)
                    }
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

    LaunchedEffect(v.vin) {
        captures = withContext(Dispatchers.IO) { SurroundHistory.load(context, v.vin) }
        refresh()
    }

    FullScreenDialog(title = "Surround view", onDismiss = onDismiss) {
        SheetNote("Photos the car took of its surroundings. Asking takes a little while; Refresh reads what Bluelink has stored.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GapRow)) {
            MorphTextButton(
                text = "Ask the car for photos",
                onClick = {
                    busy = true
                    scope.launch {
                        suspendRunCatching { repo.requestSurroundView(v) }
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
                modifier = Modifier.weight(1f),
                enabled = !busy,
                emphasis = ButtonEmphasis.Confirm,
            )
            MorphTextButton("Refresh", onClick = { refresh() }, enabled = !busy, pending = busy)
        }
        if (busy) ProgressBar(null, Modifier.padding(vertical = GapRow))
        note?.let { SurroundNote(it, emphasised = noteIsError) }
        val list = captures.orEmpty()
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).padding(top = GapRow),
            verticalArrangement = Arrangement.spacedBy(GapGroup),
        ) {
            if (list.isEmpty() && !busy) {
                item { SurroundNote("No photos on this phone yet. Ask the car for photos, then tap Refresh in a minute or two.") }
            }
            items(list) { c ->
                SurroundCard(c) {
                    // A file write: off the main thread, so the tap does not stall the screen.
                    scope.launch {
                        val where = withContext(Dispatchers.IO) { saveSurroundPhoto(context, surroundPhotoName(c.utcTime), c.jpeg) }
                        noteIsError = where == null
                        note = if (where != null) "Saved to $where" else "Couldn't save that photo."
                    }
                }
            }
        }
    }
}

/**
 * One stored set of photos: when it was taken, the photo, and a Save button. The photo is decoded off the main thread at the size it is
 * shown, by the app's image loader: decoding the full JPEG during composition stalled the list on every card that scrolled in.
 */
@Composable
private fun SurroundCard(c: SurroundCapture, onSave: () -> Unit) {
    Column(Modifier.fillMaxWidth().innerGlassPanel(GapGroup), verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RevealText(c.utcTime ?: "Photos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            MorphTextButton("Save", onSave)
        }
        AsyncImage(
            model = c.jpeg,
            contentDescription = "Surround-view photo",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().clip(SmallShape),
        )
    }
}
