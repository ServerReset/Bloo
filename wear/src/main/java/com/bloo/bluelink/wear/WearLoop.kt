package com.bloo.bluelink.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * Runs [loop] over and over while [active], and only while the watch is in front of the wearer: a face composed all night must not keep a
 * loop redrawing for nobody. When [active] is false, [onRest] puts the animation back at rest. The watch's ambient loops all take this shape.
 */
@Composable
internal fun LoopWhileInFront(active: Boolean, onRest: suspend () -> Unit, loop: suspend () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(active, lifecycleOwner) {
        if (!active) {
            onRest()
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) loop()
        }
    }
}
