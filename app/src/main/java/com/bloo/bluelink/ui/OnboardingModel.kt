package com.bloo.bluelink.ui

import com.bloo.bluelink.data.platformOverridable

internal enum class OnboardingStepKind {
    WELCOME, RESTORE, SETUP, LOOK, ALERTS, WATCH,
    /**
     * The three per-car questions. Each one blocks until it is answered: see [needsConfirmation].
     */
    CAR_POWERTRAIN, CAR_PLATFORM, CAR_CLIMATE,
    TIPS, FEATURES,
}

/** The trailing, information-only cards the deck lets you swipe through instead of tapping Next. */
internal fun OnboardingStepKind.isInfoSwipe(): Boolean =
    this == OnboardingStepKind.TIPS || this == OnboardingStepKind.FEATURES

internal data class OnboardingStep(val kind: OnboardingStepKind, val vin: String? = null)

/** Which deck of cards is showing. One component runs all three. */
internal sealed interface OnboardingMode {
    /** A device's first run: everything, ending in the app. */
    data object FirstRun : OnboardingMode

    /** Cars detected after first run, each needing its own setup card; nothing else. */
    data class NewCars(val vins: List<String>) : OnboardingMode

    /** Summoned again from Settings: the general cards, minus restore and the per-car questions. */
    data object Replay : OnboardingMode
}

/**
 * Whether notifications block leaving the [OnboardingStepKind.SETUP] card: required on API 33+
 * (POST_NOTIFICATIONS exists) until the permission is granted. Pure so the gate can be pinned by a
 * plain JVM test without a running Activity or a permission dialog.
 */
internal fun notifRequiredOnSetup(
    onSetup: Boolean,
    notificationsSupported: Boolean,
    notifGranted: Boolean,
): Boolean = onSetup && notificationsSupported && !notifGranted

/**
 * Whether a lock blocks leaving the SETUP card. Exactly ONE mechanism is required: biometrics when
 * the device has them enrolled, otherwise a PIN. Pure, so the truth table is testable.
 */
internal fun lockRequiredOnSetup(
    onSetup: Boolean,
    canBio: Boolean,
    biometricLock: Boolean,
    appPinSet: Boolean,
): Boolean = onSetup && (if (canBio) !biometricLock else !appPinSet)

/** The SETUP card blocks Next while [notifRequiredOnSetup] or [lockRequiredOnSetup] is true. */
internal fun setupIsBlocked(
    onSetup: Boolean,
    notificationsSupported: Boolean,
    notifGranted: Boolean,
    canBio: Boolean,
    biometricLock: Boolean,
    appPinSet: Boolean,
): Boolean =
    notifRequiredOnSetup(onSetup, notificationsSupported, notifGranted) ||
        lockRequiredOnSetup(onSetup, canBio, biometricLock, appPinSet)

/**
 * The cards in a deck, in order. - First run: welcome, restore-from-sync, setup (notifications,
 * lock, Drive), look and feel, which alerts to get, then three cards for each car that isn't
 * already configured (powertrain, head unit where it applies, seats and steering wheel), the watch,
 * tips and the features card. - New cars: just those three cards for each car in
 * [OnboardingMode.NewCars.vins]. - Replay: welcome, setup, look, alerts, every car's cards
 * (ungated, to revisit answers), watch, tips, features. [preConfiguredVins] skips a car's card on
 * first run: a backup restored on the RESTORE card can bring in real powertrain/seat config for a
 * car already set up on another device.
 */
internal fun buildOnboardingSteps(
    mode: OnboardingMode,
    vehicles: List<com.bloo.bluelink.data.Vehicle>,
    preConfiguredVins: Set<String> = emptySet(),
): List<OnboardingStep> = buildList {
    fun carCards(v: com.bloo.bluelink.data.Vehicle) {
        add(OnboardingStep(OnboardingStepKind.CAR_POWERTRAIN, v.vin))
        // Only Hyundai/Genesis US cars have a head-unit generation to confirm.
        if (v.platformOverridable) add(OnboardingStep(OnboardingStepKind.CAR_PLATFORM, v.vin))
        add(OnboardingStep(OnboardingStepKind.CAR_CLIMATE, v.vin))
    }
    when (mode) {
        is OnboardingMode.NewCars -> vehicles.filter { it.vin in mode.vins }.forEach(::carCards)
        OnboardingMode.Replay -> {
            add(OnboardingStep(OnboardingStepKind.WELCOME))
            add(OnboardingStep(OnboardingStepKind.SETUP))
            add(OnboardingStep(OnboardingStepKind.LOOK))
            add(OnboardingStep(OnboardingStepKind.ALERTS))
            vehicles.forEach(::carCards)
            add(OnboardingStep(OnboardingStepKind.WATCH))
            add(OnboardingStep(OnboardingStepKind.TIPS))
            add(OnboardingStep(OnboardingStepKind.FEATURES))
        }
        OnboardingMode.FirstRun -> {
            add(OnboardingStep(OnboardingStepKind.WELCOME))
            add(OnboardingStep(OnboardingStepKind.RESTORE))
            add(OnboardingStep(OnboardingStepKind.SETUP))
            add(OnboardingStep(OnboardingStepKind.LOOK))
            add(OnboardingStep(OnboardingStepKind.ALERTS))
            vehicles.filter { it.vin !in preConfiguredVins }.forEach(::carCards)
            add(OnboardingStep(OnboardingStepKind.WATCH))
            add(OnboardingStep(OnboardingStepKind.TIPS))
            add(OnboardingStep(OnboardingStepKind.FEATURES))
        }
    }
}

internal fun replayMode(mode: OnboardingMode) = mode == OnboardingMode.Replay

/** Index of the SETUP card in a first-run deck (welcome, restore, setup). */
internal const val FIRST_RUN_SETUP_INDEX = 2
