package com.bloo.bluelink.widget

import android.content.Context
import androidx.glance.GlanceStateDefinition
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.state.PreferencesGlanceStateDefinition
import com.bloo.bluelink.data.BootGate
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot

/** What a home-screen widget draws from: the saved cars, and whether the app is sealed after a restart (then there are none to show). */
internal data class WidgetCars(val sealed: Boolean, val cars: List<VehicleSnapshot>)

/** Reads the widgets' cars. Both widgets load the same way, so the sealed check and the snapshot read live here once. */
internal fun loadWidgetCars(context: Context): WidgetCars {
    val sealed = BootGate.isLocked(context)
    val cars = if (sealed) emptyList() else runCatching { SnapshotStore(context).current().vehicles }.getOrDefault(emptyList())
    return WidgetCars(sealed, cars)
}

/**
 * The base of the app's home-screen widgets: one size mode and one state definition, so each widget only says what it draws. EXACT, not
 * Responsive: Responsive renders the largest of a fixed set of nominal sizes that fits, and the nominal cell sizes are far smaller than a
 * modern launcher's real cells. Exact hands the layout the real size.
 */
internal abstract class BlooGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
}
