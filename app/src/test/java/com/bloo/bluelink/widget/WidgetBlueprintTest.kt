package com.bloo.bluelink.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The rebuilt widget's safety net: every invariant the previous widget broke
 * on a real device, checked across every size it can be dropped at and every
 * configuration it can be put in.
 *
 * This is the closest thing to "look at all 42 sizes and confirm nothing is
 * cut off" that can exist without a renderer. It cannot say a tile looks
 * GOOD -- no test can -- but it can say, exhaustively, that no tile commits
 * more vertical space than it has, that nothing is placed and left empty,
 * and that growing a widget never takes content away. Those three were the
 * entire bug history.
 */
class WidgetBlueprintTest {

    private fun sizes(): List<Triple<Int, Int, DpSize>> = buildList {
        for (c in WidgetGrid.MIN_COLS..WidgetGrid.MAX_COLS) {
            for (r in WidgetGrid.MIN_ROWS..WidgetGrid.MAX_ROWS) {
                add(Triple(c, r, WidgetGrid.nominalSize(c, r)))
            }
        }
    }

    /** The configuration axes that actually change allocation, crossed. Not
     *  every field matters here -- accent and theme are colour-only -- so this
     *  varies the ones that add or remove modules. */
    private fun configs(): List<WidgetConfig> = buildList {
        for (priority in listOf(WidgetConfig.PRIORITY_INFO, WidgetConfig.PRIORITY_CONTROLS)) {
            for (header in listOf(true, false)) {
                for (footer in listOf(true, false)) {
                    for (ring in listOf(true, false)) {
                        for (map in listOf(true, false)) {
                            for (scale in listOf(
                                WidgetConfig.MIN_TEXT_SCALE, 1f, WidgetConfig.MAX_TEXT_SCALE,
                            )) {
                                add(
                                    WidgetConfig(
                                        vin = "test",
                                        priority = priority,
                                        showHeader = header,
                                        showFooter = footer,
                                        showRing = ring,
                                        showMap = map,
                                        textScale = scale,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun facts(): List<WidgetBlueprint.Facts> = listOf(
        WidgetBlueprint.Facts(),
        WidgetBlueprint.Facts(hasPercent = false),
        WidgetBlueprint.Facts(hasCoords = false, hasMapBitmap = false),
        WidgetBlueprint.Facts(actionCount = 0, infoFieldCount = 0),
        WidgetBlueprint.Facts(actionCount = 6, infoFieldCount = 8, multipleCars = true),
        WidgetBlueprint.Facts(actionCount = 1, infoFieldCount = 1),
    )

    @Test
    fun `no blueprint ever commits more height than the tile has`() {
        // THE invariant. RemoteViews does not clip, so a layout that commits
        // more than it has does not look wrong -- the excess renders outside
        // the tile and simply is not painted, which reads as missing content.
        // Every overflow bug this widget shipped was this, and the allocator
        // is built so it cannot happen; this proves it over the whole space.
        val violations = mutableListOf<String>()
        for ((c, r, size) in sizes()) {
            for (config in configs()) {
                for (f in facts()) {
                    val bp = WidgetBlueprint.plan(size, config, f)
                    if (bp.committedHeight.value > bp.innerHeight.value + 0.01f) {
                        violations += "${c}x$r committed ${bp.committedHeight.value} " +
                            "of ${bp.innerHeight.value} (priority=${config.priority}, " +
                            "scale=${config.textScale}, bands=${bp.bands.map { it.module }})"
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "overflowing blueprints:\n" + violations.take(20).joinToString("\n"))
    }

    @Test
    fun `a hero is never placed without being drawable`() {
        // The BarHero bug in general form: a band was reserved on a generic
        // "is there room" check that was looser than what the thing drawn in
        // it actually needed, so the tile rendered a gap. A HERO band must
        // always resolve to a real style.
        val bad = mutableListOf<String>()
        for ((c, r, size) in sizes()) {
            for (config in configs()) {
                for (f in facts()) {
                    val bp = WidgetBlueprint.plan(size, config, f)
                    if (bp.has(WidgetBlueprint.Module.HERO) &&
                        config.showRing && f.hasPercent &&
                        bp.hero == WidgetBlueprint.Hero.NONE
                    ) {
                        bad += "${c}x$r reserved a hero band of " +
                            "${bp.height(WidgetBlueprint.Module.HERO).value}dp and drew nothing"
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "empty hero bands:\n" + bad.take(20).joinToString("\n"))
    }

    @Test
    fun `every size from 2x1 to 7x7 shows something about the car`() {
        // The 2x1/3x1 fallthrough in general form: no size in the declared
        // range may end up with nothing to say.
        val empty = mutableListOf<String>()
        val config = WidgetConfig(vin = "test")
        for ((c, r, size) in sizes()) {
            val bp = WidgetBlueprint.plan(size, config, WidgetBlueprint.Facts())
            val saysSomething = bp.hero != WidgetBlueprint.Hero.NONE ||
                bp.infoRows > 0 || bp.buttonCount > 0 ||
                bp.has(WidgetBlueprint.Module.HEADER)
            if (!saysSomething) empty += "${c}x$r"
        }
        assertTrue(empty.isEmpty(), "sizes that show nothing: $empty")
    }

    @Test
    fun `growing a widget never takes content away`() {
        // Monotonicity. A user dragging a resize handle outward must never see
        // a module disappear -- that reads as a bug even when each size is
        // individually defensible, and it is the property a per-tier design
        // cannot guarantee because neighbouring tiers were tuned separately.
        val regressions = mutableListOf<String>()
        val config = WidgetConfig(vin = "test")
        val f = WidgetBlueprint.Facts()
        for (c in WidgetGrid.MIN_COLS..WidgetGrid.MAX_COLS) {
            for (r in WidgetGrid.MIN_ROWS until WidgetGrid.MAX_ROWS) {
                val small = WidgetBlueprint.plan(WidgetGrid.nominalSize(c, r), config, f)
                val tall = WidgetBlueprint.plan(WidgetGrid.nominalSize(c, r + 1), config, f)
                // Row 1 is a strip and row 2 is a stack -- genuinely different
                // layouts, so the comparison starts once both are stacks.
                if (r < 2) continue
                val lost = small.bands.map { it.module }.toSet() - tall.bands.map { it.module }.toSet()
                if (lost.isNotEmpty()) {
                    regressions += "${c}x$r -> ${c}x${r + 1} lost $lost"
                }
            }
        }
        assertTrue(regressions.isEmpty(), "growing lost content:\n" + regressions.joinToString("\n"))
    }

    @Test
    fun `the hero never repeats a number the info stack will also show`() {
        for ((_, _, size) in sizes()) {
            val bp = WidgetBlueprint.plan(size, WidgetConfig(vin = "test"), WidgetBlueprint.Facts())
            when (bp.hero) {
                WidgetBlueprint.Hero.RING, WidgetBlueprint.Hero.BAR ->
                    assertTrue(WidgetInfoField.PERCENT in bp.suppressedInfo)
                WidgetBlueprint.Hero.LINE -> {
                    assertTrue(WidgetInfoField.PERCENT in bp.suppressedInfo)
                    assertTrue(WidgetInfoField.RANGE in bp.suppressedInfo)
                }
                WidgetBlueprint.Hero.NONE -> Unit
            }
        }
    }

    @Test
    fun `buttons are truncated to what fits, never drawn past the edge`() {
        for ((c, r, size) in sizes()) {
            for (f in facts()) {
                val bp = WidgetBlueprint.plan(size, WidgetConfig(vin = "test"), f)
                assertTrue(
                    bp.buttonCount <= f.actionCount,
                    "${c}x$r drew ${bp.buttonCount} buttons for ${f.actionCount} actions",
                )
            }
        }
    }

    @Test
    fun `the buttons drawn actually fit the room they were given`() {
        // Truncation is only honest if the surviving buttons fit. The old
        // escalation rule -- a row that could not fit them all became a stack
        // -- fixed a horizontal squeeze by overflowing vertically instead: on
        // a 300x78 tile, four actions became a ~146dp column inside a 78dp
        // widget, two buttons visible and the rest off the tile.
        val bad = mutableListOf<String>()
        for ((c, r, size) in sizes()) {
            for (f in facts()) {
                val bp = WidgetBlueprint.plan(size, WidgetConfig(vin = "test"), f)
                if (bp.buttonCount <= 0) continue
                val gap = bp.gap.value * (bp.buttonCount - 1)
                if (bp.buttonsStacked) {
                    val need = Scale.buttonHeight(size).value * bp.buttonCount + gap
                    val have = bp.height(WidgetBlueprint.Module.BUTTONS).value
                    // One forced button is allowed to be thinner than ideal --
                    // a small button is still pressable, a missing one is not --
                    // so only a genuine multi-button overflow is a failure.
                    if (bp.buttonCount > 1 && need > have + 0.01f) {
                        bad += "${c}x$r stacked ${bp.buttonCount} buttons needing ${need}dp in ${have}dp"
                    }
                } else {
                    val need = Scale.minButtonWidth(size).value * bp.buttonCount + gap
                    if (bp.buttonCount > 1 && need > bp.innerWidth.value + 0.01f) {
                        bad += "${c}x$r rowed ${bp.buttonCount} buttons needing ${need}dp " +
                            "in ${bp.innerWidth.value}dp"
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "buttons past their room:\n" + bad.take(20).joinToString("\n"))
    }

    @Test
    fun `a controls-priority tile still says something about the car`() {
        // "Small sizes: Controls" once meant a tile with four buttons and
        // nothing else -- no charge, no lock state, not even which car.
        // Reported across every controls-priority tier.
        val silent = mutableListOf<String>()
        val config = WidgetConfig(vin = "test", priority = WidgetConfig.PRIORITY_CONTROLS)
        for ((c, r, size) in sizes()) {
            if (r < 2) continue
            val bp = WidgetBlueprint.plan(size, config, WidgetBlueprint.Facts())
            val saysSomething = bp.hero != WidgetBlueprint.Hero.NONE ||
                bp.infoRows > 0 || bp.has(WidgetBlueprint.Module.HEADER)
            if (!saysSomething) silent += "${c}x$r"
        }
        assertTrue(silent.isEmpty(), "controls tiles showing only buttons: $silent")
    }

    @Test
    fun `every one-row strip from 2x1 to 7x1 is a real layout`() {
        for (c in WidgetGrid.MIN_COLS..WidgetGrid.MAX_COLS) {
            val bp = WidgetBlueprint.plan(
                WidgetGrid.nominalSize(c, 1), WidgetConfig(vin = "test"), WidgetBlueprint.Facts(),
            )
            assertTrue(
                bp.hero != WidgetBlueprint.Hero.NONE || bp.buttonCount > 0,
                "${c}x1 strip shows nothing",
            )
        }
    }

    @Test
    fun `a wider strip never shows fewer buttons than a narrower one`() {
        var previous = 0
        for (c in WidgetGrid.MIN_COLS..WidgetGrid.MAX_COLS) {
            val bp = WidgetBlueprint.plan(
                WidgetGrid.nominalSize(c, 1), WidgetConfig(vin = "test"),
                WidgetBlueprint.Facts(actionCount = 6),
            )
            assertTrue(
                bp.buttonCount >= previous,
                "${c}x1 shows ${bp.buttonCount} buttons, narrower tile showed $previous",
            )
            previous = bp.buttonCount
        }
    }
}
