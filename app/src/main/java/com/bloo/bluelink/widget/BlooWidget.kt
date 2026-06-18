package com.bloo.bluelink.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
 *
 * Layout is fully fluid: [SizeMode.Exact] recomposes the content at the widget's
 * *actual* pixel size on every resize (unlike Responsive, which snaps to a fixed
 * set of breakpoints and stretches), and every dimension below — pane split, pill
 * height, icon and text sizes, padding — is derived from [LocalSize]. Buttons sit
 * in weight-distributed cells so they can never overflow or clip at the edge.
 */
class BlooWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val cfg = SettingsStore(context).widgetConfig(widgetId)
        val snapshots = SnapshotStore(context).current().vehicles
        val snap = cfg?.let { c -> snapshots.firstOrNull { it.vin == c.first } }
        val actions = cfg?.second.orEmpty().mapNotNull { WidgetAction.fromKey(it) }

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val w = size.width
                val h = size.height
                // Short widgets get the single-row compact layout; taller ones the
                // status + 2×2 grid layout.
                if (h < 64.dp) {
                    if (snap == null) UnconfiguredCompact(widgetId)
                    else CompactWidgetBody(widgetId, snap, actions, w)
                } else {
                    if (snap == null) UnconfiguredView(widgetId)
                    else WidgetBody(widgetId, snap, actions, w, h)
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

    /**
     * Status read-out beside a 2×2 action grid. Everything scales with [w]/[h]:
     * the status pane shrinks to a sliver on narrow widgets, the grid drops to a
     * single column when there's no room for two, and pill height fills the
     * available vertical space so the buttons grow with the widget.
     */
    @Composable
    private fun WidgetBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
        h: Dp,
    ) {
        val pad = if (h >= 120.dp) 16.dp else if (h >= 88.dp) 13.dp else 11.dp
        val corner = (h / 4).coerceIn(18.dp, 32.dp)
        // Two grid columns once there's comfortable width; otherwise a single one.
        val gridCols = if (w >= 150.dp) 2 else 1
        // The grid needs roughly 56 dp per column plus gaps; the rest is status.
        val gridMinWidth = (gridCols * 52).dp + 12.dp
        val contentW = w - pad * 2
        // Hide the status pane entirely on very narrow widgets so the buttons fit.
        val showStatus = contentW > gridMinWidth + 40.dp

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(corner)
                .padding(pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showStatus) {
                StatusColumn(snap, h, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(10.dp))
            }
            ButtonGrid(
                widgetId = widgetId,
                vin = snap.vin,
                actions = actions,
                columns = gridCols,
                contentHeight = h - pad * 2,
                modifier = if (showStatus) {
                    GlanceModifier.defaultWeight().fillMaxHeight()
                } else {
                    GlanceModifier.fillMaxWidth().fillMaxHeight()
                },
            )
        }
    }

    @Composable
    private fun StatusColumn(snap: VehicleSnapshot, h: Dp, modifier: GlanceModifier) {
        // Reveal more detail as the widget grows taller.
        val showName = h >= 96.dp
        val showKind = h >= 84.dp
        val percentSize = when {
            h >= 130.dp -> 30.sp
            h >= 104.dp -> 26.sp
            h >= 84.dp -> 22.sp
            else -> 19.sp
        }
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            if (showName) {
                Text(
                    snap.name,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                )
            }
            val (stateLabel, stateColor) = stateOf(snap)
            Text(
                stateLabel,
                maxLines = 1,
                style = TextStyle(color = stateColor, fontWeight = FontWeight.Medium, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.height(1.dp))
            Text(
                snap.percent?.let { "$it%" } ?: "—",
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = percentSize,
                ),
            )
            if (showKind) {
                Text(
                    if (snap.isEv) "battery" else "fuel",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
            snap.rangeMi?.let {
                Text(
                    "$it mi",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
    }

    /**
     * 2×2 (or 2×1 when narrow) action grid. Each cell is a weight-distributed
     * column/row so it can never overflow; pill height is the exact share of the
     * available height, keeping a true pill shape at any widget size.
     */
    @Composable
    private fun ButtonGrid(
        widgetId: Int,
        vin: String,
        actions: List<WidgetAction>,
        columns: Int,
        contentHeight: Dp,
        modifier: GlanceModifier,
    ) {
        val gap = 6.dp
        val pillH = ((contentHeight - gap) / 2).coerceIn(26.dp, 56.dp)
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            GridRow(widgetId, vin, actions.getOrNull(0), actions.getOrNull(1), columns, pillH, gap)
            Spacer(GlanceModifier.height(gap))
            GridRow(widgetId, vin, actions.getOrNull(2), actions.getOrNull(3), columns, pillH, gap)
        }
    }

    @Composable
    private fun GridRow(
        widgetId: Int,
        vin: String,
        first: WidgetAction?,
        second: WidgetAction?,
        columns: Int,
        pillH: Dp,
        gap: Dp,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionPill(widgetId, vin, first, pillH, GlanceModifier.defaultWeight())
            if (columns >= 2) {
                Spacer(GlanceModifier.width(gap))
                ActionPill(widgetId, vin, second, pillH, GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun ActionPill(
        widgetId: Int,
        vin: String,
        action: WidgetAction?,
        pillH: Dp,
        modifier: GlanceModifier,
    ) {
        val context = LocalContext.current
        if (action == null) {
            Box(modifier.height(pillH)) {}
            return
        }
        val iconSize = (pillH * 0.46f).coerceIn(15.dp, 24.dp)
        Box(
            modifier = modifier
                .height(pillH)
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(pillH / 2)
                .clickable(actionStartActivity(authIntent(context, widgetId, vin, action))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(action.icon),
                contentDescription = action.label,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(iconSize),
            )
        }
    }

    /**
     * One-row layout for a short (≈40 dp tall) widget: percent + state on the left,
     * as many circular action buttons as the width comfortably allows on the right.
     */
    @Composable
    private fun CompactWidgetBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
    ) {
        val context = LocalContext.current
        // Reserve ~110 dp for the status read-out, then ~38 dp per button.
        val room = ((w - 110.dp) / 38.dp).toInt().coerceAtLeast(0)
        val maxButtons = room.coerceIn(0, 4)
        val showState = w >= 140.dp
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    snap.percent?.let { "$it%" } ?: "—",
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                )
                if (showState) {
                    Spacer(GlanceModifier.width(6.dp))
                    val (stateLabel, stateColor) = stateOf(snap)
                    Text(
                        stateLabel,
                        maxLines = 1,
                        style = TextStyle(color = stateColor, fontWeight = FontWeight.Medium, fontSize = 12.sp),
                    )
                }
            }
            actions.take(maxButtons).forEach { action ->
                Spacer(GlanceModifier.width(5.dp))
                Box(
                    modifier = GlanceModifier
                        .size(32.dp)
                        .background(GlanceTheme.colors.secondaryContainer)
                        .cornerRadius(16.dp)
                        .clickable(actionStartActivity(authIntent(context, widgetId, snap.vin, action))),
                    contentAlignment = Alignment.Center,
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
