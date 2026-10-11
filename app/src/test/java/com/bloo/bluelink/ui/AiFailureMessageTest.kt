package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class AiFailureMessageTest {
    @Test
    fun backgroundUseGetsAPlainFix() {
        val e = RuntimeException("AICore failed with error type 2-INFERENCE_ERROR and error code 30-UNKNOWN: Inference failed.")
        assertTrue("on screen" in aiFailureMessage(e, "Daisy"))
    }

    @Test
    fun busyOrOutOfQuotaSaysToWait() {
        assertTrue("busy" in aiFailureMessage(RuntimeException("error code 9-BUSY"), "Daisy"))
        assertTrue("Daisy" in aiFailureMessage(RuntimeException("error code 27-QUOTA"), "Daisy"))
    }

    @Test
    fun anythingElseIsTheGenericSentenceWithoutAicoreDetail() {
        val m = aiFailureMessage(IllegalStateException("kaboom"), "Daisy")
        assertTrue(m == "Couldn't summarize Daisy right now." && "kaboom" !in m)
    }
}
