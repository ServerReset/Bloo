package com.bloo.bluelink.data

import kotlinx.serialization.Serializable

/**
 * User-selectable typeface. GOOGLE_SANS uses Google Sans Flex - Google's officially open-sourced
 * (OFL) sibling of the proprietary Product Sans - so it ships legitimately as the geometric option.
 */@Serializable
enum class FontChoice { SYSTEM, ATKINSON, GOOGLE_SANS }
