package com.bloo.bluelink.ui

/**
 * Where the search bubble lives on the normal (phone) layout. Pick it up and fling it: a bottom
 * corner keeps it a bubble that only opens out into the search pill on the Settings page, the
 * bottom middle keeps it open as the pill everywhere.
 */
internal enum class SearchDock {
    LEFT, CENTER, RIGHT;

    /** The persisted horizontal fraction: 0 = left corner, 0.5 = middle, 1 = right corner. */
    val xFrac: Float get() = when (this) { LEFT -> 0f; CENTER -> 0.5f; RIGHT -> 1f }

    companion object {
        fun fromFrac(x: Float): SearchDock = when {
            x < 0.34f -> LEFT
            x > 0.66f -> RIGHT
            else -> CENTER
        }

        /**
         * Where a drag that ended with its centre at [centerFrac] of the width, moving at [lastDx]
         * (dp per event), should dock. A decisive fling goes the way it was thrown; a gentle drop
         * goes where it was dropped, so letting go in the middle third centres it.
         */
        fun landing(centerFrac: Float, lastDx: Float, current: SearchDock): SearchDock = when {
            lastDx > FLING_DP -> if (current == LEFT) CENTER.takeIf { centerFrac in 0.34f..0.66f } ?: RIGHT else RIGHT
            lastDx < -FLING_DP -> if (current == RIGHT) CENTER.takeIf { centerFrac in 0.34f..0.66f } ?: LEFT else LEFT
            else -> fromFrac(centerFrac)
        }

        private const val FLING_DP = 7f
    }
}
