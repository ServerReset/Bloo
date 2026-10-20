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
                // The hub is the refresh button: a press reloads the tile with this id, and a refresh is asked for as it does.
                if (!sealed && requestParams.currentState.lastClickableId == HUB_ID) {
                    runCatching { WearSnapshotRepository(applicationContext).refresh() }
                }
                val car = snapshot?.let { WearTileCar.pick(applicationContext, it.vehicles) ?: it.selected }
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
            val resources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
            // The face's glyphs, as drawables the tile can draw (a tile cannot use the app's Compose icons).
            GLYPHS.forEach { (name, res) ->
                resources.addIdToImageMapping(
                    name,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(res).build())
                        .build(),
                )
            }
            completer.set(resources.build())
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

        if (car != null) return faceLayout(car, open)

        // No car: say why. A sealed watch has been set up and is only waiting for its PIN; "add a car" read as not set up.
        val line = if (sealed) "Enter your PIN in Bloo" else "Add a car in Bloo on your phone"
        val glance = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("Bloo", 22f, WHITE, bold = true))
            .addContent(text(line, 13f, MUTED))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription(if (sealed) "Bloo. Enter your PIN in Bloo." else "Bloo. Add a car in Bloo on your phone.")
                            .build(),
                    )
                    .build(),
            )
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(open)
            .addContent(glance)
            .build()
    }

    /**
     * The tile as the face in the app: the charge ring round the edge, the four chosen controls as quarters inside it,
     * and the value in a hub where they meet. Pressing a quarter runs that control (through the app, so the PIN gate
     * applies); pressing the hub refreshes. Each quarter is a disc quarter, from per-corner radii.
     */
    private fun faceLayout(car: VehicleSnapshot, open: ModifiersBuilders.Modifiers): LayoutElementBuilders.LayoutElement {
        // The presets a quarter may start live in prefs the app fills; the tile can run without the app having opened.
        WearPresets.ensureLoaded(applicationContext)
        val color = tint(car)
        val stale = if (car.isStale()) car.updatedLabel() else null
        val slots = WearTileActions.forSlots(car, WearFacePrefs.load(applicationContext))
        // Quarter discs, as on the face: the OUTER corner (the one on the display's edge) rounded by the tile's whole width,
        // the inner corner (at the hub) a soft curve, the other two nearly square. [index]: 0 top-left, 1 top-right,
        // 2 bottom-left, 3 bottom-right.
        fun quarterCorner(index: Int): ModifiersBuilders.Corner {
            fun r(v: Float) = ModifiersBuilders.CornerRadius.Builder(dp(v), dp(v)).build()
            val outer = r(92f)
            val inner = r(16f)
            val flat = r(4f)
            val b = ModifiersBuilders.Corner.Builder()
            when (index) {
                0 -> b.setTopLeftRadius(outer).setTopRightRadius(flat).setBottomRightRadius(inner).setBottomLeftRadius(flat)
                1 -> b.setTopLeftRadius(flat).setTopRightRadius(outer).setBottomRightRadius(flat).setBottomLeftRadius(inner)
                2 -> b.setTopLeftRadius(flat).setTopRightRadius(inner).setBottomRightRadius(flat).setBottomLeftRadius(outer)
                else -> b.setTopLeftRadius(inner).setTopRightRadius(flat).setBottomRightRadius(outer).setBottomLeftRadius(flat)
            }
            return b.build()
        }
        fun quarter(index: Int, a: WearTileAction?): LayoutElementBuilders.LayoutElement {
            val box = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            val mods = ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(argb(if (a?.active == true) ACTIVE else if (a != null) BUTTON else QUIET))
                        .setCorner(quarterCorner(index))
                        .build(),
                )
                // The face's glass rim.
                .setBorder(ModifiersBuilders.Border.Builder().setWidth(dp(1f)).setColor(argb(RIM)).build())
            if (a != null) {
                mods.setClickable(
                    ModifiersBuilders.Clickable.Builder().setId("act_${a.key}").setOnClick(launchAction(a.key)).build(),
                ).setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(a.label).build())
                // The glyph over its word, as on the face (where the word runs along the outer edge): white on the lit fill.
                val col = LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                if (a.glyph in GLYPHS) {
                    col.addContent(
                        LayoutElementBuilders.Image.Builder()
                            .setResourceId(a.glyph)
                            .setWidth(dp(26f))
                            .setHeight(dp(26f))
                            .setColorFilter(
                                LayoutElementBuilders.ColorFilter.Builder().setTint(argb(WHITE)).build(),
                            )
                            .build(),
                    )
                    col.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).build())
                }
                col.addContent(text(a.label, 11f, WHITE, bold = true))
                box.addContent(col.build())
            }
            return box.setModifiers(mods.build()).build()
        }
        fun row(left: LayoutElementBuilders.LayoutElement, right: LayoutElementBuilders.LayoutElement) =
            LayoutElementBuilders.Row.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .addContent(left)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build())
                .addContent(right)
                .build()
        val grid = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .addContent(row(quarter(0, slots[0]), quarter(1, slots[1])))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(row(quarter(2, slots[2]), quarter(3, slots[3])))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(13f)).build())
                    .build(),
            )
            .build()
        val hub = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(72f))
            .setHeight(dp(72f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF000000.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(36f)).build())
                            .build(),
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(HUB_ID)
                            .setOnClick(ActionBuilders.LoadAction.Builder().build())
                            .build(),
                    )
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription("Refresh. ${car.name}, ${car.bigValue()}" + (car.updatedLabel()?.let { ", updated $it" } ?: ""))
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(text(car.bigValue(), 24f, if (car.percent != null) color else WHITE, bold = true))
                    // An old reading says so (in red, as the face does) instead of passing as current; otherwise the name.
                    .addContent(
                        if (stale != null) text(stale, 10f, STALE, bold = true) else text(car.name, 12f, WHITE, bold = true),
                    )
                    .build(),
            )
            .build()
        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(open)
        if (car.percent != null) root.addContent(edgeRing(car))
        return root.addContent(grid).addContent(hub).build()
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
            .addContent(arcLine(360f, (color and 0x00FFFFFF) or 0x40000000))
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
        const val RESOURCES_VERSION = "3"
        val GLYPHS = mapOf(
            "lock" to R.drawable.tile_lock, "unlock" to R.drawable.tile_unlock, "climate" to R.drawable.tile_climate,
            "bolt" to R.drawable.tile_bolt, "flash" to R.drawable.tile_flash, "refresh" to R.drawable.tile_refresh,
            "close" to R.drawable.tile_close, "horn" to R.drawable.tile_horn,
        )
        const val WHITE = 0xFFFFFFFF.toInt()
        const val MUTED = 0xFFB8B8C4.toInt()
        const val RIM = 0x33FFFFFF
        const val BUTTON = 0xFF2A2A33.toInt()
        const val STALE = 0xFFFF6B6B.toInt()
        const val HUB_ID = "refresh"
        const val QUIET = 0xFF1A1A20.toInt()
        val ACTIVE = BlooColors.brandAccent
        val GREEN = BlooColors.chargeGreen
    }
}
