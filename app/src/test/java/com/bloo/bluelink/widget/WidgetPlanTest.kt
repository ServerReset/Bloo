package com.bloo.bluelink.widget

import kotlin.test.Test
import kotlin.test.assertTrue

class WidgetPlanTest {
    private val sizes = listOf(110f, 150f, 180f, 220f, 260f, 320f, 400f)

    @Test fun nothingIsEverCutOff_atAnySizeOrFontScale() {
        for (w in sizes) for (h in sizes) for (fs in listOf(0.85f, 1f, 1.3f)) for (compact in listOf(false, true)) {
            val plan = planWidget(w, h, fs, compact, valueChars = 4, actionCount = 4)
            val pad = if (compact) 10f else 16f
            val used = plan.heightDp(fs, compact, w)
            // A value at its floor on a tiny widget is the one allowed overflow; everything else must fit.
            if (plan.valueSp > 24.5f) assertTrue(used <= h - 2 * pad + 0.5f, "w=$w h=$h fs=$fs compact=$compact used=$used plan=$plan")
        }
    }

    @Test fun theValueFillsTheRoomLeft() {
        val small = planWidget(250f, 180f, 1f, false, valueChars = 4, actionCount = 3)
        val big = planWidget(250f, 400f, 1f, false, valueChars = 4, actionCount = 3)
        assertTrue(big.valueSp > small.valueSp, "a taller widget gets a bigger value ($small vs $big)")
    }

    @Test fun aShortWidgetUsesTheCompactForm() {
        assertTrue(planWidget(180f, 110f, 1f, false, valueChars = 4, actionCount = 3).minimal)
    }

    @Test fun neverMoreControlsThanTheWidthHolds() {
        val plan = planWidget(150f, 250f, 1f, false, valueChars = 4, actionCount = 4)
        assertTrue(plan.maxActions * 46f + (plan.maxActions - 1) * 8f <= 150f - 32f + 0.5f, "$plan")
    }
}
