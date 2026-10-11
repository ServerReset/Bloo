package com.bloo.bluelink.data

/**
 * Turns a plain-language request ("stop it vibrating", "use kilometres", "make the text bigger")
 * into one of the app's settings and a value for it, with the model doing only the understanding and
 * this object doing every check. The model is shown a numbered list and may only answer `ID|value`:
 * the id must be in the list, and the value must be legal for that setting -- on/off for a switch, one
 * of its listed options for a picker, a number inside its bounds for a range. Anything else parses to
 * null, so a hallucinated setting or value cannot exist. Pure Kotlin, so the parsing is unit-tested
 * without a model.
 */
object AiSettingResolver {
    /** One choice of a picker: the stored [key] and the words a person would use for it. */
    data class Option(val key: String, val label: String = key)

    /**
     * One setting the model may choose. A switch has neither [options] nor bounds; a picker has
     * [options]; a range has [min] and [max]. [current] is the value now, as text.
     */
    data class Candidate(
        val id: String,
        val title: String,
        val current: String,
        val hint: String = "",
        val options: List<Option> = emptyList(),
        val min: Double? = null,
        val max: Double? = null,
    ) {
        val isChoice get() = options.isNotEmpty()
        val isRange get() = min != null && max != null
        val isToggle get() = !isChoice && !isRange

        companion object {
            fun toggle(id: String, title: String, on: Boolean, hint: String = "") =
                Candidate(id, title, if (on) "on" else "off", hint)
        }
    }

    /** [value] is already validated for the setting: "on"/"off", an option key, or a number as text. */
    data class Resolution(val id: String, val value: String) {
        val asBoolean: Boolean get() = value == "on"
        val asNumber: Double? get() = value.toDoubleOrNull()
    }

    private val ON = setOf("on", "true", "yes", "enable", "enabled", "1")
    private val OFF = setOf("off", "false", "no", "disable", "disabled", "0")

    fun prompt(query: String, candidates: List<Candidate>): String = buildString {
        append("Pick the one app setting the request is about, and the value to set it to.\n")
        append("Reply with ONE line in exactly this form: ID|VALUE  (or the single word none).\n")
        append("ID must be copied from the list. VALUE must be on or off for a switch, one of the listed\n")
        append("choices for a picker, or a number in the stated range. Use none if no setting fits.\n\n")
        append("Settings (id | name | currently | allowed values | related words):\n")
        candidates.forEach { c ->
            append(c.id).append(" | ").append(c.title).append(" | ").append(c.current).append(" | ")
            append(
                when {
                    c.isChoice -> "choices: " + c.options.joinToString(", ") { if (it.label == it.key) it.key else "${it.key} (${it.label})" }
                    c.isRange -> "number from ${trim(c.min!!)} to ${trim(c.max!!)}"
                    else -> "on or off"
                },
            )
            if (c.hint.isNotBlank()) append(" | ").append(c.hint)
            append('\n')
        }
        append("\nRequest: ").append(query.trim())
    }

    /** The model's reply as a [Resolution], or null if it is anything but a listed id with a legal value. */
    fun parse(reply: String, candidates: List<Candidate>): Resolution? {
        val byId = candidates.associateBy { it.id.lowercase() }
        // The model sometimes wraps the answer in prose or quotes; take the first line holding a bar.
        val line = reply.lineSequence().map { it.trim() }.firstOrNull { it.contains('|') } ?: return null
        val rawId = line.substringBefore('|').trim().trim('`', '"', '\'', '*').lowercase().removePrefix("id:").trim()
        val candidate = byId[rawId] ?: return null
        val rawValue = line.substringAfter('|').trim().trim('`', '"', '\'', '*', '.').substringBefore('|').trim()
        val value = when {
            candidate.isChoice -> candidate.options.firstOrNull {
                it.key.equals(rawValue, ignoreCase = true) || it.label.equals(rawValue, ignoreCase = true)
            }?.key
            candidate.isRange -> rawValue.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= candidate.min!! && it <= candidate.max!! }
                ?.let { trim(it) }
            else -> when (rawValue.lowercase()) {
                in ON -> "on"
                in OFF -> "off"
                else -> null
            }
        } ?: return null
        return Resolution(candidate.id, value)
    }

    private fun trim(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
