package com.bloo.bluelink.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * The Bloo home-screen widget (Jetpack Glance). A pinned car's status sits on the
 * left — name, lock/drive state, charge/fuel percentage and range — beside a 2×2
 * grid of pill-shaped, user-assignable action buttons. Each button taps through a
 * biometric/PIN gate before running (see [WidgetAuthActivity]).
 */
class BlooWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(100.dp, 40.dp), DpSize(250.dp, 80.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val cfg = SettingsStore(context).widgetConfig(widgetId)
        val snapshots = SnapshotStore(context).current().vehicles
        val snap = cfg?.let { c -> snapshots.firstOrNull { it.vin == c.first } }
        val actions = cfg?.second.orEmpty().mapNotNull { WidgetAction.fromKey(it) }

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                if (size.height < 60.dp) {
                    // Compact mode
                    if (snap == null) {
                        UnconfiguredCompact(widgetId)
                    } else {
                        CompactWidgetBody(widgetId, snap, actions)
                    }
                } else {
                    if (snap == null) {
                        UnconfiguredView(widgetId)
                    } else {
                        WidgetBody(widgetId, snap, actions)
                    }
                }
            }
        }
    }

    @Composable
    private fun UnconfiguredView(widgetId: Int) {
        val context = LocalContext.current
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(16.dp)
                .clickable(actionStartActivity(configIntent(context, widgetId))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Tap to set up the Bloo widget",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium),
            )
        }
    }

    @Composable
    private fun WidgetBody(widgetId: Int, snap: VehicleSnapshot, actions: List<WidgetAction>) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusColumn(snap, GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(12.dp))
            ButtonGrid(widgetId, snap.vin, actions, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }

    @Composable
    private fun StatusColumn(snap: VehicleSnapshot, modifier: GlanceModifier) {
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                snap.name,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
            )
            val (stateLabel, stateColor) = stateOf(snap)
            Text(
                stateLabel,
                maxLines = 1,
                style = TextStyle(color = stateColor, fontWeight = FontWeight.Medium, fontSize = 13.sp),
            )
            Spacer(GlanceModifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    snap.percent?.let { "$it%" } ?: "—",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    if (snap.isEv) "battery" else "fuel",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
            snap.rangeMi?.let {
                Text(
                    "$it mi",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        }
    }

    @Composable
    private fun ButtonGrid(widgetId: Int, vin: String, actions: List<WidgetAction>, modifier: GlanceModifier) {
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Pill(widgetId, vin, actions.getOrNull(0), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                Pill(widgetId, vin, actions.getOrNull(1), GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Pill(widgetId, vin, actions.getOrNull(2), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                Pill(widgetId, vin, actions.getOrNull(3), GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun Pill(widgetId: Int, vin: String, action: WidgetAction?, modifier: GlanceModifier) {
        val context = LocalContext.current
        if (action == null) {
            Box(modifier) {}
            return
        }
        Row(
            modifier = modifier
                .height(44.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(22.dp)
                .clickable(actionStartActivity(authIntent(context, widgetId, vin, action))),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(action.icon),
                contentDescription = action.label,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(20.dp),
            )
        }
    }

    @Composable
    private fun CompactWidgetBody(widgetId: Int, snap: VehicleSnapshot, actions: List<WidgetAction>) {
        val context = LocalContext.current
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            actions.take(4).forEachIndexed { i, action ->
                if (i > 0) Spacer(GlanceModifier.width(4.dp))
                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .background(GlanceTheme.colors.secondaryContainer)
                        .cornerRadius(20.dp)
                        .clickable(actionStartActivity(authIntent(context, widgetId, snap.vin, action))),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        provider = ImageProvider(action.icon),
                        contentDescription = action.label,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                        modifier = GlanceModifier.size(16.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun UnconfiguredCompact(widgetId: Int) {
        val context = LocalContext.current
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clickable(actionStartActivity(configIntent(context, widgetId))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Tap to set up",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium),
            )
        }
    }

    /** Lock / unlock / driving state, with a colour cue. */
    @Composable
    private fun stateOf(snap: VehicleSnapshot): Pair<String, ColorProvider> = when {
        snap.engineOn == true -> "Driving" to GlanceTheme.colors.tertiary
        snap.locked == true -> "Locked" to GlanceTheme.colors.onSurfaceVariant
        snap.locked == false -> "Unlocked" to GlanceTheme.colors.error
        else -> "—" to GlanceTheme.colors.onSurfaceVariant
    }

    private fun authIntent(context: Context, widgetId: Int, vin: String, action: WidgetAction): Intent =
        Intent(context, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN
            // Unique data so each button gets a distinct PendingIntent.
            data = Uri.parse("bloo://widget/$widgetId/${action.key}")
            putExtra(WidgetAuthActivity.EXTRA_WIDGET_ID, widgetId)
            putExtra(WidgetAuthActivity.EXTRA_VIN, vin)
            putExtra(WidgetAuthActivity.EXTRA_ACTION, action.key)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun configIntent(context: Context, widgetId: Int): Intent =
        Intent(context, WidgetConfigActivity::class.java).apply {
            data = Uri.parse("bloo://widget/config/$widgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
