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
import com.bloo.bluelink.ui.resolveWidgetAccent
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
        val accentMuted: ColorProvider,   // slightly muted accent for secondary surfaces
        val onAccent: ColorProvider,      // foreground on accent / semantic fills
        val pending: ColorProvider,       // in-flight command fill
        val charge: ColorProvider,        // charging (semantic green)
        val unlocked: ColorProvider,      // unlocked (semantic red)
        val climate: ColorProvider,       // climate-on (teal blended toward accent)
        // background is NOT stored here — callers use GlanceTheme.colors.widgetBackground
        // so the system handles dark/light adaptation automatically.
    )

    private val chargeGreen = Color(0xFF2EBD59)
    private val unlockedRed = Color(0xFFE5484D)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Construct each store once — these were previously rebuilt ~7× per render.
        val settings = SettingsStore(context)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val cfg = settings.widgetConfig(widgetId)
        val snapshots = SnapshotStore(context).current().vehicles
        val snap = cfg?.let { c -> snapshots.firstOrNull { it.vin == c.first } }
        val actions = cfg?.second.orEmpty().mapNotNull { WidgetAction.fromKey(it) }

        // Load the car photo so it can serve as the backdrop ImageProvider. Decode is
        // cached by path+mtime so repeated renders (resize/refresh) don't re-decode.
        val photoBitmap: Bitmap? = snap?.let { s ->
            val path = settings.imageUrl(s.vin)
            if (path != null && path.startsWith("/")) decodeCached(path, sample = 2) else null
        }

        val showBackground = cfg?.let { settings.widgetShowBackground(widgetId) } ?: true
        val widgetShape = cfg?.let { settings.widgetShape(widgetId) } ?: "rect"
        val widgetStyle = cfg?.let { settings.widgetStyle(widgetId) } ?: "auto"
        val widgetAccentHex = cfg?.let { settings.widgetAccent(widgetId) }
        val widgetShowName = cfg?.let { settings.widgetShowName(widgetId) } ?: true
        val widgetShowRange = cfg?.let { settings.widgetShowRange(widgetId) } ?: true
        val widgetShowState = cfg?.let { settings.widgetShowState(widgetId) } ?: true
        val widgetMetrics = cfg?.let { settings.widgetMetrics(widgetId) } ?: listOf("battery", "range")
        val pendingAction = cfg?.let { settings.widgetPendingAction(widgetId) }
        val locationAddress = cfg?.let { settings.widgetLocationAddress(widgetId) }

        // Cached location-map tile, present only after a Location action has run.
        val hasLocationAction = actions.any { it == WidgetAction.LOCATION }
        val mapBitmap: Bitmap? = if (hasLocationAction) {
            val mapFile = java.io.File(context.cacheDir, "widget_map_$widgetId.png")
            if (mapFile.exists()) decodeCached(mapFile.absolutePath, sample = 1, rgb565 = true) else null
        } else null

        // ── Pull the widget accent from the app's active colour palette ──
        // resolveWidgetAccent mirrors BlooTheme's palette logic exactly, including
        // dark-mode adaptation and dynamic color, so the widget always matches the app.
        val theme: WidgetTheme = run {
            val appearance = settings.appearance.first()
            // Per-widget accent override (hex) wins; otherwise follow the app palette.
            val accentColor: Color = widgetAccentHex
                ?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }
                ?: resolveWidgetAccent(context, appearance, snap?.vin)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(accentColor.toArgb(), hsv)
            // Pending: desaturated + dimmed so in-flight buttons read as muted.
            val pendingColor = Color(android.graphics.Color.HSVToColor(
                floatArrayOf(hsv[0], (hsv[1] * 0.4f).coerceIn(0.08f, 0.3f), 0.55f)
            ))
            // Muted accent: used for secondary/inactive chips so they still feel on-theme.
            val accentMuted = Color(android.graphics.Color.HSVToColor(
                floatArrayOf(hsv[0], (hsv[1] * 0.55f).coerceIn(0.1f, 0.5f), (hsv[2] * 0.55f).coerceAtLeast(0.18f))
            ))
            // Climate: blend accent hue toward teal (180°) at half-weight, keeping V.
            val tealHue = 180f
            val climateHue = hsv[0] + (tealHue - hsv[0]) * 0.5f
            val climateColor = Color(android.graphics.Color.HSVToColor(
                floatArrayOf(climateHue, (hsv[1] * 0.75f).coerceIn(0.35f, 0.85f), (hsv[2] * 0.8f).coerceAtLeast(0.38f))
            ))
            WidgetTheme(
                accent = ColorProvider(accentColor),
                accentMuted = ColorProvider(accentMuted),
                onAccent = ColorProvider(Color.White),
                pending = ColorProvider(pendingColor),
                charge = ColorProvider(chargeGreen),
                unlocked = ColorProvider(unlockedRed),
                climate = ColorProvider(climateColor),
            )
        }

        provideContent {
            GlanceTheme {
                val w = LocalSize.current.width
                val h = LocalSize.current.height
                val isPortrait = h > w * 1.2f
                // Skinny = portrait AND width < 155dp (catches 1- and 2-column home-screen
                // slots). WideRow triggers earlier (> 2× width-to-height) so short banners
                // never fall through to landscape.
                val isSkinny = isPortrait && (w < 155.dp || h > w * 2.0f)
                when {
                    // User-chosen alternate styles render the same at any size.
                    snap != null && widgetStyle == "minimal" ->
                        MinimalBody(widgetId, snap, w, h, showBackground, widgetShape, theme)
                    snap != null && widgetStyle == "stats" ->
                        StatsBody(widgetId, snap, w, h, showBackground, widgetShape, theme)
                    snap != null && widgetStyle == "photo" ->
                        PhotoBody(widgetId, snap, actions, w, h, photoBitmap, widgetShowName, widgetShowRange, widgetShowState, pendingAction, theme)
                    snap != null && widgetStyle == "dual" ->
                        DualBody(widgetId, snap, w, h, widgetMetrics, widgetShowName, theme)
                    h < 65.dp -> when {
                        snap != null -> CompactBody(widgetId, snap, actions, w, h, showBackground, widgetShape, pendingAction, theme)
                        cfg == null  -> UnconfiguredCompact(widgetId)
                        else         -> UnavailableCompact(showBackground, widgetShape, theme)
                    }
                    isSkinny -> when {
                        snap != null -> NarrowTallBody(widgetId, snap, actions, w, h, showBackground, widgetShape, pendingAction, theme)
                        cfg == null  -> UnconfiguredFull(widgetId)
                        else         -> UnavailableFull(showBackground, widgetShape, theme)
                    }
                    !isPortrait && w > h * 2.0f -> when {
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
        val mod = if (showBackground) GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
                  else GlanceModifier.fillMaxSize().cornerRadius(corner)
        Box(modifier = mod, contentAlignment = Alignment.Center) {
            Text("Sign in to Bloo", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium))
        }
    }

    @Composable
    private fun UnavailableCompact(showBackground: Boolean, widgetShape: String, theme: WidgetTheme) {
        val corner = if (widgetShape == "pill") 28.dp else 22.dp
        val mod = if (showBackground) GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner).padding(horizontal = 12.dp, vertical = 4.dp)
                  else GlanceModifier.fillMaxSize().cornerRadius(corner).padding(horizontal = 12.dp, vertical = 4.dp)
        Box(modifier = mod, contentAlignment = Alignment.Center) {
            Text("Sign in", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium))
        }
    }

    // ── Small shared pieces ───────────────────────────────────────────────────

    /** A filled pill showing the vehicle state (Charging / Locked / …). */
    @Composable
    private fun StateChip(label: String, color: ColorProvider) {
        Box(
            modifier = GlanceModifier.background(color).cornerRadius(10.dp).padding(horizontal = 10.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    /** A battery/fuel progress bar. [trackW] is the full track width in dp. */
    @Composable
    private fun BatteryBar(percent: Int?, trackW: Dp, theme: WidgetTheme, onPhoto: Boolean) {
        val pct = (percent ?: 0).coerceIn(0, 100)
        val fillW = trackW * (pct / 100f)
        val trackColor = if (onPhoto) ColorProvider(Color(1f, 1f, 1f, 0.22f)) else ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.28f))
        Box(modifier = GlanceModifier.width(trackW).height(7.dp).background(trackColor).cornerRadius(4.dp)) {
            if (fillW > 0.dp) {
                Box(GlanceModifier.width(fillW).height(7.dp).background(theme.accent).cornerRadius(4.dp)) {}
            }
        }
    }

    // ── Alternate user-selectable styles ──────────────────────────────────────

    /** "Minimal": car name + one big number (charge or range) + a state chip,
     *  centered. No photo/map/buttons — a clean glance. Tap opens the app. */
    @Composable
    private fun MinimalBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        w: Dp,
        h: Dp,
        showBackground: Boolean,
        widgetShape: String,
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val corner = if (widgetShape == "pill") 28.dp else 24.dp
        val base = if (showBackground) {
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }
        val (stateLabel, stateColor) = stateOf(snap, theme)
        val big = snap.percent?.let { "$it%" } ?: snap.rangeMi?.let { "$it mi" } ?: "—"
        val bigSize = if (h < 95.dp) 28.sp else 46.sp
        Box(
            modifier = base.clickable(actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN)))
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    snap.name,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    big,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = bigSize, fontWeight = FontWeight.Bold),
                )
                if (h >= 80.dp) {
                    Spacer(GlanceModifier.height(8.dp))
                    StateChip(stateLabel, stateColor)
                }
            }
        }
    }

    /** "Stats": car name + a 2×2 grid of metrics (Battery, Range, Lock, Climate).
     *  Info-dense at a glance; tap opens the app. */
    @Composable
    private fun StatsBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        w: Dp,
        h: Dp,
        showBackground: Boolean,
        widgetShape: String,
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val corner = if (widgetShape == "pill") 28.dp else 24.dp
        val base = if (showBackground) {
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }
        val lock = when (snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" }
        val climate = if (snap.climateOn == true) "On" else "Off"
        Column(
            modifier = base.clickable(actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN)))
                .padding(14.dp),
        ) {
            Text(
                snap.name,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                StatCell("Battery", snap.percent?.let { "$it%" } ?: "—", theme.accent, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                StatCell("Range", snap.rangeMi?.let { "$it mi" } ?: "—", theme.accent, GlanceModifier.defaultWeight())
            }
            if (h >= 110.dp) {
                Spacer(GlanceModifier.height(8.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    StatCell("Lock", lock, if (snap.locked == false) theme.unlocked else theme.accentMuted, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                    StatCell("Climate", climate, if (snap.climateOn == true) theme.climate else theme.accentMuted, GlanceModifier.defaultWeight())
                }
            }
        }
    }

    /** One labelled metric cell for [StatsBody]. */
    @Composable
    private fun StatCell(label: String, value: String, accent: ColorProvider, modifier: GlanceModifier = GlanceModifier) {
        Column(
            modifier = modifier
                .background(ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.16f)))
                .cornerRadius(14.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                label.uppercase(),
                maxLines = 1,
                style = TextStyle(color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                value,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    /** "Photo": the car photo full-bleed with name/% over a scrim, plus an optional
     *  action row. Falls back to a tonal surface when no photo is set. */
    @Composable
    private fun PhotoBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
        h: Dp,
        photoBitmap: Bitmap?,
        showName: Boolean,
        showRange: Boolean,
        showState: Boolean,
        pendingAction: String?,
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val open = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))
        val corner = if (w < 180.dp) 22.dp else 28.dp
        Box(modifier = GlanceModifier.fillMaxSize().cornerRadius(corner)) {
            if (photoBitmap != null) {
                Image(
                    provider = ImageProvider(photoBitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(corner),
                )
                Box(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0f, 0f, 0f, 0.42f)))) {}
            } else {
                Box(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)) {}
            }
            Column(
                GlanceModifier.fillMaxSize().padding(14.dp).clickable(open),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (showName) {
                    Text(
                        snap.name,
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        snap.percent?.let { "$it%" } ?: "—",
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    )
                    if (showRange) snap.rangeMi?.let {
                        Spacer(GlanceModifier.width(8.dp))
                        Text("$it mi", style = TextStyle(color = ColorProvider(Color(1f, 1f, 1f, 0.78f)), fontSize = 12.sp))
                    }
                    if (showState) {
                        Spacer(GlanceModifier.width(8.dp))
                        val (l, c) = stateOf(snap, theme, hasPhoto = true)
                        StateChip(l, c)
                    }
                }
                if (actions.isNotEmpty() && h >= 130.dp) {
                    Spacer(GlanceModifier.height(8.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        actions.take(4).forEachIndexed { i, a ->
                            if (i > 0) Spacer(GlanceModifier.width(6.dp))
                            ActionPill(widgetId, snap.vin, a, 40.dp, GlanceModifier.defaultWeight(), pendingAction, snap, theme, allowLabel = false)
                        }
                    }
                }
            }
        }
    }

    /** "Dual": one or two big chosen metrics (battery/range/lock/climate). */
    @Composable
    private fun DualBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        w: Dp,
        h: Dp,
        metrics: List<String>,
        showName: Boolean,
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val open = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))
        val corner = if (w < 180.dp) 22.dp else 24.dp
        val base = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        val pick = metrics.take(2).ifEmpty { listOf("battery", "range") }
        fun valueOf(m: String) = when (m) {
            "battery" -> snap.percent?.let { "$it%" } ?: "—"
            "range" -> snap.rangeMi?.let { "$it mi" } ?: "—"
            "lock" -> when (snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" }
            "climate" -> if (snap.climateOn == true) "On" else "Off"
            else -> "—"
        }
        Box(base.clickable(open).padding(14.dp)) {
            Column(GlanceModifier.fillMaxSize()) {
                if (showName) {
                    Text(snap.name, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    Spacer(GlanceModifier.height(6.dp))
                }
                if (w > h) {
                    Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        pick.forEach { m ->
                            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(m.uppercase(), maxLines = 1, style = TextStyle(color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                Spacer(GlanceModifier.height(2.dp))
                                Text(valueOf(m), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 32.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                } else {
                    Column(GlanceModifier.fillMaxWidth().defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        pick.forEach { m ->
                            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(m.uppercase(), maxLines = 1, style = TextStyle(color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                Spacer(GlanceModifier.height(2.dp))
                                Text(valueOf(m), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 32.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
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
        h: Dp,
        showBackground: Boolean,
        widgetShape: String,
        pendingAction: String?,
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val isPill = widgetShape == "pill"
        val corner = if (isPill) 28.dp else 22.dp
        val hPad = if (isPill) 14.dp else 10.dp
        // On a narrow compact strip show numbers vertically with no buttons.
        val isNarrow = w < 120.dp
        val maxButtons = if (isNarrow) 0 else ((w - 110.dp) / 39.dp).toInt().coerceIn(0, 4)
        val showState = w >= 150.dp
        // Show range inline when there's enough width but not enough for a state chip.
        val showRange = w in 190.dp..249.dp && snap.rangeMi != null

        val boxMod = if (showBackground) {
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }
        val openAction = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))

        Box(modifier = boxMod) {
            if (isNarrow) {
                // Ultra-narrow: stack name above %, buttons omitted.
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = hPad, vertical = 4.dp)
                        .clickable(openAction),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        snap.name,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium, fontSize = 9.sp),
                    )
                    Text(
                        snap.percent?.let { "$it%" } ?: "—",
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    )
                }
            } else {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = hPad, vertical = 4.dp)
                        .clickable(openAction),
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
                            if (showRange) {
                                Spacer(GlanceModifier.width(5.dp))
                                Text(
                                    "· ${snap.rangeMi} mi",
                                    maxLines = 1,
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                                )
                            }
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
        // Ultra-narrow (< 100dp wide): show only stat info, no action buttons.
        // The number layout is still vertical so the user can read the key values.
        val ultraNarrow = w < 100.dp
        val btnH = 36.dp

        val minStatusH = if (ultraNarrow) 72.dp else 66.dp
        val avail = h - pad * 2
        val fitButtons = if (ultraNarrow) 0
                         else (((avail - minStatusH - gap) / (btnH + gap)).toInt()).coerceIn(0, 4)
        val maxButtons = actions.size.coerceAtMost(fitButtons)
        val buttonAreaH = if (maxButtons > 0) (btnH * maxButtons + gap * maxButtons) else 0.dp
        val statusH = (avail - buttonAreaH).coerceAtLeast(minStatusH)
        val showKind  = statusH >= 118.dp
        val showRange = statusH >= 98.dp
        val showBar   = statusH >= 82.dp && !ultraNarrow
        val showState = statusH >= 66.dp
        val percentSize = when {
            statusH >= 130.dp -> if (ultraNarrow) 38.sp else 32.sp
            statusH >= 100.dp -> if (ultraNarrow) 30.sp else 26.sp
            statusH >= 80.dp  -> if (ultraNarrow) 24.sp else 22.sp
            else              -> 18.sp
        }
        val nameFontSize = if (ultraNarrow && w < 80.dp) 9.sp else 11.sp
        val barW = (w - pad * 2).coerceAtLeast(0.dp)

        val openAction = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))
        val (stateLabel, stateColor) = stateOf(snap, theme)

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
                    modifier = GlanceModifier.fillMaxWidth().height(statusH).clickable(openAction),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        snap.name,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = nameFontSize),
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
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }

        val showBar = h >= 80.dp
        val barW = (w * 0.22f).coerceIn(60.dp, 110.dp)

        Box(modifier = boxMod) {
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = hPad, vertical = pad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cap the info column so a long car name truncates instead of squeezing
                // the action buttons off the row.
                val infoW = (w * 0.4f).coerceIn(70.dp, 150.dp)
                Column(
                    modifier = GlanceModifier.height(pillH).width(infoW).clickable(openAction),
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
                    if (showBar) {
                        Spacer(GlanceModifier.height(3.dp))
                        BatteryBar(snap.percent, barW, theme, onPhoto = false)
                    }
                    if (h >= 70.dp) {
                        Spacer(GlanceModifier.height(3.dp))
                        StateChip(stateLabel, stateColor)
                    }
                }
                Spacer(GlanceModifier.width(8.dp))
                // Pills sit directly in the outer Row (not a nested weighted Row) —
                // RemoteViews weight distribution is unreliable two levels deep on
                // some launchers, which left pills undersized and text clipped.
                val allowLabel = pillH >= 52.dp && w >= 280.dp
                val shown = actions.take(4)
                val padCount = (4 - shown.size).coerceAtLeast(0)
                shown.forEachIndexed { i, action ->
                    if (i > 0) Spacer(GlanceModifier.width(gap))
                    ActionPill(widgetId, snap.vin, action, pillH, GlanceModifier.defaultWeight(), pendingAction, snap, theme, allowLabel = allowLabel)
                }
                repeat(padCount) { i ->
                    if (shown.isNotEmpty() || i > 0) Spacer(GlanceModifier.width(gap))
                    ActionPill(widgetId, snap.vin, null, pillH, GlanceModifier.defaultWeight(), pendingAction, snap, theme, allowLabel = false)
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
        // Use 2-column grid when the widget is wide enough; otherwise single column with labels.
        val gridCols = if (w >= 130.dp) 2 else 1
        val gridRows = if (gridCols == 2) 2 else 4
        val pillH = ((h - pad * 2 - gap * (gridRows - 1) - mapCardH) / (gridRows + 1.8f)).coerceIn(30.dp, 54.dp)
        val gridH = pillH * gridRows + gap * (gridRows - 1)
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
                        theme = theme,
                        modifier = GlanceModifier.fillMaxWidth().height(statusH).clickable(openAction),
                    )
                    Spacer(GlanceModifier.height(gap))
                }
                if (showMap) {
                    LocationCard(mapBitmap!!, locationAddress, mapCardH, openAction)
                    Spacer(GlanceModifier.height(gap))
                }
                PortraitButtonGrid(widgetId, snap.vin, actions, pillH, gap, pendingAction, snap, theme, gridCols)
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
        val showRangeInline = availH in 50.dp..69.dp && snap.rangeMi != null
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
            if (showRangeInline) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        snap.percent?.let { "$it%" } ?: "—",
                        maxLines = 1,
                        style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = percentSize),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        "${snap.rangeMi} mi",
                        maxLines = 1,
                        style = TextStyle(color = onVariant, fontSize = 12.sp),
                    )
                }
            } else {
                Text(
                    snap.percent?.let { "$it%" } ?: "—",
                    maxLines = 1,
                    style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = percentSize),
                )
            }
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
        cols: Int = 1,
    ) {
        if (cols == 1) {
            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(4) { i ->
                    if (i > 0) Spacer(GlanceModifier.height(gap))
                    ActionPill(widgetId, vin, actions.getOrNull(i), pillH, GlanceModifier.fillMaxWidth(), pendingAction, snap, theme, allowLabel = true)
                }
            }
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                repeat(2) { row ->
                    if (row > 0) Spacer(GlanceModifier.height(gap))
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ActionPill(widgetId, vin, actions.getOrNull(row * 2), pillH, GlanceModifier.defaultWeight(), pendingAction, snap, theme, allowLabel = pillH >= 44.dp)
                        Spacer(GlanceModifier.width(gap))
                        ActionPill(widgetId, vin, actions.getOrNull(row * 2 + 1), pillH, GlanceModifier.defaultWeight(), pendingAction, snap, theme, allowLabel = pillH >= 44.dp)
                    }
                }
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
        val isClimateActive = snap?.climateOn == true && action.key in CLIMATE_KEYS
        val isLockAction = action.key in LOCK_KEYS
        val isUnlockedState = isLockAction && snap?.locked == false
        val isChargeActive = snap?.charging == true && action.key in CHARGE_KEYS

        val iconRes = when {
            isPending -> R.drawable.ic_widget_refresh
            isClimateActive -> R.drawable.ic_widget_climate_active
            else -> action.icon
        }
        // Semantic colours: charging = green, unlocked = red, climate = palette teal,
        // locked/other = accent. Pending is always muted.
        val bg = when {
            isPending       -> theme.pending
            isChargeActive  -> theme.charge
            isUnlockedState -> theme.unlocked
            isClimateActive -> theme.climate
            else            -> theme.accent
        }
        return ActionVisual(isLockAction, iconRes, bg)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Composable
    private fun stateOf(snap: VehicleSnapshot, theme: WidgetTheme, hasPhoto: Boolean = false): Pair<String, ColorProvider> = when {
        snap.engineOn == true  -> "Driving"    to theme.accent
        snap.charging == true  -> "Charging"   to theme.charge
        snap.climateOn == true -> "Climate on" to theme.climate
        snap.locked == true    -> "Locked"     to theme.accentMuted
        snap.locked == false   -> "Unlocked"   to theme.unlocked
        else                   -> "—"          to ColorProvider(Color(0.42f, 0.42f, 0.46f, 1f))
    }

    private fun authIntent(context: Context, widgetId: Int, vin: String, action: WidgetAction): Intent =
        Intent(context, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN
            data = Uri.parse("bloo://widget/$widgetId/${action.key}")
            putExtra(WidgetAuthActivity.EXTRA_WIDGET_ID, widgetId)
            putExtra(WidgetAuthActivity.EXTRA_VIN, vin)
            putExtra(WidgetAuthActivity.EXTRA_ACTION, action.key)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }

    private fun configIntent(context: Context, widgetId: Int): Intent =
        Intent(context, WidgetConfigActivity::class.java).apply {
            data = Uri.parse("bloo://widget/config/$widgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Decode a file-backed bitmap, memoised by path + last-modified so the same
     * image isn't re-decoded on every resize/refresh render. [rgb565] halves the
     * memory for opaque images (map tiles) where alpha isn't needed.
     */
    private fun decodeCached(path: String, sample: Int, rgb565: Boolean = false): Bitmap? {
        val file = java.io.File(path)
        if (!file.exists()) return null
        val key = "$path:${file.lastModified()}:$sample:$rgb565"
        bitmapCache.get(key)?.let { return it }
        return runCatching {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                if (rgb565) inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(path, opts)
        }.getOrNull()?.also { bitmapCache.put(key, it) }
    }

    companion object {
        // Small LRU so the widget never holds more than a few decoded images at once.
        private val bitmapCache = object : android.util.LruCache<String, Bitmap>(6) {
            override fun sizeOf(key: String, value: Bitmap) = 1
        }

        // Hoisted membership sets — avoids allocating list literals per button per render.
        private val CLIMATE_KEYS = setOf("climate", "climate_on", "climate_off")
        private val LOCK_KEYS = setOf("doors", "lock", "unlock")
        private val CHARGE_KEYS = setOf("charge", "start_charge", "stop_charge")
    }
}
