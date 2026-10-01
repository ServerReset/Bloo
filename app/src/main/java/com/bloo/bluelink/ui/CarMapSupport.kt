@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Zoom bounds for [CarMap]'s zoom, pinch or button-driven -- 3 is "half the
 *  continent", 19 is past what OSM actually serves tiles for. */
internal const val CarMapMinZoom = 3

internal const val CarMapMaxZoom = 19


/** Where every [CarMapState] starts: street level, car dead-centre. */
internal const val CarMapDefaultZoom = 15


/** The inclusive tile-index range [CarMap] currently needs fetched -- see its own
 *  `range`/`derivedStateOf` doc for why this is its own equatable value rather
 *  than four loose Ints computed inline. */
data class TileRange(val firstX: Int, val firstY: Int, val lastX: Int, val lastY: Int)


/** Null (the default) when no host has set one up. See [ExpandedMapState]'s own doc. */
internal val LocalExpandedMap = staticCompositionLocalOf<ExpandedMapState?> { null }
