package com.bloo.bluelink.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.ContainerDimension
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders

/*
 * The tile's building blocks. A tile cannot use Compose, and protolayout's builders are verbose enough that a layout written
 * straight in them is mostly noise; these few helpers are what [BlooTileService] is written in, so its layout reads as
 * the tree it is. Sizes are dp (and sp for text), colours are ARGB ints.
 */

/** A single line of text. */
internal fun tileText(value: String, size: Float, color: Int, bold: Boolean = false): LayoutElement =
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

/** A tinted image from the tile's resources ([resourceId] is the name the resources were registered under). */
internal fun tileImage(resourceId: String, size: Float, tint: Int): LayoutElement =
    LayoutElementBuilders.Image.Builder()
        .setResourceId(resourceId)
        .setWidth(dp(size))
        .setHeight(dp(size))
        .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(argb(tint)).build())
        .build()

/** Empty space of [width] by [height] dp (either may be 0). */
internal fun tileSpace(width: Float = 0f, height: Float = 0f): LayoutElement =
    LayoutElementBuilders.Spacer.Builder().apply {
        if (width > 0f) setWidth(dp(width))
        if (height > 0f) setHeight(dp(height))
    }.build()

/** Children stacked on top of each other, centred (a hub over a grid over a ring). */
internal fun tileBox(
    vararg children: LayoutElement,
    width: ContainerDimension = expand(),
    height: ContainerDimension = expand(),
    modifiers: ModifiersBuilders.Modifiers? = null,
): LayoutElement =
    LayoutElementBuilders.Box.Builder()
        .setWidth(width)
        .setHeight(height)
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .apply { modifiers?.let { setModifiers(it) }; children.forEach { addContent(it) } }
        .build()

/** Children top to bottom, centred. */
internal fun tileColumn(
    vararg children: LayoutElement,
    width: ContainerDimension = wrap(),
    height: ContainerDimension = wrap(),
    modifiers: ModifiersBuilders.Modifiers? = null,
): LayoutElement =
    LayoutElementBuilders.Column.Builder()
        .setWidth(width)
        .setHeight(height)
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .apply { modifiers?.let { setModifiers(it) }; children.forEach { addContent(it) } }
        .build()

/** Children left to right. */
internal fun tileRow(
    vararg children: LayoutElement,
    width: ContainerDimension = expand(),
    height: ContainerDimension = expand(),
): LayoutElement =
    LayoutElementBuilders.Row.Builder()
        .setWidth(width)
        .setHeight(height)
        .apply { children.forEach { addContent(it) } }
        .build()

/** Per-corner radii: each of [topLeft], [topRight], [bottomRight], [bottomLeft] in dp. */
internal fun tileCorners(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float): ModifiersBuilders.Corner {
    fun r(v: Float) = ModifiersBuilders.CornerRadius.Builder(dp(v), dp(v)).build()
    return ModifiersBuilders.Corner.Builder()
        .setTopLeftRadius(r(topLeft)).setTopRightRadius(r(topRight))
        .setBottomRightRadius(r(bottomRight)).setBottomLeftRadius(r(bottomLeft))
        .build()
}

/** A single radius on every corner, in dp. */
internal fun tileCorner(radius: Float): ModifiersBuilders.Corner =
    ModifiersBuilders.Corner.Builder().setRadius(dp(radius)).build()

/** Background, rim, tap, spoken description and padding for one element; each is optional. */
internal fun tileModifiers(
    background: Int? = null,
    corner: ModifiersBuilders.Corner? = null,
    border: Int? = null,
    clickableId: String? = null,
    onClick: ActionBuilders.Action? = null,
    description: String? = null,
    padding: Float? = null,
): ModifiersBuilders.Modifiers =
    ModifiersBuilders.Modifiers.Builder().apply {
        if (background != null) {
            setBackground(
                ModifiersBuilders.Background.Builder().setColor(argb(background)).apply { corner?.let { setCorner(it) } }.build(),
            )
        }
        if (border != null) setBorder(ModifiersBuilders.Border.Builder().setWidth(dp(1f)).setColor(argb(border)).build())
        if (clickableId != null && onClick != null) {
            setClickable(ModifiersBuilders.Clickable.Builder().setId(clickableId).setOnClick(onClick).build())
        }
        if (description != null) {
            setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(description).build())
        }
        if (padding != null) setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(padding)).build())
    }.build()
