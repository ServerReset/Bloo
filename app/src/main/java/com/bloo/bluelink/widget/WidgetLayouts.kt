package com.bloo.bluelink.widget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
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
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.glance.action.clickable

/**
 * Alternative widget layout implementations.
 *
 * Each layout trades information density for specific use cases:
 * - ADAPTIVE: Smart tier system (current, default)
 * - COMPACT: Icon + stat for 2x2 widgets
 * - STRIP: Horizontal layout for wide/short widgets
 * - MINIMAL: Single-line ultra-compact layout
 */

/**
 * Compact layout: Icon + key stat (battery/range) with one action button.
 * Best for 2x2 or smaller widgets.
 *
 * Layout:
 * ┌──────────┐
 * │ 🔋 75%   │ (car icon + battery %)
 * │          │
 * │ [LOCK]   │ (primary action button)
 * └──────────┘
 */
@Composable
internal fun CompactLayout(
    render: Render,
    modifier: GlanceModifier = GlanceModifier
) {
    val car = render.car ?: return
    val batteryPercent = car.evStatus?.soc?.toInt() ?: 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = androidx.glance.layout.Alignment.CenterHorizontally,
    ) {
        // Car icon/badge + battery stat
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = androidx.glance.layout.Alignment.CenterVertically,
        ) {
            Text(
                "🔋",
                style = TextStyle(fontSize = 20.sp)
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                "$batteryPercent%",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(render.theme.onSurface)
                )
            )
        }

        Spacer(GlanceModifier.height(8.dp))

        // Primary action button
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(40.dp)
                .background(ColorProvider(Color(render.accentColor)))
                .cornerRadius(8.dp)
                .clickable { /* Action */ },
            contentAlignment = androidx.glance.layout.Alignment.Center,
        ) {
            Text(
                "Lock",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color.White)
                )
            )
        }
    }
}

/**
 * Strip layout: Horizontal name + status line, then button row.
 * Best for wide/short widgets (3x1, 4x1).
 *
 * Layout:
 * ┌───────────────────────────┐
 * │ Lanas Whip │ Locked • 75% │
 * │                           │
 * │ [🔒] [❄️] [⚡] [🔔]     │
 * └───────────────────────────┘
 */
@Composable
internal fun StripLayout(
    render: Render,
    modifier: GlanceModifier = GlanceModifier
) {
    val car = render.car ?: return
    val locked = car.status?.doorLocked == true
    val batteryPercent = car.evStatus?.soc?.toInt() ?: 0

    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
    ) {
        // Name and status line
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = androidx.glance.layout.Arrangement.SpaceBetween,
            verticalAlignment = androidx.glance.layout.Alignment.CenterVertically,
        ) {
            Text(
                car.name,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(render.theme.onSurface)
                )
            )
            Text(
                "${if (locked) "Locked" else "Unlocked"} • $batteryPercent%",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(render.theme.onSurfaceVariant)
                )
            )
        }

        Spacer(GlanceModifier.height(6.dp))

        // Action buttons row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalArrangement = androidx.glance.layout.Arrangement.spacedBy(6.dp),
        ) {
            // Lock/Unlock icon button
            ActionButton("🔒", 1f)
            ActionButton("❄️", 1f)
            ActionButton("⚡", 1f)
            ActionButton("🔔", 1f)
        }
    }
}

/**
 * Minimal layout: Single line - name | battery% | one button.
 * Ultra-compact for 2x1 or narrow widgets.
 *
 * Layout:
 * ┌──────────────────────────┐
 * │ Lanas Whip 75% │ 🔒     │
 * └──────────────────────────┘
 */
@Composable
internal fun MinimalLayout(
    render: Render,
    modifier: GlanceModifier = GlanceModifier
) {
    val car = render.car ?: return
    val batteryPercent = car.evStatus?.soc?.toInt() ?: 0

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = androidx.glance.layout.Arrangement.SpaceBetween,
        verticalAlignment = androidx.glance.layout.Alignment.CenterVertically,
    ) {
        // Name and stat
        Text(
            "${car.name} $batteryPercent%",
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorProvider(render.theme.onSurface)
            ),
            modifier = GlanceModifier.defaultWeight()
        )

        // Single action button
        Box(
            modifier = GlanceModifier
                .width(32.dp)
                .height(32.dp)
                .background(ColorProvider(Color(render.accentColor)))
                .cornerRadius(6.dp)
                .clickable { /* Action */ },
            contentAlignment = androidx.glance.layout.Alignment.Center,
        ) {
            Text("🔒", style = TextStyle(fontSize = 16.sp))
        }
    }
}

/**
 * Simple action button for use in layouts.
 */
@Composable
private fun ActionButton(label: String, weight: Float) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .height(32.dp)
            .background(ColorProvider(Color(0xffe0e0e0)))
            .cornerRadius(6.dp)
            .clickable { /* Action */ },
        contentAlignment = androidx.glance.layout.Alignment.Center,
    ) {
        Text(label, style = TextStyle(fontSize = 14.sp))
    }
}
