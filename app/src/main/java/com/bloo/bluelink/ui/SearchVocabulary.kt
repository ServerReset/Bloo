package com.bloo.bluelink.ui

// --- Search vocabulary: stopwords, the synonym table and the token expander ---

internal val SearchStopwords = setOf(
    "for", "the", "of", "show", "me", "what", "whats", "is", "a", "an", "to",
    "car", "cars", "my", "s", "setting", "settings", "get", "in",
)

/**
 * Words people use for things this app calls something else. Each query token expands to itself
 * plus its synonyms (token -> app vocabulary), and an entry matching any form counts as matching
 * the token.
 */
internal val SearchSynonyms: Map<String, List<String>> = mapOf(
    "vibrate" to listOf("haptic"),
    "vibration" to listOf("haptic"),
    "buzz" to listOf("haptic"),
    "dark" to listOf("theme", "night"),
    "light" to listOf("theme"),
    "night" to listOf("theme", "dark"),
    "colour" to listOf("color", "palette"),
    "colours" to listOf("color", "palette"),
    "gps" to listOf("location"),
    "map" to listOf("location"),
    "where" to listOf("location"),
    "parked" to listOf("location"),
    "font" to listOf("text", "typeface"),
    "size" to listOf("scale", "text"),
    "bigger" to listOf("scale", "text"),
    "smaller" to listOf("scale", "text"),
    "mileage" to listOf("odometer", "miles"),
    "miles" to listOf("odometer"),
    "km" to listOf("odometer", "kilometres"),
    "range" to listOf("battery", "fuel"),
    "charge" to listOf("battery", "charging"),
    "percent" to listOf("battery", "charge"),
    "battery" to listOf("charge"),
    "plug" to listOf("charge", "charging"),
    "ac" to listOf("climate"),
    "heat" to listOf("climate"),
    "heater" to listOf("climate"),
    "cool" to listOf("climate"),
    "aircon" to listOf("climate"),
    "defrost" to listOf("climate", "defog"),
    "warm" to listOf("climate"),
    "preheat" to listOf("climate"),
    "seats" to listOf("seat"),
    "doors" to listOf("lock", "door"),
    "alarm" to listOf("horn"),
    "beep" to listOf("horn"),
    "flash" to listOf("lights"),
    "headlights" to listOf("lights"),
    "backup" to listOf("sync", "drive", "google"),
    "cloud" to listOf("sync", "drive"),
    "notify" to listOf("notification", "alert"),
    "notifications" to listOf("notification", "alert"),
    "password" to listOf("pin", "credentials", "login"),
    "signout" to listOf("logout", "sign"),
    "plate" to listOf("license", "registration"),
    "service" to listOf("maintenance"),
    "tyre" to listOf("tire"),
    "update" to listOf("version", "upgrade"),
    "language" to listOf("locale"),
    "units" to listOf("unit", "metric", "imperial"),
    "celsius" to listOf("metric", "unit"),
    "fahrenheit" to listOf("imperial", "unit"),
    // Natural words for the glass / look settings.
    "transparent" to listOf("glass", "clarity"),
    "transparency" to listOf("glass", "clarity"),
    "blur" to listOf("glass"),
    "blurry" to listOf("glass"),
    "frosted" to listOf("glass"),
    "liquid" to listOf("glass"),
    "bend" to listOf("glass", "ultra"),
    "edge" to listOf("glass", "ultra"),
    "refraction" to listOf("glass"),
    "animation" to listOf("aurora", "motion"),
    "animations" to listOf("aurora", "motion"),
    "moving" to listOf("aurora", "motion"),
    "background" to listOf("aurora"),
    "wallpaper" to listOf("aurora", "dynamic"),
    "accent" to listOf("color", "palette"),
    "hue" to listOf("color", "palette"),
    "palette" to listOf("color"),
    "tiny" to listOf("scale", "text"),
    "huge" to listOf("scale", "text"),
    "zoom" to listOf("scale", "text"),
    "readable" to listOf("scale", "text", "font"),
    "accessibility" to listOf("font", "scale", "text"),
    "saturation" to listOf("vibrancy"),
    "vivid" to listOf("vibrancy"),
    "fingerprint" to listOf("biometric"),
    "biometrics" to listOf("biometric"),
    "privacy" to listOf("biometric", "ai"),
    "gemini" to listOf("ai"),
    "assistant" to listOf("ai"),
    "reminder" to listOf("notification", "service"),
    "reminders" to listOf("notification", "service"),
    "alerts" to listOf("notification"),
    "alert" to listOf("notification"),
    "ping" to listOf("notification"),
    "sound" to listOf("haptic"),
    "annoying" to listOf("haptic", "notification"),
    "temp" to listOf("temperature", "climate"),
    "temperature" to listOf("climate", "unit"),
    "kilometers" to listOf("odometer", "kilometres"),
    "bluetooth" to listOf("autolock"),
    "grace" to listOf("autolock", "lock"),
    "delay" to listOf("autolock", "lock", "minutes"),
    "minutes" to listOf("alert", "notification"),
    "watch" to listOf("wear", "notification"),
    "smartwatch" to listOf("watch"),
    "shizuku" to listOf("install", "update"),
    "install" to listOf("update", "shizuku"),
    "shortcut" to listOf("shortcuts", "launcher"),
    "launcher" to listOf("shortcuts"),
)

/** A token and every form of it worth matching. */
internal fun expandToken(t: String): List<String> {
    val extra = SearchSynonyms[t] ?: return listOf(t)
    return buildList { add(t); addAll(extra) }
}

/** A runner command id as a sentence fragment, for the confirm card. */
internal fun aiCommandLabel(cmd: String): String = when (cmd) {
    "lock" -> "Lock"
    "unlock" -> "Unlock"
    "charge_on" -> "Start charging"
    "charge_off" -> "Stop charging"
    "lights" -> "Flash the lights on"
    "horn" -> "Sound the horn on"
    "climate_on" -> "Start climate on"
    "climate_off" -> "Stop climate on"
    else -> "Run on"
}
