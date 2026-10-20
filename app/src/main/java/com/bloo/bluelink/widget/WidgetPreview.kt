package com.bloo.bluelink.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.stateLineOrParked

/** The widget sizes the settings preview can show: launcher cells as the widget sees them (110dp is 2 cells). */
internal enum class PreviewSize(val label: String, val width: Dp, val height: Dp) {
    SMALL("Small", 110.dp, 110.dp),
    TALL("Tall", 110.dp, 250.dp),
    WIDE("Wide", 250.dp, 110.dp),
    LARGE("Large", 250.dp, 250.dp),
}

/**
 * The widget as it will look at [size], drawn with ordinary Compose so it changes live with every choice. It is laid out
 * from the SAME plan the real widget uses ([planWidget]), so what the preview shows at a size -- the value's size,
 * whether controls run in a row or a grid, the chips, the spacing -- is what the widget does at that size, tall and
 * narrow included.
 */
@Composable
internal fun WidgetPreview(car: VehicleSnapshot?, photoUrl: String?, choice: Choice, size: PreviewSize) {
    val scheme = MaterialTheme.colorScheme
    val us = choice.textSize.scale
    val valueText = car?.bigValue() ?: "80%"
    val actions = 1 + (if (choice.climate) 1 else 0) + (if (choice.charge && (car?.hasBattery != false)) 1 else 0) + (if (choice.flash) 1 else 0)
    val plan = remember(size, choice, valueText, actions) {
        planWidget(size.width.value, size.height.value, us, choice.compact, valueText.length, actions, choice.refresh)
    }
    val pad = widgetPad(choice.compact).dp
    val blurRadius = when (choice.blur) {
        WidgetPhoto.BLUR_LIGHT -> 6.dp
        WidgetPhoto.BLUR_STRONG -> 28.dp
        else -> 14.dp
    }
    val levelColor = if (choice.levelTint) {
        Color(BlooColors.levelColor(car?.percent ?: 80, car?.charging == true, 0xFF888888.toInt()))
    } else {
        scheme.onSurface
    }
    // A stand-in wallpaper, so the widget reads as an object on a screen.
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)))
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size.width, size.height).clip(RoundedCornerShape(choice.corner.dp.dp)).background(scheme.surfaceContainer),
        ) {
            if (choice.photo && photoUrl != null) {
                AsyncImage(
                    model = if (photoUrl.startsWith("/")) java.io.File(photoUrl) else photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().scale(1.15f).blur(blurRadius),
                )
                Box(Modifier.fillMaxSize().background(scheme.surface.copy(alpha = if (isSystemInDarkTheme()) 0.55f else 0.65f)))
            }
            Box(Modifier.fillMaxSize().padding(pad)) {
                val header: @Composable () -> Unit = { PreviewHeader(car, choice, plan.nameSp * us) }
                val value: @Composable () -> Unit = {
                    Text(valueText, fontSize = (plan.valueSp * us).sp, fontWeight = FontWeight.Bold, color = levelColor, maxLines = 1, overflow = TextOverflow.Clip)
                }
                val status: @Composable () -> Unit = {
                    if (plan.showStatus) {
                        Text(
                            car?.stateLineOrParked() ?: "Locked · 210 mi", fontSize = (plan.statusSp * us).sp,
                            color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when (plan.arrangement) {
                    WidgetArrangement.MINIMAL -> Column(Modifier.fillMaxSize()) {
                        header()
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { value() }
                            PreviewButton(Icons.Filled.Lock, plan.buttonHeightDp.dp, Modifier)
                        }
                    }
                    WidgetArrangement.SIDE -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).fillMaxSize()) {
                            header()
                            Spacer(Modifier.weight(1f))
                            value()
                            status()
                        }
                        Spacer(Modifier.width(12.dp))
                        PreviewControls(choice, car, plan, circles = true)
                    }
                    WidgetArrangement.STACK -> Column(Modifier.fillMaxSize()) {
                        header()
                        Spacer(Modifier.height((plan.spareDp / 2f).dp))
                        Spacer(Modifier.height(8.dp))
                        value()
                        status()
                        if (plan.showDetails) car?.detailLine()?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, fontSize = (plan.detailSp * us).sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (plan.showBar && choice.extras) {
                            Spacer(Modifier.height(16.dp))
                            val pct = (car?.percent ?: 80) / 100f
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(scheme.secondaryContainer)) {
                                Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(if (choice.levelTint) levelColor else scheme.primary))
                            }
                        }
                        if (plan.showChips && choice.extras) {
                            Spacer(Modifier.height(8.dp))
                            PreviewChips(car, plan.maxChips)
                        }
                        Spacer(Modifier.height((plan.spareDp / 2f).dp))
                        Spacer(Modifier.height(8.dp))
                        PreviewControls(choice, car, plan, circles = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(car: VehicleSnapshot?, choice: Choice, nameSp: Float) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.DirectionsCar, null, tint = scheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(car?.name ?: "Your car", fontWeight = FontWeight.Bold, fontSize = nameSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (choice.refresh) PreviewButton(Icons.Filled.Refresh, 32.dp, Modifier)
    }
}

@Composable
private fun PreviewButton(icon: ImageVector, diameter: Dp, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(diameter).clip(CircleShape).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size((diameter.value * 0.45f).coerceIn(14f, 26f).dp))
    }
}

/** The controls as the plan lays them out: round buttons in a grid beside the readout, or a row / grid under it. */
@Composable
private fun PreviewControls(choice: Choice, car: VehicleSnapshot?, plan: WidgetPlan, circles: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val items = buildList {
        add(Icons.Filled.Lock to "Lock")
        if (choice.climate) add(Icons.Filled.AcUnit to "Climate")
        if (choice.charge && (car?.hasBattery != false)) add(Icons.Filled.Bolt to "Charge")
        if (choice.flash) add(Icons.Filled.FlashOn to "Flash")
    }.take(plan.maxActions)
    val d = plan.buttonHeightDp.dp
    if (circles) {
        val cols = plan.gridColumns.coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            items.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { PreviewButton(it.first, d, Modifier) } }
            }
        }
        return
    }
    val perRow = if (plan.gridColumns > 0) plan.gridColumns else items.size.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, _) ->
                    Box(
                        Modifier.weight(1f).height(d).clip(RoundedCornerShape(d / 2)).background(scheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewChips(car: VehicleSnapshot?, max: Int) {
    val scheme = MaterialTheme.colorScheme
    val chips = buildList {
        add((if (car?.climateOn == true) "Climate on" else "Climate off") to (car?.climateOn == true))
        if (car?.hasBattery != false) add((if (car?.charging == true) "Charging" else "Not charging") to (car?.charging == true))
        car?.chargeLimitPct?.let { add("Limit $it%" to false) }
    }.take(max)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { (text, on) ->
            Box(
                Modifier.height(30.dp).clip(CircleShape).background(if (on) scheme.primaryContainer else scheme.secondaryContainer).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
        }
    }
}
