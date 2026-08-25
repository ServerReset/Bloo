package com.bloo.bluelink.ui

import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.platformOverridable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins for the onboarding wizard's step-construction functions
 * [buildSetupPages] and [buildOnboardingSteps].
 *
 * Both were private to Screens.kt for most of their lives, so their exact
 * sequencing -- the order a first-run user walks through, and which pages
 * even exist -- was only ever checked by eye. A wrong page ORDER here is very
 * cheap to make (add a page to the wrong list and the user sees "steering,
 * then powertrain") and very cheap to test, because both functions are pure
 * over [Vehicle] data with no Compose or Android dependency.
 *
 * The platform-overridability rule ([platformOverridable]) is the one
 * data-driven branch: a PLATFORM page exists only for Hyundai/Genesis US
 * cars, since those are the only ones whose API even reports a head-unit
 * generation. Canada/EU cars -- which the rule excludes -- are the case the
 * tests must keep honest: those users would get a choose-your-generation
 * checkbox that changes nothing.
 */
class WizardFlowTest {

    private fun vehicle(
        vin: String,
        indicator: String,
        generation: String = "5",
    ) = Vehicle(
        vin = vin, regId = "reg-$vin", name = "Car $vin",
        model = "Model", generation = generation, brandIndicator = indicator,
        isEv = false,
    )

    private val usHyundai = vehicle("V1", "H")
    private val usGenesis = vehicle("V2", "G")
    private val usKia = vehicle("V3", "K")
    private val canHyundai = vehicle("V4", Brand.HYUNDAI_CA.code)
    private val euHyundai = vehicle("V5", Brand.HYUNDAI_EU.code)

    // --- platformOverridable itself (the rule the pages key off) -------------

    @Test
    fun platformOverridable_onlyTrueForUsHyundaiGenesis() {
        assertEquals(true, usHyundai.platformOverridable)
        assertEquals(true, usGenesis.platformOverridable)
        assertEquals(false, usKia.platformOverridable)
        assertEquals(false, canHyundai.platformOverridable)
        assertEquals(false, euHyundai.platformOverridable)
    }

    // --- buildSetupPages ----------------------------------------------------

    @Test
    fun usHyundaiGetsThePlatformPage_inTheMiddle() {
        val kinds = buildSetupPages(listOf(usHyundai)).map { it.kind }
        assertEquals(
            listOf(
                WizardStepKind.POWERTRAIN,
                WizardStepKind.PLATFORM,   // only for overridable brands
                WizardStepKind.SEATS,
                WizardStepKind.STEERING,
            ),
            kinds,
        )
    }

    @Test
    fun usKiaSkipsThePlatformPage() {
        val kinds = buildSetupPages(listOf(usKia)).map { it.kind }
        assertEquals(
            listOf(
                WizardStepKind.POWERTRAIN,
                WizardStepKind.SEATS,
                WizardStepKind.STEERING,
            ),
            kinds,
        )
    }

    @Test
    fun canadaAndEuropeAlsoSkipThePlatformPage() {
        assertEquals(
            listOf(
                WizardStepKind.POWERTRAIN,
                WizardStepKind.SEATS,
                WizardStepKind.STEERING,
            ),
            buildSetupPages(listOf(canHyundai)).map { it.kind },
        )
        assertEquals(
            listOf(
                WizardStepKind.POWERTRAIN,
                WizardStepKind.SEATS,
                WizardStepKind.STEERING,
            ),
            buildSetupPages(listOf(euHyundai)).map { it.kind },
        )
    }

    @Test
    fun multipleVehiclesInterleaveInGivenOrder() {
        val pages = buildSetupPages(listOf(usKia, usHyundai))
        // Each page carries its own vin; the runs are 3 for the Kia then 4 for
        // the Hyundai -- each car's pages come as its own run (not interleaved
        // across cars), so the user finishes one car before starting the next.
        assertEquals(
            listOf(usKia.vin, usKia.vin, usKia.vin, usHyundai.vin, usHyundai.vin, usHyundai.vin, usHyundai.vin),
            pages.map { it.vin },
        )
        assertEquals(
            listOf(
                WizardStepKind.POWERTRAIN, WizardStepKind.SEATS, WizardStepKind.STEERING,
                WizardStepKind.POWERTRAIN, WizardStepKind.PLATFORM, WizardStepKind.SEATS, WizardStepKind.STEERING,
            ),
            pages.map { it.kind },
        )
    }

    @Test
    fun emptyVehicleList_yieldsNoPages() {
        assertEquals(emptyList(), buildSetupPages(emptyList()))
    }

    // --- buildOnboardingSteps ----------------------------------------------

    @Test
    fun steps_bookendIntroAndCrashCourse_aroundEachNewCar() {
        val steps = buildOnboardingSteps(listOf(usHyundai, usKia))
        assertEquals(
            listOf(
                OnboardingStepKind.INTRO,
                OnboardingStepKind.SETUP,
                OnboardingStepKind.CAR,
                OnboardingStepKind.CAR,
                OnboardingStepKind.CRASH_COURSE,
            ),
            steps.map { it.kind },
        )
        assertEquals(listOf(usHyundai.vin, usKia.vin), steps.filter { it.kind == OnboardingStepKind.CAR }.map { it.vin })
    }

    @Test
    fun preConfiguredVins_skipTheirCarStep() {
        val steps = buildOnboardingSteps(listOf(usHyundai, usKia), preConfiguredVins = setOf(usHyundai.vin))
        assertEquals(
            listOf(
                OnboardingStepKind.INTRO,
                OnboardingStepKind.SETUP,
                OnboardingStepKind.CAR,
                OnboardingStepKind.CRASH_COURSE,
            ),
            steps.map { it.kind },
        )
        assertEquals(listOf(usKia.vin), steps.filter { it.kind == OnboardingStepKind.CAR }.map { it.vin })
    }

    @Test
    fun noVehicles_skipsCarSteps() {
        val steps = buildOnboardingSteps(emptyList())
        assertEquals(
            listOf(
                OnboardingStepKind.INTRO,
                OnboardingStepKind.SETUP,
                OnboardingStepKind.CRASH_COURSE,
            ),
            steps.map { it.kind },
        )
    }
}
