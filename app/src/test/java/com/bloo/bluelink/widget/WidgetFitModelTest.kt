package com.bloo.bluelink.widget

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The horizontal half of the widget's fit model, which until now had no test.
 *
 * Everything in [WidgetScaleTest] is vertical. Width was decided inside
 * CarWidget's own composables: [Scale.overflows] and [Scale.fittedSp] were a
 * private `wouldOverflow` and `shrunkToFit` sitting beside the Glance tree,
 * where no unit test could reach them. Their KDoc claimed the two were exact
 * inverses sharing one glyph ratio and so "can never drift apart and disagree"
 * -- an assertion about the code, written in a comment, checked by nobody.
 * These are the same claims, executable.
 *
 * Separate class from [WidgetScaleTest] because it sweeps a different space:
 * that one walks tile sizes, this one walks string lengths and font sizes.
 */
class WidgetFitModelTest {

    // Every font size the widget derives, every slot width a tile can offer,
    // and lengths from one glyph to a long car name.
    private val sps = listOf(5f, 8f, 9f, 10f, 12f, 14f, 18f, 22f, 28f, 36f, 48f)
    private val widths = listOf(1, 2, 4, 8, 12, 20, 30, 44, 60, 88, 120, 180, 260, 320)
    private val lengths = listOf(1, 2, 3, 5, 6, 7, 10, 14, 17, 24, 40)

    /** The property the pair exists to guarantee: whatever [Scale.fittedSp]
     *  returns actually fits. If this fails, the widget clips text -- the one
     *  failure the whole FitText chain was built to prevent. */
    @Test
    fun shrunkTextAlwaysFits() {
        var checked = 0
        for (bold in listOf(false, true)) {
            for (relaxed in listOf(false, true)) {
                for (sp in sps) for (w in widths) for (len in lengths) {
                    assertTrue(
                        Scale.fitsAfterShrink(len, sp, bold, w.dp, relaxed),
                        "fittedSp returned a size that still overflows: len=$len sp=$sp " +
                            "bold=$bold width=${w}dp relaxed=$relaxed -> " +
                            "${Scale.fittedSp(len, sp, bold, w.dp, relaxed)}",
                    )
                    checked++
                }
            }
        }
        assertTrue(checked > 5000, "sweep collapsed to $checked cases")
    }

    /** Text that already fits is never shrunk MATERIALLY -- at worst down to
     *  [Scale.FIT_SLACK] of its size.
     *
     *  I first wrote this as "returns sp exactly" and a sweep falsified it in
     *  two ways, both of which turned out to be the model behaving correctly:
     *
     *   - A string occupying the top 4% of its slot gets nudged down anyway
     *     (len=2 at 10sp in 12dp -> 9.6sp), because the solve targets
     *     FIT_SLACK of the width rather than all of it.
     *   - A style already below [Scale.MIN_FONT_SP] returns null even though it
     *     fits (len=10 at 5sp in 30dp), because the 9sp floor is above the
     *     size asked for. Verified that this happens only for sp < MIN_FONT_SP.
     *
     *  Neither is reachable in the widget: FitLine tests [Scale.overflows]
     *  first and only calls the solve when it returns true, so the whole
     *  already-fits branch is a guard rather than a path. Asserting the
     *  stronger claim would have been asserting something untrue about code
     *  that is fine. */
    @Test
    fun textThatFitsIsNeverShrunkMaterially() {
        for (bold in listOf(false, true)) {
            for (sp in sps) for (w in widths) for (len in lengths) {
                if (Scale.overflows(len, sp, bold, w.dp)) continue
                val fitted = Scale.fittedSp(len, sp, bold, w.dp) ?: continue
                assertTrue(
                    fitted >= sp * Scale.FIT_SLACK - 1e-4f && fitted <= sp,
                    "fitting text was shrunk past FIT_SLACK: len=$len sp=$sp " +
                        "bold=$bold width=${w}dp -> $fitted",
                )
            }
        }
    }

    /** The floors are floors, and the solve never grows the type. */
    @Test
    fun fittedSizeRespectsItsFloors() {
        for (bold in listOf(false, true)) {
            for (sp in sps) for (w in widths) for (len in lengths) {
                val strict = Scale.fittedSp(len, sp, bold, w.dp)
                if (strict != null) {
                    val floor = maxOf(sp * Scale.MIN_FONT_SCALE, Scale.MIN_FONT_SP)
                    // `strict == sp` is the already-fits case returned as-is. A
                    // style declared below the floor (5sp is a real one) is
                    // legitimately under it: the floor bounds SHRINKING, not the
                    // caller's input.
                    assertTrue(
                        strict >= floor || strict == sp,
                        "shrank below the comfortable floor $floor: got $strict " +
                            "(len=$len sp=$sp bold=$bold width=${w}dp)",
                    )
                    assertTrue(strict <= sp, "grew the type: $strict > $sp")
                }
                val loose = Scale.fittedSp(len, sp, bold, w.dp, relaxed = true)
                if (loose != null) {
                    assertTrue(
                        loose >= Scale.ABSOLUTE_MIN_SP || loose == sp,
                        "relaxed shrank below ABSOLUTE_MIN_SP: $loose",
                    )
                }
            }
        }
    }

    /** Relaxed is strictly more permissive. A caller reaches for it only after
     *  the strict solve has declined, so the reverse would make that rung of
     *  FitText's chain unreachable. */
    @Test
    fun relaxedNeverRejectsWhatStrictAccepts() {
        for (bold in listOf(false, true)) {
            for (sp in sps) for (w in widths) for (len in lengths) {
                if (Scale.fittedSp(len, sp, bold, w.dp) != null) {
                    assertTrue(
                        Scale.fittedSp(len, sp, bold, w.dp, relaxed = true) != null,
                        "relaxed rejected what strict accepted: len=$len sp=$sp " +
                            "bold=$bold width=${w}dp",
                    )
                }
            }
        }
    }

    /** Bold is never estimated as narrower than regular. That is the entire
     *  reason bold carries its own ratio: the estimate should err toward "won't
     *  fit" rather than let a title clip. */
    @Test
    fun boldIsNeverEstimatedNarrowerThanRegular() {
        assertTrue(Scale.GLYPH_RATIO_BOLD > Scale.GLYPH_RATIO_REGULAR)
        for (sp in sps) for (len in lengths) {
            assertTrue(
                Scale.textWidth(len, sp, bold = true) >= Scale.textWidth(len, sp, bold = false),
                "bold measured narrower at len=$len sp=$sp",
            )
        }
        for (sp in sps) for (w in widths) for (len in lengths) {
            // Regular overflowing while bold does not would be backwards.
            if (Scale.overflows(len, sp, bold = false, maxWidth = w.dp)) {
                assertTrue(
                    Scale.overflows(len, sp, bold = true, maxWidth = w.dp),
                    "regular overflowed but bold did not: len=$len sp=$sp width=${w}dp",
                )
            }
        }
    }

    /** More text in the same slot never gets a bigger size, and a wider slot
     *  never gets a smaller one. Both are properties a reader assumes; neither
     *  was checked. */
    @Test
    fun fittedSizeIsMonotonic() {
        for (bold in listOf(false, true)) {
            for (sp in sps) for (w in widths) {
                var prev: Float? = null
                for (len in lengths) {
                    val cur = Scale.fittedSp(len, sp, bold, w.dp, relaxed = true)
                    if (prev != null && cur != null) {
                        assertTrue(
                            cur <= prev + 1e-4f,
                            "longer text got a larger size at len=$len sp=$sp " +
                                "width=${w}dp: $cur > $prev",
                        )
                    }
                    if (cur != null) prev = cur
                }
            }
            for (sp in sps) for (len in lengths) {
                var prev: Float? = null
                for (w in widths) {
                    val cur = Scale.fittedSp(len, sp, bold, w.dp, relaxed = true)
                    if (prev != null && cur != null) {
                        assertTrue(
                            cur >= prev - 1e-4f,
                            "wider slot got a smaller size at len=$len sp=$sp width=${w}dp",
                        )
                    }
                    if (cur != null) prev = cur
                }
            }
        }
    }

    /** Zero-length text has no width to fit, so it has no answer. CarWidget's
     *  shrunkToFit used an explicit isEmpty guard that now lives in Scale. */
    @Test
    fun emptyTextHasNoFittedSize() {
        assertEquals(null, Scale.fittedSp(0, 12f, bold = false, maxWidth = 100.dp))
        assertEquals(null, Scale.fittedSp(-1, 12f, bold = false, maxWidth = 100.dp))
        assertTrue(!Scale.overflows(0, 12f, bold = false, maxWidth = 0.dp))
    }
}
