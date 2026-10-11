package com.bloo.bluelink.data

/**
 * Turns a plain-language request ("stop it vibrating", "turn the background animation off") into one
 * of the app's on/off settings, with the model doing only the understanding and this object doing
 * every check. The model is shown a numbered list and may only answer with an id from it, so a
 * hallucinated setting cannot exist: anything that is not exactly an id plus on/off parses to null.
 * Pure Kotlin, so the parsing is unit-tested without a model.
 */
object AiSettingResolver {
    /** One setting the model may choose. [hint] is the extra words that also find it in search. */
    data class Candidate(val id: String, val title: String, val on: Boolean, val hint: String = "")

    data class Resolution(val id: String, val value: Boolean)

    private val ON = setOf("on", "true", "yes", "enable", "enabled", "1")
    private val OFF = setOf("off", "false", "no", "disable", "disabled", "0")

    fun prompt(query: String, candidates: List<Candidate>): String = buildString {
        append("Pick the one app setting the request is about and whether it should be on or off.\n")
        append("Reply with ONE line in exactly this form: ID|on  or  ID|off  (or the single word none).\n")
        append("ID must be copied from the list. Use none if no setting fits.\n\n")
        append("Settings (id | name | currently | related words):\n")
        candidates.forEach {
            append(it.id).append(" | ").append(it.title).append(" | ")
                .append(if (it.on) "on" else "off")
            if (it.hint.isNotBlank()) append(" | ").append(it.hint)
            append('\n')
        }
        append("\nRequest: ").append(query.trim())
    }

    /** The model's reply as a [Resolution], or null if it is anything but a listed id plus on/off. */
    fun parse(reply: String, candidates: List<Candidate>): Resolution? {
        val ids = candidates.associateBy { it.id.lowercase() }
        // The model sometimes wraps the answer in prose or quotes; take the first line holding a bar.
        val line = reply.lineSequence().map { it.trim() }.firstOrNull { it.contains('|') } ?: return null
        val rawId = line.substringBefore('|').trim().trim('`', '"', '\'', '*').lowercase().removePrefix("id:").trim()
        val rawValue = line.substringAfter('|').trim().trim('`', '"', '\'', '*', '.').lowercase()
            .substringBefore('|').trim()
        val id = ids[rawId]?.id ?: return null
        val value = when (rawValue) {
            in ON -> true
            in OFF -> false
            else -> return null
        }
        return Resolution(id, value)
    }
}
