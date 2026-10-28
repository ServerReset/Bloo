package com.bloo.bluelink.perf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerfStatsTest {
    private fun frame(total: Float, anim: Float = 0f, layout: Float = 0f, draw: Float = 0f) =
        FrameCost(total, 0f, anim, layout, draw, 0f, 0f, 0f, 0f, 0f)

    @Test fun framesPastTheBudgetAreMissedAndTheWorstIsKept() {
        val s = ScopeStats()
        for (ms in listOf(4f, 6f, 9f, 20f, 120f)) s.add(frame(ms), budgetMs = 8.3f)
        assertEquals(5, s.frames)
        assertEquals(3, s.missed)
        assertEquals(1, s.over32)
        assertEquals(1, s.over100)
        assertEquals(120f, s.worstMs)
        assertEquals(60, s.missedPercent())
    }

    @Test fun percentilesComeFromTheFrameTimes() {
        val s = ScopeStats()
        for (i in 1..100) s.add(frame(i.toFloat()), 16f)
        assertEquals(50f, s.percentile(0.5))
        assertEquals(95f, s.percentile(0.95))
        assertEquals(100f, s.percentile(1.0))
    }

    @Test fun anEmptyScopeSaysNothing() {
        val s = ScopeStats()
        assertEquals(0f, s.percentile(0.95))
        assertEquals(0, s.missedPercent())
        assertEquals("", s.avgPhases())
    }

    @Test fun theWorstPhaseNamesWhereTheFrameWent() {
        assertEquals("layout", frame(30f, anim = 5f, layout = 20f, draw = 4f).worstPhase())
        assertEquals("animation/composition", frame(30f, anim = 25f, layout = 3f).worstPhase())
    }

    @Test fun aScopeLabelsFramesUntilItEndsThenTheScreenDoes() {
        val c = ScopeClock()
        c.screen = "Garage"
        assertEquals("Garage", c.labelAt(0L))
        c.begin("map-open", 1_000L, nowNs = 1_000_000_000L)
        assertEquals("Garage/map-open", c.labelAt(1_500_000_000L))
        assertEquals("Garage", c.labelAt(2_100_000_000L))
        c.begin("swipe", 500L, nowNs = 3_000_000_000L)
        assertEquals("Garage/swipe", c.labelAt(3_200_000_000L))
    }

    @Test fun aStallNamesTheAppCodeThatWasRunning() {
        val stack = listOf(
            StackTraceElement("java.lang.Object", "wait", "Object.java", 1),
            StackTraceElement("com.bloo.bluelink.ui.SearchFacts", "carFacts", "SearchFacts.kt", 42),
            StackTraceElement("androidx.compose.runtime.Recomposer", "run", "Recomposer.kt", 9),
        )
        val text = StallStacks.describe(listOf(stack, stack))
        assertTrue("SearchFacts.carFacts(SearchFacts.kt:42) x2" in text, text)
        assertTrue("via SearchFacts.carFacts < Recomposer.run" in text, text)
    }

    @Test fun theDistributionPutsEachFrameInItsBand() {
        val s = ScopeStats()
        for (ms in listOf(4f, 12f, 25f, 50f, 80f, 150f)) s.add(frame(ms), budgetMs = 16.7f)
        // One frame in each of the six bands.
        assertEquals("<8.3 16%  8.3-16.7 16%  16.7-33 16%  33-67 16%  67-100 16%  >100 16%", s.distribution())
    }

    @Test fun theWorstFrameKeepsItsPhases() {
        val s = ScopeStats()
        s.add(frame(10f, layout = 2f), 16f)
        s.add(frame(90f, layout = 70f), 16f)
        s.add(frame(40f), 16f)
        assertEquals(90f, s.worstCost?.totalMs)
        assertEquals("layout", s.worstCost?.worstPhase())
    }
}
