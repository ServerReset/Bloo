package com.bloo.bluelink.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
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
import kotlin.math.min

/**
 * Bloo home-screen widget — 12 auto-adapting tiers, photo/location for
 * large placements, dynamic button sizing so content never clips.
 */
class BlooWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    private class Theme(val accent: ColorProvider, val onAccent: ColorProvider)

    /** Decode a bitmap from disk, cached by path+mtime. */
    private data class CacheKey(val path: String, val mtime: Long)
    private val bitmapCache = HashMap<CacheKey, Bitmap>()

    private fun decodeCached(path: String, sample: Int = 1): Bitmap? {
        val file = java.io.File(path)
        if (!file.exists()) return null
        val key = CacheKey(path, file.lastModified())
        return bitmapCache.getOrPut(key) {
            BitmapFactory.Options().apply { inSampleSize = sample }
                .let { opts -> BitmapFactory.decodeFile(path, opts) }
        }
    }

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
            Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, 0.22f)))
        else Color.White
        val theme = Theme(ColorProvider(accentColor), ColorProvider(onAccent))

        // Load photo bitmap for large widgets where it can fit
        val photoPath = snap?.let { s -> settings.imageUrl(s.vin) }
        val photoBitmap = photoPath?.takeIf { it.startsWith("/") }?.let { decodeCached(it, sample = 2) }

        provideContent { GlanceTheme {
            val w = LocalSize.current.width; val h = LocalSize.current.height
            val ratio = if (h.value > 0f) w.value / h.value else 1f
            val corner = when { w < 80.dp || h < 80.dp -> 14.dp; w < 160.dp || h < 100.dp -> 18.dp; else -> 24.dp }
            val base = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(corner)
            val hasMedia = photoBitmap != null && w >= 220.dp && h >= 130.dp
            val btnH: Dp = if (hasMedia) 26.dp else if (h < 55.dp) 22.dp else if (h < 90.dp) 28.dp else 34.dp

            when {
                snap == null -> TapBox(base, configIntent(context, widgetId), "Tap to set up")
                w < 60.dp && h < 60.dp -> TinyXSBody(snap, base, theme)
                w < 80.dp || h < 80.dp -> TinyBody(snap, base, theme)
                h < 55.dp -> CompactSBody(snap, actions, w, base, theme, btnH)
                h < 70.dp -> CompactBody(snap, actions, w, base, theme, btnH)
                w < 120.dp -> NarrowBody(snap, actions, w, h, base, theme, btnH)
                ratio in 0.8f..1.25f && h >= 120.dp -> SquareBody(snap, actions, base, theme, btnH, hasMedia, photoBitmap)
                w > 250.dp && h > 170.dp -> LargeBody(snap, actions, w, base, theme, btnH, hasMedia, photoBitmap)
                w > 2f * h && w >= 200.dp -> WideBody(snap, actions, w, base, theme, btnH, hasMedia, photoBitmap)
                h >= 130.dp -> FullBody(snap, actions, w, base, theme, btnH, hasMedia, photoBitmap)
                else -> CompactBody(snap, actions, w, base, theme, btnH)
            }
        }}
    }

    // ── Placeholder ────────────────────────────────────────────────────────

    @Composable
    private fun TapBox(base: GlanceModifier, intent: android.content.Intent, label: String) {
        Box(base.clickable(actionStartActivity(intent)), contentAlignment = Alignment.Center) {
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium))
        }
    }

    // ── Shared helpers ─────────────────────────────────────────────────────

    private fun stateColor(snap: VehicleSnapshot, theme: Theme): ColorProvider =
        when { snap.charging == true -> ColorProvider(Color(0xFF2EBD59)); else -> theme.accent }

    @Composable
    private fun StateChip(snap: VehicleSnapshot, theme: Theme, sc: ColorProvider) {
        val label = vehicleStateLabel(snap.engineOn, snap.charging, snap.climateOn, snap.locked)
        Box(GlanceModifier.background(sc).cornerRadius(8.dp).padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(label, maxLines = 1, style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun DetailBox(label: String, value: String) {
        Column(GlanceModifier.background(ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.12f))).cornerRadius(10.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label.uppercase(), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            Text(value, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun Pill(widgetId: Int, vin: String, action: WidgetAction, h: Dp, snap: VehicleSnapshot, theme: Theme) {
        val ctx = LocalContext.current
        val icon = when (action) {
            WidgetAction.DOORS, WidgetAction.LOCK, WidgetAction.UNLOCK -> if (snap.locked == true) R.drawable.ic_shortcut_lock else R.drawable.ic_shortcut_unlock
            WidgetAction.CLIMATE, WidgetAction.CLIMATE_ON, WidgetAction.CLIMATE_OFF -> R.drawable.ic_shortcut_climate
            WidgetAction.CHARGE -> R.drawable.ic_widget_bolt
            else -> R.drawable.ic_shortcut_car
        }
        Box(GlanceModifier.height(h).padding(end = 4.dp).background(theme.accent).cornerRadius(h / 2)
            .clickable(actionStartActivity(authIntent(ctx, widgetId, vin, action))),
            contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(provider = ImageProvider(icon), contentDescription = action.label, colorFilter = ColorFilter.tint(theme.onAccent), modifier = GlanceModifier.size(h * 0.5f))
                if (h >= 28.dp) Text(action.label.take(6), maxLines = 1, style = TextStyle(color = theme.onAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            }
        }
    }

    /** Photo or fallback location icon box. */
    @Composable
    private fun MediaBox(photoBitmap: Bitmap?, size: Dp) {
        Box(GlanceModifier.size(size).cornerRadius(12.dp).background(ColorProvider(Color(0.4f, 0.4f, 0.5f, 0.15f))),
            contentAlignment = Alignment.Center) {
            if (photoBitmap != null) {
                Image(provider = ImageProvider(photoBitmap), contentDescription = "Car photo",
                    contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp))
            } else {
                Image(provider = ImageProvider(R.drawable.ic_widget_location), contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant), modifier = GlanceModifier.size(size * 0.4f))
            }
        }
    }

    @Composable
    private fun InfoRows(snap: VehicleSnapshot, w: Dp, theme: Theme, pct: Int, compact: Boolean = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold,
                    fontSize = if (w > 180.dp) 32.sp else if (compact) 20.sp else 26.sp))
            Spacer(GlanceModifier.width(6.dp))
            Column {
                val rangeSize = if (compact) 11.sp else 13.sp
                snap.rangeMi?.let { Text("${it} mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = rangeSize)) }
                Text(if (snap.isEv) "Battery" else "Fuel", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
            }
        }
    }

    @Composable
    private fun ActionRow(actions: List<WidgetAction>, snap: VehicleSnapshot, btnH: Dp, theme: Theme, limit: Int = 4) {
        if (actions.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            actions.take(limit).forEach { a ->
                Pill(0, snap.vin, a, btnH, snap, theme)
                Spacer(GlanceModifier.height(4.dp))
            }
        }
    }

    // ── Layout tiers ───────────────────────────────────────────────────────

    @Composable
    private fun TinyXSBody(snap: VehicleSnapshot, base: GlanceModifier, theme: Theme) {
        val ctx = LocalContext.current; val sc = stateColor(snap, theme)
        Box(base.clickable(actionStartActivity(authIntent(ctx, 0, snap.vin, WidgetAction.OPEN))), contentAlignment = Alignment.Center) {
            Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp))
        }
    }

    @Composable
    private fun TinyBody(snap: VehicleSnapshot, base: GlanceModifier, theme: Theme) {
        val ctx = LocalContext.current; val sc = stateColor(snap, theme)
        Box(base.clickable(actionStartActivity(authIntent(ctx, 0, snap.vin, WidgetAction.OPEN))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Box(GlanceModifier.size(5.dp).background(sc).cornerRadius(3.dp)) {}
            }
        }
    }

    @Composable
    private fun CompactSBody(snap: VehicleSnapshot, actions: List<WidgetAction>, w: Dp, base: GlanceModifier, theme: Theme, btnH: Dp) {
        val ctx = LocalContext.current
        Row(base.padding(horizontal = 8.dp).clickable(actionStartActivity(authIntent(ctx, 0, snap.vin, WidgetAction.OPEN))),
            verticalAlignment = Alignment.CenterVertically) {
            Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1, modifier = GlanceModifier.padding(end = 6.dp),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp))
            snap.rangeMi?.let { Text("· ${it}mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp)) }
            actions.take(1).forEach { a -> Pill(0, snap.vin, a, btnH, snap, theme) }
        }
    }

    @Composable
    private fun CompactBody(snap: VehicleSnapshot, actions: List<WidgetAction>, w: Dp, base: GlanceModifier, theme: Theme, btnH: Dp) {
        val ctx = LocalContext.current; val narrow = w < 120.dp; val maxBtns = min(actions.size, if (narrow) 1 else 3)
        Row(base.padding(horizontal = 8.dp, vertical = 4.dp).clickable(actionStartActivity(authIntent(ctx, 0, snap.vin, WidgetAction.OPEN))),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.padding(end = 6.dp)) {
                if (!narrow) Text(snap.name.take(14), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = if (narrow) 14.sp else 16.sp))
                    snap.rangeMi?.let { Spacer(GlanceModifier.width(4.dp)); Text("· ${it}mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp)) }
                }
            }
            actions.take(maxBtns).forEach { a -> Pill(0, snap.vin, a, btnH, snap, theme) }
        }
    }

    @Composable
    private fun NarrowBody(snap: VehicleSnapshot, actions: List<WidgetAction>, w: Dp, h: Dp, base: GlanceModifier, theme: Theme, btnH: Dp) {
        val ctx = LocalContext.current
        Column(base.padding(10.dp).clickable(actionStartActivity(authIntent(ctx, 0, snap.vin, WidgetAction.OPEN))),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(snap.name.take(12), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 24.sp))
            snap.rangeMi?.let { Text("${it} mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp)) }
            ActionRow(actions, snap, btnH, theme, limit = 3)
        }
    }

    @Composable
    private fun SquareBody(snap: VehicleSnapshot, actions: List<WidgetAction>, base: GlanceModifier, theme: Theme, btnH: Dp, hasMedia: Boolean, photo: Bitmap?) {
        val ctx = LocalContext.current
        val pct = (snap.percent ?: 0).coerceIn(0, 100)
        Row(base.padding(12.dp).clickable(actionStartActivity(authIntent(ctx, 0, snap.vin, WidgetAction.OPEN)))) {
            Column(modifier = GlanceModifier.padding(end = 10.dp)) {
                Text(snap.name.take(14), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Text(if (snap.isEv) "Battery" else "Fuel", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp))
                Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 30.sp))
                snap.rangeMi?.let { Text("${it} mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)) }
            }
            Column {
                if (hasMedia && photo != null) MediaBox(photo, 80.dp)
                else {
                    DetailBox("Lock", when (snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" })
                    Spacer(GlanceModifier.height(4.dp))
                    DetailBox("Climate", if (snap.climateOn == true) "On" else "Off")
                }
                ActionRow(actions, snap, btnH, theme, limit = 2)
            }
        }
    }

    @Composable
    private fun FullBody(snap: VehicleSnapshot, actions: List<WidgetAction>, w: Dp, base: GlanceModifier, theme: Theme, btnH: Dp, hasMedia: Boolean, photo: Bitmap?) {
        val ctx = LocalContext.current; val sc = stateColor(snap, theme); val pct = (snap.percent ?: 0).coerceIn(0, 100)
        Row(base.padding(12.dp).clickable(actionStartActivity(authIntent(ctx, 0, snap.vin, WidgetAction.OPEN)))) {
            Column(modifier = GlanceModifier.padding(end = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(snap.name.take(16), maxLines = 1, modifier = GlanceModifier.padding(end = 6.dp),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    StateChip(snap, theme, sc)
                }
                Spacer(GlanceModifier.height(6.dp))
                InfoRows(snap, w, theme, pct)
                Spacer(GlanceModifier.height(4.dp))
                val barW = w * (pct / 100f) * 0.55f
                Box(GlanceModifier.fillMaxWidth().height(5.dp).background(ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.25f))).cornerRadius(3.dp)) {
                    if (pct > 0) Box(GlanceModifier.width(barW).height(5.dp).background(theme.accent).cornerRadius(3.dp)) {}
                }
                Spacer(GlanceModifier.height(6.dp))
                Row { DetailBox("Lock", when (snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" }); Spacer(GlanceModifier.width(6.dp)); DetailBox("Climate", if (snap.climateOn == true) "On" else "Off") }
                ActionRow(actions, snap, btnH, theme, limit = 3)
            }
            if (hasMedia && photo != null) MediaBox(photo, 80.dp)
        }
    }

    @Composable
    private fun WideBody(snap: VehicleSnapshot, actions: List<WidgetAction>, w: Dp, base: GlanceModifier, theme: Theme, btnH: Dp, hasMedia: Boolean, photo: Bitmap?) {
        val ctx = LocalContext.current; val sc = stateColor(snap, theme)
        val mediaSz = 72.dp
        Row(base.padding(10.dp).clickable(actionStartActivity(authIntent(ctx, 0, snap.vin, WidgetAction.OPEN))),
            verticalAlignment = Alignment.CenterVertically) {
            if (hasMedia && photo != null) { MediaBox(photo, mediaSz); Spacer(GlanceModifier.width(10.dp)) }
            Column(modifier = GlanceModifier.padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(snap.name.take(12), maxLines = 1, modifier = GlanceModifier.padding(end = 4.dp),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    StateChip(snap, theme, sc)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(snap.percent?.let { "$it%" } ?: "—", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 24.sp))
                    Spacer(GlanceModifier.width(6.dp))
                    snap.rangeMi?.let { Text("${it} mi", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)) }
                }
            }
            actions.take(4).forEach { a -> Pill(0, snap.vin, a, btnH, snap, theme) }
        }
    }

    @Composable
    private fun LargeBody(snap: VehicleSnapshot, actions: List<WidgetAction>, w: Dp, base: GlanceModifier, theme: Theme, btnH: Dp, hasMedia: Boolean, photo: Bitmap?) {
        val ctx = LocalContext.current; val sc = stateColor(snap, theme); val pct = (snap.percent ?: 0).coerceIn(0, 100)
        val mediaSz = if (w > 300.dp) 100.dp else 80.dp
        Column(base.padding(14.dp).clickable(actionStartActivity(authIntent(ctx, 0, snap.vin, WidgetAction.OPEN)))) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = GlanceModifier.padding(end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(snap.name.take(20), maxLines = 1, modifier = GlanceModifier.padding(end = 6.dp),
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        StateChip(snap, theme, sc)
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    InfoRows(snap, w, theme, pct)
                    Spacer(GlanceModifier.height(4.dp))
                    val trackW = w * 0.55f
                    val barW = trackW * (pct / 100f)
                    Box(GlanceModifier.width(trackW).height(6.dp).background(ColorProvider(Color(0.5f, 0.5f, 0.55f, 0.25f))).cornerRadius(3.dp)) {
                        if (pct > 0) Box(GlanceModifier.width(barW).height(6.dp).background(theme.accent).cornerRadius(3.dp)) {}
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Row { DetailBox("Lock", when (snap.locked) { true -> "Locked"; false -> "Unlocked"; else -> "—" }); Spacer(GlanceModifier.width(6.dp)); DetailBox("Climate", if (snap.climateOn == true) "On" else "Off") }
                }
                if (hasMedia && photo != null) MediaBox(photo, mediaSz)
            }
            ActionRow(actions, snap, btnH, theme, limit = 6)
        }
    }

    private fun authIntent(ctx: Context, widgetId: Int, vin: String, action: WidgetAction): android.content.Intent =
        android.content.Intent(ctx, WidgetAuthActivity::class.java).apply {
            this.action = WidgetAuthActivity.ACTION_RUN; putExtra(WidgetAuthActivity.EXTRA_WIDGET_ID, widgetId)
            putExtra(WidgetAuthActivity.EXTRA_VIN, vin); putExtra(WidgetAuthActivity.EXTRA_ACTION, action.key)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun configIntent(context: Context, widgetId: Int): android.content.Intent =
        android.content.Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
}
