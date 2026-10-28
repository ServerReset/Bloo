package com.bloo.bluelink.data

/**
 * The one spelling of each car command's identifier. The parsers, the search index, the runner and the per-brand APIs all name a command by
 * one of these, so the same command cannot drift into two spellings. Lives in :shared because the brand APIs dispatch on it too.
 */
object CarCommandIds {
    const val LOCK = "lock"
    const val UNLOCK = "unlock"
    const val HORN = "horn"
    const val LIGHTS = "lights"
    const val LOCATE = "locate"
    const val TRUNK = "trunk"
    const val DEFROST = "defrost"
    const val CLIMATE_ON = "climate_on"
    const val CLIMATE_OFF = "climate_off"
    const val CLIMATE_SMART = "climate_smart"
    const val CHARGE_ON = "charge_on"
    const val CHARGE_OFF = "charge_off"
}
