package com.bloo.bluelink.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.stateLine
import com.bloo.bluelink.data.updatedLabel
import com.bloo.bluelink.ioScope
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

/**
 * The watch's Tile: the car in the carousel, and -- the part that makes it a widget rather than a
 * card -- the actions worth doing without opening the app.
 *
 * Three things it does now that it did not:
 *
 *  - It draws the car's charge as a RING, the same mechanic as the app's hero, so the tile and the
 *    app read as one product. Protolayout has the primitive (Arc + ArcLine), so this is the platform
 *    drawing it rather than a picture.
 *  - It carries up to three action buttons. A tile's button cannot run code -- a click can only be an
 *    Action -- so each one launches the app with the action in its extras, and the app runs it
 *    through the same PIN gate as a tap on the watch face. That is deliberate: there is exactly one
 *    place where a command is authorised, and the tile is not it.
 *  - It says what it means. A sealed watch used to read "Open to set up", which is the wrong sentence
 *    for someone who has already set it up and is merely locked.
 *
 * It also has a content description, so the glance is one spoken sentence instead of four fragments.
 */
class BlooTileService : TileService() {
    private val scope = ioScope()

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                // Sealed after a restart: the tile shows nothing about the car until the PIN is entered.
                val sealed = WearBootGate.isLocked(applicationContext)
                val snapshot = if (sealed) null
                else runCatching { SnapshotStore(applicationContext).current() }.getOrNull()
                val car = snapshot?.selected
                // Whatever goes wrong while drawing, the request is answered: an unset completer leaves the tile
                // spinning for good, so a failure draws the plain "open Bloo" layout instead.
                val layout = runCatching { layoutFor(car, sealed) }.getOrElse { layoutFor(null, sealed) }
                val freshness = runCatching {
                    60_000L * com.bloo.bluelink.data.DeviceStatus.policy(applicationContext).pollIntervalMultiplier
                }.getOrDefault(60_000L)
                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    // Refresh about once a minute while it is on screen.
                    .setFreshnessIntervalMillis(freshness)
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                    .build()
                completer.set(tile)
            }
            "BlooTile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "BlooTileResources"
        }

    private fun layoutFor(car: VehicleSnapshot?, sealed: Boolean = false): LayoutElementBuilders.LayoutElement {
        // The whole tile opens the app. The buttons below are separate clickables inside this one, and
        // the system routes the innermost hit, so a tap on "Charge" charges and a tap anywhere else
        // opens Bloo.
        val open = ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId("open")
                    .setOnClick(launchAction(null))
                    .build(),
            )
            .build()

        val glance = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        if (car == null) {
            // A sealed watch has been set up; it is only waiting for its PIN. Saying "add a car" read as not set up.
            val line = if (sealed) "Enter your PIN in Bloo" else "Add a car in Bloo on your phone"
            glance.addContent(text("Bloo", 22f, WHITE, bold = true))
            glance.addContent(text(line, 13f, MUTED))
            glance.setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription(if (sealed) "Bloo. Enter your PIN in Bloo." else "Bloo. Add a car in Bloo on your phone.")
                            .build(),
                    )
                    .build(),
            )
        } else {
            glance.addContent(text(car.name, 12f, MUTED))
            glance.addContent(text(car.bigValue(), 34f, tint(car), bold = true))
            car.stateLine()?.let { line ->
                glance.addContent(text(line, 13f, MUTED))
            }
            car.updatedLabel()?.let { updated ->
                glance.addContent(text("Updated $updated", 11f, if (car.isStale()) STALE else MUTED))
            }
            // One spoken sentence for the whole glance, rather than the four fragments a reader would
            // otherwise get from four separate text elements.
            glance.setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription(
                                buildString {
                                    append(car.name).append(", ").append(car.bigValue())
                                    car.stateLine()?.let { append(", ").append(it) }
                                    car.updatedLabel()?.let { append(", updated ").append(it) }
                                },
                            )
                            .build(),
                    )
                    .build(),
            )
        }

        val outer = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(glance.build())

        if (car != null) {
            val actions = WearTileActions.forCar(car, WearFacePrefs.load(applicationContext))
            if (actions.isNotEmpty()) {
                outer.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(10f)).build())
                outer.addContent(actionRow(actions))
            }
        }

        val tile = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(open)
        // The charge runs round the very edge of the display, the same ring the watch face has.
        if (car?.percent != null) tile.addContent(edgeRing(car))
        return tile.addContent(outer.build()).build()
    }

    /** The ring's colour from the level, shared with the watch face ([levelColorArgb]). */
    private fun tint(car: VehicleSnapshot): Int =
        levelColorArgb(car.percent, car.charging == true, WHITE)

    /**
     * The car's charge as a ring round the display's edge -- the app's hero, at tile scale.
     *
     * Two arc lines rather than one: a full-circle track under a partial sweep, both anchored at the top and
     * running clockwise, so the drawn part reads as "how full" against a visible remainder. A car with no
     * battery gets no ring, because a ring with nothing to say is a decoration.
     */
    private fun edgeRing(car: VehicleSnapshot): LayoutElementBuilders.LayoutElement {
        val color = tint(car)
        val pct = (car.percent ?: 0).coerceIn(0, 100)
        return LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .setArcDirection(LayoutElementBuilders.ARC_DIRECTION_CLOCKWISE)
            .addContent(arcLine(360f, TRACK))
            .addContent(arcLine(360f * (pct / 100f), color))
            .build()
    }

    private fun arcLine(sweepDegrees: Float, color: Int): LayoutElementBuilders.ArcLine =
        LayoutElementBuilders.ArcLine.Builder()
            .setLength(degrees(sweepDegrees))
            .setThickness(dp(8f))
            .setColor(argb(color))
            .setStrokeCap(LayoutElementBuilders.STROKE_CAP_ROUND)
            .build()

    /**
     * The actions worth a tap from the carousel, in the order the wearer chose them: one row for up to three,
     * two rows of two for four, so no label is squeezed.
     */
    private fun actionRow(actions: List<WearTileAction>): LayoutElementBuilders.LayoutElement {
        fun row(chunk: List<WearTileAction>, height: Float): LayoutElementBuilders.Row {
            val row = LayoutElementBuilders.Row.Builder()
                .setWidth(expand())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            chunk.forEachIndexed { index, action ->
                if (index > 0) row.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build())
                row.addContent(actionButton(action, height))
            }
            return row.build()
        }
        if (actions.size <= 3) return row(actions, 44f)
        val column = LayoutElementBuilders.Column.Builder().setWidth(expand())
        actions.chunked(2).forEachIndexed { i, chunk ->
            if (i > 0) column.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            column.addContent(row(chunk, 38f))
        }
        return column.build()
    }

    /**
     * One action, sized by `expand()` so however many there are they share the tile's width evenly.
     * A click can only be an [ActionBuilders.Action], so this launches the app with the key in its
     * extras -- the app owns the PIN gate and the command layer, and duplicating either here would
     * give the watch two places where a command can be authorised.
     */
    private fun actionButton(action: WearTileAction, heightDp: Float): LayoutElementBuilders.Box =
        LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(dp(heightDp))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(if (action.active) GREEN else BUTTON))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(heightDp / 2f)).build())
                            .build(),
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("act_${action.key}")
                            .setOnClick(launchAction(action.key))
                            .build(),
                    )
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription(action.label)
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                text(
                    action.label,
                    12f,
                    if (action.active) 0xFF101014.toInt() else WHITE,
                    bold = true,
                ),
            )
            .build()

    private fun launchAction(actionKey: String?): ActionBuilders.Action {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(WearMainActivity::class.java.name)
        if (actionKey != null) {
            activity.addKeyToExtraMapping(
                WearMainActivity.EXTRA_ACTION,
                ActionBuilders.AndroidStringExtra.Builder().setValue(actionKey).build(),
            )
        }
        return ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .apply { if (bold) setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD) }
                    .build(),
            )
            .setMaxLines(1)
            .build()

    private companion object {
        const val RESOURCES_VERSION = "2"
        const val WHITE = 0xFFFFFFFF.toInt()
        const val MUTED = 0xFFB8B8C4.toInt()
        const val TRACK = 0x33FFFFFF
        const val BUTTON = 0xFF2A2A33.toInt()
        const val STALE = 0xFFFF6B6B.toInt()
        val GREEN = BlooColors.chargeGreen
    }
}
