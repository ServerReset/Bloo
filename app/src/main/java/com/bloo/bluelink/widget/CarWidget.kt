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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.R
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.ui.ThemeMode
import com.bloo.bluelink.ui.resolveWidgetAccent
import com.bloo.bluelink.ui.resolveWidgetIsDark
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Simple, responsive car widget that shows:
 * - Vehicle location map (if available)
 * - Car name and basic status
 * - Weather at car's location
 * - Action buttons (Expand map, Open in Maps)
 *
 * Redesigned from scratch to be maintainable and actually work on all devices.
 * No complex blueprint system or tier logic -- just straightforward responsive
 * layout with sensible defaults at any size.
 */
class CarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        AppLog.log("Widget: provideGlance started")
        
        runCatching { WidgetRefreshWorker.schedule(context) }
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        
        try {
            val settings = SettingsStore(context)
            val snapshots = SnapshotStore(context)
            
            val snapshot = snapshots.current()
            val config = settings.widgetConfig()
            
            val car = if (config.vin != null) {
                snapshot.vehicles.firstOrNull { it.vin == config.vin }
            } else {
                snapshot.selected
            }
            
            val themeMode = settings.themeMode().first()
            val isDark = resolveWidgetIsDark(themeMode)
            val accentColor = if (car != null) {
                resolveWidgetAccent(car.palette, themeMode).toArgb()
            } else {
                BlooColors.primary.toArgb()
            }
            
            val mapBitmap = if (config.showMap && car?.location != null) {
                WidgetMap.fetch(context, car.location, isDark)
            } else {
                null
            }
            
            val weather = snapshot.weather[car?.vin]
            
            AppLog.log("Widget: provideContent")
            provideContent {
                val liveWeather by snapshots.payload
                    .map { live ->
                        live.weather[car?.vin]
                    }
                    .distinctUntilChanged()
                    .collectAsState(initial = weather)
                
                GlanceTheme {
                    WidgetContent(
                        carName = car?.name ?: "No vehicle",
                        location = car?.location,
                        weather = liveWeather,
                        mapBitmap = mapBitmap,
                        isDark = isDark,
                        accentColor = Color(accentColor),
                        onClick = { openAction(context, car?.vin ?: "") }
                    )
                }
            }
        } catch (t: Throwable) {
            renderError(context, appWidgetId, t)
        }
    }

    private fun renderError(context: Context, appWidgetId: Int, throwable: Throwable) {
        AppLog.log("Widget crashed: ${throwable::class.simpleName}: ${throwable.message}")
        val trace = android.util.Log.getStackTraceString(throwable)
        val views = RemoteViews(context.packageName, R.layout.car_widget_error)
        views.setOnClickPendingIntent(
            R.id.widget_error_root,
            PendingIntent.getActivity(
                context, appWidgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        val shown = trace.take(4000)
        views.setTextViewText(R.id.widget_error_text, shown)
        views.setViewVisibility(R.id.widget_error_text, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.widget_error_copy, android.view.View.VISIBLE)
        views.setOnClickPendingIntent(
            R.id.widget_error_copy,
            PendingIntent.getBroadcast(
                context, appWidgetId,
                Intent(context, WidgetErrorCopyReceiver::class.java)
                    .putExtra(WidgetErrorCopyReceiver.EXTRA_TEXT, shown),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }
}

@Composable
private fun WidgetContent(
    carName: String,
    location: com.bloo.bluelink.data.GeoLocation?,
    weather: com.bloo.bluelink.data.Weather?,
    mapBitmap: android.graphics.Bitmap?,
    isDark: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(if (isDark) ColorProvider(Color(0x1a1a1a)) else ColorProvider(Color(0xfafafa)))
            .cornerRadius(16.dp)
            .clickable(onClick)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Map (if available)
            if (mapBitmap != null) {
                Image(
                    provider = ImageProvider(mapBitmap),
                    contentDescription = "Car location",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .cornerRadius(12.dp)
                )
                Spacer(GlanceModifier.height(8.dp))
            }
            
            // Car name
            Text(
                carName,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(if (isDark) Color.White else Color.Black),
                )
            )
            
            // Weather (if available)
            if (weather != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    "${weather.temp}° • ${weather.condition}",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = ColorProvider(if (isDark) Color(0xb3ffffff) else Color(0x80000000)),
                    )
                )
            }
            
            // Action buttons
            if (location != null) {
                Spacer(GlanceModifier.height(8.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Expand button
                    Box(
                        modifier = GlanceModifier
                            .width(40.dp)
                            .height(40.dp)
                            .background(ColorProvider(accentColor))
                            .cornerRadius(8.dp)
                            .clickable { /* Expand map */ },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⤢", style = TextStyle(fontSize = 18.sp, color = ColorProvider(Color.White)))
                    }
                    
                    Spacer(GlanceModifier.width(8.dp))
                    
                    // Open in Maps button
                    Box(
                        modifier = GlanceModifier
                            .width(40.dp)
                            .height(40.dp)
                            .background(ColorProvider(accentColor))
                            .cornerRadius(8.dp)
                            .clickable {
                                val mapsIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse(
                                        "geo:${location.lat},${location.lon}?q=${location.lat},${location.lon}"
                                    )
                                }
                                context.startActivity(mapsIntent)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🗺", style = TextStyle(fontSize = 18.sp))
                    }
                }
            }
        }
    }
}

private fun openAction(context: Context, vin: String): androidx.glance.action.Action {
    return androidx.glance.action.actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            putExtra("vin", vin)
        }
    )
}
