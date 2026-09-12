package com.bloo.bluelink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Shown instead of silently restarting into [MainActivity] after an uncaught
 * exception -- see [BlooApplication]. Deliberately has no dependency on
 * BlooTheme/AppViewModel/anything else in the app: whatever just crashed it might be
 * reachable again through either of those, so this stays a plain MaterialTheme
 * default and nothing more. The whole point is a SELECTABLE stack trace that can be
 * read and copied directly off the device, with no adb already attached at the
 * moment of the crash.
 */
class CrashActivity : ComponentActivity() {
    companion object {
        const val EXTRA_STACK_TRACE = "stack_trace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "(no stack trace captured)"
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    SelectionContainer {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            Text("Bloo crashed", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Copy this and send it back -- it's the exact reason the app stopped.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(trace, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
