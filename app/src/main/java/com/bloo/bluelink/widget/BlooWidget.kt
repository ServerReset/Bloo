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
        setOf(
            DpSize(110.dp, 40.dp),
            DpSize(180.dp, 40.dp),
            DpSize(110.dp, 80.dp),
            DpSize(180.dp, 80.dp),
            DpSize(260.dp, 80.dp),
            DpSize(320.dp, 110.dp),
        )
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
                val isCompact = size.height < 60.dp
                if (isCompact) {
                    if (snap == null) {
                        UnconfiguredCompact(widgetId)
                    } else {
                        CompactWidgetBody(widgetId, snap, actions, size.width)
                    }
                } else {
                    if (snap == null) {
                        UnconfiguredView(widgetId)
                    } else {
                        WidgetBody(widgetId, snap, actions, size.width, size.height)
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
    private fun WidgetBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        widthDp: androidx.compose.ui.unit.Dp,
        heightDp: androidx.compose.ui.unit.Dp,
    ) {
        val isWide = widthDp >= 240.dp
        val isTall = heightDp >= 100.dp
        val cornerDp = 28.dp
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(cornerDp)
                .padding(if (isTall) 16.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statusWeight = if (isWide) 0.52f else 0.6f
            StatusColumn(snap, heightDp, GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(8.dp))
            ButtonGrid(
                widgetId = widgetId,
                vin = snap.vin,
                actions = actions,
                widthDp = widthDp * (1f - statusWeight) - 24.dp,
                heightDp = heightDp - (if (isTall) 32.dp else 24.dp),
                showLabels = isWide && isTall,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
        }
    }

    @Composable
    private fun StatusColumn(
        snap: VehicleSnapshot,
        heightDp: androidx.compose.ui.unit.Dp,
        modifier: GlanceModifier,
    ) {
        val isTall = heightDp >= 100.dp
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            if (isTall) {
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
            Spacer(GlanceModifier.height(2.dp))
            val percentSize = if (isTall) 26.sp else 20.sp
            Text(
                snap.percent?.let { "$it%" } ?: "—",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = percentSize,
                ),
            )
            if (isTall) {
                val kindLabel = if (snap.isEv) "battery" else "fuel"
                Text(
                    kindLabel,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
            snap.rangeMi?.let {
                Text(
                    "$it mi",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
    }

    @Composable
    private fun ButtonGrid(
        widgetId: Int,
        vin: String,
        actions: List<WidgetAction>,
        widthDp: androidx.compose.ui.unit.Dp,
        heightDp: androidx.compose.ui.unit.Dp,
        showLabels: Boolean,
        modifier: GlanceModifier,
    ) {
        val pillH = ((heightDp - 6.dp) / 2).coerceIn(28.dp, 52.dp)
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ActionPill(widgetId, vin, actions.getOrNull(0), pillH, showLabels, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(5.dp))
                ActionPill(widgetId, vin, actions.getOrNull(1), pillH, showLabels, GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(5.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ActionPill(widgetId, vin, actions.getOrNull(2), pillH, showLabels, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(5.dp))
                ActionPill(widgetId, vin, actions.getOrNull(3), pillH, showLabels, GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun ActionPill(
        widgetId: Int,
        vin: String,
        action: WidgetAction?,
        heightDp: androidx.compose.ui.unit.Dp,
        showLabel: Boolean,
        modifier: GlanceModifier,
    ) {
        val context = LocalContext.current
        if (action == null) {
            Box(modifier.height(heightDp)) {}
            return
        }
        val cornerR = heightDp / 2
        Box(
            modifier = modifier
                .height(heightDp)
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(cornerR)
                .clickable(actionStartActivity(authIntent(context, widgetId, vin, action))),
            contentAlignment = Alignment.Center,
        ) {
            val iconSize = (heightDp * 0.44f).coerceIn(14.dp, 22.dp)
            if (showLabel) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        provider = ImageProvider(action.icon),
                        contentDescription = action.label,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                        modifier = GlanceModifier.size(iconSize),
                    )
                    Text(
                        action.label,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSecondaryContainer,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            } else {
                Image(
                    provider = ImageProvider(action.icon),
                    contentDescription = action.label,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                    modifier = GlanceModifier.size(iconSize),
                )
            }
        }
    }

    /**
     * One-row layout for a short (≈40 dp tall) widget.
     */
    @Composable
    private fun CompactWidgetBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        widthDp: androidx.compose.ui.unit.Dp,
    ) {
        val context = LocalContext.current
        val maxButtons = when {
            widthDp >= 260.dp -> 4
            widthDp >= 200.dp -> 3
            widthDp >= 160.dp -> 2
            else -> 1
        }
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
                Spacer(GlanceModifier.width(6.dp))
                val (stateLabel, stateColor) = stateOf(snap)
                Text(
                    stateLabel,
                    maxLines = 1,
                    style = TextStyle(color = stateColor, fontWeight = FontWeight.Medium, fontSize = 12.sp),
                )
            }
            actions.take(maxButtons).forEach { action ->
                Spacer(GlanceModifier.width(5.dp))
                Box(
                    modifier = GlanceModifier
                        .size(30.dp)
                        .background(GlanceTheme.colors.secondaryContainer)
                        .cornerRadius(15.dp)
                        .clickable(actionStartActivity(authIntent(context, widgetId, snap.vin, action))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(action.icon),
                        contentDescription = action.label,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                        modifier = GlanceModifier.size(15.dp),
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
