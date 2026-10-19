package com.bloo.bluelink.widget

import kotlin.test.Test
import kotlin.test.assertTrue

class WidgetPlanTest {
    private val sizes = listOf(110f, 150f, 180f, 220f, 260f, 320f, 400f)

    @Test fun nothingIsEverCutOff_atAnySizeOrFontScale() {
        for (w in sizes) for (h in sizes) for (fs in listOf(0.85f, 1f, 1.3f)) for (compact in listOf(false, true)) {
            val plan = planWidget(w, h, fs, compact, valueChars = 4, actionCount = 4)
            val pad = if (compact) 10f else 16f
            val used = plan.heightDp(fs)
            // A value at its floor on a tiny widget is the one allowed overflow; everything else must fit.
            if (plan.valueSp > 24.5f) assertTrue(used <= h - 2 * pad + 0.5f, "w=$w h=$h fs=$fs compact=$compact used=$used plan=$plan")
        }
    }

    @Test fun theValueFillsTheRoomLeft() {
        val small = planWidget(250f, 180f, 1f, false, valueChars = 4, actionCount = 3)
        val big = planWidget(250f, 400f, 1f, false, valueChars = 4, actionCount = 3)
        assertTrue(big.valueSp > small.valueSp, "a taller widget gets a bigger value ($small vs $big)")
    }

    @Test fun aSmallSquareUsesTheMinimalForm() {
        assertTrue(planWidget(150f, 110f, 1f, false, valueChars = 4, actionCount = 3).minimal)
    }

    @Test fun aWideShortWidgetPutsTheControlsBesideTheReadout() {
        val plan = planWidget(340f, 150f, 1f, false, valueChars = 4, actionCount = 4)
        assertTrue(plan.arrangement == WidgetArrangement.SIDE, "$plan")
        assertTrue(plan.maxActions >= 2, "$plan")
    }

    @Test fun aTallWidgetWithRoomGainsABarAndChips() {
        val plan = planWidget(250f, 420f, 1f, false, valueChars = 3, actionCount = 4)
        assertTrue(plan.showBar && plan.showChips, "$plan")
    }

    @Test fun neverMoreControlsThanTheWidthHolds() {
        val plan = planWidget(150f, 250f, 1f, false, valueChars = 4, actionCount = 4)
        val perRow = if (plan.gridColumns > 0) plan.gridColumns else plan.maxActions
        assertTrue(perRow * 46f + (perRow - 1) * 8f <= 150f - 32f + 0.5f, "$plan")
    }

    @Test fun aNarrowTallWidgetWrapsItsControlsInsteadOfDroppingThem() {
        val plan = planWidget(110f, 300f, 1f, false, valueChars = 3, actionCount = 4)
        assertTrue(plan.arrangement == WidgetArrangement.STACK && plan.gridColumns == 2 && plan.maxActions == 4, "$plan")
    }

    @Test fun aSideGridNeverTakesTheReadoutsWidth() {
        for (w in sizes) for (h in sizes) for (compact in listOf(false, true)) for (actions in 2..4) {
            val plan = planWidget(w, h, 1f, compact, valueChars = 4, actionCount = actions)
            if (plan.arrangement != WidgetArrangement.SIDE) continue
            val pad = if (compact) 10f else 16f
            val innerW = w - 2 * pad - 8f
            val gridW = plan.gridColumns * plan.buttonHeightDp + (plan.gridColumns - 1) * 8f
            assertTrue(innerW - gridW - 12f >= 90f - 0.01f, "w=$w h=$h actions=$actions plan=$plan")
        }
    }

    @Test fun theHeaderCountsTheRefreshButton() {
        // A 2x2 with the refresh button budgets the 32dp button, not just a line of name.
        val withButton = planWidget(110f, 110f, 1f, false, valueChars = 3, actionCount = 3, refreshButton = true)
        val without = planWidget(110f, 110f, 1f, false, valueChars = 3, actionCount = 3, refreshButton = false)
        assertTrue(withButton.valueSp <= without.valueSp, "$withButton vs $without")
    }

    @Test fun aStackPlanAndItsSpareFitTheHeight() {
        for (w in sizes) for (h in sizes) for (compact in listOf(false, true)) {
            val plan = planWidget(w, h, 1f, compact, valueChars = 3, actionCount = 4)
            if (plan.arrangement != WidgetArrangement.STACK || plan.valueSp <= 24.5f) continue
            val pad = if (compact) 10f else 16f
            assertTrue(plan.spareDp >= 0f, "$plan")
            assertTrue(plan.heightDp(1f) + plan.spareDp <= h - 2 * pad - 8f + 0.5f, "w=$w h=$h plan=$plan")
        }
    }

    @Test fun aNarrowWidgetShowsOnlyTheChipsThatFit() {
        val narrow = planWidget(110f, 400f, 1f, false, valueChars = 3, actionCount = 4)
        val wide = planWidget(400f, 400f, 1f, false, valueChars = 3, actionCount = 4)
        assertTrue(narrow.maxChips == 1, "$narrow")
        assertTrue(wide.maxChips == 3, "$wide")
    }

    @Test fun theValueIsNeverWiderThanTheWidget() {
        // "84%" at the plan's size must fit the inner width (it was being clipped to "84...").
        val plan = planWidget(150f, 300f, 1f, false, valueChars = 3, actionCount = 4)
        val innerW = 150f - 32f - 8f
        assertTrue(plan.valueSp * 3 * 0.76f <= innerW + 0.5f, "$plan")
    }
}
