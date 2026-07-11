package com.bloo.bluelink.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import androidx.glance.appwidget.CircularProgressIndicator
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
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.vehicleStateLabel
import com.bloo.bluelink.ui.resolveWidgetAccent
import kotlinx.coroutines.flow.first

/**
 * Bloo home-screen widget.
 *
 * Every home-screen size — from a 1×1 tile up to a 5×5 panel — gets a layout
 * tailored to its shape that FILLS the whole cell (no dead space), scaling the
 * battery slider and the chunky action buttons up with the available room. The
 * buttons recolor to reflect live toggle state (climate on = teal, charging =
 * green, unlocked = red) so a tap gives immediate visual feedback.
 */
class BlooWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    /** Palette + semantic state colors, resolved once per render off the app theme. */
    private class Theme(
        val accent: ColorProvider,
        val onAccent: ColorProvider,
        val charge: ColorProvider,
        val unlocked: ColorProvider,
        val climate: ColorProvider,
        val pending: ColorProvider,
        val track: ColorProvider,
        val tile: ColorProvider,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsStore(context)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val cfg = settings.widgetConfig(widgetId)
        val snap = cfg?.let { c ->
            SnapshotStore(context).current().vehicles.firstOrNull { it.vin == c.first }
        }
        val actions = cfg?.second.orEmpty().mapNotNull { WidgetAction.fromKey(it) }
        val appearance = settings.appearance.first()
        val accentColor = resolveWidgetAccent(context, appearance, snap?.vin)
        val onAccent = if (accentColor.luminance() > 0.5f)
            Color(0xFF20232A) else Color.White
        val theme = Theme(
            accent = ColorProvider(accentColor),
            onAccent = ColorProvider(onAccent),
            charge = ColorProvider(Color(0xFF2EBD59)),
            unlocked = ColorProvider(Color(0xFFE0574B)),
            climate = ColorProvider(Color(0xFF16B8C6)),
            pending = ColorProvider(Color(0.55f, 0.55f, 0.60f, 0.55f)),
            track = ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.28f)),
            tile = ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.13f)),
        )

        val photoPath = snap?.let { settings.imageUrl(it.vin) }
        val photoBitmap = photoPath?.takeIf { it.startsWith("/") }?.let { decodeCached(it, sample = 2) }
        // Set by WidgetAuthActivity when a command is queued, cleared by the worker
        // — drives both the per-button "working" tint and the corner spinner.
        val pending = settings.widgetPendingAction(widgetId)

        provideContent {
            GlanceTheme {
                val w = LocalSize.current.width
                val h = LocalSize.current.height
                val corner = when {
                    w < 90.dp || h < 90.dp -> 16.dp
                    w < 180.dp || h < 130.dp -> 22.dp
                    else -> 28.dp
                }
                val base = GlanceModifier.fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)

                Box(GlanceModifier.fillMaxSize()) {
                    when {
                        snap == null ->
                            SetupTile(base, configIntent(context, widgetId))
                        // 1×1 — just the essentials, centered.
                        w < 110.dp && h < 110.dp ->
                            TinyTile(widgetId, snap, base, theme)
                        // Wide & short (2×1, 3×1, 4×1): info left, chunky buttons right.
                        h < 110.dp ->
                            ShortWideTile(widgetId, snap, actions, base, theme, pending)
                        // Narrow & tall (1×2, 1×3): stacked, buttons fill the column.
                        w < 110.dp ->
                            TallNarrowTile(widgetId, snap, actions, base, theme, pending)
                        // Small square (2×2).
                        w < 220.dp && h < 220.dp ->
                            SquareTile(widgetId, snap, actions, w, base, theme, pending)
                        // Wide & medium-height (3×2, 4×2, 5×2): info column + button cluster.
                        h < 190.dp ->
                            WideTile(widgetId, snap, actions, w, h, base, theme, pending)
                        // Everything larger (3×3, 4×3, 4×4, 5×5): the full panel.
                        else ->
                            LargeTile(widgetId, snap, actions, w, h, base, theme, pending, photoBitmap)
                    }
                    if (pending != null) {
                        Box(GlanceModifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.TopEnd) {
                            CircularProgressIndicator(GlanceModifier.size(16.dp), color = theme.accent)
                        }
                    }
                }
            }
        }
    }

    // ── Tiers ──────────────────────────────────────────────────────────────

    @Composable
    private fun SetupTile(base: GlanceModifier, intent: Intent) {
        Box(base.clickable(actionStartActivity(intent)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    provider = ImageProvider(R.drawable.ic_shortcut_car),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.size(28.dp),
                )
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    "Tap to set up",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp),
                )
            }
        }
    }

    @Composable
    private fun TinyTile(widgetId: Int, snap: VehicleSnapshot, base: GlanceModifier, theme: Theme) {
        val ctx = LocalContext.current
        Box(
            base.clickable(actionStartActivity(authIntent(ctx, widgetId, snap.vin, WidgetAction.OPEN))).padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    snap.percent?.let { "$it%" } ?: "—",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                )
                Spacer(GlanceModifier.height(4.dp))
                Box(GlanceModifier.size(7.dp).background(stateColor(snap, theme)).cornerRadius(4.dp)) {}
            }
        }
    }

    @Composable
    private fun ShortWideTile(
        widgetId: Int, snap: VehicleSnapshot, actions: List<WidgetAction>,
        base: GlanceModifier, theme: Theme, pending: String?,
    ) {
        val ctx = LocalContext.current
        Row(
            base.clickable(actionStartActivity(authIntent(ctx, widgetId, snap.vin, WidgetAction.OPEN)))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    snap.percent?.let { "$it%" } ?: "—",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                )
                snap.rangeMi?.let {
                    Text("$it mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
                }
            }
            if (actions.isNotEmpty()) {
                Spacer(GlanceModifier.width(8.dp))
                ButtonGrid(
                    widgetId, snap, actions.take(4), theme, pending, cols = actions.take(4).size,
                    showLabel = false, iconSize = 20.dp,
                    modifier = GlanceModifier.fillMaxHeight().width((actions.take(4).size * 46).dp),
                )
            }
        }
    }

    @Composable
    private fun TallNarrowTile(
        widgetId: Int, snap: VehicleSnapshot, actions: List<WidgetAction>,
        base: GlanceModifier, theme: Theme, pending: String?,
    ) {
        val ctx = LocalContext.current
        Column(
            base.clickable(actionStartActivity(authIntent(ctx, widgetId, snap.vin, WidgetAction.OPEN)))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                snap.percent?.let { "$it%" } ?: "—",
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 26.sp),
            )
            snap.rangeMi?.let {
                Text("$it mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            if (actions.isNotEmpty()) {
                ButtonGrid(
                    widgetId, snap, actions.take(3), theme, pending, cols = 1,
                    showLabel = false, iconSize = 22.dp,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                )
            }
        }
    }

    @Composable
    private fun SquareTile(
        widgetId: Int, snap: VehicleSnapshot, actions: List<WidgetAction>,
        w: Dp, base: GlanceModifier, theme: Theme, pending: String?,
    ) {
        val ctx = LocalContext.current
        val trackW = w - 24.dp
        Column(
            base.clickable(actionStartActivity(authIntent(ctx, widgetId, snap.vin, WidgetAction.OPEN)))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snap.name.take(14), maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                StateChip(snap, theme)
            }
            Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 30.sp),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    snap.rangeMi?.let {
                        Text("$it mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    }
                }
                Spacer(GlanceModifier.height(7.dp))
                BatteryMeter(snap.percent, trackW, theme, thickness = 8.dp, withThumb = true)
            }
            if (actions.isNotEmpty()) {
                Spacer(GlanceModifier.height(10.dp))
                // One chunky row (up to 3) so the hero above always keeps its height.
                val take = actions.take(3)
                ButtonGrid(
                    widgetId, snap, take, theme, pending, cols = take.size,
                    showLabel = false, iconSize = 22.dp,
                    modifier = GlanceModifier.fillMaxWidth().height(48.dp),
                )
            }
        }
    }

    @Composable
    private fun WideTile(
        widgetId: Int, snap: VehicleSnapshot, actions: List<WidgetAction>,
        w: Dp, h: Dp, base: GlanceModifier, theme: Theme, pending: String?,
    ) {
        val ctx = LocalContext.current
        val infoW = w * 0.52f
        val trackW = (infoW - 20.dp).coerceAtLeast(60.dp)
        Row(
            base.clickable(actionStartActivity(authIntent(ctx, widgetId, snap.vin, WidgetAction.OPEN)))
                .padding(14.dp),
        ) {
            Column(modifier = GlanceModifier.fillMaxHeight().width(infoW), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        snap.name.take(14), maxLines = 1, modifier = GlanceModifier.padding(end = 6.dp),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                    StateChip(snap, theme)
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 34.sp),
                )
                snap.rangeMi?.let {
                    Text(
                        "$it mi ${if (snap.isEv) "range" else "left"}", maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    )
                }
                Spacer(GlanceModifier.height(8.dp))
                BatteryMeter(snap.percent, trackW, theme, thickness = 9.dp, withThumb = true)
            }
            if (actions.isNotEmpty()) {
                Spacer(GlanceModifier.width(12.dp))
                val take = actions.take(4)
                val cols = if (take.size >= 3 && h >= 150.dp) 2 else 1
                ButtonGrid(
                    widgetId, snap, take, theme, pending, cols = cols,
                    showLabel = cols == 1 && w >= 300.dp, iconSize = 22.dp,
                    modifier = GlanceModifier.fillMaxHeight().defaultWeight(),
                )
            }
        }
    }

    @Composable
    private fun LargeTile(
        widgetId: Int, snap: VehicleSnapshot, actions: List<WidgetAction>,
        w: Dp, h: Dp, base: GlanceModifier, theme: Theme, pending: String?, photo: Bitmap?,
    ) {
        val ctx = LocalContext.current
        val take = actions.take(4)
        val hasPhoto = photo != null && w >= 240.dp
        val trackW = (w * (if (hasPhoto) 0.5f else 0.62f)).coerceAtLeast(80.dp)
        // Content scales with height so a short 4x3 doesn't clip and a tall 5x5
        // doesn't look sparse.
        val tall = h >= 250.dp
        val pctSize = if (h >= 280.dp) 46.sp else if (h >= 220.dp) 40.sp else 34.sp
        val showTiles = h >= 290.dp
        // Footer: a tall 2-col grid when there's height for it, else a single
        // chunky row. Both hero and footer take a weight share so the cell is
        // always full and neither can be squeezed to nothing.
        val footerCols = if (tall && take.size >= 3) 2 else take.size.coerceAtLeast(1)
        Column(
            base.clickable(actionStartActivity(authIntent(ctx, widgetId, snap.vin, WidgetAction.OPEN)))
                .padding(16.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snap.name.take(20), maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
                StateChip(snap, theme)
            }
            // Hero — a weight share, vertically centered so it fills evenly.
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = pctSize),
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        Column(modifier = GlanceModifier.padding(bottom = 6.dp)) {
                            snap.rangeMi?.let {
                                Text("$it mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                            }
                            Text(if (snap.isEv) "Battery" else "Fuel", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
                        }
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    BatteryMeter(snap.percent, trackW, theme, thickness = if (tall) 12.dp else 10.dp, withThumb = true)
                    if (showTiles) {
                        Spacer(GlanceModifier.height(12.dp))
                        Row {
                            DetailTile("Lock", when (snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" }, theme)
                            Spacer(GlanceModifier.width(8.dp))
                            DetailTile("Climate", if (snap.climateOn == true) "On" else "Off", theme)
                        }
                    }
                }
                if (hasPhoto) {
                    Spacer(GlanceModifier.width(12.dp))
                    Box(
                        GlanceModifier.fillMaxHeight().width(w * 0.34f).cornerRadius(18.dp).background(theme.tile),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(photo!!),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.fillMaxSize().cornerRadius(18.dp),
                        )
                    }
                }
            }
            if (take.isNotEmpty()) {
                Spacer(GlanceModifier.height(14.dp))
                ButtonGrid(
                    widgetId, snap, take, theme, pending, cols = footerCols,
                    showLabel = h >= 290.dp, iconSize = 24.dp,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                )
            }
        }
    }

    // ── Shared pieces ────────────────────────────────────────────────────────

    /**
     * A grid of chunky action buttons that FILLS [modifier]'s box: [cols] columns,
     * as many rows as needed, every cell an equal weight share so the buttons grow
     * with the widget. Empty trailing cells become invisible spacers to keep the
     * grid aligned.
     */
    @Composable
    private fun ButtonGrid(
        widgetId: Int, snap: VehicleSnapshot, actions: List<WidgetAction>, theme: Theme, pending: String?,
        cols: Int, showLabel: Boolean, iconSize: Dp, modifier: GlanceModifier,
    ) {
        if (actions.isEmpty()) return
        val columns = cols.coerceAtLeast(1)
        val rows = (actions.size + columns - 1) / columns
        Column(modifier) {
            for (r in 0 until rows) {
                if (r > 0) Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    for (c in 0 until columns) {
                        if (c > 0) Spacer(GlanceModifier.width(8.dp))
                        val idx = r * columns + c
                        val cell = GlanceModifier.fillMaxHeight().defaultWeight()
                        val action = actions.getOrNull(idx)
                        if (action != null) {
                            ChunkyButton(widgetId, snap, action, theme, pending, showLabel, iconSize, cell)
                        } else {
                            Box(cell) {}
                        }
                    }
                }
            }
        }
    }

    /** One chunky, state-colored action button that fills [modifier]'s cell. */
    @Composable
    private fun ChunkyButton(
        widgetId: Int, snap: VehicleSnapshot, action: WidgetAction, theme: Theme, pending: String?,
        showLabel: Boolean, iconSize: Dp, modifier: GlanceModifier,
    ) {
        val ctx = LocalContext.current
        val vis = actionVisual(action, snap, pending, theme)
        Box(
            modifier
                .background(vis.bg)
                .cornerRadius(18.dp)
                .clickable(actionStartActivity(authIntent(ctx, widgetId, snap.vin, action))),
            contentAlignment = Alignment.Center,
        ) {
            if (showLabel) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(vis.iconRes), contentDescription = action.label,
                        colorFilter = ColorFilter.tint(vis.fg), modifier = GlanceModifier.size(iconSize),
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(vis.label, maxLines = 1, style = TextStyle(color = vis.fg, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            } else {
                Image(
                    provider = ImageProvider(vis.iconRes), contentDescription = action.label,
                    colorFilter = ColorFilter.tint(vis.fg), modifier = GlanceModifier.size(iconSize),
                )
            }
        }
    }

    /** A slider-style battery/fuel meter with a rounded fill and a thumb knob. */
    @Composable
    private fun BatteryMeter(percent: Int?, trackW: Dp, theme: Theme, thickness: Dp, withThumb: Boolean) {
        val pct = (percent ?: 0).coerceIn(0, 100)
        val fillW = trackW * (pct / 100f)
        val thumb = thickness + 6.dp
        Box(
            GlanceModifier.width(trackW).height(thumb),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Track
            Box(GlanceModifier.width(trackW).height(thickness).background(theme.track).cornerRadius(thickness / 2)) {
                if (fillW > 0.dp) {
                    Box(GlanceModifier.width(fillW).height(thickness).background(theme.accent).cornerRadius(thickness / 2)) {}
                }
            }
            // Thumb, offset to sit on the fill's leading edge.
            if (withThumb && pct > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(GlanceModifier.width((fillW - thumb).coerceAtLeast(0.dp)))
                    Box(GlanceModifier.size(thumb).background(ColorProvider(Color.White)).cornerRadius(thumb / 2)) {
                        Box(GlanceModifier.fillMaxSize().padding(3.dp)) {
                            Box(GlanceModifier.fillMaxSize().background(theme.accent).cornerRadius(thumb / 2)) {}
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StateChip(snap: VehicleSnapshot, theme: Theme) {
        val label = vehicleStateLabel(snap.engineOn, snap.charging, snap.climateOn, snap.locked)
        val bg = when {
            snap.charging == true -> theme.charge
            snap.locked == false -> theme.unlocked
            snap.climateOn == true -> theme.climate
            else -> theme.accent
        }
        val fg = if (bg == theme.accent) theme.onAccent else ColorProvider(Color.White)
        Box(
            GlanceModifier.background(bg).cornerRadius(9.dp).padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, maxLines = 1, style = TextStyle(color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun DetailTile(label: String, value: String, theme: Theme) {
        Column(GlanceModifier.background(theme.tile).cornerRadius(12.dp).padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(label.uppercase(), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            Text(value, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }

    // ── State → visuals ──────────────────────────────────────────────────────

    private class ActionVisual(val iconRes: Int, val bg: ColorProvider, val fg: ColorProvider, val label: String)

    /**
     * Resolve a button's icon, fill, and foreground from live vehicle state so it
     * reads as a toggle: climate on = teal, charging = green, unlocked = red,
     * otherwise the app accent; the pending (in-flight) button is muted.
     */
    private fun actionVisual(action: WidgetAction, snap: VehicleSnapshot, pending: String?, theme: Theme): ActionVisual {
        val isPending = pending == action.key
        val isClimateActive = snap.climateOn == true && action.key in CLIMATE_KEYS
        val isChargeActive = snap.charging == true && action.key in CHARGE_KEYS
        val isUnlocked = action.key in LOCK_KEYS && snap.locked == false

        val iconRes = when {
            isPending -> R.drawable.ic_widget_refresh
            isClimateActive -> R.drawable.ic_widget_climate_active
            action.key in LOCK_KEYS -> if (snap.locked == true) R.drawable.ic_shortcut_lock else R.drawable.ic_shortcut_unlock
            else -> action.icon
        }
        val bg = when {
            isPending -> theme.pending
            isChargeActive -> theme.charge
            isUnlocked -> theme.unlocked
            isClimateActive -> theme.climate
            else -> theme.accent
        }
        val fg = if (bg == theme.accent) theme.onAccent else ColorProvider(Color.White)
        // Short, state-aware label so the combined lock/unlock button never lies.
        val label = when (action.key) {
            "doors" -> when (snap.locked) { true -> "Lock"; false -> "Unlock"; else -> "Doors" }
            else -> action.label.take(8)
        }
        return ActionVisual(iconRes, bg, fg, label)
    }

    private fun stateColor(snap: VehicleSnapshot, theme: Theme): ColorProvider = when {
        snap.charging == true -> theme.charge
        snap.locked == false -> theme.unlocked
        snap.climateOn == true -> theme.climate
        else -> theme.accent
    }

    // ── Intents & bitmap cache ───────────────────────────────────────────────

    private fun authIntent(ctx: Context, widgetId: Int, vin: String, action: WidgetAction): Intent =
        Intent(ctx, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN
            // Unique data URI per widget+action: PendingIntents compare with
            // filterEquals, which IGNORES extras — without this every button across
            // every widget collapses into one intent firing the last-cached action.
            data = Uri.parse("bloo://widget/$widgetId/${action.key}")
            putExtra(WidgetAuthActivity.EXTRA_WIDGET_ID, widgetId)
            putExtra(WidgetAuthActivity.EXTRA_VIN, vin)
            putExtra(WidgetAuthActivity.EXTRA_ACTION, action.key)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun configIntent(context: Context, widgetId: Int): Intent =
        Intent(context, WidgetConfigActivity::class.java).apply {
            data = Uri.parse("bloo://widget/config/$widgetId")
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Decode a file-backed bitmap, memoised by path + last-modified so a resize or
     *  refresh doesn't re-decode the same image every render. */
    private fun decodeCached(path: String, sample: Int): Bitmap? {
        val file = java.io.File(path)
        if (!file.exists()) return null
        val key = "$path:${file.lastModified()}:$sample"
        bitmapCache.get(key)?.let { return it }
        return runCatching {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()?.also { bitmapCache.put(key, it) }
    }

    companion object {
        private val bitmapCache = object : android.util.LruCache<String, Bitmap>(6) {
            override fun sizeOf(key: String, value: Bitmap) = 1
        }
        private val CLIMATE_KEYS = setOf("climate", "climate_on", "climate_off")
        private val LOCK_KEYS = setOf("doors", "lock", "unlock")
        private val CHARGE_KEYS = setOf("charge", "start_charge", "stop_charge")
    }
}
