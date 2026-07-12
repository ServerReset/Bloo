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
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.R
import com.bloo.bluelink.Shortcuts
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
 * chunky action buttons up with the available room. Buttons recolor to reflect
 * live toggle state (climate on = teal, charging = green, unlocked = red). Two
 * per-widget options: use the car's photo as a full-bleed background, and show a
 * live location/map box on large sizes.
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
        val tile: ColorProvider,
    )

    /** Everything one render needs, so tier composables stay short. */
    private class Ctx(
        val widgetId: Int,
        val snap: VehicleSnapshot,
        val actions: List<WidgetAction>,
        val theme: Theme,
        val pending: String?,
        val requireAuth: Boolean,
        val onPhoto: Boolean,
        val showLocation: Boolean,
        val map: Bitmap?,
        val photo: Bitmap?,
        val address: String?,
        val layoutMode: String,  // "info" shows data, "controls" shows buttons
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
        val onAccent = if (accentColor.luminance() > 0.5f) Color(0xFF20232A) else Color.White
        val theme = Theme(
            accent = ColorProvider(accentColor),
            onAccent = ColorProvider(onAccent),
            charge = ColorProvider(Color(0xFF2EBD59)),
            unlocked = ColorProvider(Color(0xFFE0574B)),
            climate = ColorProvider(Color(0xFF16B8C6)),
            pending = ColorProvider(Color(0.55f, 0.55f, 0.60f, 0.55f)),
            tile = ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.13f)),
        )

        val requireAuth = settings.widgetRequireAuth(widgetId)
        val photoBgOn = settings.widgetPhotoBackground(widgetId)
        val showLocation = settings.widgetShowLocation(widgetId)
        val photoPath = snap?.let { settings.imageUrl(it.vin) }
        val photo = photoPath?.takeIf { it.startsWith("/") }?.let { decodeCached(it) }
        val map = if (showLocation) decodeCached(java.io.File(context.cacheDir, "widget_map_$widgetId.png").path, maxPx = 512) else null
        val address = if (showLocation) settings.widgetLocationAddress(widgetId) else null
        val pending = settings.widgetPendingAction(widgetId)
        val photoBgActive = photoBgOn && photo != null
        val pillShape = settings.widgetPillShape(widgetId)
        val layoutMode = settings.widgetLayoutMode(widgetId) // "info" or "controls"

        provideContent {
            GlanceTheme {
                val w = LocalSize.current.width
                val h = LocalSize.current.height
                val corner = when {
                    pillShape -> 999.dp
                    w < 90.dp || h < 90.dp -> 16.dp
                    w < 180.dp || h < 130.dp -> 22.dp
                    else -> 28.dp
                }
                // Pill shape only for 1-2 unit widgets; push content in to avoid clipping
                val pillPad = if (pillShape && (w < 180.dp || h < 180.dp)) 8.dp else 0.dp
                Box(GlanceModifier.fillMaxSize().cornerRadius(corner)) {
                    // Photo background (optional): the car image full-bleed, with a
                    // scrim so the white text/buttons stay legible.
                    if (photoBgActive) {
                        Image(
                            provider = ImageProvider(photo!!), contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.fillMaxSize().cornerRadius(corner),
                        )
                        Box(GlanceModifier.fillMaxSize().cornerRadius(corner).background(ColorProvider(Color(0f, 0f, 0f, 0.42f)))) {}
                    }
                    val base = GlanceModifier.fillMaxSize()
                        .let { if (photoBgActive) it else it.background(GlanceTheme.colors.widgetBackground) }
                        .cornerRadius(corner)
                        .padding(pillPad)

                    if (snap == null) {
                        SetupTile(base, configIntent(context, widgetId))
                    } else {
                        val c = Ctx(widgetId, snap, actions, theme, pending, requireAuth, photoBgActive, showLocation, map, photo, address, layoutMode)
                        when {
                            w < 70.dp || (w < 90.dp && h < 90.dp) ->
                                if (layoutMode == "controls") ButtonStripTile(c, base) else TinyTile(c, base)
                            h < 70.dp -> ButtonStripTile(c, base)
                            h < 100.dp ->
                                if (layoutMode == "controls") ButtonStripTile(c, base) else ShortWideTile(c, base)
                            w < 110.dp ->
                                if (layoutMode == "controls") ButtonStripTile(c, base) else TallNarrowTile(c, base)
                            w < 220.dp && h < 130.dp ->
                                if (layoutMode == "controls") ButtonStripTile(c, base) else SquareTile(c, w, base)
                            w < 220.dp -> MediumTallTile(c, w, h, base)
                            h < 190.dp -> WideTile(c, w, h, base)
                            else -> LargeTile(c, w, h, base)
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
    }

    // ── Tiers ──────────────────────────────────────────────────────────────

    @Composable
    private fun SetupTile(base: GlanceModifier, intent: Intent) {
        Box(base.clickable(actionStartActivity(intent)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    provider = ImageProvider(R.drawable.ic_shortcut_car), contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant), modifier = GlanceModifier.size(28.dp),
                )
                Spacer(GlanceModifier.height(8.dp))
                Text("Tap to set up", style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp))
            }
        }
    }

    @Composable
    private fun TinyTile(c: Ctx, base: GlanceModifier) {
        val ctx = LocalContext.current
        Box(
            base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(c.snap.name.take(6), maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 8.sp))
                Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = 20.sp))
                Box(GlanceModifier.size(5.dp).background(stateColor(c.snap, c.theme)).cornerRadius(3.dp)) {}
            }
        }
    }

    @Composable
    private fun ShortWideTile(c: Ctx, base: GlanceModifier) {
        val ctx = LocalContext.current
        Row(
            base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(c.snap.name.take(12), maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 9.sp))
                Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = 20.sp))
                c.snap.rangeMi?.let { Text("$it mi", maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 11.sp)) }
            }
            if (c.actions.isNotEmpty()) {
                Spacer(GlanceModifier.width(8.dp))
                val take = c.actions.take(4)
                // Horizontal row on short widgets — one row only
                ButtonGrid(c, take, cols = take.size, showLabel = false, iconSize = 22.dp,
                    modifier = GlanceModifier.fillMaxHeight().width((take.size * 50).dp))
            }
        }
    }

    @Composable
    private fun TallNarrowTile(c: Ctx, base: GlanceModifier) {
        val ctx = LocalContext.current
        val narrow = LocalSize.current.width < 90.dp
        Column(
            base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(c.snap.name.take(10), maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = if (narrow) 10.sp else 12.sp))
            Spacer(GlanceModifier.height(2.dp))
            // Stack percent digits vertically when very narrow to avoid clipping
            if (narrow && c.snap.percent != null) {
                VerticalNumber(c.snap.percent.toString() + "%", onBg(c))
            } else {
                Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = 26.sp))
            }
            Spacer(GlanceModifier.height(2.dp))
            c.snap.rangeMi?.let { Text("$it mi", maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = if (narrow) 9.sp else 11.sp)) }
            Spacer(GlanceModifier.height(8.dp))
            if (c.actions.isNotEmpty()) {
                ButtonGrid(c, c.actions.take(4), cols = 1, showLabel = false, iconSize = 24.dp,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight())
            }
        }
    }

    /** Render a short string as a vertical stack of characters (one per line).
     *  Used on narrow widgets where horizontal text would clip. */
    @Composable
    private fun VerticalNumber(text: String, color: ColorProvider) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            text.forEach { ch ->
                Text(ch.toString(), maxLines = 1, style = TextStyle(color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp))
            }
        }
    }

    /** Split a name into two lines at a space or mid-way if no space. */
    private fun splitName(name: String): Pair<String, String> {
        val trimmed = name.trim()
        if (trimmed.length <= 8) return trimmed to ""
        val space = trimmed.indexOf(' ', 4).takeIf { it > 0 && it < trimmed.length - 2 }
        return if (space != null) trimmed.substring(0, space) to trimmed.substring(space + 1)
        else trimmed.substring(0, trimmed.length / 2) to trimmed.substring(trimmed.length / 2)
    }

    /** Strip of chunky buttons with no info (for the tiniest placements). */
    @Composable
    private fun ButtonStripTile(c: Ctx, base: GlanceModifier) {
        val ctx = LocalContext.current
        Row(base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (c.actions.isNotEmpty()) {
                ButtonGrid(c, c.actions.take(4), cols = c.actions.take(4).size, showLabel = false, iconSize = 20.dp,
                    modifier = GlanceModifier.fillMaxHeight().defaultWeight())
            }
        }
    }

    /** Tallish but moderately-wide widget: stacked name (2 lines), percent, 2×2 buttons. */
    @Composable
    private fun MediumTallTile(c: Ctx, w: Dp, h: Dp, base: GlanceModifier) {
        val ctx = LocalContext.current
        val (firstLine, secondLine) = splitName(c.snap.name)
        Column(base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(firstLine, maxLines = 1, style = TextStyle(color = onBg(c), fontSize = 13.sp, fontWeight = FontWeight.Bold))
            if (secondLine.isNotEmpty()) Text(secondLine, maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 11.sp))
            Spacer(GlanceModifier.height(6.dp))
            Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = 34.sp))
            c.snap.rangeMi?.let { Text("$it mi", maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 11.sp)) }
            Spacer(GlanceModifier.height(4.dp))
            StateChip(c)
            if (c.actions.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                val take = c.actions.take(4)
                val cols = if (take.size >= 3) 2 else take.size.coerceAtLeast(1)
                ButtonGrid(c, take, cols = cols, showLabel = false, iconSize = 22.dp,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight())
            }
        }
    }

    @Composable
    private fun SquareTile(c: Ctx, w: Dp, base: GlanceModifier) {
        val ctx = LocalContext.current
        Column(base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(12.dp)) {
            Text(c.snap.name.take(12), maxLines = 1, style = TextStyle(color = onBg(c), fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                        style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = 30.sp))
                    Spacer(GlanceModifier.width(6.dp))
                    Column(modifier = GlanceModifier.padding(bottom = 4.dp)) {
                        c.snap.rangeMi?.let { Text("$it mi", maxLines = 1, style = TextStyle(color = onBg(c), fontSize = 12.sp)) }
                    }
                }
            }
            Spacer(GlanceModifier.height(2.dp))
            StateChip(c)
            if (c.actions.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                val take = c.actions.take(4)
                val cols = if (take.size >= 3) 2 else take.size.coerceAtLeast(1)
                ButtonGrid(c, take, cols = cols, showLabel = false, iconSize = 22.dp,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight())
            }
        }
    }

    @Composable
    private fun WideTile(c: Ctx, w: Dp, h: Dp, base: GlanceModifier) {
        val ctx = LocalContext.current
        val infoW = w * 0.48f
        Row(base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(12.dp)) {
            Column(modifier = GlanceModifier.fillMaxHeight().width(infoW), verticalAlignment = Alignment.CenterVertically) {
                Text(c.snap.name.take(10), maxLines = 1, style = TextStyle(color = onBg(c), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(4.dp))
                Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = if (h >= 160.dp) 30.sp else 24.sp))
                c.snap.rangeMi?.let { Text("$it mi ${if (c.snap.isEv) "range" else "left"}", maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 11.sp)) }
                Spacer(GlanceModifier.height(4.dp))
                StateChip(c)
            }
            if (c.actions.isNotEmpty()) {
                Spacer(GlanceModifier.width(10.dp))
                val take = c.actions.take(4)
                val cols = if (take.size >= 3 && h >= 150.dp) 2 else 1
                ButtonGrid(c, take, cols = cols, showLabel = false, iconSize = 24.dp,
                    modifier = GlanceModifier.fillMaxHeight().defaultWeight())
            }
        }
    }

    @Composable
    private fun LargeTile(c: Ctx, w: Dp, h: Dp, base: GlanceModifier) {
        val ctx = LocalContext.current
        val take = c.actions.take(4)
        // A live map box takes priority on the hero's right when Location is on;
        // otherwise the car photo can sit there (unless it's the background).
        val wantMap = c.showLocation && (c.map != null || c.address != null)
        val wantPhoto = !wantMap && c.photo != null && !c.onPhoto && w >= 240.dp
        val sideW = if (w >= 340.dp) w * 0.44f else w * 0.38f
        val tall = h >= 250.dp
        val pctSize = if (h >= 280.dp) 46.sp else if (h >= 220.dp) 40.sp else 34.sp
        val showTiles = false
        val footerCols = if (tall && take.size >= 3) 2 else take.size.coerceAtLeast(1)
        Column(base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.snap.name.take(16), maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = onBg(c), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                StateChip(c)
            }
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = pctSize))
                        Spacer(GlanceModifier.width(8.dp))
                        Column(modifier = GlanceModifier.padding(bottom = 6.dp)) {
                            c.snap.rangeMi?.let { Text("$it mi", maxLines = 1, style = TextStyle(color = onBg(c), fontSize = 14.sp, fontWeight = FontWeight.Medium)) }
                            Text(if (c.snap.isEv) "Battery" else "Fuel", maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 11.sp))
                        }
                    }
                    val addr = c.address
                    if (wantMap && addr != null) {
                        Spacer(GlanceModifier.height(8.dp))
                        Text(addr.take(40), maxLines = 2, style = TextStyle(color = onBgV(c), fontSize = 11.sp))
                    }
                    if (showTiles) {
                        Spacer(GlanceModifier.height(12.dp))
                        Row {
                            DetailTile(c, "Lock", when (c.snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" })
                            Spacer(GlanceModifier.width(8.dp))
                            DetailTile(c, "Climate", if (c.snap.climateOn == true) "On" else "Off")
                        }
                    }
                }
                if (wantMap) {
                    Spacer(GlanceModifier.width(12.dp))
                    LocationBox(c, GlanceModifier.fillMaxHeight().width(sideW))
                } else if (wantPhoto) {
                    Spacer(GlanceModifier.width(12.dp))
                    Box(GlanceModifier.fillMaxHeight().width(w * 0.34f).cornerRadius(18.dp).background(c.theme.tile), contentAlignment = Alignment.Center) {
                        Image(provider = ImageProvider(c.photo!!), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.fillMaxSize().cornerRadius(18.dp))
                    }
                }
            }
            if (take.isNotEmpty()) {
                Spacer(GlanceModifier.height(14.dp))
                ButtonGrid(c, take, cols = footerCols, showLabel = h >= 290.dp, iconSize = 24.dp,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight())
            }
        }
    }

    // ── Shared pieces ────────────────────────────────────────────────────────

    /** The car's current location: a map thumbnail with the address overlaying the
     *  bottom. Before the first Location action a pin-and-label placeholder is shown. */
    @Composable
    private fun LocationBox(c: Ctx, modifier: GlanceModifier) {
        val map = c.map
        Box(modifier.cornerRadius(18.dp).background(c.theme.tile), contentAlignment = Alignment.Center) {
            if (map != null) {
                // Map bitmap with address overlay at the bottom
                Box(GlanceModifier.fillMaxSize()) {
                    Image(provider = ImageProvider(map), contentDescription = "Car location",
                        contentScale = ContentScale.Fit, modifier = GlanceModifier.fillMaxSize().cornerRadius(18.dp))
                    val addr = c.address
                    if (addr != null) {
                        Box(GlanceModifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.BottomCenter) {
                            Text(addr.take(35), maxLines = 1, style = TextStyle(color = ColorProvider(Color(0xE0000000)), fontSize = 9.sp))
                        }
                    }
                }
            } else if (c.address != null) {
                // Address text when map isn't available yet
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Image(provider = ImageProvider(R.drawable.ic_widget_location), contentDescription = null,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant), modifier = GlanceModifier.size(20.dp))
                    Spacer(GlanceModifier.height(4.dp))
                    Text(c.address.take(35), maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
                }
            } else {
                // No data yet — prompt to run the Location action
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(provider = ImageProvider(R.drawable.ic_widget_location), contentDescription = null,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant), modifier = GlanceModifier.size(26.dp))
                    Spacer(GlanceModifier.height(4.dp))
                    Text("Tap Location", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
                }
            }
        }
    }

    /** A grid of chunky action buttons that FILLS [modifier]'s box. */
    @Composable
    private fun ButtonGrid(c: Ctx, actions: List<WidgetAction>, cols: Int, showLabel: Boolean, iconSize: Dp, modifier: GlanceModifier) {
        if (actions.isEmpty()) return
        val columns = cols.coerceAtLeast(1)
        val rows = (actions.size + columns - 1) / columns
        Column(modifier) {
            for (r in 0 until rows) {
                if (r > 0) Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    for (col in 0 until columns) {
                        if (col > 0) Spacer(GlanceModifier.width(8.dp))
                        val idx = r * columns + col
                        val cell = GlanceModifier.fillMaxHeight().defaultWeight()
                        val action = actions.getOrNull(idx)
                        if (action != null) ChunkyButton(c, action, showLabel, iconSize, cell) else Box(cell) {}
                    }
                }
            }
        }
    }

    /** One chunky, state-colored action button that fills [modifier]'s cell. */
    @Composable
    private fun ChunkyButton(c: Ctx, action: WidgetAction, showLabel: Boolean, iconSize: Dp, modifier: GlanceModifier) {
        val ctx = LocalContext.current
        val vis = actionVisual(action, c.snap, c.pending, c.theme)
        Box(
            modifier.background(vis.bg).cornerRadius(18.dp).clickable(clickFor(ctx, c, action)),
            contentAlignment = Alignment.Center,
        ) {
            if (showLabel) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Image(provider = ImageProvider(vis.iconRes), contentDescription = action.label, colorFilter = ColorFilter.tint(vis.fg), modifier = GlanceModifier.size(iconSize))
                    Spacer(GlanceModifier.height(4.dp))
                    Text(vis.label, maxLines = 1, style = TextStyle(color = vis.fg, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            } else {
                Image(provider = ImageProvider(vis.iconRes), contentDescription = action.label, colorFilter = ColorFilter.tint(vis.fg), modifier = GlanceModifier.size(iconSize))
            }
        }
    }

    @Composable
    private fun StateChip(c: Ctx) {
        val label = vehicleStateLabel(c.snap.engineOn, c.snap.charging, c.snap.climateOn, c.snap.locked)
        val bg = when {
            c.snap.charging == true -> c.theme.charge
            c.snap.locked == false -> c.theme.unlocked
            c.snap.climateOn == true -> c.theme.climate
            else -> c.theme.accent
        }
        val fg = if (bg == c.theme.accent) c.theme.onAccent else ColorProvider(Color.White)
        Box(GlanceModifier.background(bg).cornerRadius(9.dp).padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(label, maxLines = 1, style = TextStyle(color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun DetailTile(c: Ctx, label: String, value: String) {
        Column(GlanceModifier.background(c.theme.tile).cornerRadius(12.dp).padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(label.uppercase(), maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 9.sp, fontWeight = FontWeight.Bold))
            Text(value, maxLines = 1, style = TextStyle(color = onBg(c), fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }

    // Text colors: white over a photo background, else the theme's onSurface roles.
    @Composable
    private fun onBg(c: Ctx): ColorProvider = if (c.onPhoto) ColorProvider(Color.White) else GlanceTheme.colors.onSurface

    @Composable
    private fun onBgV(c: Ctx): ColorProvider = if (c.onPhoto) ColorProvider(Color(0xFFE2E2E6)) else GlanceTheme.colors.onSurfaceVariant

    // ── State → visuals ──────────────────────────────────────────────────────

    private class ActionVisual(val iconRes: Int, val bg: ColorProvider, val fg: ColorProvider, val label: String)

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

    // ── Click routing ─────────────────────────────────────────────────────────

    /**
     * Route a button tap. The ONLY thing that opens the app is the Open action (and
     * the whole-widget tap). An auth-required action on an auth-on widget opens the
     * transparent biometric gate; everything else runs in the background via a
     * Glance callback so the app never opens.
     */
    private fun clickFor(ctx: Context, c: Ctx, action: WidgetAction): Action = when {
        action.kind == WidgetAction.Kind.OPEN -> actionStartActivity(openIntent(ctx, c.snap.vin))
        action.requiresAuth && c.requireAuth -> actionStartActivity(authIntent(ctx, c.widgetId, c.snap.vin, action))
        else -> actionRunCallback<WidgetActionCallback>(
            actionParametersOf(
                WidgetActionCallback.KEY_WIDGET to c.widgetId,
                WidgetActionCallback.KEY_VIN to c.snap.vin,
                WidgetActionCallback.KEY_ACTION to action.key,
            )
        )
    }

    private fun openIntent(ctx: Context, vin: String): Intent =
        Intent(ctx, MainActivity::class.java).apply {
            action = Shortcuts.ACTION
            data = Uri.parse("bloo://widget/open/$vin")
            putExtra(Shortcuts.EXTRA_VIN, vin)
            putExtra(Shortcuts.EXTRA_CMD, "open")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    private fun authIntent(ctx: Context, widgetId: Int, vin: String, action: WidgetAction): Intent =
        Intent(ctx, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN
            // Unique data URI per widget+action: PendingIntents compare with
            // filterEquals, which IGNORES extras — without this every button collapses
            // into one intent firing the last-cached action.
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

    /** Decode a file-backed bitmap downsampled so its longest edge is <= [maxPx],
     *  memoised by path + last-modified. Full-size photos handed to RemoteViews throw
     *  'exceeds maximum bitmap memory usage' and blank the widget, so always scale. */
    private fun decodeCached(path: String, maxPx: Int = 400): Bitmap? {
        val file = java.io.File(path)
        if (!file.exists()) return null
        val key = "$path:${file.lastModified()}:$maxPx"
        bitmapCache.get(key)?.let { return it }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest > 0 && longest / sample > maxPx) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()?.also { bitmapCache.put(key, it) }
    }

    companion object {
        // Sized by bytes so the static cache can never pin more than a few MB.
        private val bitmapCache = object : android.util.LruCache<String, Bitmap>(6 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }
        private val CLIMATE_KEYS = setOf("climate", "climate_on", "climate_off")
        private val LOCK_KEYS = setOf("doors", "lock", "unlock")
        private val CHARGE_KEYS = setOf("charge", "start_charge", "stop_charge")
    }
}
