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
import com.bloo.bluelink.data.formatDistance
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
 *
 * HOW GLANCE WORKS, MECHANICALLY: this is NOT normal Jetpack Compose. Glance
 * composables ([Box], [Column], [Row], [Text], [Image], etc., all from the
 * `androidx.glance.*` packages, not `androidx.compose.foundation.layout.*`)
 * don't draw pixels directly the way a real Compose UI does. Instead, when
 * [provideGlance] runs, Glance walks the composition tree it produced and
 * translates each node into an actual Android [android.widget.RemoteViews]
 * tree — the same limited, cross-process-safe view hierarchy App Widgets have
 * always used (frames, linear layouts, text views, image views...). That
 * RemoteViews tree is what the launcher process actually inflates and draws;
 * this app's process is not running when the widget sits idle on the home
 * screen. This is why Glance composables can't use arbitrary Compose runtime
 * features: no custom Canvas drawing, no arbitrary animation, no state that
 * lives only in this process, and only the handful of layout primitives that
 * have a RemoteViews equivalent. Every render is a fresh, one-shot conversion
 * from scratch — there's no persistent Composer holding state between updates
 * the way there is in a real running Compose UI; anything that needs to
 * persist between widget updates (pending-action flag, cached bitmaps, chosen
 * config) has to live in [SettingsStore]/[SnapshotStore] or the on-disk/
 * in-memory caches below, and gets re-read at the top of every [provideGlance]
 * call. Clicks work the same way: a Glance `clickable(...)` modifier doesn't
 * attach a listener the way Compose's `Modifier.clickable` does (there's
 * nothing listening — the app isn't running) — it instead bakes a
 * [android.app.PendingIntent] into the RemoteViews tree, either one that
 * starts an activity ([actionStartActivity], used for "open the app" and the
 * biometric-gated auth flow) or one that fires an [androidx.glance.appwidget.action.ActionCallback]
 * via [actionRunCallback] (used for silent background actions — this briefly
 * wakes the app's process just long enough to run [WidgetActionCallback.onAction]
 * and update the RemoteViews in place, without ever showing UI). See the
 * "Click routing" section below for exactly how each button type is wired.
 */
class BlooWidget : GlanceAppWidget() {

    // SizeMode.Exact makes Glance re-invoke provideGlance (and therefore this
    // whole composition) separately for every concrete pixel size the widget
    // is resized to on the home screen, rather than rendering once for a
    // small set of size "buckets" -- this is what lets each tier composable
    // below pick its own layout purely from LocalSize.current instead of
    // guessing which bucket it landed in.
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
        val metric: Boolean = false,
        /** "Info" mode stats to show below 3×3 -- see WidgetInfoField. Each
         *  tile checks membership for the specific fields it has room for;
         *  LargeTile (3×3+) ignores this and always shows everything. */
        val infoFields: List<WidgetInfoField> = WidgetInfoField.DEFAULTS,
    )

    /**
     * Glance's entry point for rendering one widget instance. Called by the
     * Glance runtime whenever this widget needs to be (re)rendered -- on
     * placement, on an explicit `updateAll()`/`update()` call from the workers
     * elsewhere in this file, and whenever its size changes (see [sizeMode]).
     *
     * Runs in two phases:
     *  1. Everything before `provideContent { ... }` is plain suspend Kotlin:
     *     read this widget's saved config and the latest cached vehicle
     *     snapshot from disk (never a live network call -- a widget render
     *     must stay fast and cheap), resolve the theme/accent colors, and
     *     decode/cache any bitmaps (car photo, location map tile) it needs.
     *  2. The `provideContent { ... }` block is the actual Glance composition
     *     -- this is what gets translated into RemoteViews (see the class doc
     *     comment above). It picks one of the Tier composables below based on
     *     the widget's current pixel size and hands it everything gathered in
     *     phase 1 via [Ctx], so none of the tier composables need to touch
     *     Context/DataStore themselves.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsStore(context)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val cfg = settings.widgetConfig(widgetId)
        val snap = cfg?.let { c ->
            SnapshotStore(context).current().vehicles.firstOrNull { it.vin == c.first }
        }
        val actions = cfg?.second.orEmpty().mapNotNull { WidgetAction.fromKey(it) }
        val appearance = settings.appearance.first()

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

        val accentColor = resolveWidgetAccent(context, appearance, snap?.vin)
        val onAccent = if (accentColor.luminance() > 0.5f) Color(0xFF20232A) else Color.White
        // Over a photo, every action button/status pill used to fill its cell
        // with a fully opaque state color -- flat, hard-edged rectangles that
        // completely hid the blurred photo underneath them instead of reading
        // as glass over it (the background layer already fakes real glass;
        // the foreground chrome never matched it). Baking a lower alpha into
        // each state color here, once, means every call site that already
        // just uses theme.accent/charge/unlocked/climate (ChunkyButton,
        // StateChip, ...) gets the frosted look for free.
        fun glassy(c: Color) = if (photoBgActive) c.copy(alpha = 0.62f) else c
        val theme = Theme(
            accent = ColorProvider(glassy(accentColor)),
            onAccent = ColorProvider(onAccent),
            charge = ColorProvider(glassy(Color(com.bloo.bluelink.data.BlooColors.chargeGreen))),
            unlocked = ColorProvider(glassy(Color(com.bloo.bluelink.data.BlooColors.heat))),
            climate = ColorProvider(glassy(Color(com.bloo.bluelink.data.BlooColors.climateTeal))),
            pending = ColorProvider(Color(0.55f, 0.55f, 0.60f, 0.55f)),
            tile = ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.13f)),
        )

        val layoutMode = settings.widgetLayoutMode(widgetId) // "info" or "controls"
        // No ifEmpty{DEFAULTS} fallback here -- SettingsStore.widgetInfoFields
        // already distinguishes "never configured" (returns DEFAULTS itself)
        // from "every field deliberately deselected" (returns an empty list,
        // which has to stay empty -- see its doc comment).
        val infoFields = settings.widgetInfoFields(widgetId).mapNotNull { WidgetInfoField.fromKey(it) }
        val bgAlphaLevel = settings.widgetBackgroundAlpha(widgetId) // 0 (opaque) - 9 (transparent)

        provideContent {
            GlanceTheme {
                val w = LocalSize.current.width
                val h = LocalSize.current.height
                // Pill shape needs at least one narrow dimension -- a 999dp
                // corner radius clips to a stadium against whichever side is
                // shorter, which is exactly the point for a 1-wide-many-tall
                // or 1-tall-many-wide strip. Was gated on BOTH w and h being
                // under 180dp, which silently excluded every long strip (the
                // exact shape a pill reads best on) and only ever applied to
                // roughly-square 1-2 unit tiles. Large-in-both-dimensions
                // tiles (a 4x4+ square) still correctly fall back -- a
                // near-circular radius there clips text/buttons with no room
                // to compensate.
                val pillEligible = pillShape && minOf(w, h) < 180.dp
                val corner = when {
                    pillEligible -> 999.dp
                    w < 90.dp || h < 90.dp -> 16.dp
                    w < 180.dp || h < 130.dp -> 22.dp
                    else -> 28.dp
                }
                val pillPad = if (pillEligible) 8.dp else 0.dp
                // Floor at 0.1 so even the most-transparent level keeps a faint
                // legibility scrim/tint -- a true 0.0 let bright photo/wallpaper
                // patches sit directly behind text with no contrast backstop.
                val bgAlpha = (1f - bgAlphaLevel / 9f).coerceAtLeast(0.1f) // 1.0 (opaque) -> 0.1 (transparent)
                val themeBg = if (appearance.themeMode.name == "AMOLED") ColorProvider(Color(0xFF000000))
                              else GlanceTheme.colors.widgetBackground
                Box(GlanceModifier.fillMaxSize().cornerRadius(corner)) {
                    // Photo background (optional): the car image full-bleed and
                    // genuinely blurred (not just a sharp photo behind a dark
                    // scrim) -- Glance/RemoteViews has no live blur primitive,
                    // but a real blur can still be baked into the bitmap itself
                    // once, off the render path (see blurredCached()), the same
                    // way this file already caches its decoded/downsampled
                    // bitmaps. A soft, blurred backdrop plus a lighter scrim
                    // reads as "glass over a photo" far better than a sharp
                    // photo ever could within what this platform can actually do.
                    if (photoBgActive) {
                        Image(
                            provider = ImageProvider(blurredCached(photo!!, photoPath!!)), contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.fillMaxSize().cornerRadius(corner),
                        )
                        // Was 0.30f -- too light to tame the blurred photo's own
                        // contrast (bright windows/sky patches stayed distractingly
                        // vivid right behind the text and buttons), which is also
                        // why those buttons/pills got a fully-opaque background
                        // instead of true glass -- nothing on top of them could
                        // stay readable otherwise. A stronger scrim here is what
                        // actually lets the buttons go translucent above (see
                        // `glassy()`) without losing legibility.
                        val scrimAlpha = 0.46f * bgAlpha
                        if (scrimAlpha > 0.01f) {
                            Box(GlanceModifier.fillMaxSize().cornerRadius(corner).background(ColorProvider(Color(0f, 0f, 0f, scrimAlpha)))) {}
                        }
                    }
                    // Plain tint, no glass-style distinction -- Glance has no
                    // blur/gradient primitive and a hard limit on how many
                    // nested views one widget can contain, so trying to fake
                    // distinct "Liquid"/"Frosted" looks here was never going to
                    // read as actual glass, just two slightly different flat
                    // tints. One consistent, honest tint instead: a base fill
                    // plus a thin top rim (2 layers, well inside the view budget).
                    if (!photoBgActive && bgAlphaLevel > 0) {
                        val baseTint = Color(0.12f, 0.13f, 0.16f, bgAlpha * 0.85f)
                        val rimAlpha = bgAlpha * 0.22f
                        Box(GlanceModifier.fillMaxSize().cornerRadius(corner).background(ColorProvider(baseTint))) {}
                        Box(GlanceModifier.fillMaxWidth().height(2.dp).background(ColorProvider(Color(1f, 1f, 1f, rimAlpha)))) {}
                    }
                    val base = GlanceModifier.fillMaxSize()
                        .let { m ->
                            if (photoBgActive || bgAlphaLevel > 0) m
                            else m.background(themeBg)
                        }
                        .cornerRadius(corner)
                        .padding(pillPad)

                    if (snap == null) {
                        SetupTile(base, configIntent(context, widgetId))
                    } else {
                        val metric = appearance.unitSystem == "metric"
                        val c = Ctx(widgetId, snap, actions, theme, pending, requireAuth, photoBgActive, showLocation, map, photo, address, layoutMode, metric, infoFields)
                        when {
                            w < 70.dp || (w < 80.dp && h < 80.dp) ->
                                if (layoutMode == "controls") ControlsTile(c, base) else InfoTile(c, base)
                            h < 70.dp ->
                                if (layoutMode == "controls") ControlsTile(c, base) else InfoTile(c, base)
                            h < 110.dp -> ShortWideTile(c, base)
                            w < 110.dp -> TallNarrowTile(c, base)
                            w < 220.dp && h < 130.dp -> SquareTile(c, base)
                            w < 220.dp -> MediumTallTile(c, base)
                            h < 190.dp -> WideTile(c, h, base)
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

    /** Info-only: just the percent and a state dot (for the smallest 1x1). */
    @Composable
    private fun InfoTile(c: Ctx, base: GlanceModifier) {
        val ctx = LocalContext.current
        // Only room for 3 short lines at this size -- name/percent/lock-dot,
        // each independently toggleable via the Info fields picker (range and
        // model have no room here regardless of selection).
        Box(
            base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (WidgetInfoField.NAME in c.infoFields) {
                    Text(c.snap.name.take(6), maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 8.sp))
                }
                if (WidgetInfoField.PERCENT in c.infoFields) {
                    Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = 20.sp))
                }
                if (WidgetInfoField.LOCK in c.infoFields) {
                    Box(GlanceModifier.size(5.dp).background(stateColor(c.snap, c.theme)).cornerRadius(3.dp)) {}
                }
            }
        }
    }

    /** Controls-only: chunky buttons filling the tile edge-to-edge (for tiny controls-mode).
     *  Bails out (renders nothing) when no actions are configured, rather than showing
     *  an empty box -- the caller in [provideGlance] still draws the pending spinner /
     *  background around whatever this returns. */
    @Composable
    private fun ControlsTile(c: Ctx, base: GlanceModifier) {
        if (c.actions.isEmpty()) return
        // Only ever the first 4 actions -- a widget can have at most 4 configured
        // (see WidgetConfigActivity), this coerce is just defensive.
        val take = c.actions.take(4)
        ButtonGrid(c, take, cols = take.size.coerceAtMost(2), showLabel = false, iconSize = 18.dp,
            modifier = base.padding(4.dp))
    }

    /** Shown for every tile size when this widget instance has no car assigned yet
     *  (i.e. [provideGlance] found `snap == null`). Tapping anywhere on it launches
     *  [WidgetConfigActivity] via [configIntent] so the user can pick a car. */
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
    private fun ShortWideTile(c: Ctx, base: GlanceModifier) {
        val ctx = LocalContext.current
        // At this size "controls" fully replaces the info column instead of
        // squeezing a button strip in beside it (matching TallNarrowTile and
        // the tiniest tier, which already do a full swap) -- otherwise this
        // was the one remaining tier where switching to Controls only ever
        // ADDED something rather than changing what the widget is for.
        if (c.layoutMode == "controls" && c.actions.isNotEmpty()) {
            val take = c.actions.take(4)
            ButtonGrid(c, take, cols = take.size.coerceAtMost(4), showLabel = false, iconSize = 24.dp,
                modifier = base.padding(6.dp))
            return
        }
        Row(
            base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Text(c.snap.name.take(12), maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 9.sp))
                Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = 20.sp))
                c.snap.rangeMi?.let { Text(formatDistance(it, c.metric), maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 11.sp)) }
            }
        }
    }

    /** 1 column wide, any height -- from a short 1x2 up to a full 1x5 strip.
     *  Scales with height instead of a fixed-size block that leaves a tall
     *  widget mostly empty below it: content is centered in whatever room
     *  there is, and genuinely tall sizes get a status row and a second
     *  info line the shorter sizes have no room for. */
    @Composable
    private fun TallNarrowTile(c: Ctx, base: GlanceModifier) {
        val ctx = LocalContext.current
        val h = LocalSize.current.height
        val narrow = LocalSize.current.width < 90.dp
        if (c.layoutMode == "controls") {
            if (c.actions.isEmpty()) return
            // 1 column, not ControlsTile's 2 -- this tier is narrow enough that
            // 2 columns squeezed buttons into overlapping slivers. Rows already
            // grow to fill whatever height is available (see ButtonGrid), so a
            // tall strip naturally gets bigger buttons instead of 2 small ones
            // stacked above empty space.
            val take = c.actions.take(if (h >= 260.dp) 4 else if (h >= 180.dp) 3 else 2)
            ButtonGrid(c, take, cols = 1, showLabel = h >= 220.dp, iconSize = if (h >= 260.dp) 26.dp else 20.dp,
                modifier = base.padding(6.dp))
            return
        }
        Column(
            base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (WidgetInfoField.NAME in c.infoFields) {
                Text(c.snap.name.take(10), maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = if (narrow) 10.sp else 12.sp))
                Spacer(GlanceModifier.height(2.dp))
            }
            // Stack percent digits vertically when very narrow to avoid clipping
            if (WidgetInfoField.PERCENT in c.infoFields) {
                if (narrow && c.snap.percent != null) {
                    VerticalNumber(c.snap.percent.toString() + "%", onBg(c))
                } else {
                    Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = 26.sp))
                }
                Spacer(GlanceModifier.height(2.dp))
            }
            if (WidgetInfoField.RANGE in c.infoFields) {
                c.snap.rangeMi?.let { Text(formatDistance(it, c.metric), maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = if (narrow) 9.sp else 11.sp)) }
            }
            // Extra rows only a genuinely tall strip has room for.
            if (h >= 200.dp && WidgetInfoField.LOCK in c.infoFields) {
                Spacer(GlanceModifier.height(10.dp))
                StateChip(c)
            }
            if (h >= 280.dp && WidgetInfoField.MODEL in c.infoFields && c.snap.model.isNotBlank()) {
                Spacer(GlanceModifier.height(10.dp))
                Text(c.snap.model.take(16), maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 10.sp))
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

    /** Tallish but moderately-wide widget: stacked name (2 lines), percent, 2×2 buttons. */
    @Composable
    private fun MediumTallTile(c: Ctx, base: GlanceModifier) {
        val ctx = LocalContext.current
        val (firstLine, secondLine) = splitName(c.snap.name)
        Column(base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(firstLine, maxLines = 1, style = TextStyle(color = onBg(c), fontSize = 13.sp, fontWeight = FontWeight.Bold))
            if (secondLine.isNotEmpty()) Text(secondLine, maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 11.sp))
            Spacer(GlanceModifier.height(6.dp))
            Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = 34.sp))
            c.snap.rangeMi?.let { Text(formatDistance(it, c.metric), maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 11.sp)) }
            Spacer(GlanceModifier.height(4.dp))
            StateChip(c)
            if (c.actions.isNotEmpty() && c.layoutMode == "controls") {
                Spacer(GlanceModifier.height(8.dp))
                val take = c.actions.take(4)
                val cols = if (take.size >= 3) 2 else take.size.coerceAtLeast(1)
                ButtonGrid(c, take, cols = cols, showLabel = false, iconSize = 22.dp,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight())
            }
        }
    }

    /** Roughly-square mid-size widget (under 220x130.dp): name, percent + range
     *  side by side, a state chip, and optionally a 2-column button block below
     *  filling the remaining vertical space via defaultWeight(). */
    @Composable
    private fun SquareTile(c: Ctx, base: GlanceModifier) {
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
                        c.snap.rangeMi?.let { Text(formatDistance(it, c.metric), maxLines = 1, style = TextStyle(color = onBg(c), fontSize = 12.sp)) }
                    }
                }
            }
            Spacer(GlanceModifier.height(2.dp))
            StateChip(c)
            if (c.actions.isNotEmpty() && c.layoutMode == "controls") {
                Spacer(GlanceModifier.height(8.dp))
                val take = c.actions.take(4)
                val cols = if (take.size >= 3) 2 else take.size.coerceAtLeast(1)
                ButtonGrid(c, take, cols = cols, showLabel = false, iconSize = 22.dp,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight())
            }
        }
    }

    /** Wide, short-ish widget (roomier than [ShortWideTile] but under [LargeTile]'s
     *  height threshold): info column on the left, optional button column on the
     *  right that only appears in controls mode -- see the comment on the Column
     *  sizing below for why the info column's width strategy changed. */
    @Composable
    private fun WideTile(c: Ctx, h: Dp, base: GlanceModifier) {
        val ctx = LocalContext.current
        val showButtons = c.actions.isNotEmpty() && c.layoutMode == "controls"
        Row(base.clickable(actionStartActivity(openIntent(ctx, c.snap.vin))).padding(14.dp)) {
            // A fixed 52%-of-width info column left this tile with a wide dead
            // gap before the buttons (the text never needed that much room),
            // and left the ENTIRE right half blank whenever there were no
            // buttons to show at all. Sizing to content + defaultWeight on
            // whichever side needs to stretch uses the full tile width in
            // both cases.
            Column(
                modifier = if (showButtons) GlanceModifier.fillMaxHeight() else GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.snap.name.take(14), maxLines = 1, modifier = GlanceModifier.padding(end = 6.dp),
                        style = TextStyle(color = onBg(c), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    StateChip(c)
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(c.snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = onBg(c), fontWeight = FontWeight.Bold, fontSize = 34.sp))
                c.snap.rangeMi?.let {
                    Text("${formatDistance(it, c.metric)} ${if (c.snap.hasBattery) "range" else "left"}", maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 12.sp))
                }
            }
            if (showButtons) {
                Spacer(GlanceModifier.width(12.dp))
                val take = c.actions.take(4)
                val cols = if (take.size >= 3 && h >= 150.dp) 2 else 1
                ButtonGrid(c, take, cols = cols, showLabel = false, iconSize = 22.dp,
                    modifier = GlanceModifier.fillMaxHeight().defaultWeight())
            }
        }
    }

    /** The biggest tile tier (roughly 3x3 home-screen cells and up): full name,
     *  state chip, percent/range hero numbers, and a right-hand side panel that's
     *  either the live location map, the car photo, or nothing -- plus, in
     *  controls mode, a footer row/grid of buttons. Font sizes and column counts
     *  scale further with height/width via the `tall`/`pctSize`/`footerCols` locals
     *  below so a 3x3 and a 5x5 both feel proportioned rather than the 5x5 just
     *  having empty margins. */
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
                            c.snap.rangeMi?.let { Text(formatDistance(it, c.metric), maxLines = 1, style = TextStyle(color = onBg(c), fontSize = 14.sp, fontWeight = FontWeight.Medium)) }
                            Text(if (c.snap.hasBattery) "Battery" else "Fuel", maxLines = 1, style = TextStyle(color = onBgV(c), fontSize = 11.sp))
                        }
                    }
                    val addr = c.address
                    if (wantMap && addr != null) {
                        Spacer(GlanceModifier.height(8.dp))
                        Text(addr.take(40), maxLines = 2, style = TextStyle(color = onBgV(c), fontSize = 11.sp))
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
            if (take.isNotEmpty() && c.layoutMode == "controls") {
                Spacer(GlanceModifier.height(14.dp))
                ButtonGrid(c, take, cols = footerCols, showLabel = h >= 290.dp, iconSize = 24.dp,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight())
            }
        }
    }

    // ── Shared pieces ────────────────────────────────────────────────────────

    /** The car's current location: a map thumbnail with a pin. Before the first
     *  Location action a pin-and-label placeholder is shown. */
    @Composable
    private fun LocationBox(c: Ctx, modifier: GlanceModifier) {
        val map = c.map
        Box(modifier.cornerRadius(18.dp).background(c.theme.tile), contentAlignment = Alignment.Center) {
            if (map != null) {
                Image(provider = ImageProvider(map), contentDescription = "Car location",
                    contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize().cornerRadius(18.dp))
            } else if (c.address != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Image(provider = ImageProvider(R.drawable.ic_widget_location), contentDescription = null,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant), modifier = GlanceModifier.size(20.dp))
                    Spacer(GlanceModifier.height(4.dp))
                    Text(c.address.take(35), maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(provider = ImageProvider(R.drawable.ic_widget_location), contentDescription = null,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant), modifier = GlanceModifier.size(26.dp))
                    Spacer(GlanceModifier.height(4.dp))
                    Text("Tap to locate", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
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
                    // null, not action.label: the Text right below already
                    // carries the same words -- a non-null description here
                    // was a redundant announcement.
                    Image(provider = ImageProvider(vis.iconRes), contentDescription = null, colorFilter = ColorFilter.tint(vis.fg), modifier = GlanceModifier.size(iconSize))
                    Spacer(GlanceModifier.height(4.dp))
                    Text(vis.label, maxLines = 1, style = TextStyle(color = vis.fg, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            } else {
                Image(provider = ImageProvider(vis.iconRes), contentDescription = action.label, colorFilter = ColorFilter.tint(vis.fg), modifier = GlanceModifier.size(iconSize))
            }
        }
    }

    /** The small rounded status pill ("Locked" / "Charging" / "Climate on" / etc.)
     *  shown next to the car name on most tiers. Priority when several states are
     *  true at once: charging beats unlocked beats climate-on beats the plain
     *  accent-colored default -- matches [stateColor]'s priority for the InfoTile
     *  status dot, so the widget never shows two different "which state matters
     *  most" answers depending on tile size. */
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
        // 999.dp clamps to a true pill at the chip's actual (short) height --
        // 9.dp only happened to look right at one specific tier's text size.
        Box(GlanceModifier.background(bg).cornerRadius(999.dp).padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(label, maxLines = 1, style = TextStyle(color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
    }

    // Text colors: white over a photo background, else the theme's onSurface roles.
    @Composable
    private fun onBg(c: Ctx): ColorProvider = if (c.onPhoto) ColorProvider(Color.White) else GlanceTheme.colors.onSurface

    @Composable
    private fun onBgV(c: Ctx): ColorProvider = if (c.onPhoto) ColorProvider(Color(0xFFE2E2E6)) else GlanceTheme.colors.onSurfaceVariant

    // ── State → visuals ──────────────────────────────────────────────────────

    private class ActionVisual(val iconRes: Int, val bg: ColorProvider, val fg: ColorProvider, val label: String)

    /**
     * Resolve one action button's icon/background/foreground/label from the car's
     * current live state, so e.g. the "Doors" button shows a lock icon on a red
     * background and reads "Lock" when the car is unlocked, and shows an unlock
     * icon on the accent color reading "Unlock" when it's locked -- same for
     * climate (teal when on) and charge (green when charging). If this exact
     * action is the one currently in flight ([pending] == [action]'s key) that
     * overrides everything else with a spinner icon and the muted pending color,
     * regardless of what state the car itself is in.
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
        val label = when (action.key) {
            "doors" -> when (snap.locked) { true -> "Lock"; false -> "Unlock"; else -> "Doors" }
            else -> action.label.take(8)
        }
        return ActionVisual(iconRes, bg, fg, label)
    }

    /** Same priority order as [actionVisual]'s background resolution (charging >
     *  unlocked > climate-on > accent default), but for the InfoTile's tiny status
     *  dot rather than a full button, so the smallest widget size still tells the
     *  most important current state apart at a glance. */
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

    /** Build the intent that opens the app straight to [vin]'s car page, reusing the
     *  same [Shortcuts]-based routing the launcher shortcuts and app-open widget
     *  button share -- [MainActivity.handleShortcutIntent] is what actually reads
     *  these extras once the activity starts. */
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

    /** Build the intent that opens [WidgetConfigActivity] pre-targeted at this
     *  specific widget instance, used by [SetupTile] for a never-configured widget. */
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

    /** A soft, real blur of [source] (the already-downsampled photo bitmap,
     *  itself capped to ~400px on its longest edge by [decodeCached] before
     *  this ever runs), memoised by [path]'s identity so it's computed once
     *  per photo, not on every widget refresh tick.
     *
     *  Rebuilt from scratch as a real fixed-*pixel-radius* box blur (three
     *  passes, which approximates a Gaussian closely) operating directly on
     *  the bitmap's own pixel data, replacing an earlier scale-down/scale-
     *  back-up approximation. That technique coupled "how blurred it looks"
     *  to the source's own resolution and JPEG compression in a way that
     *  was never stable: tuning passes swung between blotchy, an
     *  over-blurred low-res mush, and (at the gentlest setting) the
     *  original photo's own 8x8 JPEG block edges showing back through
     *  almost undisguised. A fixed pixel radius has neither problem --
     *  it softens by the same real amount regardless of the source photo's
     *  size or compression, and is directly tunable (see [BLUR_RADIUS_DIVISOR]).
     *
     *  Still no RenderScript/RenderEffect (Glance content is built off the
     *  main render pipeline, and RenderEffect needs a live View/RenderNode)
     *  -- but unlike the Glance composition path itself, this runs once per
     *  photo change on a small, already-downsampled bitmap and gets cached,
     *  so a real per-pixel pass here is cheap in practice (three box-blur
     *  passes over ~400x400px is well under a millisecond of integer math). */
    private fun blurredCached(source: Bitmap, path: String): Bitmap {
        val file = java.io.File(path)
        val key = "blur:$path:${file.lastModified()}"
        bitmapCache.get(key)?.let { return it }
        return runCatching {
            val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
            val radius = (maxOf(mutable.width, mutable.height) / BLUR_RADIUS_DIVISOR).coerceIn(2, 5)
            repeat(2) { boxBlurInPlace(mutable, radius) }
            mutable
        }.getOrDefault(source).also { bitmapCache.put(key, it) }
    }

    /** Blur radius scales with image size (so a bigger decoded photo doesn't
     *  read as proportionally sharper) but is clamped to a range tuned by
     *  eye against this widget's own ~400px source. History: divisor 30 /
     *  clamp 4-14 / three passes was blotchy; the fixed-radius rewrite moved
     *  to divisor 45 / clamp 3-9 / two passes; that still read as too
     *  aggressive per feedback, so it's now divisor 70 / clamp 2-5 / two
     *  passes -- a ~400px photo lands at radius ~5 (was ~8), softening JPEG
     *  block edges without the heavy "genuinely blurred" mush. Two passes
     *  stay (dropping to one lets the source's 8x8 JPEG block edges show
     *  back through); the amount is cut via the smaller radius instead. */
    private val BLUR_RADIUS_DIVISOR = 70

    /** One box-blur pass (horizontal then vertical, each an O(width*height)
     *  sliding average via per-row/per-column prefix sums, not a naive
     *  O(width*height*radius) re-sum per pixel) mutating [bmp] in place.
     *  Two calls with the same radius approximate a Gaussian blur closely
     *  enough for this purpose at a fraction of the cost of one. */
    private fun boxBlurInPlace(bmp: Bitmap, radius: Int) {
        if (radius < 1) return
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val horizontal = IntArray(w * h)
        boxBlurPass(pixels, horizontal, w, h, radius, alongRows = true)
        boxBlurPass(horizontal, pixels, w, h, radius, alongRows = false)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /** One directional box-blur pass. [alongRows] = true blurs each row
     *  horizontally (x varies, y fixed); false blurs each column vertically
     *  (y varies, x fixed). Edge pixels use a shrinking (not wrapped or
     *  clamped-weight) window -- the average of however many real neighbours
     *  exist near an edge, which is the standard box-blur edge behaviour and
     *  avoids darkening/lightening the border. */
    private fun boxBlurPass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, alongRows: Boolean) {
        val outer = if (alongRows) h else w
        val inner = if (alongRows) w else h
        // Per-channel running-sum prefix arrays, reused across every
        // row/column of this pass -- index 0 is always the empty-window
        // sum (0), so no explicit reset is needed between lines.
        val prefA = IntArray(inner + 1)
        val prefR = IntArray(inner + 1)
        val prefG = IntArray(inner + 1)
        val prefB = IntArray(inner + 1)
        for (o in 0 until outer) {
            for (i in 0 until inner) {
                val idx = if (alongRows) o * w + i else i * w + o
                val p = src[idx]
                prefA[i + 1] = prefA[i] + ((p ushr 24) and 0xFF)
                prefR[i + 1] = prefR[i] + ((p ushr 16) and 0xFF)
                prefG[i + 1] = prefG[i] + ((p ushr 8) and 0xFF)
                prefB[i + 1] = prefB[i] + (p and 0xFF)
            }
            for (i in 0 until inner) {
                val start = (i - radius).coerceAtLeast(0)
                val end = (i + radius).coerceAtMost(inner - 1)
                val count = end - start + 1
                val a = (prefA[end + 1] - prefA[start]) / count
                val r = (prefR[end + 1] - prefR[start]) / count
                val g = (prefG[end + 1] - prefG[start]) / count
                val b = (prefB[end + 1] - prefB[start]) / count
                val idx = if (alongRows) o * w + i else i * w + o
                dst[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    companion object {
        // Sized by bytes so the static cache can never pin more than a few MB.
        private val bitmapCache = object : android.util.LruCache<String, Bitmap>(6 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }
        // Grouped by the state they visually react to (not by exact WidgetAction key)
        // so e.g. both the toggle "climate" button and the explicit "climate_on"/
        // "climate_off" buttons all light up teal together when climate is active,
        // rather than only the specific button that happens to match the toggle.
        private val CLIMATE_KEYS = setOf("climate", "climate_on", "climate_off")
        private val LOCK_KEYS = setOf("doors", "lock", "unlock")
        private val CHARGE_KEYS = setOf("charge", "start_charge", "stop_charge")
    }
}
