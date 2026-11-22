package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.RevealText

/**
 * A full-screen dialog with a title and a Close button; [content] is the body, below the title row. Every full-screen sheet (the charging
 * history, the chargers nearby, the trip planner, the 3D surroundings, the car store) shares this frame, so it is written once here.
 * [edgeToEdge] lets the body run to the screen's edges (a web page); the title row keeps its margins either way.
 */
@Composable
internal fun FullScreenDialog(
    title: String,
    onDismiss: () -> Unit,
    edgeToEdge: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = GapSection, end = GapSection, top = GapSection, bottom = GapGroup),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RevealText(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    MorphTextButton("Close", onDismiss, icon = AppIcons.Close)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(if (edgeToEdge) Modifier else Modifier.padding(start = GapSection, end = GapSection, bottom = GapSection)),
                    content = content,
                )
            }
        }
    }
}
