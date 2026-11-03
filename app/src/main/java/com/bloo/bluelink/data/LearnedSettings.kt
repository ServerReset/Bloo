package com.bloo.bluelink.data

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Says the stored settings changed under the screen (something was learned on the watch, say), so the screen reads them again. The
 * sync service emits it; the view model collects it and reloads the car's configuration.
 */
object LearnedSettings {
    val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
}
