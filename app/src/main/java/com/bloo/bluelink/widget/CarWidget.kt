package com.bloo.bluelink.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.R
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.ui.ThemeMode
import com.bloo.bluelink.ui.resolveWidgetAccent
import com.bloo.bluelink.ui.resolveWidgetIsDark
import kotlinx.coroutines.flow.first

/**
 * The adaptive Bloo home-screen widget.
 *
 * ONE widget composable scales natively from a 1×1 tile to a 7×7 dashboard by
 * measuring its actual size ([SizeMode.Exact] → [LocalSize]) and choosing a layout
 * TIER, then showing progressively more modules (status ring, info stats, action
 * buttons, footer) as space allows — never cramming. What appears is the
 * intersection of "what fits" (size tier) and "what the user asked for"
 * ([WidgetConfig], edited in [WidgetConfigActivity], whose option set adapts to the
 * app's simple/advanced mode).
 *
 * Colors are resolved once in [provideGlance] via [WidgetTheme] — the exact accent
 * (per-car palette / dynamic color / vibrancy), semantic charge/unlock/climate
 * colors, and dark/light surfaces the rest of the app uses (see [resolveWidgetAccent]
 * / [resolveWidgetIsDark]) — rather than the vanilla [GlanceTheme] default, which
 * resolves to Android's generic wallpaper-derived widget palette and has no
 * relationship to Bloo's own branding or the user's in-app theme choices.
 *
 * All data is read once in [provideGlance] (suspend) and handed to the content as a
 * plain [Render] holder; the composables themselves do no I/O.
 */

/** Top-level (not nested in [CarWidget]) so the render layer's other files --
 *  [WidgetValues], and any further slice of CarWidget.kt's own composables --
 *  can take it as a plain parameter type without an outer-class qualifier.
 *  Nested-but-internal was tried first and does NOT do this: an unqualified
 *  `Render` in another file only resolves against top-level declarations,
 *  never a nested class of some other top-level type, however visible. */
internal data class Render(
    val car: VehicleSnapshot?,
    val config: WidgetConfig,
    val theme: WidgetTheme,
    val metric: Boolean,
    val multiCar: Boolean,
    /** True when the car data is older than the staleness window — surfaces so
     *  the widget can flag "this may be out of date" instead of showing an
     *  hours-old lock/charge state as if it were live. */
    val stale: Boolean,
    /** Pre-fetched location map tile (null when disabled, no coords, or fetch
     *  failed) — I/O can't run in the Glance composables, so it's done in
     *  provideGlance and handed in ready to draw. */
    val mapBitmap: android.graphics.Bitmap?,
    /** Pre-fetched, pre-blurred car photo for the "Photo background" option
     *  (null when disabled or the car has no photo set) -- same reasoning
     *  as [mapBitmap], decoded/blurred in provideGlance via [WidgetPhoto]. */
    val photoBitmap: android.graphics.Bitmap?,
) {
    /** Whether HeaderRow draws the car-switcher pill, which is taller than the
     *  two text lines beside it at the smaller text settings.
     *
     *  One definition, read by HeaderRow to decide whether to draw it and by
     *  [Scale.headerHeight] to reserve for it. As an inline condition in
     *  HeaderRow only, no budget could see it. */
    val hasSwitcher: Boolean get() = multiCar && config.vin == null

    /** The per-render facts every vertical budget needs. [size] is the only
     *  thing not already known here, so each tier builds this once from
     *  LocalSize rather than passing four arguments down. */
    fun frame(size: DpSize): Scale.Frame =
        Scale.Frame(size, theme.textScale, pillCorner(size), hasSwitcher)

    /** True when Content's root padding gets its pill-corner bonus. Derived from
     *  the same rule Content applies, so the padding it draws and the padding
     *  every budget subtracts cannot disagree -- they did for all 18 tiers,
     *  which each assumed plain contentPadding. */
    fun pillCorner(size: DpSize): Boolean =
        config.effectiveCorner == WidgetConfig.CORNER_PILL && Scale.pillAppliesAt(size)
}

class CarWidget : GlanceAppWidget() {

    // Exact = recompose for the real current size, so every launcher cell count
    // (and every mid-resize size) gets a layout tuned to its exact dimensions,
    // rather than snapping to a handful of Responsive buckets.
    override val sizeMode = SizeMode.Exact

    /**
     * Replaces Glance's default composition-failure UI, a bare "Can't show content"
     * that offers the user nothing to do about it, with a themed panel that at least
     * opens the app on tap.
     *
     * Deliberately plain [RemoteViews]: this is not a composable and not a suspend
     * function, so neither Glance content nor the per-car [WidgetTheme] (which needs
     * suspending DataStore reads) is reachable here. The layout leans on
     * `?android:attr` for light/dark, exactly as `car_widget_loading.xml` does --
     * and composing a theme at the moment composition has just failed would be the
     * wrong instinct anyway.
     *
     * Note `errorUiLayout` is NOT the hook to use: in glance-appwidget 1.1.1 it is a
     * private final field with only an internal getter, so it cannot be overridden --
     * verified against the resolved 1.1.1 artifact, not the docs.
     *
     * The [throwable] is intentionally dropped rather than logged: there is not one
     * `android.util.Log` call anywhere in `:app` or `:shared`, and quietly starting a
     * logging convention inside an error handler is not the place to make that call.
     * It does mean a composition crash stays undiagnosable, which is a real cost --
     * this is the line to add a log to if that ever needs chasing.
     */
    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        val views = RemoteViews(context.packageName, R.layout.car_widget_error)
        views.setOnClickPendingIntent(
            R.id.widget_error_root,
            PendingIntent.getActivity(
                context,
                // Keyed per widget so two failed widgets don't share (and overwrite)
                // one another's PendingIntent.
                appWidgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = WidgetConfigStore(context).get(appWidgetId)
        val snapshots = SnapshotStore(context)
        val data = snapshots.current()
        // A pinned widget shows ITS car or nothing — never silently swap to another.
        // Only a "follow" widget (null vin) tracks the app's currently-selected car.
        val car = if (config.vin != null) {
            data.vehicles.firstOrNull { it.vin == config.vin }
        } else {
            data.selected
        }
        val appearance = runCatching { SettingsStore(context).appearance.first() }.getOrNull()
            ?: SettingsStore.Appearance()
        val metric = appearance.unitSystem == "metric"
        val theme = WidgetTheme.resolve(context, appearance, config, car?.vin)
        val stale = car?.fetchedAt?.takeIf { it > 0 }?.let {
            System.currentTimeMillis() - it > com.bloo.bluelink.data.STALE_STATUS_MS
        } ?: false
        // The location map is fetched here (suspend) — not in the composables, which
        // can't do I/O — when the user enabled it and the car has coordinates. Sized
        // generously; the Image just scales it down for smaller layout slots.
        val mapBitmap = if (config.showMap && car?.lat != null && car.lon != null) {
            val density = context.resources.displayMetrics.density
            val edge = (150 * density).toInt()
            runCatching { WidgetMap.render(context, car.lat!!, car.lon!!, edge, theme.accentArgb) }.getOrNull()
        } else null
        // No-ops gracefully to the themed background when the car has no photo
        // set (SettingsStore.imageUrl is only ever a local file path here --
        // "/..." -- never a remote URL, matching how the app's own photo
        // picker stores it).
        val photoBitmap = if (config.photoBackground && car != null) {
            val path = runCatching { SettingsStore(context).imageUrl(car.vin) }.getOrNull()
            if (path != null && path.startsWith("/")) {
                WidgetPhoto.decodeCached(path)?.let { WidgetPhoto.blurredCached(it, path) }
            } else null
        } else null
        val render = Render(
            car = car,
            config = config,
            theme = theme,
            metric = metric,
            multiCar = data.vehicles.size > 1,
            stale = stale,
            mapBitmap = mapBitmap,
            photoBitmap = photoBitmap,
        )
        provideContent {
            // Observe the snapshot store INSIDE the composition, which is what
            // GlanceAppWidget's own KDoc instructs: "load initial data before calling
            // provideContent, and then observe your sources of data within the composition
            // (e.g. collectAsState). This ensures that your widget will continue to update
            // while the composition is active."
            //
            // Without this, `updateAll()` on a data change could silently do nothing.
            // update() only calls session.updateGlance() when a session was already running,
            // and updateGlance() merely re-reads GLANCE STATE -- the content flow is held as
            // `remember { widget.runGlance(...) }`, so the suspend body above is never
            // re-invoked. Every value it computed stays frozen for the life of the session.
            //
            // That is not theoretical here. Tapping a widget action writes an optimistic
            // snapshot and calls updateAll(), then the real status lands seconds later and
            // calls updateAll() again -- inside the same session (~45s after provideContent,
            // ~5s on a dozing device). The SECOND update was the one carrying the truth, and
            // it was the one dropped. Symptom: "I sent a command from the widget and it never
            // updated."
            val live by snapshots.payload.collectAsState(initial = data)
            val liveCar = if (config.vin != null) {
                live.vehicles.firstOrNull { it.vin == config.vin }
            } else {
                live.selected
            }
            // Staleness is recomputed from the live fetchedAt, not carried over: the whole
            // point of a live update is that the age changed.
            val liveStale = liveCar?.fetchedAt?.takeIf { it > 0 }?.let {
                System.currentTimeMillis() - it > com.bloo.bluelink.data.STALE_STATUS_MS
            } ?: false
            GlanceTheme {
                // theme, mapBitmap and photoBitmap deliberately keep their cold-path values.
                // All three need I/O, which Glance composables cannot do, and all three are
                // keyed to the car's IDENTITY rather than its data -- which does not change
                // within a session (switching cars goes through WidgetSwitchCarAction, and
                // that starts a new one). What changes live is the numbers, and those are
                // exactly what this passes through.
                Content(
                    render.copy(
                        car = liveCar ?: render.car,
                        stale = liveStale,
                        multiCar = live.vehicles.size > 1,
                    ),
                )
            }
        }
    }

    // Every size in this file -- text, icons, padding, the ring, the bar --
    // comes from [Scale], which now lives in WidgetScale.kt so its vertical
    // budgets can be tested. The [WidgetTier] enum still decides layout
    // STRUCTURE (what appears, how it's arranged); Scale decides how big.

    @Composable
    private fun Content(render: Render) {
        val car = render.car
        val photo = render.photoBitmap
        // Every text/tonal role swaps to a photo-safe variant when the photo
        // background is actually active (no photo set = falls straight back
        // to the normal themed surface) -- see WidgetTheme.forPhoto. Doing this
        // once here, on the Render itself, means every tier/module below just
        // keeps reading render.theme like normal and gets it for free.
        val effective = if (photo != null) render.copy(theme = render.theme.forPhoto()) else render
        val size = LocalSize.current
        // A pill only reads as a stadium while one side is short; past that
        // it would carve a large widget's own content into a lens, so it
        // falls back to the roundest ordinary corner rather than applying
        // literally. Every other choice applies at any size.
        val corner = when (render.config.effectiveCorner) {
            WidgetConfig.CORNER_SHARP -> 0.dp
            WidgetConfig.CORNER_ROUND -> 32.dp
            WidgetConfig.CORNER_PILL ->
                if (Scale.pillAppliesAt(size)) 999.dp else 32.dp
            else -> 20.dp
        }
        val outerCorner = GlanceModifier.fillMaxSize().cornerRadius(corner)
        // Base padding scales continuously with size; a pill shape needs a
        // little extra on top of that so content doesn't clip against the
        // extreme corner curve.
        //
        // Through Scale.rootPadding rather than open-coded, because this is the
        // figure every tier's vertical budget has to subtract. It was computed here
        // and nowhere else, so all 18 tiers subtracted plain contentPadding and
        // over-reported their column by 8dp on any pill widget under 180dp.
        val root = (if (photo == null) outerCorner.background(effective.theme.background) else outerCorner)
            .padding(Scale.rootPadding(size, effective.pillCorner(size)))
        if (car == null) {
            EmptyState(root, effective.theme)
            return
        }
        // Whole-card tap target UNDER everything else: only specific inner
        // elements (HeaderRow, RailLayout's own column, a button) carried
        // their own clickable before this, so the photo/background and every
        // bit of empty space between modules did nothing at all when tapped
        // -- reported from a real device, most visible on a tall tile with a
        // lot of bare photo showing. Buttons and other inner clickables
        // still take priority for their own bounds; RemoteViews resolves an
        // overlapping tap to the innermost view that registered one.
        Box(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(corner).clickable(openAction(LocalContext.current)),
        ) {
            if (photo != null) {
                Image(
                    provider = ImageProvider(photo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(corner),
                )
                // A flat dark scrim regardless of light/dark theme -- the photo's
                // own brightness varies too much car to car to trust either
                // theme's plain surface tint to stay legible under white text.
                Box(GlanceModifier.fillMaxSize().cornerRadius(corner).background(ColorProvider(Color(0f, 0f, 0f, 0.38f)))) {}
            }
            // One canvas for every size. This used to be an eighteen-arm
            // dispatch into eighteen hand-written tier layouts, each deciding
            // what to show, arranging it, and budgeting its own vertical space
            // -- three jobs tangled together in eighteen places, which is why a
            // budget fix in one tier taught the other seventeen nothing and the
            // same overflow kept reappearing one tier over.
            //
            // WidgetBlueprint now decides, and WidgetCanvas draws each module
            // inside the height it was allocated. Which arrangement a tile gets
            // (strip, side-by-side, or stack) is a property of the blueprint,
            // not of a tier table, so there is no size that can fall between
            // two entries and land somewhere nobody designed.
            Box(modifier = root) { WidgetCanvas(car, effective) }
        }
    }

    // ---- Empty / signed-out --------------------------------------------------

    @Composable
    private fun EmptyState(root: GlanceModifier, theme: WidgetTheme) {
        val size = LocalSize.current
        // [root] already carries Content's own padding, so the width text
        // actually gets is the tile minus both sides of it.
        val inner = (size.width - Scale.contentPadding(size) * 2).coerceAtLeast(16.dp)
        Box(modifier = root.clickable(openAction(LocalContext.current)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FitText(
                    "Bloo",
                    TextStyle(color = theme.accentProvider, fontSize = Scale.titleSp(size), fontWeight = FontWeight.Bold),
                    maxWidth = inner,
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
                // The call to action only earns its space once there's room
                // for it to read as a sentence. Below that the wordmark
                // alone is the honest degradation -- the whole tile is
                // still tappable and still opens the app, so nothing is
                // lost but the prompt, which beats stacking "Open to sign
                // in" into a thirteen-row letter column on a 1x1 tile.
                if (size.height >= 72.dp && inner >= 80.dp) {
                    Spacer(GlanceModifier.height(4.dp))
                    FitText(
                        "Open to sign in",
                        TextStyle(color = theme.onSurfaceVariant, fontSize = Scale.subtitleSp(size)),
                        maxWidth = inner,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                }
            }
        }
    }

    // Everything this class used to hold below Content -- eighteen tier
    // layouts, the shared modules they composed, the text styles, and the
    // breadcrumbs for constants that had already moved to Scale -- is gone.
    // The layouts were replaced by WidgetBlueprint (what to show) plus
    // WidgetCanvas (drawing it); the modules live in WidgetInfo, WidgetGauges,
    // WidgetButtons and WidgetMapModule; the styles in WidgetStyles.
}

/**
 * The widget's fully-resolved color set for one render, built once in
 * [CarWidget.provideGlance] instead of leaning on the vanilla [GlanceTheme]
 * default (which resolves to Android's generic wallpaper-derived Material You
 * palette on API 31+, with no relationship to Bloo's own branding or the
 * user's actual in-app theme settings). [accent] mirrors exactly what the rest
 * of the app shows for this car (per-car custom palette → global custom
 * palette → dynamic color → the built-in Expressive palette, all vibrancy-
 * scaled — see [resolveWidgetAccent]), unless the widget's own config picked
 * an explicit [WidgetAccent] override. [isDark] similarly follows the app's
 * real theme setting unless this widget's [WidgetConfig.theme] overrides it.
 */
internal data class WidgetTheme(
    val isDark: Boolean,
    val accent: Color,
    val accentArgb: Int,
    val accentProvider: ColorProvider,
    val onAccent: ColorProvider,
    val background: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val surfaceVariant: ColorProvider,
    val trackArgb: Int,
    val charge: ColorProvider,
    val unlocked: ColorProvider,
    val climate: ColorProvider,
    /** Multiplier applied to every font size the widget derives from its
     *  measured size. Carried here because this is already the per-render
     *  resolved-styling holder threaded to every module, so the text helpers
     *  can reach it without a config parameter on each one.
     *
     *  Scaling up cannot overflow: FitText measures against the scaled size,
     *  so a larger scale simply reaches its wrap and shrink rungs sooner. */
    val textScale: Float = 1f,
) {
    companion object {
        fun resolve(
            context: Context,
            appearance: SettingsStore.Appearance,
            config: WidgetConfig,
            vin: String?,
        ): WidgetTheme {
            val forceDark = when (config.theme) {
                WidgetConfig.THEME_LIGHT -> false
                WidgetConfig.THEME_DARK -> true
                else -> null
            }
            val isDark = resolveWidgetIsDark(context, appearance, forceDark)
            val accent = config.accent?.let { WidgetAccent.fromKey(it) }?.let { Color(it.argb) }
                ?: resolveWidgetAccent(context, appearance, vin, forceDark)
            val onAccent = if (accent.luminance() > 0.5f) Color(0xFF16171B) else Color.White
            // True black only when actually following the app's own AMOLED setting
            // (config.theme == "auto") -- a per-widget Light/Dark override is asking
            // for a deliberately different look than the app, not a void background.
            val amoled = config.theme == WidgetConfig.THEME_AUTO &&
                (appearance.themeMode == ThemeMode.AMOLED ||
                    (appearance.themeMode == ThemeMode.SYSTEM_AMOLED && isDark))
            val background = when {
                amoled -> Color.Black
                isDark -> Color(0xFF1C1D22)
                else -> Color(0xFFF4F4F7)
            }
            // Translucency is applied to the resolved surface here rather
            // than at the call site, because this is the only place the
            // background is still a Color rather than an opaque
            // ColorProvider. The photo background path never reads this, so
            // the option correctly has no effect there -- a photo brings its
            // own backdrop and its own scrim.
            val tinted = background.copy(alpha = background.alpha * config.safeBackgroundOpacity)
            val onSurface = if (isDark) Color(0xFFF2F2F5) else Color(0xFF1B1C20)
            val onSurfaceVariant = if (isDark) Color(0xFFC6C6CC) else Color(0xFF5C5E66)
            val surfaceVariant = if (isDark) Color(0xFF2A2C32) else Color(0xFFE7E7EC)
            return WidgetTheme(
                isDark = isDark,
                accent = accent,
                accentArgb = accent.toArgb(),
                accentProvider = ColorProvider(accent),
                onAccent = ColorProvider(onAccent),
                background = ColorProvider(tinted),
                textScale = config.safeTextScale,
                onSurface = ColorProvider(onSurface),
                onSurfaceVariant = ColorProvider(onSurfaceVariant),
                surfaceVariant = ColorProvider(surfaceVariant),
                // Was 0x33 (20%) in dark mode -- against a true-black AMOLED
                // background specifically, that read as barely-there rather
                // than "faint unfilled track," so the ring's own bright arc
                // looked like two disconnected floating curves instead of one
                // continuous circle with a filled portion. Bumped until the
                // empty segment reads as clearly part of the same shape.
                trackArgb = if (isDark) 0x4DFFFFFF else 0x33000000,
                charge = ColorProvider(Color(BlooColors.chargeGreen)),
                unlocked = ColorProvider(Color(BlooColors.heat)),
                climate = ColorProvider(Color(BlooColors.climateTeal)),
            )
        }
    }

    /**
     * Text/tonal roles re-tuned for legibility over an arbitrary car photo
     * instead of a flat themed surface -- text goes to a fixed near-white
     * (the photo already gets a dark scrim behind it, see [CarWidget.Content]),
     * and every state-color button fill (accent/charge/unlocked/climate) picks
     * up the same "frosted glass over a photo" translucency the rest of the
     * app's glass surfaces use, rather than sitting fully opaque on top of the
     * photo like a flat sticker.
     */
    fun forPhoto(): WidgetTheme {
        fun glassy(c: Color) = c.copy(alpha = 0.62f)
        return copy(
            onSurface = ColorProvider(Color.White),
            onSurfaceVariant = ColorProvider(Color(0xFFE4E4E8)),
            surfaceVariant = ColorProvider(Color(0x3DFFFFFF)),
            accentProvider = ColorProvider(glassy(accent)),
            charge = ColorProvider(glassy(Color(BlooColors.chargeGreen))),
            unlocked = ColorProvider(glassy(Color(BlooColors.heat))),
            climate = ColorProvider(glassy(Color(BlooColors.climateTeal))),
        )
    }
}
