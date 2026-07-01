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
import androidx.compose.runtime.remember
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
import com.bloo.bluelink.data.vehicleStateLabel
import com.bloo.bluelink.ui.resolveWidgetAccent
import kotlinx.coroutines.flow.first

/**
 * The Bloo home-screen widget (Jetpack Glance).
 *
 * Seven "Auto" layout tiers, chosen by aspect ratio and dimensions: Tiny, Compact,
 * NarrowTall, WideRow, Square, Portrait, Landscape — plus six fixed alternate
 * styles (Minimal/Stats/Photo/Dual/Ring/Map) the user can pin regardless of size.
 * Every dimension is derived from [LocalSize] via [SizeMode.Exact], so the layout
 * recomposes at the widget's true pixel size on every resize.
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
        val accentArgb: Int,              // raw accent, for bodies that draw to a Canvas (e.g. RingBody)
        val textScale: Float,             // user text-size multiplier, applied to every tiered font size
        val cornerOverride: Dp?,          // user corner-radius override; null = each body's own default
        // background is NOT stored here — callers use GlanceTheme.colors.widgetBackground
        // so the system handles dark/light adaptation automatically.
    )

    /** Apply the user's text-scale preference to a base size, clamped to a sane
     *  legible range so an extreme scale can't collapse text to nothing or blow
     *  past its container. */
    private fun scaledSp(base: Float, theme: WidgetTheme) =
        (base * theme.textScale).coerceIn(7f, base * 1.6f).sp

    /** Glance's Text has maxLines but no TextOverflow/widthIn to lean on for
     *  horizontal ellipsis, so any string that could realistically run long
     *  (car names, addresses) is pre-truncated here in code — guaranteed-safe
     *  regardless of a given launcher's RemoteViews clipping behavior. */
    private fun ellipsize(s: String, max: Int): String =
        if (s.length > max) s.take(max - 1) + "…" else s

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
        val widgetCorner = cfg?.let { settings.widgetCorner(widgetId) }?.dp
        val widgetTextScale = cfg?.let { settings.widgetTextScale(widgetId) } ?: 1f

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
                accentArgb = accentColor.toArgb(),
                textScale = widgetTextScale.coerceIn(0.75f, 1.5f),
                cornerOverride = widgetCorner,
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
                // True 1-cell placements (the widget's minResizeWidth/Height is 40dp) — too
                // small for even Compact's narrow branch to stay legible.
                val isTiny = w < 80.dp && h < 80.dp
                // Roughly-square, non-portrait boxes (2x2/3x3/4x4-ish grid slots) used to
                // fall through to Landscape or Portrait by default even though neither was
                // designed for a 1:1 box — a centered ring gauge reads far better here.
                val ratio = if (h.value > 0f) w.value / h.value else 1f
                val isSquare = !isPortrait && !isTiny && ratio in 0.72f..1.35f
                when {
                    // User-chosen alternate styles render the same at any size.
                    snap != null && widgetStyle == "minimal" ->
                        MinimalBody(widgetId, snap, w, h, showBackground, widgetShape, theme)
                    snap != null && widgetStyle == "stats" ->
                        StatsBody(widgetId, snap, w, h, widgetMetrics, showBackground, widgetShape, theme)
                    snap != null && widgetStyle == "photo" ->
                        PhotoBody(widgetId, snap, actions, w, h, photoBitmap, widgetShowName, widgetShowRange, widgetShowState, pendingAction, theme)
                    snap != null && widgetStyle == "dual" ->
                        DualBody(widgetId, snap, w, h, widgetMetrics, widgetShowName, theme)
                    snap != null && widgetStyle == "ring" ->
                        RingBody(widgetId, snap, actions, w, h, widgetShowName, pendingAction, theme)
                    snap != null && widgetStyle == "map" ->
                        MapBody(widgetId, snap, w, h, mapBitmap, locationAddress, widgetShowName, widgetShowState, theme)
                    isTiny -> when {
                        snap != null -> TinyBody(widgetId, snap, showBackground, widgetShape, theme)
                        cfg == null  -> UnconfiguredCompact(widgetId)
                        else         -> UnavailableCompact(showBackground, widgetShape, theme)
                    }
                    h < 65.dp -> when {
                        snap != null -> CompactBody(widgetId, snap, actions, w, h, showBackground, widgetShape, pendingAction, theme)
                        cfg == null  -> UnconfiguredCompact(widgetId)
                        else         -> UnavailableCompact(showBackground, widgetShape, theme)
                    }
                    isSkinny -> when {
                        snap != null -> NarrowTallBody(widgetId, snap, actions, w, h, showBackground, widgetShape, widgetShowRange, widgetShowState, pendingAction, theme)
                        cfg == null  -> UnconfiguredFull(widgetId)
                        else         -> UnavailableFull(showBackground, widgetShape, theme)
                    }
                    !isPortrait && w > h * 2.0f -> when {
                        snap != null -> WideRowBody(widgetId, snap, actions, w, h, showBackground, widgetShape, pendingAction, theme)
                        cfg == null  -> UnconfiguredCompact(widgetId)
                        else         -> UnavailableCompact(showBackground, widgetShape, theme)
                    }
                    isSquare -> when {
                        snap != null -> RingBody(widgetId, snap, actions, w, h, widgetShowName, pendingAction, theme)
                        cfg == null  -> UnconfiguredFull(widgetId)
                        else         -> UnavailableFull(showBackground, widgetShape, theme)
                    }
                    isPortrait -> when {
                        snap != null -> PortraitBody(widgetId, snap, actions, w, h, photoBitmap, mapBitmap, showBackground, widgetShape, widgetShowRange, widgetShowState, locationAddress, pendingAction, theme)
                        cfg == null  -> UnconfiguredFull(widgetId)
                        else         -> UnavailableFull(showBackground, widgetShape, theme)
                    }
                    else -> when {
                        snap != null -> LandscapeBody(widgetId, snap, actions, w, h, photoBitmap, mapBitmap, showBackground, widgetShape, widgetShowRange, widgetShowState, locationAddress, pendingAction, theme)
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
        val corner = theme.cornerOverride ?: if (widgetShape == "pill") 28.dp else 22.dp
        val mod = if (showBackground) GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
                  else GlanceModifier.fillMaxSize().cornerRadius(corner)
        Box(modifier = mod, contentAlignment = Alignment.Center) {
            Text("Sign in to Bloo", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium))
        }
    }

    @Composable
    private fun UnavailableCompact(showBackground: Boolean, widgetShape: String, theme: WidgetTheme) {
        val corner = theme.cornerOverride ?: if (widgetShape == "pill") 28.dp else 22.dp
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
        val corner = theme.cornerOverride ?: if (widgetShape == "pill") 28.dp else 24.dp
        val base = if (showBackground) {
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }
        val (stateLabel, stateColor) = stateOf(snap, theme)
        val big = snap.percent?.let { "$it%" } ?: snap.rangeMi?.let { "$it mi" } ?: "—"
        // Scale with the smaller of the two dimensions so a big square/landscape
        // widget doesn't just get a proportionally huge number floating in space.
        val minSide = minOf(w, h)
        val bigBase = when {
            minSide >= 260.dp -> 64f
            minSide >= 180.dp -> 52f
            minSide >= 120.dp -> 40f
            h < 95.dp         -> 28f
            else              -> 34f
        }
        Box(
            modifier = base.clickable(actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN)))
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    ellipsize(snap.name, 16),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = scaledSp(12f, theme), fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    big,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = scaledSp(bigBase, theme), fontWeight = FontWeight.Bold),
                )
                if (h >= 80.dp) {
                    Spacer(GlanceModifier.height(8.dp))
                    StateChip(stateLabel, stateColor)
                }
            }
        }
    }

    /** "Stats": car name + a grid of the user's chosen metrics (up to 4, from
     *  Battery/Range/Lock/Climate). Info-dense at a glance; tap opens the app. */
    @Composable
    private fun StatsBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        w: Dp,
        h: Dp,
        metrics: List<String>,
        showBackground: Boolean,
        widgetShape: String,
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val corner = theme.cornerOverride ?: if (widgetShape == "pill") 28.dp else 24.dp
        val base = if (showBackground) {
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }
        val pick = metrics.take(4).ifEmpty { listOf("battery", "range", "lock", "climate") }
        fun labelOf(m: String) = m.replaceFirstChar { it.uppercase() }
        fun valueOf(m: String) = when (m) {
            "battery" -> snap.percent?.let { "$it%" } ?: "—"
            "range" -> snap.rangeMi?.let { "$it mi" } ?: "—"
            "lock" -> when (snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" }
            "climate" -> if (snap.climateOn == true) "On" else "Off"
            else -> "—"
        }
        fun colorOf(m: String) = when (m) {
            "lock" -> if (snap.locked == false) theme.unlocked else theme.accentMuted
            "climate" -> if (snap.climateOn == true) theme.climate else theme.accentMuted
            else -> theme.accent
        }
        Column(
            modifier = base.clickable(actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN)))
                .padding(14.dp),
        ) {
            Text(
                ellipsize(snap.name, 20),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = scaledSp(14f, theme), fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                pick.take(2).forEachIndexed { i, m ->
                    if (i > 0) Spacer(GlanceModifier.width(8.dp))
                    StatCell(labelOf(m), valueOf(m), colorOf(m), theme, GlanceModifier.defaultWeight())
                }
            }
            if (pick.size > 2 && h >= 110.dp) {
                Spacer(GlanceModifier.height(8.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    pick.drop(2).take(2).forEachIndexed { i, m ->
                        if (i > 0) Spacer(GlanceModifier.width(8.dp))
                        StatCell(labelOf(m), valueOf(m), colorOf(m), theme, GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }

    /** One labelled metric cell for [StatsBody]. */
    @Composable
    private fun StatCell(label: String, value: String, accent: ColorProvider, theme: WidgetTheme, modifier: GlanceModifier = GlanceModifier) {
        Column(
            modifier = modifier
                .background(ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.16f)))
                .cornerRadius(14.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                label.uppercase(),
                maxLines = 1,
                style = TextStyle(color = accent, fontSize = scaledSp(9f, theme), fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                value,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = scaledSp(16f, theme), fontWeight = FontWeight.Bold),
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
        val corner = theme.cornerOverride ?: if (w < 180.dp) 22.dp else 28.dp
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
                        ellipsize(snap.name, 20),
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        snap.percent?.let { "$it%" } ?: "—",
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = scaledSp(30f, theme), fontWeight = FontWeight.Bold),
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
        val corner = theme.cornerOverride ?: if (w < 180.dp) 22.dp else 24.dp
        val base = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        val pick = metrics.take(2).ifEmpty { listOf("battery", "range") }
        fun valueOf(m: String) = when (m) {
            "battery" -> snap.percent?.let { "$it%" } ?: "—"
            "range" -> snap.rangeMi?.let { "$it mi" } ?: "—"
            "lock" -> when (snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" }
            "climate" -> if (snap.climateOn == true) "On" else "Off"
            else -> "—"
        }
        // Tier by the actual per-cell space (not a flat 32sp) and shrink further for
        // longer strings ("Unlocked" vs "82%") so a value never has to fight its cell.
        val cellDim = (if (w > h) w else h) / pick.size.coerceAtLeast(1)
        fun valueSizeFor(text: String): Float {
            val base = when {
                cellDim >= 140.dp -> 36f
                cellDim >= 100.dp -> 28f
                cellDim >= 70.dp  -> 22f
                else              -> 16f
            }
            return if (text.length > 5) base * 0.72f else base
        }
        Box(base.clickable(open).padding(14.dp)) {
            Column(GlanceModifier.fillMaxSize()) {
                if (showName) {
                    Text(ellipsize(snap.name, 16), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = scaledSp(12f, theme), fontWeight = FontWeight.Medium))
                    Spacer(GlanceModifier.height(6.dp))
                }
                if (w > h) {
                    Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        pick.forEach { m ->
                            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(m.uppercase(), maxLines = 1, style = TextStyle(color = theme.accent, fontSize = scaledSp(10f, theme), fontWeight = FontWeight.Bold))
                                Spacer(GlanceModifier.height(2.dp))
                                val v = valueOf(m)
                                Text(v, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = scaledSp(valueSizeFor(v), theme), fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                } else {
                    Column(GlanceModifier.fillMaxWidth().defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        pick.forEach { m ->
                            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(m.uppercase(), maxLines = 1, style = TextStyle(color = theme.accent, fontSize = scaledSp(10f, theme), fontWeight = FontWeight.Bold))
                                Spacer(GlanceModifier.height(2.dp))
                                val v = valueOf(m)
                                Text(v, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = scaledSp(valueSizeFor(v), theme), fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }

    /** "Ring": a circular charge/fuel ring with the percent centered, name above,
     *  and up to 2 action pills below when there's room. Glance has no native arc
     *  drawing, so the ring itself is rasterized once per render onto a Bitmap. */
    @Composable
    private fun RingBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        actions: List<WidgetAction>,
        w: Dp,
        h: Dp,
        showName: Boolean,
        pendingAction: String?,
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val open = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))
        val corner = theme.cornerOverride ?: if (w < 180.dp) 22.dp else 24.dp
        val base = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        val showActions = actions.isNotEmpty() && h >= 190.dp
        val reserve = if (showActions) 46.dp else 0.dp
        val ringDp = minOf(w - 24.dp, h - 24.dp - reserve).coerceIn(56.dp, 168.dp)
        val pct = (snap.percent ?: 0).coerceIn(0, 100)
        val ring = remember(pct, theme.accentArgb) { ringBitmap(pct, theme.accentArgb) }

        Box(base.clickable(open).padding(12.dp)) {
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (showName) {
                    Text(ellipsize(snap.name, 14), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    Spacer(GlanceModifier.height(4.dp))
                }
                Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                    Box(GlanceModifier.size(ringDp), contentAlignment = Alignment.Center) {
                        Image(provider = ImageProvider(ring), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                snap.percent?.let { "$it%" } ?: "—",
                                maxLines = 1,
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (ringDp > 100.dp) 26.sp else 18.sp, fontWeight = FontWeight.Bold),
                            )
                            Text(
                                if (snap.isEv) "Battery" else "Fuel",
                                maxLines = 1,
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                            )
                        }
                    }
                }
                if (showActions) {
                    Spacer(GlanceModifier.height(8.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        actions.take(2).forEachIndexed { i, a ->
                            if (i > 0) Spacer(GlanceModifier.width(8.dp))
                            ActionPill(widgetId, snap.vin, a, 34.dp, GlanceModifier.defaultWeight(), pendingAction, snap, theme, allowLabel = false)
                        }
                    }
                }
            }
        }
    }

    /** Rasterize a ring gauge: a dim full-circle track plus an accent-colored arc
     *  swept to [pct]. Rendered once at a fixed pixel size so it stays crisp when
     *  Glance scales it into whatever [Dp] box the layout gives it. */
    private fun ringBitmap(pct: Int, accentArgb: Int): Bitmap {
        val size = 240
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val stroke = size * 0.11f
        val inset = stroke / 2f + 4f
        val rect = android.graphics.RectF(inset, inset, size - inset, size - inset)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        paint.color = android.graphics.Color.argb(60, 128, 128, 128)
        canvas.drawArc(rect, 0f, 360f, false, paint)
        paint.color = accentArgb
        canvas.drawArc(rect, -90f, 360f * (pct / 100f), false, paint)
        return bmp
    }

    /** "Map": the car's last known location as the dominant visual, name/percent
     *  overlaid at the bottom. Falls back to a "run Location" placeholder until a
     *  Location action has ever run for this widget (mirrors PortraitBody/
     *  LandscapeBody's own map handling, so the two never disagree on state). */
    @Composable
    private fun MapBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        w: Dp,
        h: Dp,
        mapBitmap: Bitmap?,
        locationAddress: String?,
        showName: Boolean,
        showState: Boolean,
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val open = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))
        val corner = theme.cornerOverride ?: if (w < 180.dp) 22.dp else 26.dp
        Box(GlanceModifier.fillMaxSize().cornerRadius(corner).clickable(open)) {
            if (mapBitmap != null) {
                Image(
                    provider = ImageProvider(mapBitmap),
                    contentDescription = "Car location",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(corner),
                )
                Box(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0f, 0f, 0f, 0.34f)))) {}
            } else {
                Box(
                    modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_location),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                            modifier = GlanceModifier.size(28.dp),
                        )
                        if (h >= 90.dp) {
                            Spacer(GlanceModifier.height(6.dp))
                            Text(
                                "Run Location to see the map",
                                maxLines = 2,
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = scaledSp(11f, theme), fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                }
            }
            if (h >= 130.dp) {
                Box(GlanceModifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.TopEnd) {
                    val (stateLabel, stateColor) = stateOf(snap, theme, hasPhoto = mapBitmap != null)
                    StateChip(if (showState) stateLabel else (snap.percent?.let { "$it%" } ?: "—"), stateColor)
                }
            }
            if (showName || (mapBitmap != null && !locationAddress.isNullOrBlank())) {
                Box(GlanceModifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.BottomStart) {
                    Box(
                        modifier = GlanceModifier.background(ColorProvider(Color(0f, 0f, 0f, 0.55f)))
                            .cornerRadius(10.dp).padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Column {
                            if (showName) {
                                Text(
                                    ellipsize(snap.name, 20),
                                    maxLines = 1,
                                    style = TextStyle(color = ColorProvider(Color.White), fontSize = scaledSp(13f, theme), fontWeight = FontWeight.Bold),
                                )
                            }
                            if (mapBitmap != null && !locationAddress.isNullOrBlank() && h >= 110.dp) {
                                Text(
                                    ellipsize(locationAddress, 32),
                                    maxLines = 1,
                                    style = TextStyle(color = ColorProvider(Color(1f, 1f, 1f, 0.85f)), fontSize = scaledSp(11f, theme)),
                                )
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
        val corner = theme.cornerOverride ?: if (isPill) 28.dp else 22.dp
        val hPad = if (isPill) 14.dp else 10.dp
        // On a narrow compact strip show numbers vertically with no buttons.
        val isNarrow = w < 120.dp
        val maxButtons = if (isNarrow) 0 else ((w - 110.dp) / 39.dp).toInt().coerceIn(0, 4)
        // Range and state used to both check "w >= 150dp"-ish windows that overlapped
        // (190-249dp had both on at once), crowding the percent/name column and
        // squeezing the fixed-size action buttons off the row. Mutually exclusive now:
        // range is the cheaper fallback below the point state has room to breathe.
        val showState = w >= 250.dp
        val showRange = w in 150.dp..249.dp && snap.rangeMi != null

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
                        ellipsize(snap.name, 10),
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
                            ellipsize(snap.name, 18),
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

    // ── Tiny (icon-scale) layout ──────────────────────────────────────────────

    /** Smaller than [CompactBody] can stay legible at — true 1-cell placements
     *  (the widget's minResizeWidth/Height is 40dp). Just the percent and a
     *  state-colored dot; a name label wouldn't read at this scale. */
    @Composable
    private fun TinyBody(
        widgetId: Int,
        snap: VehicleSnapshot,
        showBackground: Boolean,
        widgetShape: String,
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val corner = theme.cornerOverride ?: if (widgetShape == "pill") 999.dp else 18.dp
        val base = if (showBackground) {
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
        } else {
            GlanceModifier.fillMaxSize().cornerRadius(corner)
        }
        val open = actionStartActivity(authIntent(context, widgetId, snap.vin, WidgetAction.OPEN))
        val (_, stateColor) = stateOf(snap, theme)
        Box(base.clickable(open), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    snap.percent?.let { "$it%" } ?: "—",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = scaledSp(17f, theme)),
                )
                Spacer(GlanceModifier.height(3.dp))
                Box(GlanceModifier.size(6.dp).background(stateColor).cornerRadius(3.dp)) {}
            }
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
        widgetShowRange: Boolean,
        widgetShowState: Boolean,
        pendingAction: String?,
        theme: WidgetTheme,
    ) {
        val context = LocalContext.current
        val isPill = widgetShape == "pill"
        val corner = theme.cornerOverride ?: if (isPill) w / 2 else (w / 4).coerceIn(16.dp, 28.dp)
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
        val showRange = statusH >= 98.dp && widgetShowRange
        val showBar   = statusH >= 82.dp && !ultraNarrow
        val showState = statusH >= 66.dp && widgetShowState
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
                        ellipsize(snap.name, if (ultraNarrow) 10 else 14),
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
        val corner = theme.cornerOverride ?: if (isPill) h / 2 else 22.dp
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
                        ellipsize(snap.name, 16),
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
        widgetShowRange: Boolean,
        widgetShowState: Boolean,
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
        val corner = theme.cornerOverride ?: if (isPill) w / 2 else (w / 4).coerceIn(20.dp, 32.dp)
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
                        showRange = widgetShowRange,
                        showState = widgetShowState,
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
                                // Glance's wrap-content Box has no max-width modifier to
                                // lean on (no TextOverflow, no widthIn), so a very long
                                // reverse-geocoded address is truncated here in code —
                                // guaranteed-safe regardless of a given launcher's exact
                                // RemoteViews clipping behavior, rather than relying on it.
                                ellipsize(address, 32),
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
        showRange: Boolean,
        showState: Boolean,
        theme: WidgetTheme,
        modifier: GlanceModifier,
    ) {
        val onSurface = if (hasPhoto) ColorProvider(Color.White) else GlanceTheme.colors.onSurface
        val onVariant = if (hasPhoto) ColorProvider(Color(1f, 1f, 1f, 0.70f)) else GlanceTheme.colors.onSurfaceVariant
        val (stateLabel, stateColor) = stateOf(snap, theme, hasPhoto)

        // Stricter thresholds so a clipped fixed-height column never half-shows a line.
        // "show*" params are the user's Show toggles; the height checks are the
        // existing space heuristic — both must allow it.
        val showKind = availH >= 96.dp
        val showRangeFit = availH >= 70.dp && showRange
        val showBar = availH >= 50.dp
        val showStateFit = availH >= 32.dp && showState
        val showRangeInline = availH in 50.dp..69.dp && snap.rangeMi != null && showRange
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
                ellipsize(snap.name, if (availH >= 120.dp) 18 else 14),
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
            if (showStateFit) {
                Spacer(GlanceModifier.height(5.dp))
                StateChip(stateLabel, stateColor)
            }
            if (showRangeFit) {
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
        widgetShowRange: Boolean,
        widgetShowState: Boolean,
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
        val corner = theme.cornerOverride ?: if (isPill) h / 2 else (h / 4).coerceIn(20.dp, 32.dp)
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
                    // The column gets roughly half the remaining width (shared
                    // defaultWeight() with ButtonGrid) — derive the bar's width from
                    // that instead of a fixed guess, which could be wider than the
                    // column actually receives on a narrow landscape widget.
                    val statusW = ((w - hPad * 2 - 10.dp) / 2f).coerceIn(60.dp, 140.dp)
                    LandscapeStatusColumn(
                        snap = snap,
                        h = h,
                        statusW = statusW,
                        hasPhoto = showPhoto,
                        locationAddress = locationAddress,
                        showRange = widgetShowRange,
                        showState = widgetShowState,
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
        statusW: Dp,
        hasPhoto: Boolean,
        locationAddress: String?,
        showRange: Boolean,
        showState: Boolean,
        theme: WidgetTheme,
        modifier: GlanceModifier,
    ) {
        val onSurface = if (hasPhoto) ColorProvider(Color.White) else GlanceTheme.colors.onSurface
        val onVariant = if (hasPhoto) ColorProvider(Color(1f, 1f, 1f, 0.70f)) else GlanceTheme.colors.onSurfaceVariant
        val (stateLabel, stateColor) = stateOf(snap, theme, hasPhoto)

        val showKind = h >= 96.dp
        val showRangeFit = h >= 76.dp && showRange
        val showBar = h >= 64.dp
        val showStateFit = h >= 70.dp && showState
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
                ellipsize(snap.name, 16),
                maxLines = 1,
                style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = scaledSp(14f, theme)),
            )
            if (showStateFit) {
                Spacer(GlanceModifier.height(2.dp))
                StateChip(stateLabel, stateColor)
            }
            Spacer(GlanceModifier.height(2.dp))
            Text(
                snap.percent?.let { "$it%" } ?: "—",
                maxLines = 1,
                style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = scaledSp(percentSize.value, theme)),
            )
            if (showBar) {
                Spacer(GlanceModifier.height(4.dp))
                BatteryBar(snap.percent, statusW, theme, onPhoto = hasPhoto)
            }
            if (showRangeFit) {
                snap.rangeMi?.let {
                    Spacer(GlanceModifier.height(3.dp))
                    Text("$it mi", maxLines = 1, style = TextStyle(color = onVariant, fontSize = 12.sp))
                }
            }
            if (locationAddress != null && h >= 100.dp) {
                Text(ellipsize(locationAddress, 28), maxLines = 1, style = TextStyle(color = onVariant, fontSize = 10.sp))
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
    private fun stateOf(snap: VehicleSnapshot, theme: WidgetTheme, hasPhoto: Boolean = false): Pair<String, ColorProvider> {
        val color = when {
            snap.engineOn == true  -> theme.accent
            snap.charging == true  -> theme.charge
            snap.climateOn == true -> theme.climate
            snap.locked == true    -> theme.accentMuted
            snap.locked == false   -> theme.unlocked
            else                   -> ColorProvider(Color(0.42f, 0.42f, 0.46f, 1f))
        }
        return vehicleStateLabel(snap.engineOn, snap.charging, snap.climateOn, snap.locked) to color
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
