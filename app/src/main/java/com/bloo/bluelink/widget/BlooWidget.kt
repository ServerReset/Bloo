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
import androidx.glance.action.Action
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
 * Five layout tiers, chosen by aspect ratio and dimensions: Compact, NarrowTall,
 * WideRow, Portrait, Landscape. Every dimension is derived from [LocalSize] via
 * [SizeMode.Exact], so the layout recomposes at the widget's true pixel size on
 * every resize.
 *
 * Colour comes entirely from the app's selected palette (see [WidgetTheme]) rather
 * than the system Material You theme, so the widget always matches the app. Only
 * two states deviate from the accent: charging (green) and unlocked (red), which
 * are universally-understood semantic cues.
 */
class BlooWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    /**
     * Palette-derived colours threaded through every layout. Computed once per
     * render from the app's active palette so the widget mirrors the app theme.
     */
    private class WidgetTheme(
        val accent: ColorProvider,        // primary button fill + locked/active states
        val onAccent: ColorProvider,      // foreground on accent / semantic fills
        val pending: ColorProvider,       // in-flight command fill
        val charge: ColorProvider,        // charging (semantic green)
        val unlocked: ColorProvider,      // unlocked (semantic red)
        val surface: ColorProvider,       // widget background (palette-tinted, day/night)
    )

    private val chargeGreen = Color(0xFF2EBD59)
    private val unlockedRed = Color(0xFFE5484D)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val cfg = SettingsStore(context).widgetConfig(widgetId)
        val snapshots = SnapshotStore(context).current().vehicles
        val snap = cfg?.let { c -> snapshots.firstOrNull { it.vin == c.first } }
        val actions = cfg?.second.orEmpty().mapNotNull { WidgetAction.fromKey(it) }

        // Load the car photo so it can serve as the backdrop ImageProvider.
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

        // ── Derive the full widget palette from the app's selected colours ──
        val theme: WidgetTheme = run {
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
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(swatchArgb, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            // Accent: a medium-value tone that reads on both light and dark surfaces.
            val accentV = if (hsv[2] < 0.35f) 0.62f else hsv[2].coerceAtMost(0.85f)
            // HSVToColor returns an ARGB Int — use Color(Int) not Color(Long) to avoid
            // the packed-64-bit colour-space misinterpretation.
            val accentColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat.coerceAtLeast(0.55f), accentV)))
            // Pending: a dimmed, desaturated accent so in-flight buttons read as muted.
            val pendingColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, (sat * 0.4f).coerceIn(0.08f, 0.3f), 0.55f)))
            // Surface: a subtly palette-tinted background that adapts day vs night.
            val darkSurface = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.16f, 0.13f)))
            val lightSurface = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.06f, 0.97f)))
            WidgetTheme(
                accent = ColorProvider(accentColor),
                onAccent = ColorProvider(Color.White),
                pending = ColorProvider(pendingColor),
                charge = ColorProvider(chargeGreen),
                unlocked = ColorProvider(unlockedRed),
                surface = ColorProvider(day = lightSurface, night = darkSurface),
            )
        }

        provideContent {
            GlanceTheme {
                val w = LocalSize.current.width
                val h = LocalSize.current.height
                val isPortrait = h > w * 1.2f
                when {
                    h < 60.dp -> when {
                        snap != null -> CompactBody(widgetId, snap, actions, w, showBackground, widgetShape, pendingAction, theme)
                        cfg == null  -> UnconfiguredCompact(widgetId)
                        else         -> UnavailableCompact(showBackground, widgetShape, theme)
                    }
                    isPortrait && (w < 110.dp || h > w * 2.5f) -> when {
                        snap != null -> NarrowTallBody(widgetId, snap, actions, w, h, showBackground, widgetShape, pendingAction, theme)
                        cfg == null  -> UnconfiguredFull(widgetId)
                        else         -> UnavailableFull(showBackground, widgetShape, theme)
                    }
                    !isPortrait && w > h * 2.2f -> when {
                        snap != null -> WideRowBody(widgetId, snap, actions, w, h, showBackground, widgetShape, pendingAction, theme)
                        cfg == null  -> UnconfiguredCompact(widgetId)
                        else         -> UnavailableCompact(showBackground, widgetShape, theme)
                    }
                    isPortrait -> when {
                        snap != null -> PortraitBody(widgetId, snap, actions, w, h, photoBitmap, mapBitmap, showBackground, widgetShape, locationAddress, pendingAction, theme)
                        cfg == null  -> UnconfiguredFull(widgetId)
                        else         -> UnavailableFull(showBackground, widgetShape, theme)
                    }
                    else -> when {
                        snap != null -> LandscapeBody(widgetId, snap, actions, w, h, photoBitmap, mapBitmap, showBackground, widgetShape, locationAddress, pendingAction, theme)
                        cfg == null  -> UnconfiguredFull(widgetId)
                        else         -> UnavailableFull(showBackground, widgetShape, theme)
                    }
                }
            }
        }
    }

    // ── Unconfigured / unavailable placeholders ───────────────────────────────

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

    @Composable
    private fun UnavailableFull(showBackground: Boolean, widgetShape: String, theme: WidgetTheme) {
        val corner = if (widgetShape == "pill") 28.dp else 22.dp
        val mod = if (showBackground) GlanceModifier.fillMaxSize().background(theme.surface).cornerRadius(corner)
                  else GlanceModifier.fillMaxSize().cornerRadius(corner)
        Box(modifier = mod, contentAlignment = Alignment.Center) {
            Text("Sign in to Bloo", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium))
        }
    }

    @Composable
    private fun UnavailableCompact(showBackground: Boolean, widgetShape: String, theme: WidgetTheme) {
        val corner = if (widgetShape == "pill") 28.dp else 22.dp
        val mod = if (showBackground) GlanceModifier.fillMaxSize().background(theme.surface).cornerRadius(corner).padding(horizontal = 12.dp, vertical = 4.dp)
                  else GlanceModifier.fillMaxSize().cornerRadius(corner).padding(horizontal = 12.dp, vertical = 4.dp)
        Box(modifier = mod, contentAlignment = Alignment.Center) {
            Text("Sign in", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium))
        }
    }

    // ── Small shared pieces ───────────────────────────────────────────────────

    /** A small filled pill showing the vehicle state (Charging / Locked / …). */
    @Composable
    private fun StateChip(label: String, color: ColorProvider) {
        Box(
            modifier = GlanceModifier.background(color).cornerRadius(9.dp).padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp, fontWeight = FontWeight.Medium),
            )
        }
    }

    /** A thin battery/fuel progress bar. [trackW] is the full track width in dp. */
    @Composable
    private fun BatteryBar(percent: Int?, trackW: Dp, theme: WidgetTheme, onPhoto: Boolean) {
        val pct = (percent ?: 0).coerceIn(0, 100)
        val fillW = trackW * (pct / 100f)
        val trackColor = if (onPhoto) ColorProvider(Color(1f, 1f, 1f, 0.25f)) else ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.35f))
        Box(modifier = GlanceModifier.width(trackW).height(5.dp).background(trackColor).cornerRadius(3.dp)) {
            if (fillW > 0.dp) {
                Box(GlanceModifier.width(fillW).height(5.dp).background(theme.accent).cornerRadius(3.dp)) {}
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
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val isPill = widgetShape == "pill"
        val corner = if (isPill) 28.dp else 22.dp
        val hPad = if (isPill) 14.dp else 10.dp
        val maxButtons = ((w - 110.dp) / 39.dp).toInt().coerceIn(0, 4)
        val showState = w >= 150.dp

        val boxMod = if (showBackground) {
            GlanceModifier.fillMaxSize().background(theme.surface).cornerRadius(corner)
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
                            val (label, color) = stateOf(snap, theme)
                            StateChip(label, color)
                        }
                    }
                }
                actions.take(maxButtons).forEach { action ->
                    Spacer(GlanceModifier.width(5.dp))
                    CircleButton(widgetId, snap.vin, action, 34.dp, pendingAction, snap, theme)
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
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val st = actionState(action, snap, pendingAction, theme)
        val corner = when {
            st.isLockAction && snap.locked == false -> size * 0.28f  // unlocked → rounded square
            else -> size / 2
        }
        Box(
            modifier = GlanceModifier
                .size(size)
                .background(st.bg)
                .cornerRadius(corner)
                .clickable(actionStartActivity(authIntent(context, widgetId, vin, action))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(st.iconRes),
                contentDescription = action.label,
                colorFilter = ColorFilter.tint(theme.onAccent),
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
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val isPill = widgetShape == "pill"
        val corner = if (isPill) w / 2 else (w / 4).coerceIn(16.dp, 28.dp)
        val pad = if (isPill) (w / 5).coerceIn(6.dp, 14.dp) else 8.dp
        val gap = 5.dp
        val btnH = 36.dp

        val minStatusH = 66.dp
        val avail = h - pad * 2
        val fitButtons = (((avail - minStatusH - gap) / (btnH + gap)).toInt()).coerceIn(0, 4)
        val maxButtons = actions.size.coerceAtMost(fitButtons)
        val buttonAreaH = if (maxButtons > 0) (btnH * maxButtons + gap * maxButtons) else 0.dp
        val statusH = (avail - buttonAreaH).coerceAtLeast(minStatusH)
        val showKind = statusH >= 118.dp
        val showRange = statusH >= 98.dp
        val showBar = statusH >= 82.dp
        val showState = statusH >= 66.dp
        val percentSize = when {
            statusH >= 130.dp -> 32.sp
            statusH >= 100.dp -> 26.sp
            statusH >= 80.dp  -> 22.sp
            else              -> 18.sp
        }
        val barW = (w - pad * 2).coerceAtLeast(0.dp)

        val openAction = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))
        val (stateLabel, stateColor) = stateOf(snap, theme)

        val boxMod = if (showBackground) {
            GlanceModifier.fillMaxSize().background(theme.surface).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }

        Box(modifier = boxMod) {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(pad),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxWidth().height(statusH).clickable(openAction),
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
                        BatteryBar(snap.percent, barW, theme, onPhoto = false)
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
                        ActionPill(widgetId, snap.vin, action, btnH, GlanceModifier.fillMaxWidth(), pendingAction, snap, theme, allowLabel = false)
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
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val isPill = widgetShape == "pill"
        val corner = if (isPill) h / 2 else 22.dp
        val pad = 8.dp
        val hPad = if (isPill) (h / 3).coerceIn(pad, 28.dp) else pad
        val gap = 6.dp
        val pillH = (h - pad * 2).coerceIn(24.dp, 54.dp)

        val openAction = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))
        val (stateLabel, stateColor) = stateOf(snap, theme)

        val boxMod = if (showBackground) {
            GlanceModifier.fillMaxSize().background(theme.surface).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }

        Box(modifier = boxMod) {
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = hPad, vertical = pad),
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
                // Narrow pills → icon only (labels would truncate to "L…").
                Row(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.take(4).forEachIndexed { i, action ->
                        if (i > 0) Spacer(GlanceModifier.width(gap))
                        ActionPill(widgetId, snap.vin, action, pillH, GlanceModifier.defaultWeight(), pendingAction, snap, theme, allowLabel = false)
                    }
                    repeat((4 - actions.size).coerceAtLeast(0)) { i ->
                        if (actions.isNotEmpty() || i > 0) Spacer(GlanceModifier.width(gap))
                        ActionPill(widgetId, snap.vin, null, pillH, GlanceModifier.defaultWeight(), pendingAction, snap, theme, allowLabel = false)
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
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val showMap = mapBitmap != null && h >= 200.dp
        val showPhoto = !showMap && photoBitmap != null && h >= 200.dp
        val isPill = widgetShape == "pill"
        val basePad = when {
            h >= 220.dp -> 16.dp
            h >= 150.dp -> 13.dp
            else -> 11.dp
        }
        val pad = if (isPill) (basePad + (w / 5).coerceAtMost(22.dp)) else basePad
        val corner = if (isPill) w / 2 else (w / 4).coerceIn(20.dp, 32.dp)
        val gap = 7.dp
        val mapCardH = if (showMap) 112.dp else 0.dp
        // Slightly smaller, refined buttons; more divisor weight → status keeps room.
        val pillH = ((h - pad * 2 - gap * 3 - mapCardH) / 5.8f).coerceIn(30.dp, 52.dp)
        val gridH = pillH * 4 + gap * 3
        val statusH = h - pad * 2 - gridH - gap - (if (showMap) mapCardH + gap else 0.dp)
        val showStatus = statusH >= 28.dp
        val barW = (w - pad * 2).coerceAtLeast(0.dp)

        val openAction = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))

        val boxMod = if (showBackground) {
            GlanceModifier.fillMaxSize().background(theme.surface).cornerRadius(corner)
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
                        theme = theme,
                        modifier = GlanceModifier.fillMaxWidth().height(statusH).clickable(openAction),
                    )
                    Spacer(GlanceModifier.height(gap))
                }
                if (showMap) {
                    LocationCard(mapBitmap!!, locationAddress, mapCardH, openAction)
                    Spacer(GlanceModifier.height(gap))
                }
                PortraitButtonGrid(widgetId, snap.vin, actions, pillH, gap, pendingAction, snap, theme)
            }
        }
    }

    /** A rounded map image with the reverse-geocoded address overlaid at the bottom. */
    @Composable
    private fun LocationCard(mapBitmap: Bitmap, address: String?, height: Dp, onClick: Action) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(height).cornerRadius(14.dp).clickable(onClick),
        ) {
            Image(
                provider = ImageProvider(mapBitmap),
                contentDescription = "Car location",
                modifier = GlanceModifier.fillMaxSize().cornerRadius(14.dp),
                contentScale = ContentScale.Crop,
            )
            if (!address.isNullOrBlank()) {
                Box(
                    modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Box(
                        modifier = GlanceModifier.background(ColorProvider(Color(0f, 0f, 0f, 0.55f)))
                            .cornerRadius(8.dp).padding(horizontal = 8.dp, vertical = 3.dp),
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
        theme: WidgetTheme,
        modifier: GlanceModifier,
    ) {
        val onSurface = if (hasPhoto) ColorProvider(Color.White) else GlanceTheme.colors.onSurface
        val onVariant = if (hasPhoto) ColorProvider(Color(1f, 1f, 1f, 0.70f)) else GlanceTheme.colors.onSurfaceVariant
        val (stateLabel, stateColor) = stateOf(snap, theme, hasPhoto)

        // Stricter thresholds so a clipped fixed-height column never half-shows a line.
        val showKind = availH >= 96.dp
        val showRange = availH >= 70.dp
        val showBar = availH >= 50.dp
        val showState = availH >= 32.dp
        val percentSize = when {
            availH >= 140.dp -> 42.sp
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
                BatteryBar(snap.percent, barW, theme, onPhoto = hasPhoto)
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
        theme: WidgetTheme,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(4) { i ->
                if (i > 0) Spacer(GlanceModifier.height(gap))
                ActionPill(widgetId, vin, actions.getOrNull(i), pillH, GlanceModifier.fillMaxWidth(), pendingAction, snap, theme, allowLabel = true)
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
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val showPhoto = photoBitmap != null && h >= 160.dp
        val isPill = widgetShape == "pill"
        val basePad = when {
            h >= 180.dp -> 18.dp
            h >= 130.dp -> 15.dp
            h >= 90.dp  -> 13.dp
            else        -> 11.dp
        }
        val hPad = if (isPill) (basePad + (h / 4).coerceAtMost(20.dp)) else basePad
        val vPad = basePad
        val corner = if (isPill) h / 2 else (h / 4).coerceIn(20.dp, 32.dp)
        val gap = 6.dp
        val gridCols = if (w >= 160.dp) 2 else 1
        val gridMinW = (gridCols * 54).dp + gap * (gridCols - 1)
        val showStatus = (w - hPad * 2) > gridMinW + 48.dp

        val openAction = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))

        val boxMod = if (showBackground) {
            GlanceModifier.fillMaxSize().background(theme.surface).cornerRadius(corner)
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
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = hPad, vertical = vPad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showStatus) {
                    LandscapeStatusColumn(
                        snap = snap,
                        h = h,
                        hasPhoto = showPhoto,
                        locationAddress = locationAddress,
                        theme = theme,
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
                    theme = theme,
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
        theme: WidgetTheme,
        modifier: GlanceModifier,
    ) {
        val onSurface = if (hasPhoto) ColorProvider(Color.White) else GlanceTheme.colors.onSurface
        val onVariant = if (hasPhoto) ColorProvider(Color(1f, 1f, 1f, 0.70f)) else GlanceTheme.colors.onSurfaceVariant
        val (stateLabel, stateColor) = stateOf(snap, theme, hasPhoto)

        val showKind = h >= 96.dp
        val showRange = h >= 76.dp
        val showBar = h >= 64.dp
        val showState = h >= 70.dp
        val percentSize = when {
            h >= 200.dp -> 38.sp
            h >= 170.dp -> 33.sp
            h >= 140.dp -> 28.sp
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
                BatteryBar(snap.percent, 90.dp, theme, onPhoto = hasPhoto)
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
        theme: WidgetTheme,
    ) {
        val pillH = ((contentH - gap) / 2).coerceIn(26.dp, 56.dp)
        // Two columns are narrow — labels would truncate, so icon-only there.
        val allowLabel = cols == 1
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            GridRow(widgetId, vin, actions.getOrNull(0), actions.getOrNull(1), cols, pillH, gap, pendingAction, snap, theme, allowLabel)
            Spacer(GlanceModifier.height(gap))
            GridRow(widgetId, vin, actions.getOrNull(2), actions.getOrNull(3), cols, pillH, gap, pendingAction, snap, theme, allowLabel)
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
        theme: WidgetTheme,
        allowLabel: Boolean,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionPill(widgetId, vin, first, pillH, GlanceModifier.defaultWeight(), pendingAction, snap, theme, allowLabel)
            if (cols >= 2) {
                Spacer(GlanceModifier.width(gap))
                ActionPill(widgetId, vin, second, pillH, GlanceModifier.defaultWeight(), pendingAction, snap, theme, allowLabel)
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
        pendingAction: String?,
        snap: VehicleSnapshot?,
        theme: WidgetTheme,
        allowLabel: Boolean,
    ) {
        val context = LocalContext.current
        if (action == null) {
            Box(modifier.height(pillH)) {}
            return
        }
        val st = actionState(action, snap, pendingAction, theme)
        // Refined rounded-rectangle: corner capped so tall pills don't become lozenges.
        val baseCorner = (pillH / 2).coerceAtMost(20.dp)
        val corner = if (st.isLockAction && snap?.locked == false) pillH * 0.22f else baseCorner

        val showLabel = allowLabel && pillH >= 44.dp
        val iconSize = if (showLabel) (pillH * 0.34f).coerceIn(14.dp, 20.dp)
                       else (pillH * 0.46f).coerceIn(14.dp, 24.dp)
        val fg = theme.onAccent
        Box(
            modifier = modifier
                .height(pillH)
                .background(st.bg)
                .cornerRadius(corner)
                .clickable(actionStartActivity(authIntent(context, widgetId, vin, action))),
            contentAlignment = Alignment.Center,
        ) {
            if (showLabel) {
                // For the combined lock/unlock button use a short state-aware label
                // so it never truncates inside narrow portrait grid cells.
                val displayLabel = when (action.key) {
                    "doors" -> when (snap?.locked) {
                        true  -> "Locked"
                        false -> "Unlocked"
                        else  -> "Doors"
                    }
                    else -> action.label
                }
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
                        displayLabel,
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

    /** Resolved background + icon for an action, all keyed off the app palette. */
    private class ActionVisual(val isLockAction: Boolean, val iconRes: Int, val bg: ColorProvider)

    @Composable
    private fun actionState(
        action: WidgetAction,
        snap: VehicleSnapshot?,
        pendingAction: String?,
        theme: WidgetTheme,
    ): ActionVisual {
        val isPending = pendingAction == action.key
        val isClimateActive = snap?.climateOn == true &&
            action.key in listOf("climate", "climate_on", "climate_off")
        val isLockAction = action.key in listOf("doors", "lock", "unlock")
        val isUnlockedState = isLockAction && snap?.locked == false
        val isChargeActive = snap?.charging == true &&
            action.key in listOf("charge", "start_charge", "stop_charge")

        val iconRes = when {
            isPending -> R.drawable.ic_widget_refresh
            isClimateActive -> R.drawable.ic_widget_climate_active
            else -> action.icon
        }
        // Locked + climate-active + idle all use the accent (the active icon conveys
        // climate state); only charging (green) and unlocked (red) break from theme.
        val bg = when {
            isPending -> theme.pending
            isChargeActive -> theme.charge
            isUnlockedState -> theme.unlocked
            else -> theme.accent
        }
        return ActionVisual(isLockAction, iconRes, bg)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Composable
    private fun stateOf(snap: VehicleSnapshot, theme: WidgetTheme, hasPhoto: Boolean = false): Pair<String, ColorProvider> = when {
        snap.engineOn == true -> "Driving" to theme.accent
        snap.charging == true -> "Charging" to theme.charge
        snap.locked == true  -> "Locked" to theme.accent
        snap.locked == false -> "Unlocked" to theme.unlocked
        else                 -> "—" to ColorProvider(Color(0.42f, 0.42f, 0.46f, 1f))
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
