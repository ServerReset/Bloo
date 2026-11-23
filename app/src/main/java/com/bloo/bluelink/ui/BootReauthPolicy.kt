package com.bloo.bluelink.ui

/**
 * The decisions of the sign-in after a phone restart, apart from the prompts and stores they act on, so the rules can be tested:
 * the restart sign-in takes typed details only (never a fingerprint), and once they are in, the person is asked for their
 * fingerprint or screen lock at most ONCE before the app opens.
 */
internal object BootReauthPolicy {
    /** What happens once the typed details have been checked. */
    enum class Step {
        /** The details are wrong (and the vault could read them, so that is certain). */
        Reject,

        /** Nothing is sealed and no app lock is on: open straight away, with no prompt at all. */
        Open,

        /** One prompt (the strong kind, which unseals the vault), then open. */
        Prompt,

        /** A prompt is needed but cannot be shown. */
        CannotPrompt,
    }

    fun afterDetails(detailsMatch: Boolean, vaultLocked: Boolean, appLockArmed: Boolean, canPrompt: Boolean): Step = when {
        !detailsMatch && !vaultLocked -> Step.Reject
        !vaultLocked && !appLockArmed -> Step.Open
        !canPrompt -> Step.CannotPrompt
        else -> Step.Prompt
    }

    /** After the one prompt: open only if it passed and the details match (checked again if the vault was sealed before it). */
    fun opensAfterPrompt(passed: Boolean, detailsMatch: Boolean): Boolean = passed && detailsMatch

    /** The restart sign-in offers no fingerprint: the phone's own unlock is not proof of the account's details. */
    fun offersFingerprint(canUseBiometrics: Boolean, bootReauth: Boolean): Boolean = canUseBiometrics && !bootReauth

    /**
     * Whether a cold start puts the app lock up. Not straight after the restart sign-in's own prompt ([justConfirmed]), and not in the
     * first minutes after a boot once the device is unlocked: either would be a second prompt for the same person.
     */
    fun coldStartLocks(lockOn: Boolean, justBooted: Boolean, deviceLocked: Boolean, justConfirmed: Boolean): Boolean =
        lockOn && !(justBooted && !deviceLocked) && !justConfirmed
}
