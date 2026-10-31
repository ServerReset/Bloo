package com.bloo.uicommon

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where a shared component is drawn. The same component lays itself out for each: the phone's screens get the full sizes, and a round
 * watch face gets smaller controls, tighter rows and fewer lines, so one component serves both without a second copy of it.
 */
enum class SurfaceProfile { PHONE, ROUND }

/** The surface the shared components are being drawn on. The phone is the default; the watch sets [SurfaceProfile.ROUND] at its root. */
val LocalSurfaceProfile = staticCompositionLocalOf { SurfaceProfile.PHONE }
