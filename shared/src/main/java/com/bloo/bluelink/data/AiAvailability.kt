package com.bloo.bluelink.data

/**
 * What the on-device AI probe found, kept as data so it can be shown (Debug card, activity log),
 * retried and unit-tested. The old probe collapsed every outcome, including a crash, into
 * "unsupported", which left no way to tell "this phone has no Gemini Nano" from "the check failed".
 */
sealed interface AiAvailability {
    /** The model is on the device and ready. */
    data object Available : AiAvailability

    /** Supported, but the model must be downloaded first (it downloads on first use). */
    data object Downloadable : AiAvailability

    /** Supported, and the model is downloading now. */
    data object Downloading : AiAvailability

    /** The system answered that this device or build cannot run the feature. */
    data object Unavailable : AiAvailability

    /** The check itself failed (a missing service, an exception): [reason] is the exception, not a guess. */
    data class Error(val reason: String) : AiAvailability

    /** Whether the AI features may be offered: the model is, or will be, usable here. */
    val usable: Boolean get() = this is Available || this is Downloadable || this is Downloading

    /** One plain line for the Debug card and the log. */
    fun describe(): String = when (this) {
        Available -> "Available"
        Downloadable -> "Supported, model downloads on first use"
        Downloading -> "Supported, model downloading"
        Unavailable -> "Not available on this device"
        is Error -> "Check failed: $reason"
    }
}

/** When to look again after a probe that did not find a usable model. */
object AiProbePolicy {
    /** Waits between attempts: AICore can still be starting, or the first call can race the foreground. */
    private val backoffMs = longArrayOf(3_000L, 15_000L, 60_000L)

    /** The least gap between probes triggered by coming back to the app. */
    const val FOREGROUND_RECHECK_MS = 60_000L

    /**
     * How long to wait before attempt [attempt] + 1, or null to stop. A usable result stops the
     * loop; a definite "Unavailable" is retried only gently (a model can arrive with an update),
     * while an Error is retried the same way since it may be transient.
     */
    fun nextDelayMs(result: AiAvailability, attempt: Int): Long? =
        if (result.usable) null else backoffMs.getOrNull(attempt)

    /** Whether returning to the foreground should probe again. */
    fun shouldRecheckOnForeground(last: AiAvailability?, lastProbeMs: Long, nowMs: Long): Boolean =
        last?.usable != true && nowMs - lastProbeMs >= FOREGROUND_RECHECK_MS
}
