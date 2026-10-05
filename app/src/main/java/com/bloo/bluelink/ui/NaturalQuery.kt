package com.bloo.bluelink.ui

/** Turns a sentence somebody typed into the handful of words worth matching. */

/**
 * Phrases people say that map onto what the index contains. Applied in order, to the lowercased
 * query.
 */
internal val NaturalPhrases: List<Pair<Regex, String>> = listOf(
    Regex("\\b(dark|night) ?mode\\b") to "theme dark",
    Regex("\\blight ?mode\\b") to "theme light",
    Regex("\\bbattery ?saver\\b") to "battery",
    Regex("\\b(too )?(small|tiny)\\b.*\\b(text|screen|words|font|writing)\\b|\\b(text|screen|words|font|writing)\\b.*\\b(too )?(small|tiny)\\b") to "text scale bigger",
    Regex("\\b(can'?t|cannot|hard to|trouble) (read|see)\\b") to "text scale bigger font accessibility",
    Regex("\\b(too )?(big|huge|large)\\b.*\\b(text|screen|words|font)\\b") to "text scale smaller",
    Regex("\\bsee[- ]?through\\b") to "transparent glass",
    Regex("\\b(frosted|frosty|liquid) glass\\b") to "glass",
    Regex("\\bhow (far|many miles|much range)\\b") to "range",
    Regex("\\bwhere('?s| is| did i park| am i parked)\\b") to "location",
    Regex("\\bwhat('?s| is) (the )?(charge|battery)( level)?\\b") to "battery",
    Regex("\\b(log|sign) ?(me )?out\\b") to "signout",
    Regex("\\b(lock|unlock) (it )?automatically\\b") to "autolock",
    Regex("\\bwhen i (walk|leave|go) away\\b") to "autolock",
    Regex("\\bfinger ?print\\b|\\bface (id|unlock)\\b") to "biometric",
    Regex("\\bstop (the )?(buzz|buzzing|vibrating|vibrations?|shaking)\\b") to "haptic",
    Regex("\\bstop (the )?(notifications?|alerts?|pings?)\\b") to "notification",
    Regex("\\bmake (it )?(pretty|prettier|nicer)\\b") to "theme color glass",
)

/**
 * Conversational scaffolding: dropped when a real subject remains, kept when it is all there is.
 */
internal val NaturalFiller: Set<String> = setOf(
    "how", "do", "does", "did", "can", "could", "would", "should", "will", "you", "your", "i", "im", "ive",
    "we", "it", "its", "this", "that", "there", "way", "please", "pls", "want", "wanna", "need", "like",
    "let", "lets", "help", "find", "where", "which", "when", "why", "who", "and", "or", "be", "am",
    "with", "from", "about", "so", "just", "really", "very", "too", "much", "all", "some", "any",
    "turn", "switch", "toggle", "enable", "disable", "activate", "deactivate", "stop", "start",
    "on", "off", "make", "change", "set", "adjust", "update", "edit", "modify", "go", "open", "see",
    "look", "looks", "feel", "feels", "when", "if", "then", "got", "have", "has", "had", "rid",
)

/**
 * Light plural trim so "alerts" finds "alert" and "vibrations" finds "vibration"; words with a
 * synonym keep their form.
 */
private fun trimPlural(t: String): String =
    if (t.length > 3 && t.endsWith("s") && !t.endsWith("ss") && t !in SearchSynonyms) t.dropLast(1) else t

/**
 * The words of [query] worth matching, in order. Falls back in two steps so a short query never
 * matches nothing by being too polite: if scaffolding removal would leave no words, only stopwords
 * go; and a query that is entirely stopwords keeps all.
 */
internal fun searchTokens(query: String): List<String> {
    var q = query.lowercase().replace("'", "")
    for ((rx, to) in NaturalPhrases) q = rx.replace(q, " $to ")
    val raw = q.split(RxSearchTokens).filter { it.isNotBlank() }
    val real = raw.filter { it !in SearchStopwords && it !in NaturalFiller }
    val chosen = real.ifEmpty { raw.filter { it !in SearchStopwords } }.ifEmpty { raw }
    return chosen.map(::trimPlural).distinct()
}
