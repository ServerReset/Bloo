package com.bloo.bluelink.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import com.bloo.bluelink.R
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.VehicleSnapshot
import kotlinx.coroutines.flow.first

/**
 * The Bloo home-screen widget (Jetpack Glance).
 *
 * Five layout tiers, chosen by aspect ratio and dimensions:
 *
 *  • Compact (h < 60 dp): Single row — car name (always) above percent + state,
 *    with circular action buttons on the right when width allows.
 *  • NarrowTall (portrait & (w < 110 dp or h > w × 2.5)): Single-column stack —
 *    car name, percent, state, range/battery, then action buttons stacked
 *    vertically. Covers 1-cell-wide, many-tall placements.
 *  • WideRow (!portrait, w > h × 2.2): Compact status + 4 action pills in a row.
 *  • Portrait (h ≥ 60 dp, h > w × 1.2): Status info stacked above a button grid;
 *    photo / map backdrop on larger sizes.
 *  • Landscape (h ≥ 60 dp, h ≤ w × 1.2): Status column beside the button grid;
 *    photo backdrop on larger sizes.
 *
 * Every dimension is derived from [LocalSize] via [SizeMode.Exact], so the layout
 * recomposes at the widget's true pixel size on every resize — nothing clips or
 * overflows. Info density scales inversely with space.
 *
 * Polish features: battery progress bar, state badge chips, action labels on
 * large buttons, and a cached OpenStreetMap location card when the widget is
 * large and a Location button is configured.
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
        val pendingAction = cfg?.let { SettingsStore(context).widgetPendingAction(widgetId) }
        val locationAddress = cfg?.let { SettingsStore(context).widgetLocationAddress(widgetId) }

        // Cached location-map tile, present only after a Location action has run.
        val hasLocationAction = actions.any { it == WidgetAction.LOCATION }
        val mapBitmap: Bitmap? = if (hasLocationAction) {
            val mapFile = java.io.File(context.cacheDir, "widget_map_$widgetId.png")
            if (mapFile.exists()) runCatching { BitmapFactory.decodeFile(mapFile.absolutePath) }.getOrNull() else null
        } else null

        // Derive the accent colour from the app's selected palette (or per-car override).
        // Used for default widget button backgrounds so they match the user's chosen theme.
        val accentBg: ColorProvider
        val accentFg = ColorProvider(Color.White)
        run {
            val appearance = SettingsStore(context).appearance.first()
            val vin = snap?.vin
            val customPalettes = appearance.customPalettes
            val swatchArgb: Int = (
                vin?.let { appearance.carCustomPaletteIds[it] }
                    ?.let { id -> customPalettes.firstOrNull { it.id == id }?.primaryArgb }
                    ?: appearance.activeCustomPaletteId
                        ?.let { id -> customPalettes.firstOrNull { it.id == id }?.primaryArgb }
                    ?: appearance.colorPalette.swatch.toArgb()
            )
            // Shift to a medium-value tone that reads on both light and dark backgrounds.
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(swatchArgb, hsv)
            if (hsv[2] < 0.35f) hsv[2] = 0.55f  // boost very dark seeds
            val accentColor = Color(android.graphics.Color.HSVToColor(hsv).toLong() and 0xFFFFFFFFL)
            accentBg = ColorProvider(accentColor)
        }

        provideContent {
            GlanceTheme {
                val w = LocalSize.current.width
                val h = LocalSize.current.height
                val isPortrait = h > w * 1.2f
                when {
                    h < 60.dp -> when {
                        snap != null -> CompactBody(widgetId, snap, actions, w, showBackground, widgetShape, pendingAction, accentBg, accentFg)
                        cfg == null  -> UnconfiguredCompact(widgetId)
                        else         -> UnavailableCompact(showBackground, widgetShape)
                    }
                    isPortrait && (w < 110.dp || h > w * 2.5f) -> when {
                        snap != null -> NarrowTallBody(widgetId, snap, actions, w, h, showBackground, widgetShape, pendingAction, accentBg, accentFg)
                        cfg == null  -> UnconfiguredFull(widgetId)
                        else         -> UnavailableFull(showBackground, widgetShape)
                    }
                    !isPortrait && w > h * 2.2f -> when {
                        snap != null -> WideRowBody(widgetId, snap, actions, w, h, showBackground, widgetShape, pendingAction, accentBg, accentFg)
                        cfg == null  -> UnconfiguredCompact(widgetId)
                        else         -> UnavailableCompact(showBackground, widgetShape)
                    }
                    isPortrait -> when {
                        snap != null -> PortraitBody(widgetId, snap, actions, w, h, photoBitmap, mapBitmap, showBackground, widgetShape, locationAddress, pendingAction, accentBg, accentFg)
                        cfg == null  -> UnconfiguredFull(widgetId)
                        else         -> UnavailableFull(showBackground, widgetShape)
                    }
                    else -> when {
                        snap != null -> LandscapeBody(widgetId, snap, actions, w, h, photoBitmap, mapBitmap, showBackground, widgetShape, locationAddress, pendingAction, accentBg, accentFg)
                        cfg == null  -> UnconfiguredFull(widgetId)
                        else         -> UnavailableFull(showBackground, widgetShape)
                    }
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

    // ── Data-unavailable placeholders (widget configured, snapshot missing) ─────

    @Composable
    private fun UnavailableFull(showBackground: Boolean, widgetShape: String) {
        val corner = if (widgetShape == "pill") 28.dp else 20.dp
        val mod = if (showBackground) GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
                  else GlanceModifier.fillMaxSize().cornerRadius(corner)
        Box(modifier = mod, contentAlignment = Alignment.Center) {
            Text("Sign in to Bloo", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium))
        }
    }

    @Composable
    private fun UnavailableCompact(showBackground: Boolean, widgetShape: String) {
        val corner = if (widgetShape == "pill") 28.dp else 20.dp
        val mod = if (showBackground) GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner).padding(horizontal = 12.dp, vertical = 4.dp)
                  else GlanceModifier.fillMaxSize().cornerRadius(corner).padding(horizontal = 12.dp, vertical = 4.dp)
        Box(modifier = mod, contentAlignment = Alignment.Center) {
            Text("Sign in", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium))
        }
    }

    // ── State badge chip ──────────────────────────────────────────────────────

    /** A small filled pill that shows the vehicle state (Charging / Locked / …). */
    @Composable
    private fun StateChip(label: String, color: ColorProvider, fontSize: Int = 10) {
        Box(
            modifier = GlanceModifier
                .background(color)
                .cornerRadius(10.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = fontSize.sp, fontWeight = FontWeight.Medium),
            )
        }
    }

    /** A thin battery/fuel progress bar. [trackW] is the full track width in dp. */
    @Composable
    private fun BatteryBar(percent: Int?, trackW: Dp, accentBg: ColorProvider, onPhoto: Boolean) {
        val pct = (percent ?: 0).coerceIn(0, 100)
        val fillW = trackW * (pct / 100f)
        val trackColor = if (onPhoto) ColorProvider(Color(1f, 1f, 1f, 0.25f)) else GlanceTheme.colors.onSurfaceVariant
        Box(
            modifier = GlanceModifier.width(trackW).height(5.dp).background(trackColor).cornerRadius(3.dp),
        ) {
            if (fillW > 0.dp) {
                Box(GlanceModifier.width(fillW).height(5.dp).background(accentBg).cornerRadius(3.dp)) {}
            }
        }
    }

    // ── Compact single-row layout ─────────────────────────────────────────────

    @Composable
    private fun CompactBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
        showBackground: Boolean,
        widgetShape: String,
        pendingAction: String?,
        accentBg: ColorProvider,
        accentFg: ColorProvider,
    ) {
        val context = LocalContext.current
        val isPill = widgetShape == "pill"
        val corner = if (isPill) 28.dp else 20.dp
        val hPad = if (isPill) 14.dp else 10.dp
        val maxButtons = ((w - 110.dp) / 39.dp).toInt().coerceIn(0, 4)
        val showState = w >= 150.dp

        val boxMod = if (showBackground) {
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }

        Box(modifier = boxMod) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = hPad, vertical = 4.dp)
                    .clickable(actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        snap.name,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium, fontSize = 10.sp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            snap.percent?.let { "$it%" } ?: "—",
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        )
                        if (showState) {
                            Spacer(GlanceModifier.width(7.dp))
                            val (label, color) = stateOf(snap)
                            StateChip(label, color)
                        }
                    }
                }
                actions.take(maxButtons).forEach { action ->
                    Spacer(GlanceModifier.width(5.dp))
                    CircleButton(widgetId, snap.vin, action, 34.dp, pendingAction, snap, accentBg, accentFg)
                }
            }
        }
    }

    /** A fixed-size circular icon button used in compact / narrow layouts. */
    @Composable
    private fun CircleButton(
        widgetId: Int,
        vin: String,
        action: WidgetAction,
        size: Dp,
        pendingAction: String?,
        snap: VehicleSnapshot,
        accentBg: ColorProvider,
        accentFg: ColorProvider,
    ) {
        val context = LocalContext.current
        val st = actionState(action, snap, pendingAction)
        // Lock morph: circle when locked, rounded-square when unlocked.
        val corner = when {
            st.isLockAction -> when (snap.locked) {
                false -> size * 0.28f
                else -> size / 2
            }
            else -> size / 2
        }
        Box(
            modifier = GlanceModifier
                .size(size)
                .background(st.bg(accentBg))
                .cornerRadius(corner)
                .clickable(actionStartActivity(authIntent(context, widgetId, vin, action))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(st.iconRes),
                contentDescription = action.label,
                colorFilter = ColorFilter.tint(st.fg(accentFg)),
                modifier = GlanceModifier.size(size * 0.5f),
            )
        }
    }

    // ── Narrow-tall layout ────────────────────────────────────────────────────

    @Composable
    private fun NarrowTallBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
        h: Dp,
        showBackground: Boolean,
        widgetShape: String,
        pendingAction: String?,
        accentBg: ColorProvider,
        accentFg: ColorProvider,
    ) {
        val context = LocalContext.current
        val isPill = widgetShape == "pill"
        val corner = if (isPill) w / 2 else (w / 4).coerceIn(14.dp, 28.dp)
        val pad = if (isPill) (w / 5).coerceIn(6.dp, 14.dp) else 8.dp
        val gap = 5.dp
        val btnH = 34.dp

        val minStatusH = 64.dp
        val avail = h - pad * 2
        val fitButtons = (((avail - minStatusH - gap) / (btnH + gap)).toInt()).coerceIn(0, 4)
        val maxButtons = actions.size.coerceAtMost(fitButtons)
        val buttonAreaH = if (maxButtons > 0) (btnH * maxButtons + gap * maxButtons) else 0.dp
        val statusH = (avail - buttonAreaH).coerceAtLeast(minStatusH)
        val showKind = statusH >= 110.dp
        val showRange = statusH >= 92.dp
        val showBar = statusH >= 80.dp
        val showState = statusH >= 64.dp
        val percentSize = when {
            statusH >= 130.dp -> 34.sp
            statusH >= 100.dp -> 28.sp
            statusH >= 80.dp  -> 22.sp
            statusH >= 55.dp  -> 18.sp
            else              -> 14.sp
        }
        val barW = (w - pad * 2).coerceAtLeast(0.dp)

        val openAction = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))
        val (stateLabel, stateColor) = stateOf(snap)

        val boxMod = if (showBackground) {
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }

        Box(modifier = boxMod) {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(pad),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(statusH)
                        .clickable(openAction),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        snap.name,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        snap.percent?.let { "$it%" } ?: "—",
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = percentSize),
                    )
                    if (showBar) {
                        Spacer(GlanceModifier.height(4.dp))
                        BatteryBar(snap.percent, barW, accentBg, onPhoto = false)
                    }
                    if (showState) {
                        Spacer(GlanceModifier.height(4.dp))
                        StateChip(stateLabel, stateColor)
                    }
                    if (showRange) {
                        snap.rangeMi?.let {
                            Spacer(GlanceModifier.height(2.dp))
                            Text("$it mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
                        }
                    }
                    if (showKind) {
                        Text(
                            if (snap.isEv) "Battery" else "Fuel",
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                        )
                    }
                }
                if (maxButtons > 0) {
                    Spacer(GlanceModifier.height(gap))
                    actions.take(maxButtons).forEachIndexed { i, action ->
                        if (i > 0) Spacer(GlanceModifier.height(gap))
                        ActionPill(widgetId, snap.vin, action, btnH, GlanceModifier.fillMaxWidth(), pendingAction, snap, accentBg, accentFg)
                    }
                }
            }
        }
    }

    // ── Wide-row layout ───────────────────────────────────────────────────────

    @Composable
    private fun WideRowBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
        h: Dp,
        showBackground: Boolean,
        widgetShape: String,
        pendingAction: String?,
        accentBg: ColorProvider,
        accentFg: ColorProvider,
    ) {
        val context = LocalContext.current
        val isPill = widgetShape == "pill"
        val corner = if (isPill) h / 2 else 20.dp
        val pad = 8.dp
        val hPad = if (isPill) (h / 3).coerceIn(pad, 28.dp) else pad
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
                    .padding(horizontal = hPad, vertical = pad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = GlanceModifier.height(pillH).clickable(openAction),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        snap.name,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium, fontSize = 10.sp),
                    )
                    Text(
                        snap.percent?.let { "$it%" } ?: "—",
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    )
                    if (h >= 70.dp) {
                        Spacer(GlanceModifier.height(2.dp))
                        StateChip(stateLabel, stateColor)
                    }
                }
                Spacer(GlanceModifier.width(8.dp))
                Row(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.take(4).forEachIndexed { i, action ->
                        if (i > 0) Spacer(GlanceModifier.width(gap))
                        ActionPill(widgetId, snap.vin, action, pillH, GlanceModifier.defaultWeight(), pendingAction, snap, accentBg, accentFg)
                    }
                    repeat((4 - actions.size).coerceAtLeast(0)) { i ->
                        if (actions.isNotEmpty() || i > 0) Spacer(GlanceModifier.width(gap))
                        ActionPill(widgetId, snap.vin, null, pillH, GlanceModifier.defaultWeight(), pendingAction, snap, accentBg, accentFg)
                    }
                }
            }
        }
    }

    // ── Portrait layout ───────────────────────────────────────────────────────

    @Composable
    private fun PortraitBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
        h: Dp,
        photoBitmap: Bitmap?,
        mapBitmap: Bitmap?,
        showBackground: Boolean,
        widgetShape: String,
        locationAddress: String?,
        pendingAction: String?,
        accentBg: ColorProvider,
        accentFg: ColorProvider,
    ) {
        val context = LocalContext.current
        val showMap = mapBitmap != null && h >= 200.dp
        val showPhoto = !showMap && photoBitmap != null && h >= 200.dp
        val isPill = widgetShape == "pill"
        val basePad = when {
            h >= 220.dp -> 18.dp
            h >= 150.dp -> 14.dp
            else -> 11.dp
        }
        val pad = if (isPill) (basePad + (w / 5).coerceAtMost(22.dp)) else basePad
        val corner = if (isPill) w / 2 else (w / 4).coerceIn(18.dp, 32.dp)
        val gap = 7.dp
        // Map card eats some vertical space when shown.
        val mapCardH = if (showMap) 116.dp else 0.dp
        val pillH = ((h - pad * 2 - gap * 3 - mapCardH) / 5.2f).coerceIn(30.dp, 60.dp)
        val gridH = pillH * 4 + gap * 3
        val statusH = h - pad * 2 - gridH - gap - (if (showMap) mapCardH + gap else 0.dp)
        val showStatus = statusH >= 28.dp
        val barW = (w - pad * 2).coerceAtLeast(0.dp)

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
                modifier = GlanceModifier.fillMaxSize().padding(pad),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showStatus) {
                    PortraitStatusSection(
                        snap = snap,
                        availH = statusH,
                        hasPhoto = showPhoto,
                        barW = barW,
                        accentBg = accentBg,
                        modifier = GlanceModifier.fillMaxWidth().height(statusH).clickable(openAction),
                    )
                    Spacer(GlanceModifier.height(gap))
                }
                if (showMap) {
                    LocationCard(
                        mapBitmap = mapBitmap!!,
                        address = locationAddress,
                        height = mapCardH,
                        onClick = openAction,
                    )
                    Spacer(GlanceModifier.height(gap))
                }
                PortraitButtonGrid(
                    widgetId = widgetId,
                    vin = snap.vin,
                    actions = actions,
                    pillH = pillH,
                    gap = gap,
                    pendingAction = pendingAction,
                    snap = snap,
                    accentBg = accentBg,
                    accentFg = accentFg,
                )
            }
        }
    }

    /** A rounded map image with the reverse-geocoded address overlaid at the bottom. */
    @Composable
    private fun LocationCard(
        mapBitmap: Bitmap,
        address: String?,
        height: Dp,
        onClick: androidx.glance.action.Action,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(height)
                .cornerRadius(14.dp)
                .clickable(onClick),
        ) {
            Image(
                provider = ImageProvider(mapBitmap),
                contentDescription = "Car location",
                modifier = GlanceModifier.fillMaxSize().cornerRadius(14.dp),
                contentScale = ContentScale.Crop,
            )
            if (!address.isNullOrBlank()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .background(ColorProvider(Color(0f, 0f, 0f, 0.55f)))
                            .cornerRadius(8.dp)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_location),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                                modifier = GlanceModifier.size(12.dp),
                            )
                            Spacer(GlanceModifier.width(4.dp))
                            Text(
                                address,
                                maxLines = 1,
                                style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PortraitStatusSection(
        snap: VehicleSnapshot,
        availH: Dp,
        hasPhoto: Boolean,
        barW: Dp,
        accentBg: ColorProvider,
        modifier: GlanceModifier,
    ) {
        val onSurface = if (hasPhoto) ColorProvider(Color.White) else GlanceTheme.colors.onSurface
        val onVariant = if (hasPhoto) ColorProvider(Color(1f, 1f, 1f, 0.70f)) else GlanceTheme.colors.onSurfaceVariant
        val (stateLabel, stateColor) = stateOf(snap, hasPhoto)

        val showKind = availH >= 72.dp
        val showRange = availH >= 56.dp
        val showBar = availH >= 48.dp
        val showState = availH >= 32.dp
        val percentSize = when {
            availH >= 140.dp -> 44.sp
            availH >= 110.dp -> 36.sp
            availH >= 80.dp  -> 28.sp
            availH >= 55.dp  -> 22.sp
            else             -> 16.sp
        }
        val nameFontSize = if (availH >= 120.dp) 15.sp else 13.sp

        Column(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                snap.name,
                maxLines = 1,
                style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = nameFontSize),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                snap.percent?.let { "$it%" } ?: "—",
                maxLines = 1,
                style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = percentSize),
            )
            if (showBar) {
                Spacer(GlanceModifier.height(5.dp))
                BatteryBar(snap.percent, barW, accentBg, onPhoto = hasPhoto)
            }
            if (showState) {
                Spacer(GlanceModifier.height(5.dp))
                StateChip(stateLabel, stateColor)
            }
            if (showRange) {
                snap.rangeMi?.let {
                    Spacer(GlanceModifier.height(3.dp))
                    Text("$it mi", maxLines = 1, style = TextStyle(color = onVariant, fontSize = 12.sp))
                }
            }
            if (showKind) {
                Text(
                    if (snap.isEv) "Battery" else "Fuel",
                    maxLines = 1,
                    style = TextStyle(color = onVariant, fontSize = 11.sp),
                )
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
        pendingAction: String?,
        snap: VehicleSnapshot,
        accentBg: ColorProvider,
        accentFg: ColorProvider,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(4) { i ->
                if (i > 0) Spacer(GlanceModifier.height(gap))
                ActionPill(widgetId, vin, actions.getOrNull(i), pillH, GlanceModifier.fillMaxWidth(), pendingAction, snap, accentBg, accentFg)
            }
        }
    }

    // ── Landscape layout ──────────────────────────────────────────────────────

    @Composable
    private fun LandscapeBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
        h: Dp,
        photoBitmap: Bitmap?,
        mapBitmap: Bitmap?,
        showBackground: Boolean,
        widgetShape: String,
        locationAddress: String?,
        pendingAction: String?,
        accentBg: ColorProvider,
        accentFg: ColorProvider,
    ) {
        val context = LocalContext.current
        val showPhoto = photoBitmap != null && h >= 160.dp
        val isPill = widgetShape == "pill"
        val basePad = when {
            h >= 180.dp -> 20.dp
            h >= 130.dp -> 16.dp
            h >= 90.dp  -> 13.dp
            else        -> 11.dp
        }
        val hPad = if (isPill) (basePad + (h / 4).coerceAtMost(20.dp)) else basePad
        val vPad = basePad
        val corner = if (isPill) h / 2 else (h / 4).coerceIn(18.dp, 32.dp)
        val gap = 6.dp
        val gridCols = if (w >= 160.dp) 2 else 1
        val gridMinW = (gridCols * 54).dp + gap * (gridCols - 1)
        val showStatus = (w - hPad * 2) > gridMinW + 48.dp

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
                    .padding(horizontal = hPad, vertical = vPad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showStatus) {
                    LandscapeStatusColumn(
                        snap = snap,
                        h = h,
                        hasPhoto = showPhoto,
                        locationAddress = locationAddress,
                        accentBg = accentBg,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(openAction),
                    )
                    Spacer(GlanceModifier.width(10.dp))
                }
                ButtonGrid(
                    widgetId = widgetId,
                    vin = snap.vin,
                    actions = actions,
                    cols = gridCols,
                    contentH = h - vPad * 2,
                    gap = gap,
                    modifier = if (showStatus) GlanceModifier.defaultWeight().fillMaxHeight()
                               else GlanceModifier.fillMaxWidth().fillMaxHeight(),
                    pendingAction = pendingAction,
                    snap = snap,
                    accentBg = accentBg,
                    accentFg = accentFg,
                )
            }
        }
    }

    @Composable
    private fun LandscapeStatusColumn(
        snap: VehicleSnapshot,
        h: Dp,
        hasPhoto: Boolean,
        locationAddress: String?,
        accentBg: ColorProvider,
        modifier: GlanceModifier,
    ) {
        val onSurface = if (hasPhoto) ColorProvider(Color.White) else GlanceTheme.colors.onSurface
        val onVariant = if (hasPhoto) ColorProvider(Color(1f, 1f, 1f, 0.70f)) else GlanceTheme.colors.onSurfaceVariant
        val (stateLabel, stateColor) = stateOf(snap, hasPhoto)

        val showKind = h >= 96.dp
        val showRange = h >= 76.dp
        val showBar = h >= 64.dp
        val showState = h >= 70.dp
        val percentSize = when {
            h >= 200.dp -> 40.sp
            h >= 170.dp -> 34.sp
            h >= 140.dp -> 29.sp
            h >= 110.dp -> 24.sp
            h >= 86.dp  -> 20.sp
            else        -> 17.sp
        }

        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                snap.name,
                maxLines = 1,
                style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp),
            )
            if (showState) {
                Spacer(GlanceModifier.height(2.dp))
                StateChip(stateLabel, stateColor)
            }
            Spacer(GlanceModifier.height(2.dp))
            Text(
                snap.percent?.let { "$it%" } ?: "—",
                maxLines = 1,
                style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = percentSize),
            )
            if (showBar) {
                Spacer(GlanceModifier.height(4.dp))
                BatteryBar(snap.percent, 90.dp, accentBg, onPhoto = hasPhoto)
            }
            if (showRange) {
                snap.rangeMi?.let {
                    Spacer(GlanceModifier.height(3.dp))
                    Text("$it mi", maxLines = 1, style = TextStyle(color = onVariant, fontSize = 12.sp))
                }
            }
            if (locationAddress != null && h >= 100.dp) {
                Text(locationAddress, maxLines = 1, style = TextStyle(color = onVariant, fontSize = 10.sp))
            }
            if (showKind) {
                Text(
                    if (snap.isEv) "Battery" else "Fuel",
                    maxLines = 1,
                    style = TextStyle(color = onVariant, fontSize = 11.sp),
                )
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
        pendingAction: String?,
        snap: VehicleSnapshot,
        accentBg: ColorProvider,
        accentFg: ColorProvider,
    ) {
        val pillH = ((contentH - gap) / 2).coerceIn(26.dp, 56.dp)
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            GridRow(widgetId, vin, actions.getOrNull(0), actions.getOrNull(1), cols, pillH, gap, pendingAction, snap, accentBg, accentFg)
            Spacer(GlanceModifier.height(gap))
            GridRow(widgetId, vin, actions.getOrNull(2), actions.getOrNull(3), cols, pillH, gap, pendingAction, snap, accentBg, accentFg)
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
        pendingAction: String?,
        snap: VehicleSnapshot,
        accentBg: ColorProvider,
        accentFg: ColorProvider,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionPill(widgetId, vin, first, pillH, GlanceModifier.defaultWeight(), pendingAction, snap, accentBg, accentFg)
            if (cols >= 2) {
                Spacer(GlanceModifier.width(gap))
                ActionPill(widgetId, vin, second, pillH, GlanceModifier.defaultWeight(), pendingAction, snap, accentBg, accentFg)
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
        pendingAction: String? = null,
        snap: VehicleSnapshot? = null,
        accentBg: ColorProvider = GlanceTheme.colors.secondaryContainer,
        accentFg: ColorProvider = GlanceTheme.colors.onSecondaryContainer,
    ) {
        val context = LocalContext.current
        if (action == null) {
            Box(modifier.height(pillH)) {}
            return
        }
        val st = actionState(action, snap, pendingAction)
        // Lock/unlock morph: pill when locked, rounded-square when unlocked.
        val corner = when {
            st.isLockAction -> when (snap?.locked) {
                false -> pillH * 0.2f
                else -> pillH / 2
            }
            else -> pillH / 2
        }

        val showLabel = pillH >= 44.dp
        val iconSize = if (showLabel) (pillH * 0.36f).coerceIn(14.dp, 20.dp)
                       else (pillH * 0.46f).coerceIn(14.dp, 24.dp)
        val fg = st.fg(accentFg)
        Box(
            modifier = modifier
                .height(pillH)
                .background(st.bg(accentBg))
                .cornerRadius(corner)
                .clickable(actionStartActivity(authIntent(context, widgetId, vin, action))),
            contentAlignment = Alignment.Center,
        ) {
            if (showLabel) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(st.iconRes),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(fg),
                        modifier = GlanceModifier.size(iconSize),
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        action.label,
                        maxLines = 1,
                        style = TextStyle(color = fg, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    )
                }
            } else {
                Image(
                    provider = ImageProvider(st.iconRes),
                    contentDescription = action.label,
                    colorFilter = ColorFilter.tint(fg),
                    modifier = GlanceModifier.size(iconSize),
                )
            }
        }
    }

    // ── Action visual state ───────────────────────────────────────────────────

    @Composable
    private fun actionState(action: WidgetAction, snap: VehicleSnapshot?, pendingAction: String?): ActionStateColors {
        val isPending = pendingAction == action.key
        val isClimateActive = snap?.climateOn == true &&
            action.key in listOf("climate", "climate_on", "climate_off")
        val isLockAction = action.key in listOf("doors", "lock", "unlock")
        val isLocked = snap?.locked
        val isLockedState = isLockAction && isLocked == true
        val isUnlockedState = isLockAction && isLocked == false
        val isChargeActive = snap?.charging == true &&
            action.key in listOf("charge", "start_charge", "stop_charge")

        val bgPending = GlanceTheme.colors.secondaryContainer
        val fgPending = GlanceTheme.colors.onSecondaryContainer
        val bgClimate = GlanceTheme.colors.tertiary
        val fgClimate = GlanceTheme.colors.onTertiary
        val bgCharge = ColorProvider(Color(0xFF2EBD59))
        val bgLocked = GlanceTheme.colors.primary
        val bgUnlocked = GlanceTheme.colors.error

        val iconRes = when {
            isPending -> R.drawable.ic_widget_refresh
            isClimateActive -> R.drawable.ic_widget_climate_active
            else -> action.icon
        }

        return ActionStateColors(
            isLockAction = isLockAction,
            iconRes = iconRes,
            bgResolver = { accent ->
                when {
                    isPending -> bgPending
                    isClimateActive -> bgClimate
                    isChargeActive -> bgCharge
                    isLockedState -> bgLocked
                    isUnlockedState -> bgUnlocked
                    else -> accent
                }
            },
            fgResolver = { accent ->
                when {
                    isPending -> fgPending
                    isClimateActive -> fgClimate
                    else -> accent
                }
            },
        )
    }

    private class ActionStateColors(
        val isLockAction: Boolean,
        val iconRes: Int,
        val bgResolver: (ColorProvider) -> ColorProvider,
        val fgResolver: (ColorProvider) -> ColorProvider,
    ) {
        fun bg(accent: ColorProvider) = bgResolver(accent)
        fun fg(accent: ColorProvider) = fgResolver(accent)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Composable
    private fun stateOf(snap: VehicleSnapshot, hasPhoto: Boolean = false): Pair<String, ColorProvider> = when {
        snap.engineOn == true -> "Driving" to
            if (hasPhoto) ColorProvider(Color(0.20f, 0.70f, 0.30f, 1f)) else GlanceTheme.colors.tertiary
        snap.charging == true -> "Charging" to ColorProvider(Color(0xFF2EBD59))
        snap.locked == true  -> "Locked" to
            if (hasPhoto) ColorProvider(Color(0.30f, 0.55f, 0.95f, 1f)) else GlanceTheme.colors.primary
        snap.locked == false -> "Unlocked" to
            if (hasPhoto) ColorProvider(Color(0.90f, 0.30f, 0.30f, 1f)) else GlanceTheme.colors.error
        else                 -> "—" to
            if (hasPhoto) ColorProvider(Color(0.45f, 0.45f, 0.45f, 1f)) else GlanceTheme.colors.onSurfaceVariant
    }

    private fun authIntent(context: Context, widgetId: Int, vin: String, action: WidgetAction): Intent =
        Intent(context, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN
            data = Uri.parse("bloo://widget/$widgetId/${action.key}")
            putExtra(WidgetAuthActivity.EXTRA_WIDGET_ID, widgetId)
            putExtra(WidgetAuthActivity.EXTRA_VIN, vin)
            putExtra(WidgetAuthActivity.EXTRA_ACTION, action.key)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }

    private fun configIntent(context: Context, widgetId: Int): Intent =
        Intent(context, WidgetConfigActivity::class.java).apply {
            data = Uri.parse("bloo://widget/config/$widgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
