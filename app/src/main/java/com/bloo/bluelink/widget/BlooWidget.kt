package com.bloo.bluelink.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
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
import androidx.glance.layout.ContentScale
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
 * The Bloo home-screen widget (Jetpack Glance).
 *
 * Four layout tiers, chosen by aspect ratio and height:
 *
 *  • Compact (h < 60 dp): Single row — percent + state + circular action buttons.
 *  • WideRow (h < 80 dp, w > h × 3): Compact status + 4 pills in a single row.
 *  • Portrait (h ≥ 60 dp, h > w × 1.2): Status info stacked above a 2-row button
 *    grid; photo backdrop when h ≥ 200 dp.
 *  • Landscape (h ≥ 60 dp, h ≤ w × 1.2): Status column beside the button grid;
 *    photo backdrop when h ≥ 160 dp.
 *
 * Every dimension is derived from [LocalSize] via [SizeMode.Exact], so the layout
 * recomposes at the widget's true pixel size on every resize — nothing clips or
 * overflows. Info density scales inversely with space: smaller widgets pack more
 * detail per dp; larger ones breathe.
 *
 * Tapping the status area routes through [WidgetAuthActivity] with the OPEN action
 * so it stays consistent with button-tap auth logic.
 */
class BlooWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val cfg = SettingsStore(context).widgetConfig(widgetId)
        val snapshots = SnapshotStore(context).current().vehicles
        val snap = cfg?.let { c -> snapshots.firstOrNull { it.vin == c.first } }
        val actions = cfg?.second.orEmpty().mapNotNull { WidgetAction.fromKey(it) }

        // Load the car photo so it can serve as the backdrop ImageProvider.
        // Only absolute-path photos are read here; remote URLs would need networking.
        val photoBitmap: Bitmap? = snap?.let { s ->
            val path = SettingsStore(context).imageUrl(s.vin)
            if (path != null && path.startsWith("/")) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(path, opts)
                }.getOrNull()
            } else null
        }

        val showBackground = cfg?.let { SettingsStore(context).widgetShowBackground(widgetId) } ?: true
        val widgetShape = cfg?.let { SettingsStore(context).widgetShape(widgetId) } ?: "rect"
        val locationAddress = cfg?.let { SettingsStore(context).widgetLocationAddress(widgetId) }

        provideContent {
            GlanceTheme {
                val w = LocalSize.current.width
                val h = LocalSize.current.height
                val isPortrait = h > w * 1.2f
                when {
                    h < 60.dp -> if (snap == null) UnconfiguredCompact(widgetId)
                                 else CompactBody(widgetId, snap, actions, w)
                    h < 80.dp && w > h * 3f -> if (snap == null) UnconfiguredCompact(widgetId)
                                               else WideRowBody(widgetId, snap, actions, w, h, showBackground, widgetShape)
                    isPortrait -> if (snap == null) UnconfiguredFull(widgetId)
                                  else PortraitBody(widgetId, snap, actions, w, h, photoBitmap, showBackground, widgetShape, locationAddress)
                    else       -> if (snap == null) UnconfiguredFull(widgetId)
                                  else LandscapeBody(widgetId, snap, actions, w, h, photoBitmap, showBackground, widgetShape, locationAddress)
                }
            }
        }
    }

    // ── Unconfigured placeholders ─────────────────────────────────────────────

    @Composable
    private fun UnconfiguredFull(widgetId: Int) {
        val context = LocalContext.current
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
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

    // ── Compact single-row layout ─────────────────────────────────────────────

    /**
     * Minimal one-row layout for short widgets (h < 60 dp):
     * percent + state label on the left, up to four circular action buttons on the right.
     */
    @Composable
    private fun CompactBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
    ) {
        val context = LocalContext.current
        // How many 34 dp buttons fit after reserving 100 dp for status.
        val maxButtons = ((w - 100.dp) / 34.dp).toInt().coerceIn(0, 4)
        val showState = w >= 150.dp
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
                        fontSize = 17.sp,
                    ),
                )
                if (showState) {
                    Spacer(GlanceModifier.width(7.dp))
                    val (label, color) = stateOf(snap)
                    Text(label, maxLines = 1, style = TextStyle(color = color, fontWeight = FontWeight.Medium, fontSize = 12.sp))
                }
            }
            actions.take(maxButtons).forEach { action ->
                Spacer(GlanceModifier.width(5.dp))
                Box(
                    modifier = GlanceModifier
                        .size(34.dp)
                        .background(GlanceTheme.colors.secondaryContainer)
                        .cornerRadius(17.dp)
                        .clickable(actionStartActivity(authIntent(context, widgetId, snap.vin, action))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(action.icon),
                        contentDescription = action.label,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                        modifier = GlanceModifier.size(17.dp),
                    )
                }
            }
        }
    }

    // ── Wide-row layout ───────────────────────────────────────────────────────

    /**
     * Ultra-wide short layout (h < 80 dp, w > h × 3): compact status on the left,
     * four action pills in a single row on the right.
     */
    @Composable
    private fun WideRowBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
        h: Dp,
        showBackground: Boolean,
        widgetShape: String,
    ) {
        val context = LocalContext.current
        val corner = if (widgetShape == "pill") h / 2 else 20.dp
        val pad = 8.dp
        val gap = 5.dp
        val pillH = (h - pad * 2).coerceIn(24.dp, 54.dp)

        val openAction = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))
        val (stateLabel, stateColor) = stateOf(snap)

        val boxMod = if (showBackground) {
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }

        Box(modifier = boxMod) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = pad, vertical = pad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Compact status: percent + state
                Column(
                    modifier = GlanceModifier.height(pillH).clickable(openAction),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        snap.percent?.let { "$it%" } ?: "—",
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    )
                    Text(stateLabel, maxLines = 1, style = TextStyle(color = stateColor, fontSize = 10.sp))
                }
                Spacer(GlanceModifier.width(8.dp))
                // 4 buttons in a row
                Row(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.take(4).forEachIndexed { i, action ->
                        if (i > 0) Spacer(GlanceModifier.width(gap))
                        ActionPill(widgetId, snap.vin, action, pillH, GlanceModifier.defaultWeight())
                    }
                    repeat((4 - actions.size).coerceAtLeast(0)) { i ->
                        if (actions.isNotEmpty() || i > 0) Spacer(GlanceModifier.width(gap))
                        ActionPill(widgetId, snap.vin, null, pillH, GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }

    // ── Portrait layout ───────────────────────────────────────────────────────

    /**
     * Tall, narrow layout: status info centred at the top, 2-row button grid at the
     * bottom. The status section grows and reveals progressively more detail as the
     * available height increases. Photo backdrop shown when h ≥ 200 dp.
     */
    @Composable
    private fun PortraitBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
        h: Dp,
        photoBitmap: Bitmap?,
        showBackground: Boolean,
        widgetShape: String,
        locationAddress: String?,
    ) {
        val context = LocalContext.current
        val showPhoto = photoBitmap != null && h >= 200.dp
        val pad = when {
            h >= 220.dp -> 18.dp
            h >= 150.dp -> 14.dp
            else -> 11.dp
        }
        val corner = if (widgetShape == "pill") w / 2 else (w / 4).coerceIn(18.dp, 32.dp)
        val gap = 7.dp
        // 4 full-width stacked pills; height derived from available space with generous floor
        val pillH = ((h - pad * 2 - gap * 3) / 5.2f).coerceIn(36.dp, 60.dp)
        val gridH = pillH * 4 + gap * 3
        val statusH = h - pad * 2 - gridH - gap
        val showStatus = statusH >= 28.dp

        val openAction = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))

        val boxMod = if (showBackground) {
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }

        Box(modifier = boxMod) {
            if (showPhoto) {
                Image(
                    provider = ImageProvider(photoBitmap!!),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0f, 0f, 0f, 0.48f)))) {}
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(pad),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showStatus) {
                    PortraitStatusSection(
                        snap = snap,
                        availH = statusH,
                        hasPhoto = showPhoto,
                        locationAddress = locationAddress,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(statusH)
                            .clickable(openAction),
                    )
                    Spacer(GlanceModifier.height(gap))
                }
                PortraitButtonGrid(
                    widgetId = widgetId,
                    vin = snap.vin,
                    actions = actions,
                    pillH = pillH,
                    gap = gap,
                )
            }
        }
    }

    @Composable
    private fun PortraitStatusSection(
        snap: VehicleSnapshot,
        availH: Dp,
        hasPhoto: Boolean,
        locationAddress: String?,
        modifier: GlanceModifier,
    ) {
        val onSurface = if (hasPhoto) ColorProvider(Color.White) else GlanceTheme.colors.onSurface
        val onVariant = if (hasPhoto) ColorProvider(Color(1f, 1f, 1f, 0.70f)) else GlanceTheme.colors.onSurfaceVariant

        val (stateLabel, stateColor) = stateOf(snap, hasPhoto)

        // Derive what to show based on available height.
        val showName = availH >= 90.dp
        val showKind = availH >= 65.dp
        val showRange = availH >= 52.dp
        val showState = availH >= 36.dp
        val percentSize = when {
            availH >= 140.dp -> 44.sp
            availH >= 110.dp -> 36.sp
            availH >= 80.dp  -> 28.sp
            availH >= 55.dp  -> 22.sp
            else             -> 16.sp
        }
        val nameFontSize = when {
            availH >= 120.dp -> 15.sp
            else             -> 13.sp
        }

        Column(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showName) {
                Text(
                    snap.name,
                    maxLines = 1,
                    style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = nameFontSize),
                )
                Spacer(GlanceModifier.height(2.dp))
            }
            Text(
                snap.percent?.let { "$it%" } ?: "—",
                maxLines = 1,
                style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = percentSize),
            )
            if (showState) {
                Text(stateLabel, maxLines = 1, style = TextStyle(color = stateColor, fontWeight = FontWeight.Medium, fontSize = 12.sp))
            }
            if (locationAddress != null && availH >= 70.dp) {
                Text(locationAddress, maxLines = 1, style = TextStyle(color = onVariant, fontSize = 10.sp))
            }
            if (showKind) {
                Text(
                    if (snap.isEv) "battery" else "fuel",
                    maxLines = 1,
                    style = TextStyle(color = onVariant, fontSize = 11.sp),
                )
            }
            if (showRange) {
                snap.rangeMi?.let {
                    Text("$it mi", maxLines = 1, style = TextStyle(color = onVariant, fontSize = 12.sp))
                }
            }
        }
    }

    @Composable
    private fun PortraitButtonGrid(
        widgetId: Int,
        vin: String,
        actions: List<WidgetAction>,
        pillH: Dp,
        gap: Dp,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(4) { i ->
                if (i > 0) Spacer(GlanceModifier.height(gap))
                ActionPill(widgetId, vin, actions.getOrNull(i), pillH, GlanceModifier.fillMaxWidth())
            }
        }
    }

    // ── Landscape layout ──────────────────────────────────────────────────────

    /**
     * Side-by-side layout: status column (left, clickable to open car) and button
     * grid (right). Both sides take equal weight so neither can clip the other.
     * Photo backdrop shown when h ≥ 160 dp.
     */
    @Composable
    private fun LandscapeBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
        h: Dp,
        photoBitmap: Bitmap?,
        showBackground: Boolean,
        widgetShape: String,
        locationAddress: String?,
    ) {
        val context = LocalContext.current
        val showPhoto = photoBitmap != null && h >= 160.dp
        val pad = when {
            h >= 180.dp -> 20.dp
            h >= 130.dp -> 16.dp
            h >= 90.dp  -> 13.dp
            else        -> 11.dp
        }
        val corner = if (widgetShape == "pill") h / 2 else (h / 4).coerceIn(18.dp, 32.dp)
        val gap = 6.dp
        val gridCols = if (w >= 160.dp) 2 else 1
        val gridMinW = (gridCols * 54).dp + gap * (gridCols - 1)
        val showStatus = (w - pad * 2) > gridMinW + 48.dp

        val openAction = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))

        val boxMod = if (showBackground) {
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }

        Box(modifier = boxMod) {
            if (showPhoto) {
                Image(
                    provider = ImageProvider(photoBitmap!!),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0f, 0f, 0f, 0.45f)))) {}
            }

            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(pad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showStatus) {
                    LandscapeStatusColumn(
                        snap = snap,
                        h = h,
                        hasPhoto = showPhoto,
                        locationAddress = locationAddress,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .clickable(openAction),
                    )
                    Spacer(GlanceModifier.width(10.dp))
                }
                ButtonGrid(
                    widgetId = widgetId,
                    vin = snap.vin,
                    actions = actions,
                    cols = gridCols,
                    contentH = h - pad * 2,
                    gap = gap,
                    modifier = if (showStatus) GlanceModifier.defaultWeight().fillMaxHeight()
                               else GlanceModifier.fillMaxWidth().fillMaxHeight(),
                )
            }
        }
    }

    /**
     * Left-side status column in the landscape layout. Reveals info progressively:
     *  h ≥ 60 dp  → state + percent
     *  h ≥ 76 dp  → + range
     *  h ≥ 86 dp  → + battery/fuel label
     *  h ≥ 96 dp  → + car name
     *  h ≥ 160 dp → photo backdrop; text turns white
     */
    @Composable
    private fun LandscapeStatusColumn(
        snap: VehicleSnapshot,
        h: Dp,
        hasPhoto: Boolean,
        locationAddress: String?,
        modifier: GlanceModifier,
    ) {
        val onSurface = if (hasPhoto) ColorProvider(Color.White) else GlanceTheme.colors.onSurface
        val onVariant = if (hasPhoto) ColorProvider(Color(1f, 1f, 1f, 0.70f)) else GlanceTheme.colors.onSurfaceVariant
        val (stateLabel, stateColor) = stateOf(snap, hasPhoto)

        val showName = h >= 96.dp
        val showKind = h >= 86.dp
        val showRange = h >= 76.dp
        val percentSize = when {
            h >= 200.dp -> 40.sp
            h >= 170.dp -> 34.sp
            h >= 140.dp -> 29.sp
            h >= 110.dp -> 24.sp
            h >= 86.dp  -> 20.sp
            else        -> 17.sp
        }

        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            if (showName) {
                Text(
                    snap.name,
                    maxLines = 1,
                    style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                )
            }
            Text(stateLabel, maxLines = 1, style = TextStyle(color = stateColor, fontWeight = FontWeight.Medium, fontSize = 12.sp))
            Spacer(GlanceModifier.height(1.dp))
            Text(
                snap.percent?.let { "$it%" } ?: "—",
                maxLines = 1,
                style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = percentSize),
            )
            if (locationAddress != null && h >= 70.dp) {
                Text(locationAddress, maxLines = 1, style = TextStyle(color = onVariant, fontSize = 10.sp))
            }
            if (showKind) {
                Text(
                    if (snap.isEv) "battery" else "fuel",
                    maxLines = 1,
                    style = TextStyle(color = onVariant, fontSize = 11.sp),
                )
            }
            if (showRange) {
                snap.rangeMi?.let {
                    Text("$it mi", maxLines = 1, style = TextStyle(color = onVariant, fontSize = 12.sp))
                }
            }
        }
    }

    // ── Shared button grid ────────────────────────────────────────────────────

    @Composable
    private fun ButtonGrid(
        widgetId: Int,
        vin: String,
        actions: List<WidgetAction>,
        cols: Int,
        contentH: Dp,
        gap: Dp,
        modifier: GlanceModifier,
    ) {
        val pillH = ((contentH - gap) / 2).coerceIn(26.dp, 56.dp)
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            GridRow(widgetId, vin, actions.getOrNull(0), actions.getOrNull(1), cols, pillH, gap)
            Spacer(GlanceModifier.height(gap))
            GridRow(widgetId, vin, actions.getOrNull(2), actions.getOrNull(3), cols, pillH, gap)
        }
    }

    @Composable
    private fun GridRow(
        widgetId: Int,
        vin: String,
        first: WidgetAction?,
        second: WidgetAction?,
        cols: Int,
        pillH: Dp,
        gap: Dp,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionPill(widgetId, vin, first, pillH, GlanceModifier.defaultWeight())
            if (cols >= 2) {
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
        val iconSize = (pillH * 0.46f).coerceIn(14.dp, 24.dp)
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Composable
    private fun stateOf(snap: VehicleSnapshot, hasPhoto: Boolean = false): Pair<String, ColorProvider> = when {
        snap.engineOn == true -> "Driving" to
            if (hasPhoto) ColorProvider(Color(0.35f, 0.95f, 0.45f, 1f)) else GlanceTheme.colors.tertiary
        snap.charging == true -> "Charging" to
            if (hasPhoto) ColorProvider(Color(0.4f, 0.85f, 0.4f, 1f)) else GlanceTheme.colors.tertiary
        snap.locked == true  -> "Locked" to
            if (hasPhoto) ColorProvider(Color(1f, 1f, 1f, 0.70f)) else GlanceTheme.colors.onSurfaceVariant
        snap.locked == false -> "Unlocked" to
            if (hasPhoto) ColorProvider(Color(1f, 0.42f, 0.42f, 1f)) else GlanceTheme.colors.error
        else                 -> "—" to
            if (hasPhoto) ColorProvider(Color(1f, 1f, 1f, 0.55f)) else GlanceTheme.colors.onSurfaceVariant
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
