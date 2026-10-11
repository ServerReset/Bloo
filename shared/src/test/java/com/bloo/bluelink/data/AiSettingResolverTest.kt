package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiSettingResolverTest {
    private val list = listOf(
        AiSettingResolver.Candidate("t0", "Haptic feedback", true, "vibration"),
        AiSettingResolver.Candidate("t1", "Aurora background", false),
    )

    @Test
    fun aCleanAnswerResolves() {
        assertEquals(AiSettingResolver.Resolution("t0", false), AiSettingResolver.parse("t0|off", list))
        assertEquals(AiSettingResolver.Resolution("t1", true), AiSettingResolver.parse("T1 | ON", list))
    }

    @Test
    fun proseAndQuotesAroundTheAnswerAreTolerated() {
        assertEquals(
            AiSettingResolver.Resolution("t0", false),
            AiSettingResolver.parse("Sure!\n`t0|off`\nHope that helps", list),
        )
        assertEquals(AiSettingResolver.Resolution("t1", true), AiSettingResolver.parse("\"id: t1\"|enabled.", list))
    }

    @Test
    fun anythingNotAListedIdIsRejected() {
        assertNull(AiSettingResolver.parse("t9|on", list))
        assertNull(AiSettingResolver.parse("delete_everything|on", list))
        assertNull(AiSettingResolver.parse("none", list))
        assertNull(AiSettingResolver.parse("", list))
    }

    @Test
    fun aValueThatIsNotOnOrOffIsRejected() {
        assertNull(AiSettingResolver.parse("t0|maybe", list))
        assertNull(AiSettingResolver.parse("t0|", list))
    }

    @Test
    fun thePromptListsEveryCandidateWithItsCurrentValue() {
        val p = AiSettingResolver.prompt("stop it vibrating", list)
        assertTrue("t0 | Haptic feedback | on | vibration" in p)
        assertTrue("t1 | Aurora background | off" in p)
        assertTrue(p.trimEnd().endsWith("Request: stop it vibrating"))
    }
}
