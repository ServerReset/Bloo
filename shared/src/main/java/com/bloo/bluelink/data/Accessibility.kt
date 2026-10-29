package com.bloo.bluelink.data

/**
 * The accessibility suite, decided in one place. Every accessibility choice the app reads comes out of [resolveAccessibility]: the
 * user's own switches, what Android's Accessibility settings ask for, and what Battery Saver overrides. A setting that is held at a
 * value other than the one the user chose comes back with its [OverrideReason], so the Settings card can grey it out and say why.
 *
 * Pure Kotlin, so it is tested without a device (see AccessibilityTest). Nothing here reads the system: the caller gathers
 * [AccessibilityDevice] once and passes it in.
 */

/** Why a setting is held at a value other than the one the user chose. */
enum class OverrideReason {
    /** Battery Saver is on: it turns reduce motion on, whatever the switch says. */
    BATTERY_SAVER,

    /** Android's own "Remove animations" is on: the app cannot turn animation back on. */
    SYSTEM_SETTING,
}

/** The switches the user set, as stored. A switch is only what they chose; [resolveAccessibility] decides what they get. */
data class AccessibilityChoices(
    val reduceMotion: Boolean = false,
    val keyboardShortcuts: Boolean = true,
)

/** What the device reports that the suite has to respect. Gathered once, by the caller. */
data class AccessibilityDevice(
    /** Android's "Remove animations" (animator duration scale 0). */
    val systemReduceMotion: Boolean = false,
    /** The system Battery Saver switch. */
    val batterySaver: Boolean = false,
    /** A screen reader (TalkBack or another) is on. Reported to the user; it changes no setting by itself. */
    val screenReaderOn: Boolean = false,
)

/** One switch as the app will use it: its [value], and the [override] holding it there, or null when the user's choice stands. */
data class ResolvedSwitch(val value: Boolean, val override: OverrideReason? = null) {
    /** True when a reason holds the switch, so the control is shown but cannot be changed. */
    val locked: Boolean get() = override != null
}

/** Every accessibility switch, resolved. The rest of the app reads [reduceMotion] and [keyboardShortcuts]. */
data class AccessibilityResolved(
    val reduceMotion: ResolvedSwitch,
    val keyboardShortcuts: ResolvedSwitch,
    val screenReaderOn: Boolean,
) {
    companion object {
        /** The resolution before the device has reported anything: every choice at its default, nothing overridden. */
        val Default: AccessibilityResolved = resolveAccessibility(AccessibilityChoices(), AccessibilityDevice())
    }
}

/**
 * The one rule set. In order of precedence:
 *
 * 1. Android's "Remove animations" wins outright: reduce motion is on and locked.
 * 2. Battery Saver comes next: reduce motion is on and locked. Its own choice is kept, so it comes back as the user left it when
 *    Battery Saver goes off.
 * 3. Otherwise the switch is the user's choice.
 *
 * Keyboard shortcuts are never overridden: a keyboard does not drain the battery, and it is what a desktop user relies on.
 */
fun resolveAccessibility(choices: AccessibilityChoices, device: AccessibilityDevice): AccessibilityResolved {
    val reduceMotion = when {
        device.systemReduceMotion -> ResolvedSwitch(true, OverrideReason.SYSTEM_SETTING)
        device.batterySaver -> ResolvedSwitch(true, OverrideReason.BATTERY_SAVER)
        else -> ResolvedSwitch(choices.reduceMotion)
    }
    return AccessibilityResolved(
        reduceMotion = reduceMotion,
        keyboardShortcuts = ResolvedSwitch(choices.keyboardShortcuts),
        screenReaderOn = device.screenReaderOn,
    )
}
