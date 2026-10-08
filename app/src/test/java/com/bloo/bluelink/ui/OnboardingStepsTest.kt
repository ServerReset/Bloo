package com.bloo.bluelink.ui

import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.platformOverridable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins for [buildOnboardingSteps]: which cards each deck holds and in what order. The order is
 * cheap to get wrong (add a card to the wrong branch and a user sees "tips, then setup") and
 * cheap to test, because the builder is pure over [Vehicle] data.
 */
class OnboardingStepsTest {

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

    private fun kinds(steps: List<OnboardingStep>) = steps.map { it.kind }

    private fun OnboardingStepKind.isCarCard() = name.startsWith("CAR_")

    private val firstRunHead = listOf(
        OnboardingStepKind.WELCOME, OnboardingStepKind.RESTORE, OnboardingStepKind.SETUP,
        OnboardingStepKind.LOOK, OnboardingStepKind.ALERTS,
    )
    private val tail = listOf(OnboardingStepKind.WATCH, OnboardingStepKind.TIPS, OnboardingStepKind.FEATURES)

    @Test
    fun firstRun_asksEveryCarForPowertrainHeadUnitAndSeats() {
        val steps = buildOnboardingSteps(OnboardingMode.FirstRun, listOf(usHyundai, usKia))
        assertEquals(
            firstRunHead +
                // A US Hyundai has a head unit to confirm; a US Kia does not.
                listOf(OnboardingStepKind.CAR_POWERTRAIN, OnboardingStepKind.CAR_PLATFORM, OnboardingStepKind.CAR_CLIMATE) +
                listOf(OnboardingStepKind.CAR_POWERTRAIN, OnboardingStepKind.CAR_CLIMATE) + tail,
            kinds(steps),
        )
        assertEquals(
            listOf(usHyundai.vin, usHyundai.vin, usHyundai.vin, usKia.vin, usKia.vin),
            steps.filter { it.kind.isCarCard() }.map { it.vin },
        )
    }

    @Test
    fun firstRun_skipsCarsARestoredBackupAlreadyConfigured() {
        val steps = buildOnboardingSteps(OnboardingMode.FirstRun, listOf(usHyundai, usKia), preConfiguredVins = setOf(usHyundai.vin))
        assertEquals(setOf(usKia.vin), steps.filter { it.kind.isCarCard() }.map { it.vin }.toSet())
    }

    @Test
    fun firstRun_withNoCars_hasNoCarCards() {
        assertEquals(firstRunHead + tail, kinds(buildOnboardingSteps(OnboardingMode.FirstRun, emptyList())))
    }

    @Test
    fun newCars_isJustTheCarCardsForEachNamedCar() {
        val steps = buildOnboardingSteps(OnboardingMode.NewCars(listOf(usKia.vin)), listOf(usHyundai, usKia))
        assertEquals(
            listOf(OnboardingStep(OnboardingStepKind.CAR_POWERTRAIN, usKia.vin), OnboardingStep(OnboardingStepKind.CAR_CLIMATE, usKia.vin)),
            steps,
        )
    }

    @Test
    fun replay_dropsRestoreButKeepsCars() {
        assertEquals(
            listOf(
                OnboardingStepKind.WELCOME, OnboardingStepKind.SETUP, OnboardingStepKind.LOOK, OnboardingStepKind.ALERTS,
                *buildOnboardingSteps(OnboardingMode.NewCars(listOf(usHyundai.vin, usKia.vin)), listOf(usHyundai, usKia)).map { it.kind }.toTypedArray(),
                OnboardingStepKind.WATCH, OnboardingStepKind.TIPS, OnboardingStepKind.FEATURES,
            ),
            kinds(buildOnboardingSteps(OnboardingMode.Replay, listOf(usHyundai, usKia))),
        )
    }

    @Test
    fun onlyTheTrailingInfoCardsSwipe() {
        OnboardingStepKind.entries.forEach { kind ->
            val expected = kind == OnboardingStepKind.TIPS || kind == OnboardingStepKind.FEATURES
            assertEquals(expected, kind.isInfoSwipe(), kind.name)
        }
    }
}
