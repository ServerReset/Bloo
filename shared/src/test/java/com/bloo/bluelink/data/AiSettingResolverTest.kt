package com.bloo.bluelink.data

import com.bloo.bluelink.data.AiSettingResolver.Candidate
import com.bloo.bluelink.data.AiSettingResolver.Option
import com.bloo.bluelink.data.AiSettingResolver.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiSettingResolverTest {
    private val list = listOf(
        Candidate.toggle("t0", "Haptic feedback", true, "vibration"),
        Candidate.toggle("t1", "Aurora background", false),
        Candidate("c0", "Units", "imperial", options = listOf(Option("imperial", "Imperial"), Option("metric", "Metric / kilometres"))),
        Candidate("r0", "Text scale", "1", min = 0.85, max = 1.3),
        Candidate("r1", "Door alert delay", "5", min = 1.0, max = 120.0),
        Candidate("x0", "Weather location", "none", text = true),
        Candidate("a0", "Reset pebble order", "", options = listOf(Option("run", "Reset"))),
    )

    @Test
    fun aCleanSwitchAnswerResolves() {
        assertEquals(Resolution("t0", "off"), AiSettingResolver.parse("t0|off", list))
        assertEquals(Resolution("t1", "on"), AiSettingResolver.parse("T1 | ON", list))
        assertTrue(AiSettingResolver.parse("t1|on", list)!!.asBoolean)
    }

    @Test
    fun proseAndQuotesAroundTheAnswerAreTolerated() {
        assertEquals(Resolution("t0", "off"), AiSettingResolver.parse("Sure!\n`t0|off`\nHope that helps", list))
        assertEquals(Resolution("t1", "on"), AiSettingResolver.parse("\"id: t1\"|enabled.", list))
    }

    @Test
    fun aPickerAcceptsItsKeysOrLabelsAndNothingElse() {
        assertEquals(Resolution("c0", "metric"), AiSettingResolver.parse("c0|metric", list))
        assertEquals(Resolution("c0", "metric"), AiSettingResolver.parse("c0|Metric / kilometres", list))
        assertNull(AiSettingResolver.parse("c0|furlongs", list))
        assertNull(AiSettingResolver.parse("c0|on", list))
    }

    @Test
    fun aRangeMustBeANumberInsideItsBounds() {
        assertEquals(Resolution("r0", "1.2"), AiSettingResolver.parse("r0|1.2", list))
        assertEquals(Resolution("r1", "30"), AiSettingResolver.parse("r1|30", list))
        assertEquals(30.0, AiSettingResolver.parse("r1|30", list)!!.asNumber)
        assertNull(AiSettingResolver.parse("r0|5", list))
        assertNull(AiSettingResolver.parse("r1|0", list))
        assertNull(AiSettingResolver.parse("r1|lots", list))
        assertNull(AiSettingResolver.parse("r1|NaN", list))
    }

    @Test
    fun anythingNotAListedIdIsRejected() {
        assertNull(AiSettingResolver.parse("t9|on", list))
        assertNull(AiSettingResolver.parse("delete_everything|on", list))
        assertNull(AiSettingResolver.parse("none", list))
        assertNull(AiSettingResolver.parse("", list))
    }

    @Test
    fun aSwitchValueThatIsNotOnOrOffIsRejected() {
        assertNull(AiSettingResolver.parse("t0|maybe", list))
        assertNull(AiSettingResolver.parse("t0|", list))
    }

    @Test
    fun thePromptDescribesEveryKindOfSetting() {
        val p = AiSettingResolver.prompt("make the text bigger", list)
        assertTrue("t0 | Haptic feedback | on | on or off | vibration" in p)
        assertTrue("c0 | Units | imperial | choices: imperial (Imperial), metric (Metric / kilometres)" in p)
        assertTrue("r0 | Text scale | 1 | number from 0.85 to 1.3" in p)
        assertTrue(p.trimEnd().endsWith("Request: make the text bigger"))
    }

    @Test
    fun aFreeTextSettingTakesShortTextOnly() {
        assertEquals(Resolution("x0", "San Jose"), AiSettingResolver.parse("x0|San Jose", list))
        assertNull(AiSettingResolver.parse("x0|", list))
        assertNull(AiSettingResolver.parse("x0|" + "a".repeat(200), list))
    }

    @Test
    fun anActionIsAPickerWithOneOption() {
        assertEquals(Resolution("a0", "run"), AiSettingResolver.parse("a0|run", list))
        assertNull(AiSettingResolver.parse("a0|on", list))
    }
}
