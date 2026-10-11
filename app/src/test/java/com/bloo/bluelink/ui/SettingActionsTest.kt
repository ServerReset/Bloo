package com.bloo.bluelink.ui

import com.bloo.bluelink.data.AiSettingResolver
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the contract between the settings the app can change by plain-language request
 * ([SettingActions]) and the strict parser that guards them ([AiSettingResolver]): whatever a setting
 * currently reads as, and every value it allows, must be something the resolver accepts, so a legal
 * answer from the model can never be thrown away and an illegal one can never get through.
 */
class SettingActionsTest {
    private val ctx = SettingContext(SettingsStore.Appearance(), SettingsStore.NotificationPrefs(), UiState())
    private val car = Vehicle(
        vin = "V1", regId = "reg-V1", name = "Daisy", model = "Ioniq 5", generation = "5", brandIndicator = "H", isEv = true,
    )

    private fun everyAction(): List<SettingAction> {
        val state = UiState(vehicles = listOf(car, car.copy(vin = "V2", regId = "reg-V2", name = "Lana")))
        return availableSettingActions(state, SettingsStore.Appearance())
    }

    @Test
    fun idsAndTitlesAreUnique() {
        val all = everyAction()
        assertEquals(all.size, all.map { it.id }.toSet().size, "duplicate id")
        assertEquals(all.size, all.map { it.title }.toSet().size, "duplicate title")
    }

    @Test
    fun whatEachSettingReadsAsIsLegalForItsKind() {
        val state = UiState(vehicles = listOf(car, car.copy(vin = "V2", regId = "reg-V2", name = "Lana")))
        val c = SettingContext(SettingsStore.Appearance(), SettingsStore.NotificationPrefs(), state)
        everyAction().forEach { a ->
            val v = a.read(c)
            when {
                a.isAction -> assertEquals("", v, a.id)
                a.text -> assertTrue(v.isNotEmpty(), a.id)
                a.options.isNotEmpty() -> assertTrue(a.options.any { it.key == v }, "${a.id} reads '$v', not one of ${a.options.map { it.key }}")
                a.min != null -> assertTrue(v.toDouble() in a.min!!..a.max!!, "${a.id} reads $v outside ${a.min}..${a.max}")
                else -> assertTrue(v == "on" || v == "off", "${a.id} reads '$v'")
            }
        }
    }

    @Test
    fun everyLegalAnswerParsesAndEveryIllegalOneDoesNot() {
        val state = UiState(vehicles = listOf(car))
        val c = SettingContext(SettingsStore.Appearance(), SettingsStore.NotificationPrefs(), state)
        val actions = availableSettingActions(state, SettingsStore.Appearance())
        val candidates = actions.map { it.candidate(c) }
        actions.forEach { a ->
            when {
                a.text -> assertEquals(AiSettingResolver.Resolution(a.id, "San Jose"), AiSettingResolver.parse("${a.id}|San Jose", candidates))
                a.options.isNotEmpty() -> a.options.forEach { o ->
                    assertEquals(AiSettingResolver.Resolution(a.id, o.key), AiSettingResolver.parse("${a.id}|${o.key}", candidates), "${a.id}|${o.key}")
                }
                a.min != null -> {
                    assertNotNull(AiSettingResolver.parse("${a.id}|${a.min}", candidates), "${a.id} lower bound")
                    assertNotNull(AiSettingResolver.parse("${a.id}|${a.max}", candidates), "${a.id} upper bound")
                    assertEquals(null, AiSettingResolver.parse("${a.id}|${a.max!! + 1}", candidates), "${a.id} above range")
                    assertEquals(null, AiSettingResolver.parse("${a.id}|${a.min!! - 1}", candidates), "${a.id} below range")
                }
                else -> {
                    assertEquals(AiSettingResolver.Resolution(a.id, "on"), AiSettingResolver.parse("${a.id}|on", candidates))
                    assertEquals(AiSettingResolver.Resolution(a.id, "off"), AiSettingResolver.parse("${a.id}|off", candidates))
                }
            }
        }
    }

    @Test
    fun thePromptListsEverySettingTheModelMayPick() {
        val state = UiState(vehicles = listOf(car))
        val c = SettingContext(SettingsStore.Appearance(), SettingsStore.NotificationPrefs(), state)
        val candidates = availableSettingActions(state, SettingsStore.Appearance()).map { it.candidate(c) }
        val prompt = AiSettingResolver.prompt("make it dark", candidates)
        candidates.forEach { assertTrue("${it.id} | ${it.title} |" in prompt, it.id) }
    }

    @Test
    fun aDeviceWithoutGeminiNanoIsNeverOfferedTheAiSwitches() {
        val titles = availableSettingActions(UiState(aiSupported = false), SettingsStore.Appearance()).map { it.title }
        assertTrue("On-device AI" !in titles, "the AI switch must not exist without Gemini Nano")
        assertTrue("Summarize when I open the app" !in titles)
        val withNano = availableSettingActions(UiState(aiSupported = true, aiEnabled = true), SettingsStore.Appearance()).map { it.title }
        assertTrue("On-device AI" in withNano && "Summarize when I open the app" in withNano)
    }

    @Test
    fun theAutoSummaryChoiceIsOnlyOfferedOnceAiIsOn() {
        val off = availableSettingActions(UiState(aiSupported = true, aiEnabled = false), SettingsStore.Appearance()).map { it.title }
        assertTrue("Summarize when I open the app" !in off)
    }

    @Test
    fun thePinnedPebbleChoiceNamesOnlyPebblesTheCarHas() {
        val state = UiState(vehicles = listOf(car))
        val pinned = availableSettingActions(state, SettingsStore.Appearance()).first { it.title.startsWith("Pinned pebble") }
        val keys = pinned.options.map { it.key }
        assertTrue("none" in keys && "summary" !in keys && "controls" !in keys && "update" !in keys)
        assertEquals("none", pinned.read(SettingContext(SettingsStore.Appearance(), SettingsStore.NotificationPrefs(), state)))
    }
}
