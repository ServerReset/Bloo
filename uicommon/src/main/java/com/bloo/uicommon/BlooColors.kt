package com.bloo.uicommon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp

/**
 * Colour helper shared between the phone and watch Compose UIs.
 *
 * NOT to be confused with `com.bloo.bluelink.data.BlooColors` in :shared, which is a set of
 * packed-ARGB Int CONSTANTS usable from non-Compose surfaces (Glance widget, Protolayout tile).
 * This one holds Compose `Color` FUNCTIONS and depends on androidx.compose.ui.graphics, so it
 * cannot live in :shared and the two cannot merge. The name collision is deliberate-ish history;
 * import the right one for the surface.
 *
 * Foundation-only by design: no Material dependency, so it takes and returns bare `Color` and
 * never `MaterialTheme` colours.
 */
object BlooColors {
    /**
     * The default button fill colour. Used by [MorphButton] (or its wear twin)
     * so idle buttons read clearly against any surface they sit on, without
     * needing a border. Pushes the highest available surface tone slightly
     * toward `onSurface` — more in dark themes, less in light — so the
     * button always contrasts the surface behind it.
     */
    fun buttonContainer(surface: Color, onSurface: Color): Color {
        val dark = surface.luminance() < 0.5f
        return if (dark) lerp(surface, onSurface, 0.18f)
        else lerp(surface, onSurface, 0.20f)
    }

    // onAccent() and accentMuted() (plus a private Color.toArgbInt() only accentMuted used) were
    // deleted here: nothing on either surface ever called them. The object's old doc claimed all
    // three were "shared utilities between phone and watch", but only buttonContainer ever was.
    // The widget derives its own on-accent tone inline (CarWidget WidgetTheme.build) with a
    // different dark value, so routing it through a shared onAccent would have changed its colour,
    // not just its call site -- another reason this was dead rather than merely uncalled.
}
